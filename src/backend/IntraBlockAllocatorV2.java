package backend;

import ir.IRFunction;
import ir.datatype.IRArrayType;
import ir.operand.IRVariableOperand;
import mips.MIPSInstruction;
import mips.MIPSOp;
import mips.operand.Addr;
import mips.operand.Imm;
import mips.operand.Register;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sample Naive Register Allocator.
 *
 * This allocator maps *every* virtual register to a slot on the stack.
 * It uses $t0, $t1, and $t2 as temporary scratch registers to load
 * operands, perform the operation, and store the result back to the stack.
 *
 * It does *not* perform liveness analysis and is very inefficient,
 * but it is simple and correct.
 */
public class IntraBlockAllocatorV2 {

    // Define our physical registers
    private static final Register $fp = new Register("$fp"); // Frame Pointer
    private static final Register $sp = new Register("$sp"); // Stack Pointer
    private static final Register $ra = new Register("$ra"); // Return Address
    private static final Register $v0 = new Register("$v0"); // Return Value
    private static final Register $a0 = new Register("$a0"); // Argument 0
    private static final Register $a1 = new Register("$a1"); // Argument 1

    // Scratch registers for operations
    private static final Register $t0 = new Register("$t0");
    private static final Register $t1 = new Register("$t1");
    // $t2 is needed for some ops like branches
    private static final Register $t2 = new Register("$t2");
    private static final Register $t3 = new Register("$t3");


    /**
     * Internal class to hold the stack layout for a function.
     */
    private static class StackLayout {
        // Map virtual register name (e.g., "v-local--i") to its stack offset from $fp
        Map<String, Integer> offsets = new HashMap<>();
        // Total size needed for this stack frame (in bytes, positive number)
        int totalStackSize = 0;
    }

    /**
     * Allocates physical registers for a single function.
     * Returns a *new* MIPSTranslation object with the allocated instructions.
     */
    public MIPSTranslation allocate(MIPSTranslation translation) {
        StackLayout layout = buildStackLayout(translation.irFunction);
        List<MIPSInstruction> newInstructions = new ArrayList<>();

        // 1. Generate the function prologue
        newInstructions.addAll(generatePrologue(translation.functionName, layout.totalStackSize));

        // 2. Translate ("expand") every virtual instruction
        for (MIPSInstruction inst : translation.mipsInstructions) {
            // Pass-through labels and simple jumps
            if (inst.label != null && inst.op == null) {
                newInstructions.add(new MIPSInstruction(null, inst.label));
                continue;
            }
            if (inst.op == MIPSOp.J || inst.op == MIPSOp.SYSCALL) {
                newInstructions.add(inst);
                continue;
            }

            // Handle instruction expansion
            switch (inst.op) {
                case JR:
                    // This is a RETURN. We must insert the epilogue *before* the jump.
                    newInstructions.addAll(generateEpilogue(layout.totalStackSize));
                    newInstructions.add(new MIPSInstruction(MIPSOp.JR, null, $ra));
                    break;

                // Special case: `move $v-param, $a0` (Storing ABI args to stack)
                // Special case: `move $v-local, $v0` (Storing return value to stack)
                case MOVE:
                    expandMove(inst, layout, newInstructions);
                    break;

                // Arithmetic: add $vD, $vS, $vT
                case ADD:
                case SUB:
                case MUL:
                case DIV:
                    expandBinaryOp(inst, layout, newInstructions);
                    break;

                // Arithmetic with immediate: addi $vD, $vS, imm
                case SLL:
                case ADDI:
                    expandBinaryOpImm(inst, layout, newInstructions);
                    break;

                // Load immediate: li $vD, imm
                case LI:
                    expandLoadImm(inst, layout, newInstructions);
                    break;

                // Branch: bne $vS, $vT, label
                case BNE:
                case BEQ:
                case BGT:
                case BLT:
                case BGE:
                    expandBranch(inst, layout, newInstructions);
                    break;
                case LW:
                case SW:
                    expandSwLw(inst, layout, newInstructions);
                    break;
                // Function call: move $a0, $v-local (loading args)
                // is handled by expandMove.
                case JAL:
                    // The `jal` itself is just one instruction.
                    // NOTE: This naive allocator doesn't save $t0-$t9 registers
                    // because it assumes they are not live across instructions.
                    // A *real* allocator would need to save any live $t regs here.
                    newInstructions.add(inst);
                    break;

                default:
                    // Warn or error on unhandled instructions
                    System.err.println("Warning: Unhandled MIPS op in allocator: " + inst.op);
                    newInstructions.add(inst); // Pass it through
            }
        }

        return new MIPSTranslation(translation.irFunction, newInstructions);
    }

