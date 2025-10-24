package backend;

import ir.IRFunction;
import mips.MIPSInstruction;

import java.util.List;

public class MIPSTranslation {
    public String functionName;

    public IRFunction irFunction;
    public List<MIPSInstruction> mipsInstructions;

    public MIPSTranslation(IRFunction irFunction, List<MIPSInstruction> mipsInstructions) {
        this.functionName = irFunction.name;
        
        this.irFunction = irFunction;
        this.mipsInstructions = mipsInstructions;
    }
}
