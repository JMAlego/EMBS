import java.util.Arrays;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class TransitionActivityCounter extends TypedAtomicActor {

    private TypedIOPort busState        = new TypedIOPort(this, "Bus State", true, false);
    private TypedIOPort transitionCount = new TypedIOPort(this, "Transition Count", false, true);

    protected String previousBusState;

    protected int busActivitySoFar;

    protected static final int  DEFAULT_CHANNEL   = 0;
    protected static final char DEFAULT_BUS_STATE = '0';

    private StringParameter busWidthParameter;
    private int             busWidth = 16;

    public TransitionActivityCounter(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        busState.setTypeEquals(BaseType.STRING);

        transitionCount.setTypeEquals(BaseType.INT);

        busWidthParameter = new StringParameter(this, "Bus Width");
        busWidthParameter.setExpression("16");
    }

    public void initialize() throws IllegalActionException {
        super.initialize();

        busWidth = Integer.valueOf(busWidthParameter.getExpression());

        char[] busDefaultStateArray = new char[busWidth];
        Arrays.fill(busDefaultStateArray, DEFAULT_BUS_STATE);
        String defaultBusState = new String(busDefaultStateArray);

        previousBusState = defaultBusState;
        busActivitySoFar = 0;
    }

    protected int calculateHammingDistance(String before, String after) {
        int transitions = 0;

        if (before.length() != busWidth || after.length() != busWidth) {
            throw new IllegalArgumentException("Both arguments must have the same length.");
        }

        for (int bitIndex = 0; bitIndex < busWidth; bitIndex++) {
            if (before.charAt(bitIndex) != after.charAt(bitIndex))
                transitions++;
        }

        return transitions;
    }

    public void fire() throws IllegalActionException {
        if (busState.hasToken(DEFAULT_CHANNEL)) {
            Token newBusState = busState.get(DEFAULT_CHANNEL);
            String newAddressBusStateString = ((StringToken) newBusState).stringValue();

            busActivitySoFar += calculateHammingDistance(previousBusState, newAddressBusStateString);
            previousBusState = newAddressBusStateString;

            transitionCount.send(DEFAULT_CHANNEL, new IntToken(busActivitySoFar));
        }
    }

}
