package lsi.instruction;

import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.kernel.util.IllegalActionException;

/*
 * 
 * Class represents a 16-bit word used by the LSI Instruction Processor.
 * 
 * Each instance can represent either a data word, or one of the four types of instructions 
 * that the LSI Instruction Processor can handle.
 * 
 * In case of DATA, the instance will have type=-1 and data=DATA, where DATA is the value read from the data sub-bus; address and time can have arbitrary values and are unused. 
 * In case of READ, the instance will have type=1 and address=ADDRESS, where ADDRESS is the content of the register that will be used to drive the address sub-bus; data and time can have arbitrary values and are unused. 
 * In case of WRITE, the instance will have type=2, address=ADDRESS and data=DATA, where ADDRESS is the content of the register that will be used to drive the address sub-bus and DATA is the content of the register that will be used to drive the data sub-bus; time can have arbitrary value and is unused.
 * In case of EXECUTE, the instance will have type=0 and time=TIME, where TIME is the time it takes for the PE to process the instruction; data and address can have arbitrary values and are unused.
 * In case of JUMP, the instance will have type=3 and address=ADDRESS, where ADDRESS is the content of the register that will be assigned to the PE program counter; data and time can have arbitrary values and are unused.
 * 
 * Instances are able to generate standard format RecordToken instances representing themselves.
 * 
 */


public class Instruction {

	public final static int DATA = -1;
	public final static int EXECUTE = 0;
	public final static int READ = 1;
	public final static int WRITE = 2;
	public final static int JUMP = 3;

	
	
	
	public final int data;
	public final int type;
	public final int address;
	public final int time;
	
	public Instruction(int type, int data, int address, int time){
		
		this.type = type;
		this.data = data;
		this.address = address;
		this.time = time;
	}

	
	public RecordToken getToken() throws IllegalActionException{

		String[] labels_ = new String[4];
        labels_[0] = "type";
        labels_[1] = "data";
        labels_[2] = "address";
        labels_[3] = "time";

        Token[] values_ = new Token[4];
		values_[0] = new IntToken(this.type);
	    values_[1] = new IntToken(this.data);
	    values_[2] = new IntToken(this.address); 
	    values_[3] = new IntToken(this.time); 
	    
	    return new RecordToken(labels_, values_);
	}

	
	
	public static RecordType getTokenType(){
		
		String[] labels_ = new String[4];
        labels_[0] = "type";
        labels_[1] = "data";
        labels_[2] = "address";
        labels_[3] = "time";
        
        Type[] types = new Type[4];
        types[0] = BaseType.INT;
        types[1] = BaseType.INT;
        types[2] = BaseType.INT;
        types[3] = BaseType.INT;
        		
        return new RecordType(labels_, types);
        
	}
	
	
	public String toString(){
		
		String si = "";
		
		if(this.type==0) si="X "+this.time;
		else if(this.type==1) si="R "+this.address;
		else if(this.type==2) si="W "+this.address+ " "+this.data;
		else if(this.type==3) si="J "+this.address;
		else si="D "+this.data;
		
		return si;
		
	}
	
}
