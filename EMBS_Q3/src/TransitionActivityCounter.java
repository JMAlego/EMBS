import java.util.Arrays;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * Actor to count transition activity on an input bus.
 */
@SuppressWarnings("serial")
public class TransitionActivityCounter extends TypedAtomicActor {

    // Ports
    private TypedIOPort busState        = new TypedIOPort(this, "Bus State", true, false);
    private TypedIOPort transitionCount = new TypedIOPort(this, "Transition Count", false, true);

    // Previous bus state storage
    protected String previousBusState;

    // Activity counter
    protected int busActivitySoFar;

    // Default values
    private static final int  DEFAULT_CHANNEL   = 0;
    private static final char DEFAULT_BUS_STATE = '0';

    // Bus width parameter
    private StringParameter busWidthParameter;

    // Bus width storage
    private int busWidth = 16;

    public TransitionActivityCounter(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        // Bus state is a string
        busState.setTypeEquals(BaseType.STRING);

        // Transition count is an integer
        transitionCount.setTypeEquals(BaseType.INT);

        // Setup the bus width parameter with 16-bit as default
        busWidthParameter = new StringParameter(this, "Bus Width");
        busWidthParameter.setExpression("16");
    }

    public void initialize() throws IllegalActionException {
        super.initialize();

        // Get the bus width parameter
        busWidth = Integer.valueOf(busWidthParameter.getExpression());

        // Create a default state string
        char[] busDefaultStateArray = new char[busWidth];
        Arrays.fill(busDefaultStateArray, DEFAULT_BUS_STATE);
        String defaultBusState = new String(busDefaultStateArray);

        // Set default state for previous bus state storage
        previousBusState = defaultBusState;

        // Reset activity to 0
        busActivitySoFar = 0;
    }

    /**
     * Calculate the hamming distance from the before string to the after string.
     * Both arguments should be of the same length otherwise an
     * {@link IllegalArgumentException} will be thrown.
     */
    protected int calculateHammingDistance(String before, String after) {
        // Transition counter
        int transitions = 0;

        // Sanity check
        if (before.length() != busWidth || after.length() != busWidth) {
            throw new IllegalArgumentException("Both arguments must have the same length.");
        }

        // Loop through bits comparing them
        for (int bitIndex = 0; bitIndex < busWidth; bitIndex++) {
            if (before.charAt(bitIndex) != after.charAt(bitIndex))
                transitions++;
        }

        return transitions;
    }

    @Override
    public void fire() throws IllegalActionException {
        // If there is an updated bus state
        if (busState.hasToken(DEFAULT_CHANNEL)) {
            // Read the bus state into a string
            Token newBusState = busState.get(DEFAULT_CHANNEL);
            String newAddressBusStateString = ((StringToken) newBusState).stringValue();

            // Add the hamming distance from the last value to the activity so far counter
            busActivitySoFar += calculateHammingDistance(previousBusState, newAddressBusStateString);

            // Update previous state to current state
            previousBusState = newAddressBusStateString;

            // Send current transition count
            transitionCount.send(DEFAULT_CHANNEL, new IntToken(busActivitySoFar));
        }
    }

}
