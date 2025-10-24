package backend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ir.IRFunction;
import ir.IRInstruction;
import ir.IRInstruction.OpCode;
import ir.datatype.IRArrayType;
import ir.datatype.IRIntType;
import ir.operand.*;

import mips.MIPSInstruction;
import mips.MIPSOp;
import mips.operand.Register;
import mips.operand.Imm;
import mips.operand.Addr;

/**
 * Converts a single IRInstruction into one or more MIPSInstruction(s).
 * This is the “instruction selection” phase of the backend.
 */
public class IR2MIPS {
    public static final Map<String, Integer> syscallMap = new HashMap<>() {{
        put("puti", 1);
        put("putc", 11);
        put("geti", 5);
        put("getc", 10);
    }};

    public static List<MIPSInstruction> translate(IRInstruction ir, IRFunction function) {
        List<MIPSInstruction> out = new ArrayList<>();
        IROperand[] ops = ir.operands;

        switch (ir.opCode) {
            // === Arithmetic / logic ===
            case ASSIGN:
                // e.g. assign, t, 0
                //      assign, i, t0
                //             var, imm/reg
                if (isImm(ops[1])) {
                    out.add(new MIPSInstruction(MIPSOp.LI, null,
                            regVar(ops[0], function), imm(ops[1])));
                }
                else if (isReg(ops[1])) {
                    out.add(new MIPSInstruction(MIPSOp.MOVE, null,
                            regVar(ops[0], function), regVar(ops[1], function)));
                }
                break;
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
                var results = translateBinOps(ir, function);
                out.addAll(results);
                break;

            // === Control flow ===
            case GOTO:
                out.add(new MIPSInstruction(MIPSOp.J, null,
                        labelOp(ops[0], function)));
                break;

            case BREQ:
            case BRNEQ:
            case BRLT:
            case BRGT:
            case BRGEQ:
                results = translateBranchOps(ir, function);
                out.addAll(results);
                break;

            case LABEL:
                // insert label into map
                throw new RuntimeException("Label should be handled by the program translator!");

            // === Arrays ===
            case ARRAY_LOAD:
            case ARRAY_STORE:
                results = translateArrayOps(ir, function);
                out.addAll(results);
                break;
            // === Calls / returns ===
            case CALL:
            case CALLR:
                out.addAll(translateCallOps(ir.opCode, ops, function, syscallMap));
                break;
            case RETURN:
                if (ops.length > 0) {
                    // move $v0, value
                    if (isImm(ops[0])) {
                        out.add(new MIPSInstruction(MIPSOp.LI, null,
                                Register.$v0, imm(ops[0])));
                    }
                    else if (isReg(ops[0])) {
                        out.add(new MIPSInstruction(MIPSOp.MOVE, null,
                                Register.$v0, regVar(ops[0], function)));

                    }
                }
                out.add(new MIPSInstruction(MIPSOp.JR, null, Register.$ra));
                // TODO: Function prologue and epilogue
                break;

            default:
                System.err.println("Unimplemented IR op: " + ir.opCode);
        }

        return out;
    }

    public static List<MIPSInstruction> allocateStackSpace(int size)
    {
        var out = new ArrayList<MIPSInstruction>();
        out.add(new MIPSInstruction(MIPSOp.ADDI, null, Register.$sp, Register.$sp, Imm.Dec(-size)));
        return out;
    }

    public static List<MIPSInstruction> deallocateStackSpace(int size)
    {
        var out = new ArrayList<MIPSInstruction>();
        out.add(new MIPSInstruction(MIPSOp.ADDI, null, Register.$sp, Register.$sp, Imm.Dec(size)));
        return out;
    }

    public static List<MIPSInstruction> callerTeardownForCall(IRFunction selfFunction)
    {
        var out = new ArrayList<MIPSInstruction>();

        // restore fp, ra
        out.add(loadReg(Register.$sp, Register.$fp, 0));
        out.add(loadReg(Register.$sp, Register.$ra, 4));
        out.addAll(deallocateStackSpace(4 * 2));

        // restore our own arguments
        var offset = 0;
        for (int i = 0; i < selfFunction.parameters.size(); i++) {
            out.add(loadReg(Register.$sp, new Register("$a" + i), offset));
            offset += 4;
        }
        // deallocate stack
        var deallocateStack = deallocateStackSpace(offset);
        out.addAll(deallocateStack);

        return out;
    }

