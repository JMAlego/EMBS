package embs;

import com.ibm.saguaro.logger.Logger;
import com.ibm.saguaro.system.*;

public class Source {
    static Radio radio = new Radio();

    /*-*-*- General Notes -*-*-
     *
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

    /*-*-*- Constant Values -*-*-*/

    /*
     * Constant values are marked with "final" in a comment as Moterunner does
     * not support final variables.
     */

    /* -- Radio Settings -- */
    private static /* final */ byte SOURCE_CHANNEL         = 0;
    private static /* final */ byte SOURCE_PAN_ID          = 0x11;
    private static /* final */ byte SOURCE_ADDRESS         = 0x42;
    private static /* final */ byte SOURCE_SPECIAL_MESSAGE = 0x32;

    /* -- Timing Constants */
    private static /* final */ long MAX_TIMEOUT = Time.toTickSpan(Time.SECONDS, 60); // 2 * 60 Seconds

    /* -- Beacon Constants -- */
    private static /* final */ int  LAST_BEACON_VALUE     = 1;
    private static /* final */ int  MIN_BEACON_COUNT      = 2;
    private static /* final */ int  MAX_BEACON_COUNT      = 10;
    private static /* final */ long MAX_INTER_BEACON_TIME = Time.toTickSpan(Time.MILLISECS, 1500); // Max "t"
    private static /* final */ long MIN_INTER_BEACON_TIME = Time.toTickSpan(Time.MILLISECS, 250);  // Min "t"

    /* -- Sink Constants -- */
    private static /* final */ int  SINK_COUNT        = 3;
    private static /* final */ int  SINK_A_INDEX      = 0;
    private static /* final */ int  SINK_B_INDEX      = 1;
    private static /* final */ int  SINK_C_INDEX      = 2;
    private static /* final */ int  SINK_BASE_PAN     = 0x11;
    private static /* final */ int  SINK_BASE_ADDRESS = 0x11;
    private static /* final */ byte SINK_BASE_CHANNEL = 0;

    /* -- Message Constants -- */
    private static /* final */ int MESSAGE_PAYLOAD_START_INDEX        = 11;
    private static /* final */ int MESSAGE_SOURCE_ADDRESS_START_INDEX = 9;

    /* -- Write Constants -- */

    private static /* final */ int WRITE_UNLOCKED = -1;
    
    /* -- Generic Constants -- */
    
    private static /* final */ int NO_VALUE = -1;

    /* -- LED Constants -- */

    private static /* final */ byte LED_ON    = 1;
    private static /* final */ byte LED_OFF   = 0;
    private static /* final */ byte LED_RED   = 2;
    private static /* final */ byte LED_GREEN = 1;

    /*-*-*- Dynamic Variables -*-*-*/

    /* -- Beacon Data -- */

    // The times that each beacon is received
    private static long[] beaconTimings = new long[SINK_COUNT * MAX_BEACON_COUNT];

    // Estimate of the "t" value for each beacon
    private static long[] interBeaconEstimate = new long[SINK_COUNT];

    // The previous highest "n" seen for a beacon so far, this is important
    // for determining if our "n" estimate is accurate
    private static byte[] previousBeaconN = new byte[SINK_COUNT];

    // The highest "n" seen for a beacon so far
    private static byte[] maxBeaconN = new byte[SINK_COUNT];

    // The time the last beacon for a sink was received
    private static long[] lastBeaconTime = new long[SINK_COUNT];

    // The time that the next scheduled transmission is due
    private static long[] nextScheduledTransmitTime = new long[SINK_COUNT];

    /* -- State Tracking -- */

    // Indicates if estimation should stop, this is so we don't waste time
    // listening when we don't need to
    private static boolean estimationDone = false;

    // Indicates whether in transmit mode or receive mode
    private static boolean transmitting = false;

    // The beacon we're currently listening to, this is useful if our receive
    // task is interrupted
    private static int currentSinkReceiveIndex = NO_VALUE;

    // Indicate whether a task has exclusive write access, this is needed due
    // to the change in channel required to transmit to a particular sink
    private static int writeInProgressLock = WRITE_UNLOCKED;

    /* Transmit Timers */
    private static Timer timerSinkA             = new Timer();
    private static Timer timerSinkB             = new Timer();
    private static Timer timerSinkC             = new Timer();
    private static Timer estimatorTimer         = new Timer();
    private static Timer receptionTimeoutTimer  = new Timer();
    private static Timer transmissionDelayTimer = new Timer();

