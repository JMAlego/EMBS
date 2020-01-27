import ptolemy.actor.TypedIOPort;
import ptolemy.data.StringToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * Bus-Invert encoder actor, based on the {@link BusEncoder} abstract base.
 */
@SuppressWarnings("serial")
public class BusInvertEncoder extends BusEncoder {

    /**
     * A container class for encode results.
     */
    protected static class EncodeResult {
        public boolean busIsInverted;
        public String  busState;

        public EncodeResult(boolean busIsInverted, String busState) {
            this.busIsInverted = busIsInverted;
            this.busState = busState;
        }
    }

    // State information
    private boolean busIsInvertedState;

    // New output port for inversion state
    protected TypedIOPort outputPortBusInvert;

    public BusInvertEncoder(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        // Create new output port
        outputPortBusInvert = new TypedIOPort(this, "Bus Invert", false, true);

        // Set type for new output port
        outputPortBusInvert.setTypeEquals(BaseType.STRING);
    }

    /**
     * Invert's the bus state. This is done using textual replacement as it is the
     * fastest way to implement this in Java, given our input is a string.
     */
    protected String invertBusState(String busState) {
        // '?' Acts as an intermediary so that we don't end up with all zeros.
        return busState.replace('0', '?').replace('1', '0').replace('?', '1');
    }

    /**
     * Calculate the hamming distance from the before string to the after string.
     * Both arguments should be of the same length otherwise an
     * {@link IllegalArgumentException} will be thrown.
     */
    protected int calculateHammingDistance(String before, String after) {
        // Transition count
        int transitions = 0;

        // Sanity check
        if (before.length() != after.length()) {
            throw new IllegalArgumentException("Both arguments must have the same length.");
        }

        // Loop through bits, comparing
        for (int bitIndex = 0; bitIndex < before.length(); bitIndex++) {
            if (before.charAt(bitIndex) != after.charAt(bitIndex))
                transitions++;
        }

        return transitions;
    }

    /**
     * Perform bus invert encoding on the input state, given the previous state and
     * bus invert state.
     */
    protected EncodeResult busInvertEncode(String newBusState, String previousBusState, boolean busIsInverted) {
        // Calculate hamming distances for invert and non-invert
        int nonInvertedHammingDistance = calculateHammingDistance(previousBusState, newBusState);
        int invertedHammingDistance = calculateHammingDistance(previousBusState, invertBusState(newBusState));

        boolean inversionStateNeedsChange = false;
        String resultBusState = newBusState;

        // Compare hamming distances
        if (nonInvertedHammingDistance < invertedHammingDistance) { // if non-inverted is better
            inversionStateNeedsChange = busIsInverted;
        } else if (nonInvertedHammingDistance > invertedHammingDistance) { // if inverted is better
            inversionStateNeedsChange = !busIsInverted;
            resultBusState = invertBusState(newBusState);
        } else if (busIsInverted) {
            resultBusState = invertBusState(newBusState);
        }

        // Invert bus if needed
        if (inversionStateNeedsChange) {
            busIsInverted = !busIsInverted;
        }

        // Package return values in an EncodeResult
        return new EncodeResult(busIsInverted, resultBusState);
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        // Default bus state is not inverted
        busIsInvertedState = false;
    }

    @Override
    protected void updateOutputPorts(String newEncodedBusState) throws IllegalActionException {
        super.updateOutputPorts(newEncodedBusState);

        // Update the bus invert port, seperate to allow overriding
        updateBusInvertPort();
    }

    /**
     * Update the bus invert port state. This is separate so it can be overridden.
     */
    protected void updateBusInvertPort() throws IllegalActionException {
        outputPortBusInvert.send(DEFAULT_CHANNEL, new StringToken(busIsInvertedState ? "1" : "0"));
    }

    @Override
    protected String encode(String newBusStateString) {
        // Perform encoding
        EncodeResult encodingResult = busInvertEncode(newBusStateString, previousBusState, busIsInvertedState);

        // Update bus inversion state
        busIsInvertedState = encodingResult.busIsInverted;

        // return encoded bus state
        return encodingResult.busState;
    }

}
