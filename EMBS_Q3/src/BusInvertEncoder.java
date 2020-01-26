import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class BusInvertEncoder extends TypedAtomicActor {

    protected static class EncodeResult {
        public boolean busIsInverted;
        public String  busState;

        public EncodeResult(boolean busIsInverted, String busState) {
            this.busIsInverted = busIsInverted;
            this.busState = busState;
        }
    }

    private boolean dataBusIsInverted;
    private boolean addressBusIsInverted;

    private TypedIOPort dataBusState    = new TypedIOPort(this, "Data Bus State", true, false);
    private TypedIOPort addressBusState = new TypedIOPort(this, "Address Bus State", true, false);

    private TypedIOPort dataBusStateEncoded    = new TypedIOPort(this, "Encoded Data Bus State", false, true);
    private TypedIOPort addressBusStateEncoded = new TypedIOPort(this, "Encoded Address Bus State", false, true);

    private TypedIOPort dataBusInvert    = new TypedIOPort(this, "Data Bus Invert", false, true);
    private TypedIOPort addressBusInvert = new TypedIOPort(this, "Address Bus Invert", false, true);

    private String previousDataBusState;
    private String previousAddressBusState;

    private static final int    DEFAULT_CHANNEL   = 0;
    private static final String DEFAULT_BUS_STATE = "0000000000000000";
    private static final int    BUS_WIDTH         = 16;

    private static final StringToken BUS_INVERTED     = new StringToken("1");
    private static final StringToken BUS_NOT_INVERTED = new StringToken("0");

    public BusInvertEncoder(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        dataBusStateEncoded.setTypeEquals(BaseType.STRING);
        addressBusStateEncoded.setTypeEquals(BaseType.STRING);

        dataBusInvert.setTypeEquals(BaseType.STRING);
        addressBusInvert.setTypeEquals(BaseType.STRING);
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        dataBusIsInverted = false;
        addressBusIsInverted = false;

        previousDataBusState = DEFAULT_BUS_STATE;
        previousAddressBusState = DEFAULT_BUS_STATE;
    }

    protected String invertBusState(String busState) {
        // '?' Acts as an intermediary so that we don't end up with all zeros.
        return busState.replace('0', '?').replace('1', '0').replace('?', '1');
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

    protected EncodeResult encode(String newBusState, String previousBusState, boolean busIsInverted) {
        int nonInvertedHammingDistance = calculateHammingDistance(previousBusState, newBusState);
        int invertedHammingDistance = calculateHammingDistance(previousBusState, invertBusState(newBusState));

        boolean inversionStateNeedsChange = false;
        String resultBusState = newBusState;

        if (nonInvertedHammingDistance < invertedHammingDistance) { // if non-inverted is better
            inversionStateNeedsChange = busIsInverted;
        } else if (nonInvertedHammingDistance > invertedHammingDistance) { // if inverted is better
            inversionStateNeedsChange = !busIsInverted;
            resultBusState = invertBusState(newBusState);
        } else if (busIsInverted) {
            resultBusState = invertBusState(newBusState);
        }

        if (inversionStateNeedsChange) {
            // We invert the bus.
            busIsInverted = !busIsInverted;
        }

        return new EncodeResult(busIsInverted, resultBusState);
    }

    private void processAddressTransition(String newInputState) {
        EncodeResult result = encode(newInputState, previousAddressBusState, addressBusIsInverted);
        addressBusIsInverted = result.busIsInverted;
        previousAddressBusState = result.busState;
    }

    private void processDataTransition(String newInputState) {
        EncodeResult result = encode(newInputState, previousDataBusState, dataBusIsInverted);
        dataBusIsInverted = result.busIsInverted;
        previousDataBusState = result.busState;
    }

    @Override
    public void fire() throws IllegalActionException {
        if (addressBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newAddressBusState = addressBusState.get(DEFAULT_CHANNEL);
            String newAddressBusStateString = ((StringToken) newAddressBusState).stringValue();
            processAddressTransition(newAddressBusStateString);
            addressBusStateEncoded.send(DEFAULT_CHANNEL, new StringToken(previousAddressBusState));
            addressBusInvert.send(DEFAULT_CHANNEL, addressBusIsInverted ? BUS_INVERTED : BUS_NOT_INVERTED);
        }

        if (dataBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newDataBusState = dataBusState.get(DEFAULT_CHANNEL);
            String newDataBusStateString = ((StringToken) newDataBusState).stringValue();
            processDataTransition(newDataBusStateString);
            dataBusStateEncoded.send(DEFAULT_CHANNEL, new StringToken(previousDataBusState));
            dataBusInvert.send(DEFAULT_CHANNEL, dataBusIsInverted ? BUS_INVERTED : BUS_NOT_INVERTED);
        }
    }
}
