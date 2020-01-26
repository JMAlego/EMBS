import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class XorEncoder extends TypedAtomicActor {

    private TypedIOPort dataBusState    = new TypedIOPort(this, "Data Bus State", true, false);
    private TypedIOPort addressBusState = new TypedIOPort(this, "Address Bus State", true, false);

    private TypedIOPort dataBusStateEncoded    = new TypedIOPort(this, "Encoded Data Bus State", false, true);
    private TypedIOPort addressBusStateEncoded = new TypedIOPort(this, "Encoded Address Bus State", false, true);

    private String previousDataBusState;
    private String previousAddressBusState;

    private static final int    DEFAULT_CHANNEL   = 0;
    private static final String DEFAULT_BUS_STATE = "0000000000000000";

    public XorEncoder(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);

        dataBusStateEncoded.setTypeEquals(BaseType.STRING);
        addressBusStateEncoded.setTypeEquals(BaseType.STRING);
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        previousDataBusState = DEFAULT_BUS_STATE;
        previousAddressBusState = DEFAULT_BUS_STATE;
    }

    protected String encode(String newBusState, String previousBusState) {
        int xored = Integer.parseInt(newBusState, 2) ^ Integer.parseInt(previousBusState, 2);
        return Integer.toBinaryString(0x10000 | xored).substring(1);
    }

    private void processAddressTransition(String newInputState) {
        String result = encode(newInputState, previousAddressBusState);
        previousAddressBusState = result;
    }

    private void processDataTransition(String newInputState) {
        String result = encode(newInputState, previousDataBusState);
        previousDataBusState = result;
    }

    @Override
    public void fire() throws IllegalActionException {
        if (addressBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newAddressBusState = addressBusState.get(DEFAULT_CHANNEL);
            String newAddressBusStateString = ((StringToken) newAddressBusState).stringValue();
            processAddressTransition(newAddressBusStateString);
            addressBusStateEncoded.send(DEFAULT_CHANNEL, new StringToken(previousAddressBusState));
        }

        if (dataBusState.hasToken(DEFAULT_CHANNEL)) {
            Token newDataBusState = dataBusState.get(DEFAULT_CHANNEL);
            String newDataBusStateString = ((StringToken) newDataBusState).stringValue();
            processDataTransition(newDataBusStateString);
            dataBusStateEncoded.send(DEFAULT_CHANNEL, new StringToken(previousDataBusState));
        }
    }
}