    /**
     * Creates the stack layout for a function.
     * Allocates space for $ra, $fp, and all locals/params.
     */
    private StackLayout buildStackLayout(IRFunction function) {
        StackLayout layout = new StackLayout();
        // Start at -8 to hold $ra (at -4) and old $fp (at -8)
        // This means $fp will point to the location of the old $fp.
        // Locals will be at negative offsets, $ra will be at 0($fp).
        int currentOffset = -8;

        // Allocate space for all parameters (they will be stored on stack)
        for (IRVariableOperand param : function.parameters) {
            if (IR2MIPS.isLocalVar(param, function))
            {
                continue;
            }
            layout.offsets.put(param.getName(), currentOffset);
            currentOffset -= MIPSInstruction.WORD_SIZE;
        }

        // Allocate space for all local variables
        for (IRVariableOperand var : function.variables) {
            if (!IR2MIPS.isLocalVar(var, function))
            {
                continue;
            }

            // Special case with arrays, we need to actually allocate currentOffset * ARRSIZE
            // otherwise, it will smash the stack
            var actualSize = MIPSInstruction.WORD_SIZE;
            if (var.type instanceof IRArrayType) {
                actualSize *= ((IRArrayType)var.type).getSize();
            }

            layout.offsets.put(var.getName(), currentOffset);
            currentOffset -= actualSize;
        }

        // Total size is the absolute value of the final offset, aligned
        layout.totalStackSize = -currentOffset;

        // Add space for $ra and $fp
        layout.totalStackSize += 8;

        // Ensure stack is 8-byte aligned (good practice)
        if (layout.totalStackSize % 8 != 0) {
            layout.totalStackSize += 4;
        }

        return layout;
    }

    /**
     * Generates the standard MIPS function prologue.
     */
    private List<MIPSInstruction> generatePrologue(String funcName, int stackSize) {
        List<MIPSInstruction> prologue = new ArrayList<>();
        // 1. Add label for the function
        prologue.add(new MIPSInstruction(MIPSOp.NOT_AN_OP_LABEL, funcName));
        // 2. Adjust stack pointer
        prologue.add(new MIPSInstruction(MIPSOp.ADDI, null, $sp, $sp, Imm.Dec(-stackSize)));
        // 3. Save return address and old frame pointer
        prologue.add(new MIPSInstruction(MIPSOp.SW, null, $ra, new Addr(Imm.Dec(stackSize - 4), $sp)));
        prologue.add(new MIPSInstruction(MIPSOp.SW, null, $fp, new Addr(Imm.Dec(stackSize - 8), $sp)));
        // 4. Set new frame pointer
        prologue.add(new MIPSInstruction(MIPSOp.ADDI, null, $fp, $sp, Imm.Dec(stackSize - 8)));

        return prologue;
    }

    /**
     * Generates the standard MIPS function epilogue.
     */
    private List<MIPSInstruction> generateEpilogue(int stackSize) {
        List<MIPSInstruction> epilogue = new ArrayList<>();
        // 1. Restore $ra and $fp
        epilogue.add(new MIPSInstruction(MIPSOp.LW, null, $ra, new Addr(Imm.Dec(stackSize - 4), $sp)));
        epilogue.add(new MIPSInstruction(MIPSOp.LW, null, $fp, new Addr(Imm.Dec(stackSize - 8), $sp)));
        // 2. Restore stack pointer
        epilogue.add(new MIPSInstruction(MIPSOp.ADDI, null, $sp, $sp, Imm.Dec(stackSize)));

        return epilogue;
    }

    // --- Instruction Expansion Helpers ---

