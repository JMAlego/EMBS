import java.util.Arrays;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * An abstract base for bus encoders.
 */
@SuppressWarnings("serial")
public abstract class BusEncoder extends TypedAtomicActor {
    // Ports
    private TypedIOPort inputPortBusState;
    private TypedIOPort outputPortBusStateEncoded;

    // Parameters
    private StringParameter busWidthParameter;

    // State and configuration information
    protected String previousBusState;
    protected String previousBusStateUnencoded;
    protected int    busWidth;

    // Static default values
    protected static final int  DEFAULT_CHANNEL   = 0;
    protected static final char DEFAULT_BUS_STATE = '0';

    public BusEncoder(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);

        // Setup input and output ports
        inputPortBusState = new TypedIOPort(this, "Bus State", true, false);
        outputPortBusStateEncoded = new TypedIOPort(this, "Encoded Bus State", false, true);

        // Set input and output types
        inputPortBusState.setTypeEquals(BaseType.STRING);
        outputPortBusStateEncoded.setTypeEquals(BaseType.STRING);

        // Create parameter for bus width
        busWidthParameter = new StringParameter(this, "Bus Width");
        busWidthParameter.setExpression("16");
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        // Handle bus width parameter
        busWidth = Integer.valueOf(busWidthParameter.getExpression());

        // Fill default bus state
        char[] busDefaultStateArray = new char[busWidth];
        Arrays.fill(busDefaultStateArray, DEFAULT_BUS_STATE);
        String defaultBusState = new String(busDefaultStateArray);

        // Set previous bus state to default
        previousBusState = defaultBusState;
        previousBusStateUnencoded = defaultBusState;
    }

    /**
     * Performs encoding of some kind and returns the encoded bus state.
     */
    protected abstract String encode(String newBusStateString);

    /**
     * Updates output ports, should be overridden if ports encodings change.
     */
    protected void updateOutputPorts(String newEncodedBusState) throws IllegalActionException {
        // Send output
        outputPortBusStateEncoded.send(DEFAULT_CHANNEL, new StringToken(newEncodedBusState));
    }

    @Override
    public void fire() throws IllegalActionException {
        super.fire();

        // If our input port has a value
        if (inputPortBusState.hasToken(DEFAULT_CHANNEL)) {
            // Get the input binary string
            Token newBusState = inputPortBusState.get(DEFAULT_CHANNEL);
            String newBusStateString = ((StringToken) newBusState).stringValue();
            

            // Run encoding
            String newEncodedBusState = encode(newBusStateString);

            // Outdate all output ports
            updateOutputPorts(newEncodedBusState);

            // Update previous state
            previousBusState = newEncodedBusState;
            previousBusStateUnencoded = newBusStateString;
        }
    }

}
