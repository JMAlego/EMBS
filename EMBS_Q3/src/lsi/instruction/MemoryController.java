package lsi.instruction;

/*
 * 
 * Actor represents a 16-bit word Memory and its controller.
 * 
 * Memory contents are instances of the Instruction class.
 * 
 * Its contents are initialised out of a text file specified as a parameter, which is parsed upon initialisation.
 * 
 * It receives RecordToken instances (following the lsi.instruction.Instruction format) over its input port, and reacts
 * to read or write requests accordingly.
 * 
 *  * 
 */


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.StringTokenizer;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

@SuppressWarnings("serial")
public class MemoryController extends TypedAtomicActor {


	protected TypedIOPort input, output, clk;
	protected Instruction[] memory;
	int readAddress;
	StringParameter memoryFile;

	public MemoryController(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException  {

		super(container, name);

		input = new TypedIOPort(this, "input", true, false);
		output = new TypedIOPort(this, "output", false, true);

		clk = new TypedIOPort(this, "clk", true, false);


		input.setTypeEquals(Instruction.getTokenType());
		output.setTypeEquals(Instruction.getTokenType());

		memoryFile = new StringParameter(this, "memory file");
		memoryFile.setExpression("test");


	}

	@Override
	public void initialize() throws IllegalActionException{

		readAddress = -1;
		memory = new Instruction[65536];
		for(int i=0;i<memory.length;i++){

			memory[i]= new Instruction(-1, 0, -1, -1); 						// data: 0

		}


		if(memoryFile.stringValue().equals("test")){
			createTestProgram();
		}
		else{
			try{
				FileReader f = new FileReader(memoryFile.stringValue());
				BufferedReader r = new BufferedReader(f);
				String line;
				while ((line = r.readLine()) != null) {
					StringTokenizer st = new StringTokenizer(line);
					int storage = Integer.parseInt(st.nextToken()); 
					int type = Integer.parseInt(st.nextToken()); 
					int data = Integer.parseInt(st.nextToken()); 
					int address = Integer.parseInt(st.nextToken()); 
					int time = Integer.parseInt(st.nextToken()); 

					memory[storage] = new Instruction(type, data, address, time);
				}
				
				r.close();
			}
			catch(IOException e){
				System.out.println("Reading from file failed: " + e);
			}

		}

	}


	@Override
	public void fire()throws IllegalActionException{


		if(clk.hasToken(0)){

			clk.get(0); // consume clock token

			if(readAddress!=-1){ //if a read has been requested, perform it

				output.send(0, memory[readAddress].getToken()); // sends back the content of the requested memory address
				readAddress=-1;  // confirm that read has been performed
			}	
		}

		// receive request

		else if(input.hasToken(0)){ 

			RecordToken t = (RecordToken)input.get(0);
			int type = ((IntToken)t.get("type")).intValue();
			if(type==Instruction.READ){  // set address to be read and sent back on the next clock cycle
				readAddress = ((IntToken)t.get("address")).intValue();
				assert readAddress != -1;
			}
			else if(type==Instruction.WRITE){ // write to memory immediately
				int address = ((IntToken)t.get("address")).intValue();
				assert address != -1;
				int data = ((IntToken)t.get("data")).intValue();
				assert data != -1;

				memory[address] = new Instruction(-1,data,-1,-1);  // write to memory
			}

		}		

	}

	@Override
	public void wrapup(){

		for(int i=0;i<memory.length;i++){
			System.out.println(i+" "+memory[i]);
		}
	}





	public void createTestProgram(){

		memory[0] = new Instruction(Instruction.READ, 41260, 10, -1);  		//READ 10
		memory[1] = new Instruction(Instruction.READ, 41204, 11, -1);  		//READ 11
		memory[2] = new Instruction(Instruction.EXECUTE, 8240, -1, 1);  	//EXECUTE 1
		memory[3] = new Instruction(Instruction.WRITE, 4096, 21, -1);  		//WRITE  on 21
		memory[4] = new Instruction(Instruction.READ, 41218, 12, -1);  		//READ 12
		memory[5] = new Instruction(Instruction.WRITE, 4122, 22, -1);  		//WRITE  on 22
		memory[6] = new Instruction(Instruction.JUMP, 61444, 100, -1);  		//JUMP to 100


		memory[10] = new Instruction(-1, 910, -1, -1); 						// data: 910
		memory[11] = new Instruction(-1, 911, -1, -1); 						// data: 911
		memory[12] = new Instruction(-1, 912, -1, -1); 						// data: 912



		memory[100] = new Instruction(Instruction.READ, 44011, 110, -1);  		//READ 110
		memory[101] = new Instruction(Instruction.READ, 44012, 111, -1);  		//READ 111
		memory[102] = new Instruction(Instruction.EXECUTE, 8844, -1, 1); 	 	//EXECUTE 1
		memory[103] = new Instruction(Instruction.WRITE, 5189, 23, -1);  	//WRITE  on 23
		memory[104] = new Instruction(Instruction.READ, 44011, 112, -1);  		//READ 112
		memory[105] = new Instruction(Instruction.WRITE, 5189, 24, -1);  	//WRITE  on 24
		memory[106] = new Instruction(Instruction.EXECUTE, 8333, -1, 1000);  	//EXECUTE 1000
		memory[107] = new Instruction(Instruction.JUMP, 61444, 0,-1);  		//JUMP to 0

		memory[110] = new Instruction(-1, 1910, -1, -1); 						// data: 1910
		memory[111] = new Instruction(-1, 1911, -1, -1); 						// data: 1911
		memory[112] = new Instruction(-1, 1912, -1, -1); 						// data: 1912




	}


	public void pruneDependencies() {
		super.pruneDependencies();
		removeDependency(input, output);
		removeDependency(clk, output);

	}

}