    /** Expands: move $vD, $vS OR move $vD, $a0 OR move $a0, $vS */
    private void expandMove(MIPSInstruction inst, StackLayout layout, List<MIPSInstruction> newInstructions) {
        Register dest = (Register) inst.operands.get(MIPSInstruction.R_D_MOV);
        Register src = (Register) inst.operands.get(MIPSInstruction.R_S_MOV);

        Register[] loadedRegisters = getLoadedRegisterPair(src, dest, layout, newInstructions);
        newInstructions.add(new MIPSInstruction(MIPSOp.MOVE, null, loadedRegisters[1], loadedRegisters[0]));
        saveRegister(dest, loadedRegisters[1], layout, newInstructions);
    }

    /** Expands: add $vD, $vS, $vT */
    private void expandBinaryOp(MIPSInstruction inst, StackLayout layout, List<MIPSInstruction> newInstructions) {
        Register vD = (Register) inst.operands.get(MIPSInstruction.R_D_BOP);
        Register vS = (Register) inst.operands.get(MIPSInstruction.R_S_BOP);
        Register vT = (Register) inst.operands.get(MIPSInstruction.R_T_BOP);
        Register[] loadedRegisters = getLoadedRegisterTrio(vD, vS, vT, layout, newInstructions);
        newInstructions.add(new MIPSInstruction(inst.op, null, loadedRegisters[0], loadedRegisters[1], loadedRegisters[2]));
        saveRegister(vD, loadedRegisters[0], layout, newInstructions);
    }

    /** Expands: addi $vD, $vS, imm */
    private void expandBinaryOpImm(MIPSInstruction inst, StackLayout layout, List<MIPSInstruction> newInstructions) {
        Register vD = (Register) inst.operands.get(MIPSInstruction.R_D_BOP);
        Register vS = (Register) inst.operands.get(MIPSInstruction.R_S_BOP);
        Imm imm = (Imm) inst.operands.get(MIPSInstruction.R_T_BOP); // MIPSInstruction class seems to use R_T for imm

        // int offsetD = layout.offsets.get(vD.name);
        // int offsetS = layout.offsets.get(vS.name);

        Register[] loadedRegisters = getLoadedRegisterPair(vS, vD, layout, newInstructions);

        // 1. Load vS into $t0
        // newInstructions.add(new MIPSInstruction(MIPSOp.LW, inst.label, $t0, new Addr(Imm.Dec(offsetS), $fp)));
        // 2. Perform operation
        newInstructions.add(new MIPSInstruction(inst.op, null, loadedRegisters[1], loadedRegisters[0], imm));
        // 3. Store result from $t0 into vD
        saveRegister(vD, loadedRegisters[1], layout, newInstructions);
    }

    /** Expands: li $vD, imm */
    private void expandLoadImm(MIPSInstruction inst, StackLayout layout, List<MIPSInstruction> newInstructions) {
        Register vD = (Register) inst.operands.get(MIPSInstruction.R_D_LI);
        Imm imm = (Imm) inst.operands.get(MIPSInstruction.IMM_LI);

        Register loadedD = getLoadedRegister(vD, layout, newInstructions, true);

        // 1. Load immediate into $t0
        newInstructions.add(new MIPSInstruction(MIPSOp.LI, inst.label, loadedD, imm));
        // 2. Store from $t0 into vD
        saveRegister(vD, loadedD, layout, newInstructions);
    }

    /** Expands: bne $vS, $vT, label */
    private void expandBranch(MIPSInstruction inst, StackLayout layout, List<MIPSInstruction> newInstructions) {
        Register vS = (Register) inst.operands.get(MIPSInstruction.R_S_BR);
        Register vT = (Register) inst.operands.get(MIPSInstruction.R_T_BR);
        Addr label = (Addr) inst.operands.get(MIPSInstruction.LABEL_BR);

        Register[] loadedRegisters = getLoadedRegisterPair(vT, vS, layout, newInstructions);
        // 3. Perform branch
        newInstructions.add(new MIPSInstruction(inst.op, null, loadedRegisters[1], loadedRegisters[0], label));
    }

    private void expandSwLw(MIPSInstruction inst, StackLayout layout, List<MIPSInstruction> newInstructions)
    {
        Register vD = (Register) inst.operands.get(MIPSInstruction.R_D_LI);
        Addr vAddr = (Addr) inst.operands.get(MIPSInstruction.ADDR_SW);

        Register vBase = vAddr.register;

        Register[] loadedRegisters = getLoadedRegisterPair(vD, vBase, layout, newInstructions);

        var newvAddr = new Addr(Imm.Dec(vAddr.constant.getInt()), loadedRegisters[1]);

        newInstructions.add(new MIPSInstruction(inst.op, null, loadedRegisters[0], newvAddr));
        System.out.println(newInstructions.get(newInstructions.size() - 1));

        // if we are loading a word to a virtual register, we need to save it
        if (inst.op == MIPSOp.LW)
        {
            saveRegister(vD, loadedRegisters[0], layout, newInstructions);
        }
    }

