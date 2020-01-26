import ptolemy.actor.TypedIOPort;
import ptolemy.data.IntToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class TransitionActivityBusInvert extends TransitionActivityBase {

    protected int addressBusInvertActivitySoFar;
    protected int dataBusInvertActivitySoFar;

    protected boolean dataBusIsInverted;
    protected boolean addressBusIsInverted;

    private TypedIOPort addressInvertTransitionCount;
    private TypedIOPort dataInvertTransitionCount;
    private TypedIOPort addressTransitionCountWithInvert;
    private TypedIOPort dataTransitionCountWithInvert;
    private TypedIOPort combinedTransitionCountWithInvert;

    public TransitionActivityBusInvert(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        addressInvertTransitionCount = new TypedIOPort(this, "Address Invert Transition Count", false, true);
        dataInvertTransitionCount = new TypedIOPort(this, "Data Invert Transition Count", false, true);
        addressTransitionCountWithInvert = new TypedIOPort(this, "Address Transition Count With Invert", false, true);
        dataTransitionCountWithInvert = new TypedIOPort(this, "Data Transition Count With Invert", false, true);
        combinedTransitionCountWithInvert = new TypedIOPort(this, "Combined Transition Count With Invert", false, true);

        addressInvertTransitionCount.setTypeEquals(BaseType.INT);
        dataInvertTransitionCount.setTypeEquals(BaseType.INT);

        addressTransitionCountWithInvert.setTypeEquals(BaseType.INT);
        dataTransitionCountWithInvert.setTypeEquals(BaseType.INT);
        combinedTransitionCountWithInvert.setTypeEquals(BaseType.INT);
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();

        dataBusIsInverted = false;
        addressBusIsInverted = false;

        addressBusInvertActivitySoFar = 0;
        dataBusInvertActivitySoFar = 0;
    }

    protected String invertBusState(String busState) {
        // '?' Acts as an intermediary so that we don't end up with all zeros.
        return busState.replace('0', '?').replace('1', '0').replace('?', '1');
    }

    @Override
    protected void processAddressTransition(String newInputState) {
        int nonInvertedHammingDistance = calculateHammingDistance(previousAddressBusState, newInputState);
        int invertedHammingDistance = calculateHammingDistance(previousAddressBusState, invertBusState(newInputState));
        int finalHammingDistance = nonInvertedHammingDistance;

        boolean inversionStateNeedsChange = false;
        String newBusState = newInputState;

        if (nonInvertedHammingDistance < invertedHammingDistance) { // if non-inverted is better
            inversionStateNeedsChange = addressBusIsInverted;
        } else if (nonInvertedHammingDistance > invertedHammingDistance) { // if inverted is better
            inversionStateNeedsChange = !addressBusIsInverted;
            finalHammingDistance = invertedHammingDistance;
            newBusState = invertBusState(newInputState);
        } else if (addressBusIsInverted) {
            finalHammingDistance = invertedHammingDistance;
            newBusState = invertBusState(newInputState);
        }

        if (inversionStateNeedsChange) {
            // We invert the bus.
            addressBusIsInverted = !addressBusIsInverted;
            addressBusInvertActivitySoFar++;
        }

        addressBusActivitySoFar += finalHammingDistance;
        previousAddressBusState = newBusState;
    }

    @Override
    protected void processDataTransition(String newInputState) {
        int nonInvertedHammingDistance = calculateHammingDistance(previousDataBusState, newInputState);
        int invertedHammingDistance = calculateHammingDistance(previousDataBusState, invertBusState(newInputState));
        int finalHammingDistance = nonInvertedHammingDistance;

        boolean inversionStateNeedsChange = false;
        String newBusState = newInputState;

        if (nonInvertedHammingDistance < invertedHammingDistance) { // if non-inverted is better
            inversionStateNeedsChange = dataBusIsInverted;
        } else if (nonInvertedHammingDistance > invertedHammingDistance) { // if inverted is better
            inversionStateNeedsChange = !dataBusIsInverted;
            finalHammingDistance = invertedHammingDistance;
            newBusState = invertBusState(newInputState);
        } else if (dataBusIsInverted) {
            newBusState = invertBusState(newInputState);
            finalHammingDistance = invertedHammingDistance;
        }

        if (inversionStateNeedsChange) {
            // We invert the bus.
            dataBusIsInverted = !dataBusIsInverted;
            dataBusInvertActivitySoFar++;
        }

        dataBusActivitySoFar += finalHammingDistance;
        previousDataBusState = newBusState;
    }

    @Override
    protected void processTick() throws IllegalActionException {
        super.processTick();

        addressInvertTransitionCount.send(DEFAULT_CHANNEL, new IntToken(addressBusInvertActivitySoFar));
        dataInvertTransitionCount.send(DEFAULT_CHANNEL, new IntToken(dataBusInvertActivitySoFar));

        addressTransitionCountWithInvert.send(DEFAULT_CHANNEL,
                new IntToken(addressBusInvertActivitySoFar + addressBusActivitySoFar));
        dataTransitionCountWithInvert.send(DEFAULT_CHANNEL,
                new IntToken(dataBusInvertActivitySoFar + dataBusActivitySoFar));
        combinedTransitionCountWithInvert.send(DEFAULT_CHANNEL, new IntToken(addressBusInvertActivitySoFar
                + dataBusActivitySoFar + addressBusInvertActivitySoFar + addressBusActivitySoFar));
    }
}
