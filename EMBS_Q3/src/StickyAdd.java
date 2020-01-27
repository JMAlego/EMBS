import java.util.Arrays;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * Actor to perform a "sticky" add, e.g. an add where each channel remembers
 * it's last value and all channels are added together, so if there is no new
 * value the last one is used. This exists to stop the need synchronisation
 * between actors.
 */
@SuppressWarnings("serial")
public class StickyAdd extends TypedAtomicActor {

    // Ports
    private TypedIOPort addIn  = new TypedIOPort(this, "Add", true, false);
    private TypedIOPort sumOut = new TypedIOPort(this, "Sum", false, true);

    // Default channel
    private static final int DEFAULT_CHANNEL = 0;

    // The memory that makes the add "sticky"
    private int[] memory;

    public StickyAdd(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);

        // Input allows multiple ports
        addIn.setMultiport(true);

        // All inputs and outputs are integers
        addIn.setTypeEquals(BaseType.INT);
        sumOut.setTypeEquals(BaseType.INT);
    }

    public void initialize() throws IllegalActionException {
        super.initialize();

        // Initialise the memory based on current input width
        memory = new int[addIn.getWidth()];

        // Fill memory with zeros
        Arrays.fill(memory, 0);
    }

    public void fire() throws IllegalActionException {
        // Current sum
        int sum = 0;

        // For each channel
        for (int channelIndex = 0; channelIndex < addIn.getWidth(); channelIndex++) {
            // If channel has token
            if (addIn.hasToken(channelIndex)) {
                // Get the token value and update the memory for that channel
                IntToken token = (IntToken) addIn.get(channelIndex);
                memory[channelIndex] = token.intValue();
            }
            // Add the current channel to the sum (this value may or may not have just been
            // updated)
            sum += memory[channelIndex];
        }

        // Send out sum
        sumOut.send(DEFAULT_CHANNEL, new IntToken(sum));
    }

}
