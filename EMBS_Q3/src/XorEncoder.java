import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * Transition Signalling Code (XOR) encoder actor, based on the
 * {@link BusEncoder} abstract base.
 */
@SuppressWarnings("serial")
public class XorEncoder extends BusEncoder {

    public XorEncoder(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
    }

    @Override
    protected String encode(String newBusStateString) {
        // XOR the current source bus value with the previous source bus value
        final int xored = Integer.parseInt(newBusStateString, 2) ^ Integer.parseInt(previousBusStateUnencoded, 2);

        // Re-encode as a binary string
        return Integer.toBinaryString(((int) Math.pow(2, busWidth)) | xored).substring(1);
    }
}