    public static List<MIPSInstruction> translateCallOps(
        OpCode type,
        IROperand[] ops,
        IRFunction selfFunction,
        Map<String, Integer> syscallMap) {

        List<MIPSInstruction> instructions = new ArrayList<>();
        Addr functionTarget;

        IROperand[] functionArgs;
        // --- Pre-Call Logic (Argument Setup) ---
        if (type == OpCode.CALL) {
            functionTarget = functionOp(ops[0]);
            functionArgs = Arrays.copyOfRange(ops, 1, ops.length);
        } else if (type == OpCode.CALLR) {
            functionTarget = functionOp(ops[1]);
            functionArgs = Arrays.copyOfRange(ops, 2, ops.length);
            // Note: Original CALLR logic did not use ops[2...], so we don't either.
        } else {
            // Should not happen if only called from CALL/CALLR
            throw new IllegalArgumentException("Invalid OpCode for call: " + type);
        }

        boolean isSyscall = syscallMap.containsKey(functionTarget.label);

        // --- Common Call Logic (SYSCALL or JAL) ---
        if (isSyscall) {
            /** FUNCTION ARGUMENTS SETUP **/
            // virtualize argument
            for (int i = 0; i < functionArgs.length; i++) {
                var arg = functionArgs[i];
                var reg = new Register("$a" + i);

                // check arg type
                if (isImm(arg)) {
                    // do LI
                    instructions.add(new MIPSInstruction(MIPSOp.LI, null, reg, imm(arg)));
                }
                if (isReg(arg)) {
                    instructions.add(new MIPSInstruction(MIPSOp.MOVE, null, reg, regVar(arg, selfFunction)));
                }
            }


            instructions.add(new MIPSInstruction(MIPSOp.LI, null,
                    Register.$v0, Imm.Dec(syscallMap.get(functionTarget.label))));
            instructions.add(new MIPSInstruction(MIPSOp.SYSCALL, null));
        } else {
            // regular calls, do setup


            /** PROTECT OUR OWN ARGUMENTS **/
            // how many params we have
            int numParams = selfFunction.parameters.size();
            // allocate stack
            if (numParams > 0) {
                var allocateStack = allocateStackSpace(numParams * 4);
                instructions.addAll(allocateStack);

                // save our own arguments to stack
                for (int i = 0; i < numParams; i++) {
                    var reg = new Register("$a" + i);
                    instructions.add(saveReg(Register.$sp, reg, i * 4));
                }
            }

            /** FUNCTION ARGUMENTS SETUP **/
            // virtualize argument
            for (int i = 0; i < functionArgs.length; i++) {
                var arg = functionArgs[i];
                var reg = new Register("$a" + i);
                if (i > 4)
                {
                    reg = regVarMan("stackarg--" + (i - 4));
                }

                // check arg type
                if (isImm(arg)) {
                    // do LI
                    instructions.add(new MIPSInstruction(MIPSOp.LI, null, reg, imm(arg)));
                }
                if (isReg(arg)) {
                    var a = (IRVariableOperand)arg;
                    // is the arg an array and local
                    if (isLocalVar(a, selfFunction)) {
                        if (a.type instanceof IRArrayType) {
                            // use move and addi
                            var fpOffset = computeLocalVarFpOffset(a, selfFunction);
                            instructions.add(new MIPSInstruction(MIPSOp.MOVE, null, reg, Register.$fp));
                            instructions.add(new MIPSInstruction(MIPSOp.ADDI, null, reg, reg, Imm.Dec(fpOffset)));
                            continue;
                        }
                    }
                    instructions.add(new MIPSInstruction(MIPSOp.MOVE, null, reg, regVar(a, selfFunction)));
                }
            }

            /** SAVE FP, RA **/
            var allocateFramePtr = allocateStackSpace(8);
            instructions.addAll(allocateFramePtr);
            instructions.add(saveReg(Register.$sp, Register.$fp, 0));
            instructions.add(saveReg(Register.$sp, Register.$ra, 4));


            instructions.add(new MIPSInstruction(MIPSOp.JAL, null,
                    functionTarget));
        }

        // --- Post-Call Logic (Return Value) ---
        if (type == OpCode.CALLR) {
            // Move result from $v0 into the destination register
            instructions.add(new MIPSInstruction(MIPSOp.MOVE, null,
                    regVar(ops[0], selfFunction), Register.$v0));
        }

        if (!isSyscall) {
            var callerTeardown = callerTeardownForCall(selfFunction);
            instructions.addAll(callerTeardown);
        }

        return instructions;
    }