    // The transmission buffer
    private static byte[] transmitBuffer = new byte[12];

    static {
        // Reset all variables to defaults
        resetVariables();

        // Open the radio
        radio.open(Radio.DID, null, 0, 0);

        // Setup receive handler
        radio.setRxHandler(new DevCallback(null) {
            @Override
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Source.onReceive(flags, data, len, info, time);
            }
        });

        // Setup the transmit handler to indicate successful transmission
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
        transmitBuffer[1] = Radio.FCA_SRC_SADDR | Radio.FCA_DST_SADDR;
        Util.set16le(transmitBuffer, 3, SINK_BASE_PAN);
        Util.set16le(transmitBuffer, 5, SINK_BASE_ADDRESS);
        Util.set16le(transmitBuffer, 7, SOURCE_PAN_ID);
        Util.set16le(transmitBuffer, 9, SOURCE_ADDRESS);
        transmitBuffer[11] = SOURCE_SPECIAL_MESSAGE;

        transmitting = false;

        estimatorTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 100));
    }

    private static void resetVariables() {
        for (int sinkIndex = 0; sinkIndex < SINK_COUNT; sinkIndex++) {
            interBeaconEstimate[sinkIndex] = 0;
            maxBeaconN[sinkIndex] = 0;
            previousBeaconN[sinkIndex] = 0;
            lastBeaconTime[sinkIndex] = 0;
            nextScheduledTransmitTime[sinkIndex] = NO_VALUE;

            for (int beaconIndex = 0; beaconIndex < MAX_BEACON_COUNT; beaconIndex++) {
                beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex] = NO_VALUE;
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
        if (previousBeaconN[sinkIndex] == maxBeaconN[sinkIndex]) {
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
        LED.setState(LED_GREEN, LED_ON);

        if (writeInProgressLock == WRITE_UNLOCKED || writeInProgressLock == sinkIndex) {
            writeInProgressLock = sinkIndex;
            transmitting = true;

            changeRadio(sinkIndex);
        } else {
            if (nextScheduledTransmitTime[sinkIndex] == NO_VALUE) {
                scheduleTransmitTask(sinkIndex, time);
            } else {
                scheduleTransmitTask(sinkIndex, nextScheduledTransmitTime[sinkIndex]);
            }
        }
    }

    protected static int onTransmit(int flags, byte[] data, int len, int info, long time) {
        LED.setState(LED_RED, LED_OFF);

        if (data != null) {
            writeInProgressLock = WRITE_UNLOCKED;
            transmitting = false;

            if (currentSinkReceiveIndex != NO_VALUE) {
                changeRadio(currentSinkReceiveIndex);
            }
        }

        return 0;
    }

    protected static void estimatorTask(byte arg0, long arg1) {
        int sinkToEstimateIndex = NO_VALUE;
        for (int sinkIndex = 0; sinkIndex < SINK_COUNT; sinkIndex++) {
            if (interBeaconEstimate[sinkIndex] == 0) {
                sinkToEstimateIndex = sinkIndex;
                break;
            }
        }

        if (sinkToEstimateIndex != NO_VALUE) {
            currentSinkReceiveIndex = sinkToEstimateIndex;

            changeRadio(sinkToEstimateIndex);

            startRx();
        } else {
            estimationDone = true;
        }
    }

    private static void changeRadio(int sinkIndex) {
        if (writeInProgressLock == sinkIndex || writeInProgressLock == WRITE_UNLOCKED) {
            radio.stopRx();

            radio.setPanId(SINK_BASE_PAN + sinkIndex, true);

            radio.setShortAddr(SOURCE_ADDRESS);

            radio.setChannel((byte) (SINK_BASE_CHANNEL + (byte) sinkIndex));
        }
    }

    private static void startRx() {
        radio.startRx(Device.ASAP, 0, Time.currentTicks() + MAX_TIMEOUT);
    }

    private static long estimateT(int sinkIndex) {
        long skipped = 0; // "Skipped" or "missed" beacons e.g. if we receive 3,4,6.. then 5 was "skipped"
        long lastValue = NO_VALUE;
        long differenceSum = 0;
        long count = 0;

        for (int beaconIndex = MAX_BEACON_COUNT - 1; beaconIndex >= 0; beaconIndex--) {
            // The current beacon to check
            long beaconValue = beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex];

            // If the beacon was not received
            if (beaconValue == NO_VALUE) {
                // If the last value we read was received, count as a missed value
                if (lastValue != NO_VALUE) {
                    skipped++;
                }

                // Else do nothing
            } else {
                // If this is not the first value
                if (lastValue != NO_VALUE) {
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
            return NO_VALUE;
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

        byte beaconValue = data[MESSAGE_PAYLOAD_START_INDEX];
        int beaconAddress = Util.get16le(data, MESSAGE_SOURCE_ADDRESS_START_INDEX);

        if (beaconAddress == 0x42) {
            return 0;
        }

        int sinkIndex = sinkAddressToIndex(beaconAddress);

        if (sinkIndex == NO_VALUE) {
            Logger.appendString(csr.s2b("INVALID BEACON VALUE!"));
            Logger.flush(Mote.ERROR);

            return 0;
        }

        if (maxBeaconN[sinkIndex] < beaconValue) {
            previousBeaconN[sinkIndex] = maxBeaconN[sinkIndex];
            maxBeaconN[sinkIndex] = beaconValue;
        }

        lastBeaconTime[sinkIndex] = time;

        if (writeInProgressLock != WRITE_UNLOCKED && sinkIndex != writeInProgressLock) {
            return 0;
        }

        if (!transmitting && !estimationDone) {
            handleEstimationReception(beaconValue, sinkIndex, time);
        } else if (transmitting) {
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
            if (estimateOfT != NO_VALUE) {
                interBeaconEstimate[sinkIndex] = estimateOfT;

                receptionTimeoutTimer.cancelAlarm();

                currentSinkReceiveIndex = NO_VALUE;

                writeInProgressLock = sinkIndex;
                handleTransmitSync(beaconValue, sinkIndex, time);

                estimatorTimer.setAlarmBySpan(2 * estimateOfT);
            }
            // Do nothing
        } else {
            if (estimateOfT == NO_VALUE) {
                estimateOfT = MAX_INTER_BEACON_TIME;
            }
            receptionTimeoutTimer.cancelAlarm();
            receptionTimeoutTimer.setAlarmBySpan(2 * estimateOfT);
        }

    }

    protected static void receptionTimeoutTask(byte param, long time) {
        if (currentSinkReceiveIndex != NO_VALUE) {
            Logger.appendString(csr.s2b("Reception ended due to timeout."));
            Logger.flush(Mote.WARN);

            long estimateOfT = estimateT(currentSinkReceiveIndex);

            interBeaconEstimate[currentSinkReceiveIndex] = estimateOfT;

            scheduleTransmitTask(currentSinkReceiveIndex, lastBeaconTime[currentSinkReceiveIndex]);

            currentSinkReceiveIndex = NO_VALUE;
            estimatorTimer.setAlarmBySpan(estimateOfT + MIN_INTER_BEACON_TIME);
        }
    }

    private static void handleTransmitSync(byte beaconValue, int sinkIndex, long time) {
        LED.setState(LED_GREEN, LED_ON);

        if (beaconValue == 1) {
            Util.set16le(transmitBuffer, 3, SINK_BASE_PAN + sinkIndex);
            Util.set16le(transmitBuffer, 5, SINK_BASE_ADDRESS + sinkIndex);
            Util.set16le(transmitBuffer, 7, SINK_BASE_PAN + sinkIndex);

            long delay = interBeaconEstimate[sinkIndex] + (MIN_INTER_BEACON_TIME / 2);
            transmissionDelayTimer.setAlarmTime(time + delay);

            LED.setState(LED_RED, LED_ON);
            LED.setState(LED_GREEN, LED_OFF);
        }

        scheduleTransmitTask(sinkIndex, time);
    }

    protected static void delayedTransmission(byte param, long time) {
        radio.transmit(Device.ASAP | Radio.TXMODE_POWER_MAX, transmitBuffer, 0, transmitBuffer.length, 0);
        Logger.appendString(csr.s2b("+TX+"));
        Logger.flush(Mote.WARN);
    }

    private static int sinkAddressToIndex(int address) {
        if (address > 0x13 || address < 0x11) {
            return NO_VALUE;
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
