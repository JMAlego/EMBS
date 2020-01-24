package lsi.instruction;

/*
 * 
 * Actor represents a bus with separate 16-bit address and data lines connecting masters to a single shared memory.
 *
 * It serves an arbitrary number of masters connected to its input and output ports. It accepts RecordToken instances
 * following the standard format defined in lsi.instruction.Instruction. A single token can encapsulate the contents driven 
 * to its address and/data lines, as well as the arbitration, write and read request signals. Likewise, it uses RecordToken
 * instances to implicitly represent grant and acknowledge signals.
 * 
 * Arbitration of requests is based on fixed priorities, with master at input channel 0 having the highest priority and the 
 * master at input channel n with the lowest priority (where n+1 is the number of masters).
 * 
 * Once given arbitration to a master, the bus forwards its request to the shared memory via its toMemory port and, 
 * in case of a READ transaction, waits for a response on its fromMemory port.
 * 
 * Actor also has three ports for debug purposes:
 * 
 * - debug: outputs the ID of the master that holds arbitration to the bus (or -1 in case of a memory-driven DATA value)
 * - data bus state: upon a change, outputs the state of the data sub-bus, in a string representing a 16-bit binary value 
 * - address bus state: upon a change, outputs the state of the address sub-bus, in a string representing a 16-bit binary value 
 * 
 */

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;


@SuppressWarnings("serial")
public class SingleSharedMemoryBus extends TypedAtomicActor {

	protected int activeMaster, masters;
	protected int[] currentArbitrationRequests;
	protected IntToken[] debugTokens;

	protected RecordToken toSend;
	protected Time sendTime;
	protected boolean toMaster;

	protected TypedIOPort input, output, clk, debug, dataBusState, addressBusState, toMemory, fromMemory;

	public SingleSharedMemoryBus(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException  {

		super(container, name);

		clk = new TypedIOPort(this, "clk", true, false); // clock signal

		
		toMemory = new TypedIOPort(this, "toMemory", false, true); // sends requests to memory
		fromMemory = new TypedIOPort(this, "fromMemory", true, false); // receives data from memory

		
		input = new TypedIOPort(this, "input", true, false); // receives requests from masters 
		output = new TypedIOPort(this, "output", false, true); // outputs data to masters

		// both set as multiports, to accept an arbitrary number of masters
		input.setMultiport(true);
		output.setMultiport(true);

		
		// all ports handle RecordToken following the format 
		// defined within the lsi.instruction.Instruction class
		input.setTypeEquals(Instruction.getTokenType());
		output.setTypeEquals(Instruction.getTokenType());
		fromMemory.setTypeEquals(Instruction.getTokenType());
		toMemory.setTypeEquals(Instruction.getTokenType());


		// state ports, outputs the current state of each sub-bus
		// in binary as a StringToken
		
		dataBusState = new TypedIOPort(this, "data bus state", false, true);
		addressBusState = new TypedIOPort(this, "address bus state", false, true);

		dataBusState.setTypeEquals(BaseType.STRING);
		addressBusState.setTypeEquals(BaseType.STRING);
		
		
		// debug port, outputs the index of the master that has been 
		// granted arbitration of the bus
		debug = new TypedIOPort(this, "debug", false, true);
		debug.setTypeEquals(BaseType.INT);



	}


	public void initialize() throws IllegalActionException{

		super.initialize();
		activeMaster=-1; // no active master upon initialisation

		masters=input.getWidth(); // number of masters obtained from the width of the input multiport

		currentArbitrationRequests = new int[masters]; // instantiate an array to handle arbitration requests

		// create one token per master, to be sent out via debug port
		// avoids creating new tokens, lower memory and processing overheads

		debugTokens = new IntToken[masters+1];

		for(int i=0;i<masters;i++){
			debugTokens[i] = new IntToken(i); // one per master
		}
		debugTokens[masters] = new IntToken(-1); // plus one for memory


		// initialise state-holding variables
		
		toMaster=false;
		toSend=null;

	}

	public void fire() throws IllegalActionException{


		if(clk.hasToken(0)){

			clk.get(0); // consume clock token

			if(toSend!=null){  // data driven to the bus needs to be sent to destination

				if(toMaster){ // if second phase of a read transaction
					
					output.send(activeMaster, toSend); // send response to active master
					debug.send(0,debugTokens[masters]); // send out debug info
					dataBusState.send(0, new StringToken(getDataBusCurrentState(toSend))); // outputs new data bus state
					activeMaster=-1; 	// finish transaction

				}
				else{        // else, first phase of a read or write transaction
					toMemory.send(0, toSend); // send request to memory
					output.send(activeMaster, toSend); // GRANT signal - sends back a token to the successful master to confirm it was granted arbitration
					debug.send(0, debugTokens[activeMaster]); // send out debug info
					addressBusState.send(0,  new StringToken(getAddressBusCurrentState(toSend))); // // outputs new address bus state

					// if request is a WRITE, close the transaction right after sending it to memory
					int type = ((IntToken)toSend.get("type")).intValue();
					if(type==Instruction.WRITE){ 
						activeMaster=-1;  
						dataBusState.send(0,  new StringToken(getDataBusCurrentState(toSend))); // // outputs new data bus state

						
					}

				}

				toSend=null; // confirm destination has been notified
				
			}
		}

		else if(activeMaster!=-1){     //transaction ongoing, check if there's data from memory to be sent

			if(fromMemory.hasToken(0)){
				// send data from memory to active master over the next clock cycle
				toSend = (RecordToken) fromMemory.get(0);
				toMaster=true;

			}

		}		

		else {   // no ongoing transactions, process arbitration requests

			for(int i=0;i<masters;i++){

				if(input.hasToken(i)) currentArbitrationRequests[i]=1;
				else currentArbitrationRequests[i]=0;

			}

			activeMaster = performArbitration();

			if(activeMaster!=-1){ // if there's a successful request

				toSend = (RecordToken)input.get(activeMaster); // queue a read request over the next clock cycle
				toMaster=false;  // read request should be sent to memory

			}
		}

		// discard all remaining arbitration requests received on the current cycle
		for(int i=0;i<masters;i++){

			if(input.hasToken(i)) input.get(i);

		}

	}



	protected int performArbitration(){

		for(int i=0; i<currentArbitrationRequests.length;i++){

			if(currentArbitrationRequests[i]==1) return i;   // performs fixed priority arbitration, with 0 as the highest priority

		}

		return -1;

	}



	protected String getDataBusCurrentState(RecordToken token){
		int data = ((IntToken)token.get("data")).intValue();
		if(data > 65535 || data < 0) return "ERROR";
		else return Integer.toBinaryString(0x10000 | data).substring(1); // adds zero padding by adding then removing a 1 in the 17th place
		
	}
	
	protected String getAddressBusCurrentState(RecordToken token){
		int add = ((IntToken)token.get("address")).intValue();
		if(add > 65535 || add < 0) return "ERROR";
		else return Integer.toBinaryString(0x10000 | add).substring(1); // adds zero padding by adding then removing a 1 in the 17th place
		
	}
	
	


	public void pruneDependencies() {
		super.pruneDependencies();
		removeDependency(input, output);
		removeDependency(input, toMemory);
	}


}