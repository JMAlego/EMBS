package embs;

import com.ibm.saguaro.logger.Logger;
import com.ibm.saguaro.system.*;

public class Source {
    static Radio radio = new Radio();

    /*-*-*- Constant Values -*-*-*/

    /*
     * Constant values are marked with "final" in a comment as Moterunner does
     * not support final variables.
     */

    /* -- Radio Settings -- */
    private static /* final */ byte SOURCE_CHANNEL          =  0;
    private static /* final */ byte SOURCE_PAN_ID           =  0x11;
    private static /* final */ byte SOURCE_ADDRESS          =  0x42;
    private static /* final */ byte SOURCE_SPECIAL_MESSAGE  =  0x32;

    /* -- Timing Constants */
    private static /* final */ int MAX_TIMEOUT = 120; // 2 * 60 Seconds

    /* -- Beacon Constants -- */
    private static /* final */ int LAST_BEACON_VALUE     = 1;
    private static /* final */ int MIN_BEACON_COUNT      = 2;
    private static /* final */ int MAX_BEACON_COUNT      = 10;
    private static /* final */ long MAX_INTER_BEACON_TIME = Time.toTickSpan(Time.MILLISECS, 1500);                                                       // Milliseconds
    private static /* final */ long MIN_INTER_BEACON_TIME     = Time.toTickSpan(Time.MILLISECS, 250);                                                       // Milliseconds

    /* -- Sink Constants -- */
    private static /* final */ int SINK_COUNT = 3;
    private static /* final */ int SINK_A_INDEX = 0;
    private static /* final */ int SINK_B_INDEX = 1;
    private static /* final */ int SINK_C_INDEX = 2;
    private static /* final */ int SINK_BASE_PAN = 0x11;
    private static /* final */ int SINK_BASE_ADDRESS = 0x11;
    private static /* final */ byte SINK_BASE_CHANNEL = 0;

    /* Message Constants */
    private static /* final */ int MESSAGE_PAYLOAD_START_INDEX        = 11;
    private static /* final */ int MESSAGE_SOURCE_ADDRESS_START_INDEX = 9;
    private static /* final */ int MESSAGE_BRAODCAST_ADDRESS          = 0xFFFF;

    /* -- Source State Pseudo-Enumeration -- */
    private static /* final */ int STATE_START        = 0;
    private static /* final */ int STATE_RECEPTION    = 1;
    private static /* final */ int STATE_TRANSMISSION = 2;

    /*-*-*- Dynamic Variables -*-*-*/

    /* -- Beacon Data -- */
    private static long beaconTimings[]       = new long[SINK_COUNT * MAX_BEACON_COUNT];
    private static long interBeaconEstimate[] = new long[SINK_COUNT];
    private static byte previousBeaconN[]     = new byte[SINK_COUNT];
    private static byte maxBeaconN[]          = new byte[SINK_COUNT];
    private static long lastBeaconTime[]      = new long[SINK_COUNT];
    private static boolean estimationDone     = false;
    
    private static long nextScheduledTransmitTime[] = new long[SINK_COUNT];

    /* -- State Tracking -- */
    private static int currentState            = STATE_START;
    private static int currentSinkReceiveIndex = -1;          // The beacon we're currently listening to

    private static int writeInProgressLock = -1;

    /* Transmit Timers */
    private static Timer timerSinkA             = new Timer();
    private static Timer timerSinkB             = new Timer();
    private static Timer timerSinkC             = new Timer();
    private static Timer estimatorTimer         = new Timer();
    private static Timer receptionTimeoutTimer  = new Timer();
    private static Timer transmissionDelayTimer = new Timer();
    
    private static byte[] transmitBuffer = new byte[12];

    static {
        resetVariables();

        radio.open(Radio.DID, null, 0, 0);

        radio.setRxHandler(new DevCallback(null) {
        	@Override
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Source.onReceive(flags, data, len, info, time);
            }
        });
        
