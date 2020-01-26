import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class BusDoubleInvertEncoder extends TypedAtomicActor {

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
    private boolean dataBusIsInverted2;
    private boolean addressBusIsInverted2;

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

    public BusDoubleInvertEncoder(CompositeEntity container, String name)
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
        dataBusIsInverted2 = false;
        addressBusIsInverted2 = false;

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

        for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
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
        String resultAddressBusState = "";
        EncodeResult result = encode(newInputState.substring(0, 8), previousAddressBusState.substring(0, 8),
                addressBusIsInverted);
        addressBusIsInverted = result.busIsInverted;
        resultAddressBusState = result.busState;
        EncodeResult result2 = encode(newInputState.substring(8), previousAddressBusState.substring(8),
                addressBusIsInverted2);
        addressBusIsInverted2 = result2.busIsInverted;
        resultAddressBusState += result2.busState;
        previousAddressBusState = resultAddressBusState;
    }

    private void processDataTransition(String newInputState) {
        String resultDataBusState = "";
        EncodeResult result = encode(newInputState.substring(0, 8), previousDataBusState.substring(0, 8),
                dataBusIsInverted);
        dataBusIsInverted = result.busIsInverted;
        resultDataBusState = result.busState;
        EncodeResult result2 = encode(newInputState.substring(8), previousDataBusState.substring(8),
                dataBusIsInverted2);
        dataBusIsInverted2 = result2.busIsInverted;
        resultDataBusState += result2.busState;
        previousDataBusState = resultDataBusState;
    }

    @Override
    public void fire() throws IllegalActionException {
        if (addressBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newAddressBusState = addressBusState.get(DEFAULT_CHANNEL);
            String newAddressBusStateString = ((StringToken) newAddressBusState).stringValue();
            processAddressTransition(newAddressBusStateString);
            StringToken inversionState = new StringToken(
                    (addressBusIsInverted ? "1" : "0") + (addressBusIsInverted2 ? "1" : "0"));
            addressBusStateEncoded.send(DEFAULT_CHANNEL, new StringToken(previousAddressBusState));
            addressBusInvert.send(DEFAULT_CHANNEL, inversionState);
        }

        if (dataBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newDataBusState = dataBusState.get(DEFAULT_CHANNEL);
            String newDataBusStateString = ((StringToken) newDataBusState).stringValue();
            processDataTransition(newDataBusStateString);
            dataBusStateEncoded.send(DEFAULT_CHANNEL, new StringToken(previousDataBusState));
            StringToken inversionState = new StringToken(
                    (dataBusIsInverted ? "1" : "0") + (dataBusIsInverted2 ? "1" : "0"));
            dataBusInvert.send(DEFAULT_CHANNEL, inversionState);
        }
    }
}
