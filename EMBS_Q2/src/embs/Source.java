package embs;

import com.ibm.saguaro.logger.Logger;
import com.ibm.saguaro.system.*;

/**
 * Source assembly responsible for synchronising and transmitting to Sinks
 * using the EMBS MAC protocol.
 */
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
     * 
     * 16 bit values in the beacon are encoded in little-endian.
     * 
     * - Transmit Notes -
     * 
     * Our short address is 0x42.
     * 
     * The payload is 0x32 as a nice power of two, though this could be any value.
     * 
     */

    /*-*-*- Constant Values -*-*-*/

    /*
     * Constant values are marked with "final" in a comment as Mote Runner does
     * not support final variables.
     */

    private static /* final */ boolean ENABLE_DEBUG_PRINTING = false;
    private static /* final */ boolean ENABLE_TX_PRINTING    = true;

    /* -- Source Settings -- */

    // Source radio settings.
    private static /* final */ byte SOURCE_PAN_ID          = 0x11;
    private static /* final */ byte SOURCE_ADDRESS         = 0x42;
    private static /* final */ byte SOURCE_SPECIAL_MESSAGE = 0x32;

    /* -- Timing Constants */

    // The maximum time that anything should take, e.g. longer than the demo.
    private static /* final */ long MAX_TIMEOUT = Time.toTickSpan(Time.SECONDS, 60);

    /* -- Beacon Constants -- */

    // The received beacon value of the last sync frame.
    private static /* final */ int LAST_BEACON_VALUE = 1;

    // The maximum number of beacons in a sync phase e.g. max "n".
    private static /* final */ int MAX_BEACON_COUNT = 10;

    // The maximum and minimum inter-beacon time e.g. "t".
    private static /* final */ long MAX_INTER_BEACON_TIME = Time.toTickSpan(Time.MILLISECS, 1500);
    private static /* final */ long MIN_INTER_BEACON_TIME = Time.toTickSpan(Time.MILLISECS, 250);

    /* -- Sink Constants -- */

    // The number of sinks.
    private static /* final */ int SINK_COUNT = 3;

    // The internal indicies of the sinks.
    private static /* final */ int SINK_A_INDEX = 0;
    private static /* final */ int SINK_B_INDEX = 1;
    private static /* final */ int SINK_C_INDEX = 2;

    // The base PAN, addres, and channel of the sinks.
    // This assumes that sinks are consecutive, as per the brief.
    private static /* final */ int  SINK_BASE_PAN     = 0x11;
    private static /* final */ int  SINK_BASE_ADDRESS = 0x11;
    private static /* final */ byte SINK_BASE_CHANNEL = 0;

    /* -- Message Constants -- */

    // Index of the message payload and the source address into received messages.
    private static /* final */ int MESSAGE_PAYLOAD_START_INDEX        = 11;
    private static /* final */ int MESSAGE_SOURCE_ADDRESS_START_INDEX = 9;

    /* -- Write Constants -- */

    // Represents that there is no write lock in place.
    private static /* final */ int WRITE_UNLOCKED = -1;

    /* -- Generic Constants -- */

    // A value to represent a "no value" for integer variables.
    private static /* final */ int NO_VALUE = -1;

    /* -- LED Constants -- */

    // LED states
    private static /* final */ byte LED_ON  = 1;
    private static /* final */ byte LED_OFF = 0;

    // LED colour indices.
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

    /* -- State Tracking -- */

    // The time that the next scheduled transmission is due
    private static long[] nextScheduledTransmitTime = new long[SINK_COUNT];

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
    // Timers for sink transmission
    private static Timer timerSinkA = new Timer();
    private static Timer timerSinkB = new Timer();
    private static Timer timerSinkC = new Timer();

    // Delay for the start of the estimation code
    private static Timer estimatorTimer = new Timer();

    // Timeout on reception, detects when reception was interrupted by transmission
    private static Timer receptionTimeoutTimer = new Timer();

    // Delays the transmission until inside the transmission slot
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

        // Map the estimator timer to the estimator task
        estimatorTimer.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                estimatorTask(param, time);
            }
        });

        // Map the Sink A transmit task to the Sink A transmit timer
        timerSinkA.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                sinkTransmitTaskA(param, time);
            }
        });

        // Map the Sink B transmit task to the Sink B transmit timer
        timerSinkB.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                sinkTransmitTaskB(param, time);
            }
        });

        // Map the Sink C transmit task to the Sink C transmit timer
        timerSinkC.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                sinkTransmitTaskC(param, time);
            }
        });

        // Map reception timeout to reception timeout task
        receptionTimeoutTimer.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                receptionTimeoutTask(param, time);
            }
        });

        // Map transmission delay timer to delayed transmission handler
        transmissionDelayTimer.setCallback(new TimerEvent(null) {
            @Override
            public void invoke(byte param, long time) {
                delayedTransmission(param, time);
            }
        });

        // Setup callback to free radio use after assembly delete
        Assembly.setSystemInfoCallback(new SystemInfo(null) {
            public int invoke(int type, int info) {
                return Source.onDelete(type, info);
            }
        });

        // Setup template for message transmissions.
        transmitBuffer[0] = Radio.FCF_DATA;
        transmitBuffer[1] = Radio.FCA_SRC_SADDR | Radio.FCA_DST_SADDR;
        Util.set16le(transmitBuffer, 3, SINK_BASE_PAN);
        Util.set16le(transmitBuffer, 5, SINK_BASE_ADDRESS);
        Util.set16le(transmitBuffer, 7, SOURCE_PAN_ID);
        Util.set16le(transmitBuffer, 9, SOURCE_ADDRESS);
        transmitBuffer[11] = SOURCE_SPECIAL_MESSAGE;

        // Set transmission state to false.
        transmitting = false;

        // Start radio, this must be done now and then left for a short period
        // of time (the following timer does this) so that the radio can start
        // up properly.
        setRadioProperties(SINK_A_INDEX);
        startRx();

        // Trigger first run of estimator.
        estimatorTimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 100));
    }

    /**
     * Reset globals to initial values at startup.
     */
    private static void resetVariables() {
        // For each sink...
        for (int sinkIndex = 0; sinkIndex < SINK_COUNT; sinkIndex++) {
            // Clear the following globals:
            interBeaconEstimate[sinkIndex] = 0;
            maxBeaconN[sinkIndex] = 0;
            previousBeaconN[sinkIndex] = 0;
            lastBeaconTime[sinkIndex] = 0;
            nextScheduledTransmitTime[sinkIndex] = NO_VALUE;

            // For each beacon slot...
            for (int beaconIndex = 0; beaconIndex < MAX_BEACON_COUNT; beaconIndex++) {
                // Set it to the "no value" value.
                beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex] = NO_VALUE;
            }
        }
    }

    /**
     * Proxies to {@link #scheduleTransmitTask(int, long)}.
     */
    protected static void sinkTransmitTaskA(byte param, long time) {
        sinkTransmitTask(SINK_A_INDEX, time);
    }

    /**
     * Proxies to {@link #scheduleTransmitTask(int, long)}.
     */
    protected static void sinkTransmitTaskB(byte param, long time) {
        sinkTransmitTask(SINK_B_INDEX, time);
    }

    /**
     * Proxies to {@link #scheduleTransmitTask(int, long)}.
     */
    protected static void sinkTransmitTaskC(byte param, long time) {
        sinkTransmitTask(SINK_C_INDEX, time);
    }

    /**
     * Schedule a transmit task.
     */
    private static void scheduleTransmitTask(int sinkIndex, long time) {
        // The next arrival is the time of the last arrival (now) plus the
        // length "t" times 10 (the sleep phase) plus the number of messages in
        // a sync phase ("n").
        // Note: this lags slightly behind to ensure synchronisation can occur.
        long nextEventTick = time + interBeaconEstimate[sinkIndex] * (10 + maxBeaconN[sinkIndex]);

        // If we are sure of the value of "n" we can push our estimate by half
        // of "t" and still synchronise successfully.
        if (previousBeaconN[sinkIndex] == maxBeaconN[sinkIndex]) {
            nextEventTick += (interBeaconEstimate[sinkIndex] / 2);
        }

        // Log the time at which this event should occur, this may not be the
        // time at which it does, as the system may be busy.
        nextScheduledTransmitTime[sinkIndex] = nextEventTick;

        // Set alarm based on index.
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

    /**
     * Handles all transmit tasks for all sinks, A, B, and C.
     */
    private static void sinkTransmitTask(int sinkIndex, long time) {
        // If write is unlocked or it is already locked to the right sink
        if (writeInProgressLock == WRITE_UNLOCKED || writeInProgressLock == sinkIndex) {
            LED.setState(LED_GREEN, LED_ON);

            writeInProgressLock = sinkIndex;
            transmitting = true;

            changeRadio(sinkIndex);
        } else {
            // If this is the first time a transmit task is being scheduled for this sink
            if (nextScheduledTransmitTime[sinkIndex] == NO_VALUE) {
                scheduleTransmitTask(sinkIndex, time);
            } else {
                // We schedule here based on when this task was meant to run,
                // this may be in the past as we may have been busy at that time
                scheduleTransmitTask(sinkIndex, nextScheduledTransmitTime[sinkIndex]);
            }
        }
    }

    /**
     * Handles cleanup after transmission of a message.
     */
    protected static int onTransmit(int flags, byte[] data, int len, int info, long time) {
        // If we actually sent a packet
        if (data != null) {
            // Indicate transmission
            LED.setState(LED_RED, LED_OFF);

            // Unlock transmission and mark as not transmitting
            writeInProgressLock = WRITE_UNLOCKED;
            transmitting = false;

            // Change the radio back to the receive setting, if it had one
            if (currentSinkReceiveIndex != NO_VALUE) {
                changeRadio(currentSinkReceiveIndex);
            }
        }

        return 0;
    }

    /**
     * This task is responsible for choosing which sink to estimate "n" and "t" for
     * next.
     */
    protected static void estimatorTask(byte param, long time) {
        if (ENABLE_DEBUG_PRINTING) {
            Logger.appendString(csr.s2b("Running estimation task."));
            Logger.flush(Mote.WARN);
        }

        if (estimationDone)
            return;

        // Find an sink not yet estimated
        int sinkToEstimateIndex = NO_VALUE;
        for (int sinkIndex = 0; sinkIndex < SINK_COUNT; sinkIndex++) {
            // If a sink isn't estimated it's estimate will be zero, no actual
            // sink will have this estimate as the minimum "t" is 250 milliseconds
            if (interBeaconEstimate[sinkIndex] == 0 || interBeaconEstimate[sinkIndex] == NO_VALUE) {
                sinkToEstimateIndex = sinkIndex;
                break;
            }
        }

        // If we found a sink to estimate
        if (sinkToEstimateIndex != NO_VALUE) {
            if (ENABLE_DEBUG_PRINTING) {
                Logger.appendString(csr.s2b("Found sink to estimate."));
                Logger.flush(Mote.WARN);
            }

            // Note the sink and start listening
            currentSinkReceiveIndex = sinkToEstimateIndex;

            changeRadio(sinkToEstimateIndex);

            startRx();
        } else {
            if (ENABLE_DEBUG_PRINTING) {
                Logger.appendString(csr.s2b("Finished estimation."));
                Logger.flush(Mote.WARN);
            }

            // Setting this mean no more estimation will take place.
            estimationDone = true;
        }
    }

    /**
     * Update radio properties to match settings for a sink. Should not be used to
     * change radio settings, use {@link #changeRadio(int)} instead as this method
     * does no safety checks.
     */
    private static void setRadioProperties(int sinkIndex) {
        radio.setPanId(SINK_BASE_PAN + sinkIndex, true);

        radio.setShortAddr(SOURCE_ADDRESS);

        radio.setChannel((byte) (SINK_BASE_CHANNEL + (byte) sinkIndex));
    }

    /**
     * Change the radio to the settings for a particular sink.
     */
    private static void changeRadio(int sinkIndex) {
        // We can only change settings if the write lock is unlocked, or says
        // we should be targeting the sink we were passed in
        if (writeInProgressLock == sinkIndex || writeInProgressLock == WRITE_UNLOCKED) {
            if (ENABLE_DEBUG_PRINTING) {
                Logger.appendString(csr.s2b("Changing Channel."));
                Logger.flush(Mote.WARN);
            }

            radio.stopRx();

            // Set radio properties to match sink
            setRadioProperties(sinkIndex);
        }
    }

    /**
     * Starts the radio receiving.
     */
    private static void startRx() {
        radio.startRx(Device.ASAP, 0, Time.currentTicks() + MAX_TIMEOUT);
    }

    /**
     * Responsible for estimating the true value of "t". Averages the beacon
     * interval, while taking missed beacons into account.
     */
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

        // If we found no differences, return the "no value" indicator
        if (count == 0) {
            return NO_VALUE;
        }

        // Calculate average of all differences
        return differenceSum / count;
    }

    /**
     * Record the receive time of a beacon.
     */
    private static void recordBeacon(int beaconIndex, int sinkIndex, long time) {
        beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex - 1] = time;
    }

    /**
     * Handle beacon reception.
     */
    protected static int onReceive(int flags, byte[] data, int len, int info, long time) {
        // If receiving has timed out, restart it.
        if (data == null) {
            startRx();

            return 0;
        }

        if (ENABLE_DEBUG_PRINTING) {
            Logger.appendString(csr.s2b("Received Message!"));
            Logger.flush(Mote.WARN);
        }

        // Ignore messages which are too short.
        if (data.length <= MESSAGE_PAYLOAD_START_INDEX) {
            if (ENABLE_DEBUG_PRINTING) {
                Logger.appendString(csr.s2b("MESSAGE TOO SHORT!"));
                Logger.flush(Mote.ERROR);
            }

            return 0;
        }

        // Read the value and source address of the beacon.
        byte beaconValue = data[MESSAGE_PAYLOAD_START_INDEX];
        int beaconAddress = Util.get16le(data, MESSAGE_SOURCE_ADDRESS_START_INDEX);

        // If source address is this assembly, ignore it.
        if (beaconAddress == 0x42) {
            return 0;
        }

        // Get the sink "index" based on it's address.
        int sinkIndex = sinkAddressToIndex(beaconAddress);

        // Throw an error if we got an illegal value.
        if (sinkIndex == NO_VALUE) {
            if (ENABLE_DEBUG_PRINTING) {
                Logger.appendString(csr.s2b("INVALID BEACON VALUE!"));
                Logger.flush(Mote.ERROR);
            }

            return 0;
        }

        // If this is the highest "n" for a beacon, note it.
        if (maxBeaconN[sinkIndex] < beaconValue) {
            previousBeaconN[sinkIndex] = maxBeaconN[sinkIndex];
            maxBeaconN[sinkIndex] = beaconValue;
        }

        // Note the last time a beacon was received from this sink.
        lastBeaconTime[sinkIndex] = time;

        // If write is locked to a different channel, exit
        if (writeInProgressLock != WRITE_UNLOCKED && sinkIndex != writeInProgressLock) {
            return 0;
        }

        // If we're not transmitting and we're not done with estimation.
        if (!transmitting && !estimationDone) {
            handleEstimationReception(beaconValue, sinkIndex, time);
        } else if (transmitting) { // If we are transmitting
            handleTransmitSync(beaconValue, sinkIndex, time);
        }

        return 0;
    }

    /**
     * Handle messages which are received and need to be used for estimation of "t".
     */
    private static void handleEstimationReception(byte beaconValue, int sinkIndex, long time) {
        // Record the beacon.
        recordBeacon(beaconValue, sinkIndex, time);

        if (ENABLE_DEBUG_PRINTING) {
            Logger.appendString(csr.s2b("Received "));
            Logger.appendByte(beaconValue);
        }

        // Estimate "t" so far
        long estimateOfT = estimateT(sinkIndex);

        if (ENABLE_DEBUG_PRINTING) {
            Logger.appendString(csr.s2b(" from "));
            Logger.appendInt(sinkIndex);
            Logger.appendString(csr.s2b(", estimate is "));
            Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, estimateOfT));

            Logger.flush(Mote.WARN);
        }

        // If this is the last beacon in a sync phase
        if (beaconValue == LAST_BEACON_VALUE) {
            // If we have an estimate for "t"
            if (estimateOfT != NO_VALUE) {
                // Log our estimate of "t"
                interBeaconEstimate[sinkIndex] = estimateOfT;

                // Cancel the timeout
                receptionTimeoutTimer.cancelAlarm();

                // Clear the current sink
                currentSinkReceiveIndex = NO_VALUE;

                // Lock for writing
                writeInProgressLock = sinkIndex;

                // Prepare for transmit
                handleTransmitSync(beaconValue, sinkIndex, time);

                // Set estimator to start again after transmission
                estimatorTimer.setAlarmBySpan(2 * estimateOfT);
            }
            // Else, do nothing
        } else { // Else, it wasn't the last beacon in a sync phase
            // If we don't have an estimate for "t".
            if (estimateOfT == NO_VALUE) {
                // Guess it's the max "t" for the timeout
                estimateOfT = MAX_INTER_BEACON_TIME;
            }

            // Set timeout to twice our current estimate for "t".
            receptionTimeoutTimer.cancelAlarm();
            receptionTimeoutTimer.setAlarmBySpan(2 * estimateOfT);
        }

    }

    /**
     * Handle timeout of estimation reception, usually caused by a conflicting
     * transmission.
     */
    protected static void receptionTimeoutTask(byte param, long time) {
        // If we are (or were) in a reception phase
        if (currentSinkReceiveIndex != NO_VALUE) {
            // Log the timeout
            if (ENABLE_DEBUG_PRINTING) {
                Logger.appendString(csr.s2b("Reception ended due to timeout."));
                Logger.flush(Mote.WARN);
            }

            // Get our current estimate of "t"
            long estimateOfT = estimateT(currentSinkReceiveIndex);

            // Set the estimate for this sink to our current estimate.
            // If the estimate is NO_VALUE (-1) or zero then the estimator task
            // will pick this beacon to estimate again, so we don't need to
            // worry about those values.
            interBeaconEstimate[currentSinkReceiveIndex] = estimateOfT;

            // Schedule the next transmission, based on the last reception time.
            // This is slightly less accurate, but the accuracy will increase
            // with each transmission cycle.
            scheduleTransmitTask(currentSinkReceiveIndex, lastBeaconTime[currentSinkReceiveIndex]);

            currentSinkReceiveIndex = NO_VALUE;
            estimatorTimer.setAlarmBySpan(estimateOfT + MIN_INTER_BEACON_TIME);
        }
    }

    /**
     * Handle synchronisation for transmission.
     */
    private static void handleTransmitSync(byte beaconValue, int sinkIndex, long time) {
        // Indicate that the sync phase has started.
        LED.setState(LED_GREEN, LED_ON);

        // If this is the last beacon
        if (beaconValue == LAST_BEACON_VALUE) {
            // Set transmission destination.
            Util.set16le(transmitBuffer, 3, SINK_BASE_PAN + sinkIndex);
            Util.set16le(transmitBuffer, 5, SINK_BASE_ADDRESS + sinkIndex);
            Util.set16le(transmitBuffer, 7, SINK_BASE_PAN + sinkIndex);

            // Calculate a small delay to put us inside the transmission
            // window. This is the rest of the final beacon + half the minimum
            // value of "t" (250 / 2).
            long delay = interBeaconEstimate[sinkIndex] + (MIN_INTER_BEACON_TIME / 2);
            transmissionDelayTimer.setAlarmTime(time + delay);

            // Indicate that sync is over and we are about to actually transmit.
            LED.setState(LED_RED, LED_ON);
            LED.setState(LED_GREEN, LED_OFF);
        }

        // Schedule the next send.
        scheduleTransmitTask(sinkIndex, time);
    }

    /**
     * Perform the actual transmission of a message to a sink.
     */
    protected static void delayedTransmission(byte param, long time) {
        // Transmit message to sink.
        radio.transmit(Device.ASAP | Radio.TXMODE_POWER_MAX, transmitBuffer, 0, transmitBuffer.length, 0);

        // Log transmission.
        if (ENABLE_DEBUG_PRINTING || ENABLE_TX_PRINTING) {
            Logger.appendString(csr.s2b("((TX))"));
            Logger.flush(Mote.WARN);
        }
    }

    /**
     * Helper to convert from short address to internal sink index.
     */
    private static int sinkAddressToIndex(int address) {
        // If the address is greater than Sink C's address or less than Sink A's.
        if (address >= SINK_BASE_ADDRESS + SINK_COUNT || address < SINK_BASE_ADDRESS) {
            // Return the accepted "no value" value.
            return NO_VALUE;
        }

        // Else, the conversion is as simple as the address minus the base address.
        return address - SINK_BASE_ADDRESS;
    }

    /**
     * Clean up after delete.
     */
    protected static int onDelete(int type, int info) {
        // If the event was actually a delete event.
        if (type == Assembly.SYSEV_DELETED) {
            // Stop and close the radio.
            radio.stopRx();
            radio.close();
        }
        return 0;
    }
}
