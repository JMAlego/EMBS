import java.util.Arrays;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class StickyAdd extends TypedAtomicActor {

    private TypedIOPort addIn  = new TypedIOPort(this, "Add", true, false);
    private TypedIOPort sumOut = new TypedIOPort(this, "Sum", false, true);

    private static final int DEFAULT_CHANNEL = 0;

    private int[] memory;

    public StickyAdd(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);

        addIn.setMultiport(true);

        addIn.setTypeEquals(BaseType.INT);
        sumOut.setTypeEquals(BaseType.INT);
    }

    public void initialize() throws IllegalActionException {
        super.initialize();

        memory = new int[addIn.getWidth()];
        Arrays.fill(memory, 0);
    }

    public void fire() throws IllegalActionException {
        int sum = 0;

        for (int portIndex = 0; portIndex < addIn.getWidth(); portIndex++) {
            if (addIn.hasToken(portIndex)) {
                IntToken token = (IntToken) addIn.get(portIndex);
                memory[portIndex] = token.intValue();
            }
            sum += memory[portIndex];
        }

        sumOut.send(DEFAULT_CHANNEL, new IntToken(sum));
    }

}