    public static List<MIPSInstruction> translateBinOps(IRInstruction ir, IRFunction selfFunction) {
        Map<OpCode, MIPSOp> map = new HashMap<OpCode, MIPSOp>() {{
            put(OpCode.ADD, MIPSOp.ADD);
            put(OpCode.SUB, MIPSOp.SUB);
            put(OpCode.MULT, MIPSOp.MUL);
            put(OpCode.DIV, MIPSOp.DIV);
            put(OpCode.AND, MIPSOp.AND);
            put(OpCode.OR, MIPSOp.OR);
        }};

        List<MIPSInstruction> out = new ArrayList<>();
        IROperand[] ops = ir.operands;

        var dst = regVar(ops[0], selfFunction);
        var src1 = regVar(ops[1], selfFunction);
        if (isImm(ops[2])) {
            // special case for addi
            if (ir.opCode == OpCode.ADD) {
                out.add(new MIPSInstruction(MIPSOp.ADDI, null, dst, src1, imm(ops[2])));
            } else {
                var tempReg = tempReg();
                var inst = tmpImmReg(ops[2]);
                out.add(inst);

                out.add(new MIPSInstruction(map.get(ir.opCode), null, dst, src1, tempReg));
            }
        } else if (isReg(ops[2])) {
            out.add(new MIPSInstruction(map.get(ir.opCode), null, dst, src1, regVar(ops[2], selfFunction)));
        }
        else
        {
            throw new IllegalArgumentException("Unexpected condition");
        }

        return out;
    }

    public static List<MIPSInstruction> translateBranchOps(IRInstruction ir, IRFunction function) {
        Map<OpCode, MIPSOp> map = new HashMap<OpCode, MIPSOp>() {{
            put(OpCode.BREQ, MIPSOp.BEQ);
            put(OpCode.BRNEQ, MIPSOp.BNE);
            put(OpCode.BRLT, MIPSOp.BLT);
            put(OpCode.BRGT, MIPSOp.BGT);
            put(OpCode.BRGEQ, MIPSOp.BGE);
        }};

        List<MIPSInstruction> out = new ArrayList<>();
        IROperand[] ops = ir.operands;

        var dst = labelOp(ops[0], function);
        var src1 = regVar(ops[1], function);
        if (isImm(ops[2])) {
            var tempReg = tempReg();
            var inst = tmpImmReg(ops[2]);
            out.add(inst);

            out.add(new MIPSInstruction(map.get(ir.opCode), null, src1, tempReg, dst));
        } else if (isReg(ops[2])) {
            out.add(new MIPSInstruction(map.get(ir.opCode), null, src1, regVar(ops[2], function), dst));
        }
        else
        {
            throw new IllegalArgumentException("Unexpected condition");
        }

        return out;
    }

    public static List<MIPSInstruction> translateArrayOps(IRInstruction ir, IRFunction function) {
        var out = new ArrayList<MIPSInstruction>();
        var ops = ir.operands;
        Register arrOffset;

        // TODO: Identify the origin of Array operand
        // if array operand is a local variable, need use fp relative to load
        // if array operand is a parameter, then we can just use existing addresses

        // array operand is a imm vs register?
        if (isImm(ops[2])) {
            arrOffset = tempReg();
            out.add(new MIPSInstruction(MIPSOp.LI, null, arrOffset, imm(ops[2])));
        }
        else if (isReg(ops[2])) {
            arrOffset = regVar(ops[2], function);
        }
        else {
            throw new RuntimeException("Unexpected condition");
        }

        // prepare base and offset
        out.add(new MIPSInstruction(MIPSOp.SLL, null, regVarMan("temp--off"), arrOffset, Imm.Dec(2)));

        Register base;
        // check array base to see if it is a stack location or a lv location
        // if it is localvar location, use fp relative to address, otherwise, directly throw in the argument
        if (isLocalVar(ops[1], function))
        {
            var offset = computeLocalVarFpOffset(ops[1], function);

            base = regVarMan("temp--base");
            out.add(new MIPSInstruction(MIPSOp.ADDI, null, base, Register.$fp, Imm.Dec(offset)));
        }
        else
        {
            base = regVar(ops[1], function);
        }

        out.add(new MIPSInstruction(MIPSOp.SUB, null, regVarMan("temp--addr"), base, regVarMan("temp--off")));

        if (ir.opCode == OpCode.ARRAY_LOAD)
        {
            // array load, a, arr, 0
            // a := arr[0]
            // array based offset is $fp + (local var index)
            // check if offset operand is a imm or a register
            out.add(new MIPSInstruction(MIPSOp.LW, null, regVar(ops[0], function), new Addr(Imm.Dec(0), regVarMan("temp--addr"))));
        }
        else {
            // array store, a, arr, 0
            // arr[0] := a
            // array based offset is $fp + (local var index)
            out.add(new MIPSInstruction(MIPSOp.SW, null, regVar(ops[0], function), new Addr(Imm.Dec(0), regVarMan("temp--addr"))));
        }

        return out;
    }

