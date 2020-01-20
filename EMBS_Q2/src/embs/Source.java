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
    private static /* final */ byte SOURCE_CHANNEL = 0;
    private static /* final */ byte SOURCE_PAN_ID  = 0x11;
    private static /* final */ byte SOURC_ADDRESS  = 0x42;

    /* -- Timing Constants */
    private static /* final */ int MAX_TIMEOUT = 120; // 2 * 60 Seconds

    /* -- Beacon Constants -- */
    private static /* final */ int LAST_BEACON_VALUE     = 1;
    private static /* final */ int MIN_BEACON_COUNT      = 2;
    private static /* final */ int MAX_BEACON_COUNT      = 10;
    private static /* final */ int MAX_INTER_BEACON_TIME = 1500;                                                       // Milliseconds
    private static /* final */ int RECEPTION_LEEWAY      = 1000;                                                       // Milliseconds
    private static /* final */ int MAX_RECEPTION_PERIOD  = MAX_BEACON_COUNT * MAX_INTER_BEACON_TIME + RECEPTION_LEEWAY;

    /* -- Sink Constants -- */
    private static /* final */ int SINK_COUNT = 3;

    /* Message Constants */
    private static /* final */ int MESSAGE_PAYLOAD_START_INDEX = 11;
    private static /* final */ int MESSAGE_BRAODCAST_ADDRESS   = 0xFFFF;

    /* -- Source State Pseudo-Enumeration -- */
    private static /* final */ int STATE_START        = 0;
    private static /* final */ int STATE_RECEPTION    = 1;
    private static /* final */ int STATE_TRANSMISSION = 2;

    /*-*-*- Dynamic Variables -*-*-*/

    /* -- Beacon Data -- */
    private static long beaconTimings[]       = new long[SINK_COUNT * MAX_BEACON_COUNT];
    private static long interBeaconEstimate[] = new long[SINK_COUNT];

    /* -- State Tracking -- */
    private static int currentState            = STATE_START;
    private static int currentSinkReceiveIndex = 0;          // The beacon we're currently listening to
    
    private static boolean radioLock = false;

    /* Transmit Timers */
    private static Timer timerSinkA = new Timer();
    private static Timer timerSinkB = new Timer();
    private static Timer timerSinkC = new Timer();
    
    static {
        resetVariables();

        radio.open(Radio.DID, null, 0, 0);

        radio.setRxHandler(new DevCallback(null) {
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Source.onReceive(flags, data, len, info, time);
            }
        });

        Assembly.setSystemInfoCallback(new SystemInfo(null) {
            public int invoke(int type, int info) {
                return Source.onDelete(type, info);
            }
        });

        // Setup radio channel, pan, and address
        setupRadio();

        // Start Radio RX
        startRx();
    }

    private static void resetVariables() {
        for (int sinkIndex = 0; sinkIndex < SINK_COUNT; sinkIndex++) {
            interBeaconEstimate[sinkIndex] = -1;

            for (int beaconIndex = 0; beaconIndex < MAX_BEACON_COUNT; beaconIndex++) {
                beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex] = -1;
            }
        }
    }

    private static void setupRadio() {
        radio.setPanId(SOURCE_PAN_ID, true);

        radio.setShortAddr(SOURC_ADDRESS);

        radio.setChannel(SOURCE_CHANNEL);
    }

    private static void startRx() {
        radio.startRx(Device.ASAP, 0, Time.currentTicks() + Time.toTickSpan(Time.SECONDS, MAX_TIMEOUT));
    }

    private static int estimateN(int sinkIndex) {
        int k = 0;
        int m = 0;

        for (int beaconIndex = 0; beaconIndex < MAX_BEACON_COUNT; beaconIndex++) {
            long beaconValue = beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex];
            if (beaconValue != -1) {
                if (beaconIndex + 1 > m)
                    m = beaconIndex + 1;
                k++;
            }
        }

        if (k == 0)
            return 2;

        return m + ((m - k) / k);
    }
    
    private static long estimateT(int sinkIndex) {
        long skipped = 0;
        long lastValue = -1;
        long differenceSum = 0;
        long count = 0;
        
        for (int beaconIndex = MAX_BEACON_COUNT - 1; beaconIndex >= 0; beaconIndex--) {
            long beaconValue = beaconTimings[(sinkIndex * MAX_BEACON_COUNT) + beaconIndex];
            if(beaconValue == -1) {
                if(lastValue != -1) {
                    skipped++;
                }
            }else {
                if(lastValue != -1) {
                    differenceSum += (beaconValue - lastValue) / (skipped + 1);
                    count++;
                }
                lastValue = beaconValue;
                skipped = 0;
            }
        }
        
        if (count == 0) {
            return Time.toTickSpan(Time.MILLISECS, 250);
        }

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
        
        if (radioLock) {
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
        recordBeacon(beaconValue, currentSinkReceiveIndex, time);
        
        Logger.appendString(csr.s2b("Received "));
        Logger.appendByte(beaconValue);
        
        if(beaconValue == LAST_BEACON_VALUE) {
            Logger.appendString(csr.s2b(" ! "));
            Logger.appendLong(Time.fromTickSpan(Time.MILLISECS, estimateT(currentSinkReceiveIndex)));
            Logger.appendString(csr.s2b(" LAST."));

            radioLock = false;
        }else {
            radioLock = true;
        }
        
        Logger.flush(Mote.WARN);

        return 0;
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
