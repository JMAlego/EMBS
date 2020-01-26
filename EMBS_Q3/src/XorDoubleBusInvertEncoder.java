import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class XorDoubleBusInvertEncoder extends BusDoubleInvertEncoder {

    public XorDoubleBusInvertEncoder(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
    }

    private String xorBusStates(String newBusState, String previousBusState) {
        final int xored = Integer.parseInt(newBusState, 2) ^ Integer.parseInt(previousBusState, 2);
        return Integer.toBinaryString(0x100 | xored).substring(1);
    }

    @Override
    protected EncodeResult encode(String newBusState, String previousBusState, boolean busIsInverted) {
        newBusState = xorBusStates(newBusState, busIsInverted ? invertBusState(previousBusState) : previousBusState);

        return super.encode(newBusState, previousBusState, busIsInverted);
    }

}