    private boolean isVirtualRegister(Register vD) {
        return vD.name.startsWith("$v-local--") || vD.name.startsWith("$v-param--");
    }

    private boolean isTempRegister(Register vD) {
        return vD.name.startsWith("$v-temp--");
    }

    private void saveRegister(Register virtualRegister, Register valueRegister, StackLayout layout, List<MIPSInstruction> newInstructions) {
        // only save if virtual registers
        if (isVirtualRegister(virtualRegister)) {
            // get true name
            String trueName = virtualRegister.name.substring(10);
            int offset = layout.offsets.get(trueName);
            // save the register to the offset
            newInstructions.add(new MIPSInstruction(MIPSOp.SW, null, valueRegister, new Addr(Imm.Dec(offset), $fp)));
        }
    }

    private Register[] getLoadedRegisterTrio(Register vD, Register vS, Register vT, StackLayout layout, List<MIPSInstruction> newInstructions) {
        return new Register[] { getLoadedRegisterV(vD, $t1, layout, newInstructions), getLoadedRegisterV(vS, $t2, layout, newInstructions), getLoadedRegisterV(vT, $t3, layout, newInstructions) };
    }

    private Register[] getLoadedRegisterPair(Register vT, Register vS, StackLayout layout, List<MIPSInstruction> newInstructions) {
        if (vT.name.equals(vS.name)) {
            var common = getLoadedRegisterV(vT, $t1, layout, newInstructions);
            return new Register[]{common, common};
        }
        return new Register[] { getLoadedRegisterV(vT, $t1, layout, newInstructions), getLoadedRegisterV(vS, $t2, layout, newInstructions) };
    }

    private Register getTempRegisterAssignment(Register vD)
    {
        if (!isTempRegister(vD))
        {
            throw new RuntimeException("Error: Trying to get temp register assignment for non-temp register: " + vD.name);
        }

        var tempPurpose = vD.name.substring(9);
        if (tempPurpose.equals("tmp")) {
            return Register.T9;
        }

        // if we are using temp for array ops (off & addr)
        if (tempPurpose.equals("off")) {
            // use T8
            return Register.T8;
        }
        if (tempPurpose.equals("addr") || tempPurpose.equals("base")) {
            return Register.T9;
        }

        System.out.println("Error: Unknown temp register purpose: " + tempPurpose);
        throw new RuntimeException("Unknown temp register purpose: " + tempPurpose);
    }

    private Register getLoadedRegisterV(Register vD, Register tmpRegister, StackLayout layout, List<MIPSInstruction> newInstructions) {
        return getLoadedRegisterV(vD, tmpRegister, layout, newInstructions, false);
    }

    private Register getLoadedRegisterV(Register vD, Register tmpRegister, StackLayout layout, List<MIPSInstruction> newInstructions, boolean bypassLoad) {
        // if the vD is a virtual register
        String trueName;
        if (isVirtualRegister(vD)) {
            // get true name
            trueName = vD.name.substring(10);
            int offset = layout.offsets.get(trueName);
            // load register from offset
            if (!bypassLoad) {
                newInstructions.add(new MIPSInstruction(MIPSOp.LW, null, tmpRegister, new Addr(Imm.Dec(offset), $fp)));
            }
            return tmpRegister;
        }
        else {
            // already a physical register
            if (isTempRegister(vD)) {
                return getTempRegisterAssignment(vD);
            }
            return vD;
        }
    }

    private Register getLoadedRegister(Register vD, StackLayout layout, List<MIPSInstruction> newInstructions) {
        return getLoadedRegisterV(vD, $t1, layout, newInstructions, false);
    }

    private Register getLoadedRegister(Register vD, StackLayout layout, List<MIPSInstruction> newInstructions, boolean bypassLoad) {
        return getLoadedRegisterV(vD, $t1, layout, newInstructions, bypassLoad);
    }
}