        radio.setTxHandler(new DevCallback(null) {
        	@Override
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Source.onTransmit(flags, data, len, info, time);
            }
        });

        estimatorTimer.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                estimatorTask(param, time);
            }
        });
        
        timerSinkA.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                sinkTransmitTaskA(param, time);
            }
        });
        
        timerSinkB.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                sinkTransmitTaskB(param, time);
            }
        });
        
        timerSinkC.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                sinkTransmitTaskC(param, time);
            }
        });
        
        receptionTimeoutTimer.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                receptionTimeoutTask(param, time);
            }
        });
        
        transmissionDelayTimer.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                delayedTransmission(param, time);
            }
        });

        Assembly.setSystemInfoCallback(new SystemInfo(null) {
            public int invoke(int type, int info) {
                return Source.onDelete(type, info);
            }
        });
        
        transmitBuffer[0] = Radio.FCF_BEACON;
        transmitBuffer[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
        Util.set16le(transmitBuffer, 3, SINK_BASE_PAN);
        Util.set16le(transmitBuffer, 5, SINK_BASE_ADDRESS);
        Util.set16le(transmitBuffer, 7, SOURCE_PAN_ID);
        Util.set16le(transmitBuffer, 9, SOURCE_ADDRESS);
        transmitBuffer[11] = SOURCE_SPECIAL_MESSAGE;

        // Setup radio channel, pan, and address
        setupRadio();

        // Start Radio RX
        startRx();
        
        currentState = STATE_RECEPTION;

        estimatorTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 100));
    }

    private static void resetVariables() {
        for (int sinkIndex = 0; sinkIndex < SINK_COUNT; sinkIndex++) {
            interBeaconEstimate[sinkIndex] = 0;
            maxBeaconN[sinkIndex] = 0;
            previousBeaconN[sinkIndex] = 0;
            lastBeaconTime[sinkIndex] = 0;
            nextScheduledTransmitTime[sinkIndex] = -1;
            
            for (int beaconIndex = 0; beaconIndex < MAX_BEACON_COUNT; beaconIndex++) {
                beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex] = -1;
            }
        }
    }

	protected static void sinkTransmitTaskA(byte param, long time) {
    	sinkTransmitTask(SINK_A_INDEX, time);
	}
    
    protected static void sinkTransmitTaskB(byte param, long time) {
    	sinkTransmitTask(SINK_B_INDEX, time);
	}
    
    protected static void sinkTransmitTaskC(byte param, long time) {
    	sinkTransmitTask(SINK_C_INDEX, time);
	}
    
    private static void scheduleTransmitTask(int sinkIndex, long time) {
    	long nextEventTick = time + interBeaconEstimate[sinkIndex] * (10 + maxBeaconN[sinkIndex]);
    	if(previousBeaconN[sinkIndex] == maxBeaconN[sinkIndex]) {    		
    		nextEventTick += (interBeaconEstimate[sinkIndex] / 2);
    	}
    	nextScheduledTransmitTime[sinkIndex] = nextEventTick;
    	
    	switch (sinkIndex) {
		case 0:
			timerSinkA.setAlarmTime(nextEventTick);
			break;
		case 1:
			timerSinkB.setAlarmTime(nextEventTick);
			break;
		case 2:
			timerSinkC.setAlarmTime(nextEventTick);
			break;

		default:
			break;
		}
    }

    private static void sinkTransmitTask(int sinkIndex, long time) {
    	LED.setState((byte)1, (byte)1);
    	
    	if(writeInProgressLock == -1 || writeInProgressLock == sinkIndex) {
    		writeInProgressLock = sinkIndex;
    		currentState = STATE_TRANSMISSION;

    		changeRadio(sinkIndex);
    	}else {
    		if(nextScheduledTransmitTime[sinkIndex] == -1) {
    			scheduleTransmitTask(sinkIndex, time);
    		}else {
    			scheduleTransmitTask(sinkIndex, nextScheduledTransmitTime[sinkIndex]);
    		}
    	}
	}

	protected static int onTransmit(int flags, byte[] data, int len, int info, long time) {
		LED.setState((byte)2, (byte)0);
		
		if(data != null) {
			writeInProgressLock = -1;
			currentState = STATE_RECEPTION;
			
			if(currentSinkReceiveIndex != -1) {
				changeRadio(currentSinkReceiveIndex);
			}
		}
		
		return 0;
	}

	private static void setupRadio() {
        radio.setPanId(SOURCE_PAN_ID, true);

        radio.setShortAddr(SOURCE_ADDRESS);

        radio.setChannel(SOURCE_CHANNEL);
    }

    protected static void estimatorTask(byte arg0, long arg1) {
        int sinkToEstimateIndex = -1;
        for (int sinkIndex = 0; sinkIndex < SINK_COUNT; sinkIndex++) {
            if (interBeaconEstimate[sinkIndex] == 0) {
                sinkToEstimateIndex = sinkIndex;
                break;
            }
        }

        if (sinkToEstimateIndex != -1) {
        	currentSinkReceiveIndex = sinkToEstimateIndex;
            changeRadio(sinkToEstimateIndex);
            startRx();
        }else {
        	estimationDone = true;
        }
    }

    private static void changeRadio(int sinkIndex) {
    	if(writeInProgressLock == sinkIndex || writeInProgressLock == -1) {
	        radio.stopRx();
	
	        radio.setPanId(SINK_BASE_PAN + sinkIndex, true);
	
	        radio.setShortAddr(SOURCE_ADDRESS);
	
	        radio.setChannel((byte) (SINK_BASE_CHANNEL + (byte) sinkIndex));
    	}
    }

    private static void startRx() {
        radio.startRx(Device.ASAP, 0, Time.currentTicks() + Time.toTickSpan(Time.SECONDS, MAX_TIMEOUT));
    }

    private static long estimateT(int sinkIndex) {
        long skipped = 0; // "Skipped" or "missed" beacons e.g. if we receive 3,4,6.. then 5 was "skipped"
        long lastValue = -1;
        long differenceSum = 0;
        long count = 0;

        for (int beaconIndex = MAX_BEACON_COUNT - 1; beaconIndex >= 0; beaconIndex--) {
            // The current beacon to check
            long beaconValue = beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex];

            // If the beacon was not received
            if (beaconValue == -1) {
                // If the last value we read was received, count as a missed value
                if (lastValue != -1) {
                    skipped++;
                }

                // Else do nothing
            } else {
                // If this is not the first value
                if (lastValue != -1) {
                    // Calculate the average gap between the last two present values
                    differenceSum += (beaconValue - lastValue) / (skipped + 1);
                    count++;
                }

                lastValue = beaconValue;
                skipped = 0;
            }
        }

        // If we found no differences, return error value
        if (count == 0) {
            return -1;
        }

        // Calculate average of all differences
        return differenceSum / count;
    }

    private static void recordBeacon(int beaconIndex, int sinkIndex, long time) {
        beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex - 1] = time;
    }

    protected static int onReceive(int flags, byte[] data, int len, int info, long time) {
        if (data == null) {
            startRx();

            return 0;
        }

        /*
         * +----------------------+
         * |Beacon Message Format |
         * +--+-------------------+
         * | 0|FCF                |
         * | 1|FCA                |
         * | 2|FCA                |
         * | 3|Destination PAN ID |
         * | 4|Destination PAN ID |
         * | 5|Destination Address|
         * | 6|Destination Address|
         * | 7|Source PAN ID      |
         * | 8|Source PAN ID      |
         * | 9|Source Address     |
         * |10|Source Address     |
         * |11|Payload            |
         * +--+-------------------+
         */
        
        byte beaconValue = data[MESSAGE_PAYLOAD_START_INDEX];
        int beaconAddress = Util.get16le(data, MESSAGE_SOURCE_ADDRESS_START_INDEX);
        
        if(beaconAddress == 0x42) {
        	return 0;
        }
        
        int sinkIndex = sinkAddressToIndex(beaconAddress);
        
        if (sinkIndex == -1) {
            Logger.appendString(csr.s2b("INVALID BEACON VALUE!"));
            Logger.flush(Mote.ERROR);

            return 0;
        }

        if(maxBeaconN[sinkIndex] < beaconValue)
        {
        	previousBeaconN[sinkIndex] = maxBeaconN[sinkIndex];
        	maxBeaconN[sinkIndex] = beaconValue;
        }
        
        lastBeaconTime[sinkIndex] = time;

        if(writeInProgressLock != -1 && sinkIndex != writeInProgressLock) {
        	return 0;
        }
        
        if(currentState == STATE_RECEPTION && estimationDone == false) {
        	handleEstimationReception(beaconValue, sinkIndex, time);
        } else if(currentState == STATE_TRANSMISSION) {
        	handleTransmitSync(beaconValue, sinkIndex, time);
        }

        return 0;
    }
    
    private static void handleEstimationReception(byte beaconValue, int sinkIndex, long time) {
        recordBeacon(beaconValue, sinkIndex, time);

        Logger.appendString(csr.s2b("Received "));
        Logger.appendByte(beaconValue);

        long estimateOfT = estimateT(sinkIndex);
        Logger.appendString(csr.s2b(" ! "));
        Logger.appendInt(sinkIndex);
        Logger.appendString(csr.s2b(" ! "));
        Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, estimateOfT));

        Logger.flush(Mote.WARN);

        if (beaconValue == LAST_BEACON_VALUE) {
        	if(estimateOfT != -1) {        		
        		interBeaconEstimate[sinkIndex] = estimateOfT;
        		
        		receptionTimeoutTimer.cancelAlarm();
        		
        		currentSinkReceiveIndex = -1;
        		
        		writeInProgressLock = sinkIndex;
        		handleTransmitSync(beaconValue, sinkIndex, time);
        		
        		estimatorTimer.setAlarmBySpan(2 * estimateOfT);
        	}
        	// Do nothing
        }else {
        	if(estimateOfT == -1) {   
        		estimateOfT = MAX_INTER_BEACON_TIME;
        	}
        	receptionTimeoutTimer.cancelAlarm();
        	receptionTimeoutTimer.setAlarmBySpan(2 * estimateOfT);
        }

    }
    
    protected static void receptionTimeoutTask(byte param, long time) {
    	if(currentSinkReceiveIndex != -1) {
	    	Logger.appendString(csr.s2b("Reception ended due to timeout."));
	    	Logger.flush(Mote.WARN);
	    	
	    	long estimateOfT = estimateT(currentSinkReceiveIndex);
	    	
	    	interBeaconEstimate[currentSinkReceiveIndex] = estimateOfT;
	    	
	    	scheduleTransmitTask(currentSinkReceiveIndex, lastBeaconTime[currentSinkReceiveIndex]);
	    	
	    	currentSinkReceiveIndex = -1;
	    	estimatorTimer.setAlarmBySpan(estimateOfT + MIN_INTER_BEACON_TIME);
    	}
	}
    
    private static void handleTransmitSync(byte beaconValue, int sinkIndex, long time) {
    	LED.setState((byte)1, (byte)1);
    	
    	if(beaconValue == 1) {
    		Util.set16le(transmitBuffer, 3, SINK_BASE_PAN + sinkIndex);
    		Util.set16le(transmitBuffer, 5, SINK_BASE_ADDRESS + sinkIndex);
    		Util.set16le(transmitBuffer, 7, SINK_BASE_PAN + sinkIndex);
    		
    		long delay = interBeaconEstimate[sinkIndex] + (MIN_INTER_BEACON_TIME / 2);
    		transmissionDelayTimer.setAlarmTime(time + delay);
    		LED.setState((byte)2, (byte)1);
    		LED.setState((byte)1, (byte)0);
    	}
    	
    	scheduleTransmitTask(sinkIndex, time);
    }
    
	protected static void delayedTransmission(byte param, long time) {
		radio.transmit(Device.ASAP|Radio.TXMODE_POWER_MAX, transmitBuffer, 0, transmitBuffer.length, 0);
		Logger.appendString(csr.s2b("+TX+"));
		Logger.flush(Mote.WARN);
	}

    private static int sinkAddressToIndex(int address) {
        if (address > 0x13 || address < 0x11) {
            return -1;
        }
        return address - 0x11;
    }

    /**
     * Clean up after delete.
     */
    protected static int onDelete(int type, int info) {
        if (type == Assembly.SYSEV_DELETED) {
            radio.stopRx();
            radio.close();
        }
        return 0;
    }
}
