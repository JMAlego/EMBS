import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class TransitionActivityBase extends TypedAtomicActor {

    private TypedIOPort clock                   = new TypedIOPort(this, "clock", true, false);
    private TypedIOPort dataBusState            = new TypedIOPort(this, "Data Bus State", true, false);
    private TypedIOPort addressBusState         = new TypedIOPort(this, "Address Bus State", true, false);
    private TypedIOPort addressTransitionCount  = new TypedIOPort(this, "Address Transition Count", false, true);
    private TypedIOPort dataTransitionCount     = new TypedIOPort(this, "Data Transition Count", false, true);
    private TypedIOPort combinedTransitionCount = new TypedIOPort(this, "Combined Transition Count", false, true);

    protected String previousAddressBusState;
    protected String previousDataBusState;

    protected int addressBusActivitySoFar;
    protected int dataBusActivitySoFar;

    protected static final int    DEFAULT_CHANNEL   = 0;
    protected static final String DEFAULT_BUS_STATE = "0000000000000000";
    protected static final int    BUS_WIDTH         = 16;

    public TransitionActivityBase(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        dataBusState.setTypeEquals(BaseType.STRING);
        addressBusState.setTypeEquals(BaseType.STRING);

        addressTransitionCount.setTypeEquals(BaseType.INT);
        dataTransitionCount.setTypeEquals(BaseType.INT);
        combinedTransitionCount.setTypeEquals(BaseType.INT);
    }

    public void initialize() throws IllegalActionException {
        super.initialize();

        previousAddressBusState = DEFAULT_BUS_STATE;
        previousDataBusState = DEFAULT_BUS_STATE;

        addressBusActivitySoFar = 0;
        dataBusActivitySoFar = 0;
    }

    protected int calculateHammingDistance(String before, String after) {
        int transitions = 0;

        if (before.length() != after.length()) {
            throw new IllegalArgumentException("Both arguments must have the same length.");
        }

        for (int bitIndex = 0; bitIndex < BUS_WIDTH; bitIndex++) {
            if (before.charAt(bitIndex) != after.charAt(bitIndex))
                transitions++;
        }

        return transitions;
    }

    protected void processAddressTransition(String newState) {
        addressBusActivitySoFar += calculateHammingDistance(previousAddressBusState, newState);
        previousAddressBusState = newState;
    }

    protected void processDataTransition(String newState) {
        dataBusActivitySoFar += calculateHammingDistance(previousDataBusState, newState);
        previousDataBusState = newState;
    }

    protected void processTick() throws IllegalActionException {
        if (addressBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newAddressBusState = addressBusState.get(DEFAULT_CHANNEL);
            String newAddressBusStateString = ((StringToken) newAddressBusState).stringValue();
            processAddressTransition(newAddressBusStateString);
        }

        if (dataBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newDataBusState = dataBusState.get(DEFAULT_CHANNEL);
            String newDataBusStateString = ((StringToken) newDataBusState).stringValue();
            processDataTransition(newDataBusStateString);
        }

        addressTransitionCount.send(DEFAULT_CHANNEL, new IntToken(addressBusActivitySoFar));
        dataTransitionCount.send(DEFAULT_CHANNEL, new IntToken(dataBusActivitySoFar));
        combinedTransitionCount.send(DEFAULT_CHANNEL, new IntToken(addressBusActivitySoFar + dataBusActivitySoFar));
    }

    public void fire() throws IllegalActionException {
        if (clock.hasToken(DEFAULT_CHANNEL)) {
            clock.get(DEFAULT_CHANNEL);
            processTick();
        }
    }

}
