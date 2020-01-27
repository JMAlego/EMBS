import java.util.Arrays;

import ptolemy.data.StringToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * Split 8-bit (M-Bit) Bus-Invert encoder actor, based on the
 * {@link BusInvertEncoder}
 * actor.
 */
@SuppressWarnings("serial")
public class ByteBusInvertEncoder extends BusInvertEncoder {

    // State of invert lines
    private boolean[] busIsInvertedState;

    // Number of byte blocks in bus
    private int byteBlocksCount;

    // Block to split the bus into
    private static final int BYTE_BLOCK_SIZE = 8;

    public ByteBusInvertEncoder(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
    }

    @Override
    protected void updateBusInvertPort() throws IllegalActionException {
        // String builder so we can build the state of the invert lines
        StringBuilder busInvertLines = new StringBuilder();

        // For each block
        for (int busInvertIndex = 0; busInvertIndex < byteBlocksCount; busInvertIndex++) {
            // If bus inverted append a '1' else a '0'
            busInvertLines.append(busIsInvertedState[busInvertIndex] ? '1' : '0');
        }

        // Set port output to built string
        outputPortBusInvert.send(DEFAULT_CHANNEL, new StringToken(busInvertLines.toString()));
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        // Calculate number of byte blocks
        byteBlocksCount = (int) Math.ceil((double) busWidth / BYTE_BLOCK_SIZE);

        // Initialise invert lines
        busIsInvertedState = new boolean[byteBlocksCount];

        // Set invert lines to false by default
        Arrays.fill(busIsInvertedState, false);
    }

    @Override
    protected String encode(String newBusStateString) {
        // String builder to store full bus state from 8-bit blocks
        StringBuilder finalBusState = new StringBuilder();

        // For each block of 8-bits
        for (int busInvertIndex = 0; busInvertIndex < byteBlocksCount; busInvertIndex++) {
            // Extract block of 8-bits from current and previous bus state
            final String currentBlockState = newBusStateString.substring(busInvertIndex * BYTE_BLOCK_SIZE,
                    (busInvertIndex + 1) * BYTE_BLOCK_SIZE);
            final String previousBlockState = previousBusState.substring(busInvertIndex * BYTE_BLOCK_SIZE,
                    (busInvertIndex + 1) * BYTE_BLOCK_SIZE);

            // Perform bus invert encoding on the block
            EncodeResult encodingResult = busInvertEncode(currentBlockState, previousBlockState,
                    busIsInvertedState[busInvertIndex]);

            // If the bus was inverted, update the current block's invert state
            busIsInvertedState[busInvertIndex] = encodingResult.busIsInverted;

            // Add the bus back onto the final bus
            finalBusState.append(encodingResult.busState);
        }

        // Return encoded full bus
        return finalBusState.toString();
    }
}
