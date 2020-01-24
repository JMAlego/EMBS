package lsi.instruction;


/*
 * 
 * Actor represents a 16-bit word LSI Instruction Processor.
 * 
 * It follows the state machine shown in the LSI Instruction Processor specification.
 * 
 * Upon fetch, read or write states, actor keeps issuing arbitration requests to the bus
 * via output port at every clock cycle until a GRANT token is received from the bus on 
 * the input port.
 * 
 * Upon GRANT token reception, actor closes the transaction and carries on in case of a
 * WRITE, or keeps waiting for a DATA token on the input  port in case of a READ.
 * 
 * Upon READ token reception, actor closes the transaction and carries on.
 * 
 * 
 * Actor has a debug port which shows which state of the state machine it is in.
 * 
 * 
 */


import ptolemy.actor.NoRoomException;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.RecordToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class InstructionProcessor extends TypedAtomicActor{

	protected TypedIOPort input, output, debug, clk;
	protected Parameter initPC;
	protected int PC;


	protected int state;
	protected int timer=0;
	protected int raddress;
	protected int rdata;


	protected static final int EXECUTE = 0;
	protected static final int READ = 1;
	protected static final int WRITE = 2;
	protected static final int FETCH = 3;
	protected static final int DECODE = 4;
	protected static final int DATA_WAIT = 5;






	public InstructionProcessor(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException  {

		super(container, name);

		// ports
		input = new TypedIOPort(this, "input", true, false);
		output = new TypedIOPort(this, "output", false, true);
		debug = new TypedIOPort(this, "debug", false, true);
		clk = new TypedIOPort(this, "clk", true, false);

		input.setTypeEquals(Instruction.getTokenType());
		output.setTypeEquals(Instruction.getTokenType());
		debug.setTypeEquals(BaseType.INT);


		initPC = new Parameter(this, "initial PC");


		initPC.setTypeEquals(BaseType.INT);
	}


	public void initialize() throws IllegalActionException{

		PC = ((IntToken)initPC.getToken()).intValue();
		setState(InstructionProcessor.FETCH);
		timer=0;
	}




	public void fire() throws IllegalActionException{



		if(clk.hasToken(0)){

			clk.get(0); // consume clock token
			if(timer!=0) timer--;  // decrement timer


			//
			// INPUT-TRIGGERED TRANSITIONS
			//

			if(input.hasToken(0)){
				//
				// FETCH GRANT RECEIVED
				//
				if(state == InstructionProcessor.FETCH){
					input.get(0); // GRANT consumed
					PC++; // increment PC
					setState(InstructionProcessor.DECODE); // get ready to DECODE instruction when it comes from memory
				}
				//
				// WRITE GRANT RECEIVED
				//
				else if(state == InstructionProcessor.WRITE){
					input.get(0); // GRANT received and consumed
					setState(InstructionProcessor.FETCH); // go back to FETCH state in the next cycle
				}
				//
				// READ GRANT RECEIVED
				//
				else if(state == InstructionProcessor.READ){
					input.get(0); // GRANT received and consumed
					setState(InstructionProcessor.DATA_WAIT);  // get ready to read DATA when it comes from memory
				}
				//
				// READ DATA ACK RECEIVED
				//
				else if(state == InstructionProcessor.DATA_WAIT){
					input.get(0); // DATA ACK received
					setState(InstructionProcessor.FETCH);// go back to FETCH state  in the next cycle
				}
				//
				// DECODE FETCHED INSTRUCTION
				//
				else if(state== InstructionProcessor.DECODE){

					RecordToken token = (RecordToken)input.get(0);
					IntToken insttype = (IntToken) token.get("type");

					if(insttype.intValue()==Instruction.EXECUTE){   // must wait for a number of clock cycles

						IntToken cycles = (IntToken) token.get("time"); 
						timer = cycles.intValue(); // sets timer
						setState(InstructionProcessor.EXECUTE);  // changes state to EXECUTE
					}
					else if(insttype.intValue()==Instruction.JUMP){  // must change the content of the PC
						IntToken newPC = (IntToken) token.get("address"); 
						PC = newPC.intValue(); // updates the PC
						setState(InstructionProcessor.FETCH); // changes state to FETCH
					}
					else if(insttype.intValue()==Instruction.WRITE){  // must issue a write request
						raddress = ((IntToken) token.get("address")).intValue();
						rdata = ((IntToken) token.get("data")).intValue();
						setState(InstructionProcessor.WRITE); // changes state to WRITE
					}
					else if(insttype.intValue()==Instruction.READ){  // must issue a read request
						raddress = ((IntToken) token.get("address")).intValue();
						setState(InstructionProcessor.READ); // changes state to READ
					}
				}

			}

			//
			// CLOCK-TRIGGERED TRANSITIONS
			//
			else{
				//
				// EXECUTE
				//
				if(state== InstructionProcessor.EXECUTE){
					if(timer==0){ // check if current execution has been finished
						setState(InstructionProcessor.FETCH);  // if finished, move to FETCH state
					}			
				}
				//
				// WRITE (again, potentially), no state change
				//
				else if(state == InstructionProcessor.WRITE){
					output.send(0, new Instruction(Instruction.WRITE, rdata, raddress, -1).getToken());
				}
				//
				// READ (again, potentially), no state change
				//
				else if(state == InstructionProcessor.READ){
					output.send(0, new Instruction(Instruction.READ, -1, raddress, -1).getToken());
				}
				//
				// FETCH (again, potentially), no state change
				//
				else if(state == InstructionProcessor.FETCH){
					output.send(0, new Instruction(Instruction.READ, -1, PC, -1).getToken()); // issues a read request to the memory position in the PC
				}
			}
		}


	}




	protected void setState(int newstate) throws NoRoomException, IllegalActionException{

		state = newstate;
		debug.send(0,  new IntToken(state));

	}




}