    private static boolean isReg(IROperand irOperand) {
        return irOperand instanceof IRVariableOperand;
    }

    private static boolean isImm(IROperand irOperand) {
        return irOperand instanceof IRConstantOperand;
    }


    public static Register regVar(IROperand irOp, IRFunction function) {
        if (!(irOp instanceof IRVariableOperand)) {
            throw new IllegalArgumentException("Expected IRVariableOperand");
        }

        if (isLocalVar(irOp, function)) {
            return new Register("$v-local--" + ((IRVariableOperand)irOp).getName());
        }
        else
        {
            return new Register("$v-param--" + ((IRVariableOperand)irOp).getName());
        }
    }

    public static Register regVarMan(String name)
    {
        return new Register("$v-" + name);
    }

    public static Addr functionOp(IROperand irOp)
    {
        if (!(irOp instanceof IRFunctionOperand)) {
            throw new IllegalArgumentException("Expected IRFunctionOperand");
        }

        return new Addr(((IRFunctionOperand)irOp).getName());
    }

    public static boolean isLocalVar(IROperand irOperand, IRFunction function)
    {
        if (!(irOperand instanceof IRVariableOperand)) {
            throw new IllegalArgumentException("Expected IRVariableOperand");
        }

        // check if ir var operand appears in local var decl
        for (IRVariableOperand params : function.parameters) {
            if (params.getName().equals(((IRVariableOperand)irOperand).getName())) {
                return false;
            }
        }

        // var is probably from var list
        return true;
    }

    public static int computeLocalVarFpOffset(IROperand var, IRFunction function)
    {
        // generates a fp offset for a given local variable
        var stackVarSize = 0;
        for (int i = 0; i < function.variables.size(); i++) {
            var variable = function.variables.get(i);
            // ensure variable is not a parameter
            if (function.parameters.contains(variable))
                continue;

            if (variable.getName().equals(((IRVariableOperand)var).getName())) {
                return stackVarSize;
            }

            if (variable.type instanceof IRArrayType)
            {
                var size = ((IRArrayType)variable.type).getSize();
                stackVarSize += (size * 4);
            }
            else if (variable.type instanceof IRIntType)
            {
                stackVarSize += 4;
            }
        }

        return stackVarSize;
    }

    public static Map<String, Integer> computeLocalVarFpOffsetTable(IRFunction function)
    {
        Map<String, Integer> out = new HashMap<>();
        // generates a fp offset for a given local variable
        var stackVarSize = 0;
        for (int i = 0; i < function.variables.size(); i++) {
            var variable = function.variables.get(i);
            // ensure variable is not a parameter
            if (function.parameters.contains(variable))
                continue;

            out.put(variable.getName(), stackVarSize);

            if (variable.type instanceof IRArrayType)
            {
                var size = ((IRArrayType)variable.type).getSize();
                stackVarSize += (size * 4);
            }
            else if (variable.type instanceof IRIntType)
            {
                stackVarSize += 4;
            }
        }

        return out;
    }

    public static Addr labelOp(IROperand irOp, IRFunction function) {
        if (!(irOp instanceof IRLabelOperand)) {
            throw new IllegalArgumentException("Expected IRConstantOperand");
        }

        return new Addr(((IRLabelOperand)irOp).getName() + "_" + function.name);
    }

    public static MIPSInstruction saveReg(Register target, Register value, int offset) {
        return new MIPSInstruction(MIPSOp.SW, null, value, new Addr(Imm.Dec(offset), target));
    }

    public static MIPSInstruction loadReg(Register target, Register value, int offset) {
        return new MIPSInstruction(MIPSOp.LW, null, value, new Addr(Imm.Dec(offset), target));
    }

    public static Imm imm(IROperand irOp) {
        if (!(irOp instanceof IRConstantOperand)) {
            throw new IllegalArgumentException("Expected IRConstantOperand");
        }

        var constantOp = (IRConstantOperand)irOp;
        return new Imm(constantOp.getValueString(), "DEC");
    }

    public static MIPSInstruction tmpImmReg(IROperand irOp) {
        var reg = tempReg();
        if (!(irOp instanceof IRConstantOperand)) {
            throw new IllegalArgumentException("Expected IRConstantOperand");
        }

        var constantOp = (IRConstantOperand)irOp;
        return new MIPSInstruction(MIPSOp.LI, null, reg, imm(constantOp));
    }

    public static Register tempReg() {
        return regVarMan("temp--tmp");
    }
}
