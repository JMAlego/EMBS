import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class PartialBusInvertEncoder extends BusInvertEncoder {

    public PartialBusInvertEncoder(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
    }

    @Override
    protected String invertBusState(String busState) {
        return super.invertBusState(busState.substring(8)) + busState.substring(8);
    }

}
