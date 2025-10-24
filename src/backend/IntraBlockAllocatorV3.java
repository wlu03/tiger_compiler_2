package backend;

import ir.IRFunction;
import ir.datatype.IRArrayType;
import ir.operand.IRVariableOperand;
import mips.MIPSInstruction;
import mips.MIPSOp;
import mips.operand.Addr;
import mips.operand.Imm;
import mips.operand.Register;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Intra-block allocator (V3): next-use based caching of stack-backed virtuals in $t regs.
 * Emits a single prologue at function entry and epilogue before JR paths. Passes through IR labels.
 */
public class IntraBlockAllocatorV3 {

    private static final Register FP = Register.$fp;
    private static final Register SP = Register.$sp;
    private static final Register RA = Register.$ra;

    // Allocable caller-saved regs inside blocks (reserve T8/T9 for array/address temps)
    private static final Register[] ALLOCABLE = new Register[] {
            Register.T1, Register.T2, Register.T3, Register.T4
    };

    private static class StackLayout {
        Map<String, Integer> offsets = new HashMap<>();
        int totalStackSize = 0;
    }

    private static class BlockState {
        Map<String, Integer> offsetOf = new HashMap<>();
        Map<String, Register> regMap = new HashMap<>();
        Map<Register, String> physMap = new HashMap<>();
        Set<String> dirty = new HashSet<>();
        List<Map<String, Integer>> nextUse = new ArrayList<>();
        Deque<Register> freeRegs = new ArrayDeque<>();
    }

    public MIPSTranslation allocate(MIPSTranslation translation) {
        StackLayout layout = buildStackLayout(translation.irFunction);
        List<MIPSInstruction> out = new ArrayList<>();

        // Prologue
        out.addAll(generatePrologue(translation.functionName, layout.totalStackSize));

        // Prepare blocks
        List<Integer> boundaries = computeBlockBoundaries(translation.mipsInstructions);

        BlockState state = new BlockState();
        state.offsetOf.putAll(layout.offsets);

        for (int bi = 0; bi + 1 < boundaries.size(); bi++) {
            int start = boundaries.get(bi);
            int end = boundaries.get(bi + 1);
            if (start >= end) continue; // avoid empty blocks
            List<MIPSInstruction> block = translation.mipsInstructions.subList(start, end);

            // Compute next-use for this block
            state.nextUse = computeNextUse(block);

            // Init state
            state.regMap.clear();
            state.physMap.clear();
            state.dirty.clear();
            state.freeRegs.clear();
            for (Register r : ALLOCABLE) state.freeRegs.add(r);

            boolean terminated = false; // if block ended with non-fallthrough

            // Process block forward
            for (int i = 0; i < block.size(); i++) {
                MIPSInstruction inst = block.get(i);

                if (inst.op == MIPSOp.NOT_AN_OP_LABEL) {
                    out.add(inst);
                    continue;
                }

                // Control transfers: spill before emitting
                if (isControlTransfer(inst)) {
                    spillAllDirty(state, out);
                    clearCache(state);
                }

                // Build rewrite map
                Map<Register, Register> rewrite = new HashMap<>();

                // Ensure read registers
                for (Register r : inst.getReads()) {
                    if (isStackBacked(r)) {
                        Register p = ensureInReg(state, trueName(r), i, out);
                        rewrite.put(r, p);
                    } else if (isTempVirtual(r)) {
                        rewrite.put(r, getTempRegisterAssignment(r));
                    }
                }

                // Ensure address base registers (redundant for getReads() but safe)
                if (inst.op == MIPSOp.LW || inst.op == MIPSOp.SW) {
                    Addr a = (Addr)inst.operands.get(inst.op == MIPSOp.LW ? MIPSInstruction.ADDR_LW : MIPSInstruction.ADDR_SW);
                    Register base = a.register;
                    if (base != null) {
                        if (isStackBacked(base)) {
                            Register p = ensureInReg(state, trueName(base), i, out);
                            rewrite.put(base, p);
                        } else if (isTempVirtual(base)) {
                            rewrite.put(base, getTempRegisterAssignment(base));
                        }
                    }
                }

                // Ensure write registers
                Register w = inst.getWrite();
                if (w != null) {
                    if (isStackBacked(w)) {
                        Register p = ensureRegForDef(state, trueName(w), i, out);
                        markDirty(state, trueName(w));
                        rewrite.put(w, p);
                    } else if (isTempVirtual(w)) {
                        rewrite.put(w, getTempRegisterAssignment(w));
                    }
                }

                // Re-emit rewritten instruction
                MIPSInstruction rewritten = rewriteInst(inst, rewrite);

                // Optional: drop self-move
                if (rewritten.op == MIPSOp.MOVE) {
                    Register d = (Register)rewritten.operands.get(MIPSInstruction.R_D_MOV);
                    Register s = (Register)rewritten.operands.get(MIPSInstruction.R_S_MOV);
                    if (!d.equals(s)) out.add(rewritten);
                } else if (rewritten.op == MIPSOp.JR) {
                    // return path: epilogue then JR
                    out.addAll(generateEpilogue(layout.totalStackSize));
                    out.add(rewritten);
                    terminated = true;
                } else {
                    out.add(rewritten);
                }

                if (rewritten.op == MIPSOp.J) {
                    terminated = true;
                }
            }

            // End-of-block flush unless already spilled for control terminator (JR/J)
            if (!terminated) {
                spillAllDirty(state, out);
            }
            clearCache(state);
        }

        return new MIPSTranslation(translation.irFunction, out);
    }

    private static boolean isControlTransfer(MIPSInstruction inst) {
        return inst.op == MIPSOp.BEQ || inst.op == MIPSOp.BNE || inst.op == MIPSOp.BLT ||
               inst.op == MIPSOp.BGT || inst.op == MIPSOp.BGE || inst.op == MIPSOp.J ||
               inst.op == MIPSOp.JAL || inst.op == MIPSOp.SYSCALL || inst.op == MIPSOp.JR;
    }

    private static boolean isStackBacked(Register r) {
        if (r == null) return false;
        String n = r.name;
        return n.startsWith("$v-local--") || n.startsWith("$v-param--");
    }

    private static boolean isTempVirtual(Register r) {
        return r != null && r.name.startsWith("$v-temp--");
    }

    private static String trueName(Register r) {
        if (r.name.startsWith("$v-local--")) return r.name.substring(10);
        if (r.name.startsWith("$v-param--")) return r.name.substring(10);
        return r.name;
    }

    private static Register getTempRegisterAssignment(Register v) {
        String purpose = v.name.substring(9); // after "$v-temp--"
        if (purpose.equals("tmp")) return Register.T9;
        if (purpose.equals("off")) return Register.T8;
        if (purpose.equals("addr") || purpose.equals("base")) return Register.T9;
        throw new RuntimeException("Unknown temp register purpose: " + purpose);
    }

    private static void clearCache(BlockState state) {
        state.regMap.clear();
        state.physMap.clear();
        state.dirty.clear();
        state.freeRegs.clear();
        for (Register r : ALLOCABLE) state.freeRegs.add(r);
    }

    private static void markDirty(BlockState state, String v) {
        state.dirty.add(v);
    }

    private static void spill(BlockState state, String v, List<MIPSInstruction> out) {
        if (!state.regMap.containsKey(v)) return;
        Register phys = state.regMap.get(v);
        if (state.dirty.contains(v)) {
            if (!state.offsetOf.containsKey(v)) {
                throw new RuntimeException("Missing stack offset for virtual: " + v);
            }
            int off = state.offsetOf.get(v);
            out.add(new MIPSInstruction(MIPSOp.SW, null, phys, new Addr(Imm.Dec(off), FP)));
            state.dirty.remove(v);
        }
        state.regMap.remove(v);
        state.physMap.remove(phys);
        state.freeRegs.addLast(phys);
    }

    private static void spillAllDirty(BlockState state, List<MIPSInstruction> out) {
        // Copy to avoid concurrent modification
        List<String> toSpill = new ArrayList<>(state.dirty);
        for (String v : toSpill) spill(state, v, out);
    }

    private static String chooseVictim(BlockState state, int i) {
        String best = null;
        int bestNext = -1; // pick the farthest next use
        boolean bestDirty = true;
        for (Map.Entry<String, Register> e : state.regMap.entrySet()) {
            String v = e.getKey();
            int nu = state.nextUse.get(i).getOrDefault(v, Integer.MAX_VALUE);
            boolean isDirty = state.dirty.contains(v);
            if (nu == Integer.MAX_VALUE && !isDirty) {
                return v; // ideal: no future use and clean
            }
            if (nu > bestNext || (nu == bestNext && bestDirty && !isDirty)) {
                best = v; bestNext = nu; bestDirty = isDirty;
            }
        }
        if (best == null) {
            for (String v : state.regMap.keySet()) { best = v; break; }
        }
        return best;
    }

    private static Register ensureInReg(BlockState state, String v, int i, List<MIPSInstruction> out) {
        if (state.regMap.containsKey(v)) return state.regMap.get(v);
        if (!state.offsetOf.containsKey(v)) {
            throw new RuntimeException("Missing stack offset for virtual: " + v);
        }
        Register phys;
        if (!state.freeRegs.isEmpty()) {
            phys = state.freeRegs.removeFirst();
        } else {
            String victim = chooseVictim(state, i);
            spill(state, victim, out);
            phys = state.freeRegs.removeFirst();
        }
        state.regMap.put(v, phys);
        state.physMap.put(phys, v);
        int off = state.offsetOf.get(v);
        out.add(new MIPSInstruction(MIPSOp.LW, null, phys, new Addr(Imm.Dec(off), FP)));
        return phys;
    }

    private static Register ensureRegForDef(BlockState state, String v, int i, List<MIPSInstruction> out) {
        if (state.regMap.containsKey(v)) return state.regMap.get(v);
        Register phys;
        if (!state.freeRegs.isEmpty()) {
            phys = state.freeRegs.removeFirst();
        } else {
            String victim = chooseVictim(state, i);
            spill(state, victim, out);
            phys = state.freeRegs.removeFirst();
        }
        state.regMap.put(v, phys);
        state.physMap.put(phys, v);
        return phys;
    }

    private List<Map<String, Integer>> computeNextUse(List<MIPSInstruction> block) {
        List<Map<String, Integer>> nextUse = new ArrayList<>();
        for (int i = 0; i < block.size(); i++) nextUse.add(new HashMap<>());
        Map<String, Integer> liveNext = new HashMap<>();
        for (int i = block.size() - 1; i >= 0; i--) {
            MIPSInstruction inst = block.get(i);
            Map<String, Integer> nu = new HashMap<>(liveNext);
            nextUse.set(i, nu);

            // defs kill
            Register w = inst.getWrite();
            if (w != null && isStackBacked(w)) {
                liveNext.put(trueName(w), Integer.MAX_VALUE);
            }
            // reads set to i (includes LW/SW base via getReads)
            for (Register r : inst.getReads()) {
                if (isStackBacked(r)) liveNext.put(trueName(r), i);
            }
        }
        return nextUse;
    }

    private List<Integer> computeBlockBoundaries(List<MIPSInstruction> insts) {
        List<Integer> b = new ArrayList<>();
        b.add(0);
        for (int i = 0; i < insts.size(); i++) {
            MIPSInstruction in = insts.get(i);
            if (in.op == MIPSOp.NOT_AN_OP_LABEL) b.add(i);
            if (isControlTransfer(in)) b.add(i + 1);
        }
        if (b.get(b.size() - 1) != insts.size()) b.add(insts.size());
        // dedupe and sort
        List<Integer> out = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
        for (int x : b) if (!seen.contains(x)) { out.add(x); seen.add(x); }
        out.sort(Integer::compareTo);
        return out;
    }

    private MIPSInstruction rewriteInst(MIPSInstruction inst, Map<Register, Register> rewrite) {
        switch (inst.op) {
            case MOVE: {
                Register d = map((Register)inst.operands.get(MIPSInstruction.R_D_MOV), rewrite);
                Register s = map((Register)inst.operands.get(MIPSInstruction.R_S_MOV), rewrite);
                return new MIPSInstruction(MIPSOp.MOVE, null, d, s);
            }
            case ADD: case SUB: case MUL: case DIV: case AND: case OR: case ADDI: case ANDI: case ORI: case SLL: {
                Register d = map((Register)inst.operands.get(MIPSInstruction.R_D_BOP), rewrite);
                Register s = map((Register)inst.operands.get(MIPSInstruction.R_S_BOP), rewrite);
                Object t = inst.operands.get(MIPSInstruction.R_T_BOP);
                if (t instanceof Register) {
                    Register tr = map((Register)t, rewrite);
                    return new MIPSInstruction(inst.op, null, d, s, tr);
                } else {
                    return new MIPSInstruction(inst.op, null, d, s, (Imm)t);
                }
            }
            case LI: {
                Register d = map((Register)inst.operands.get(MIPSInstruction.R_D_LI), rewrite);
                Imm imm = (Imm)inst.operands.get(MIPSInstruction.IMM_LI);
                return new MIPSInstruction(MIPSOp.LI, null, d, imm);
            }
            case LA: {
                Register d = map((Register)inst.operands.get(MIPSInstruction.R_D_LI), rewrite);
                Object second = inst.operands.get(1);
                if (second instanceof Addr) {
                    Addr a = (Addr)second;
                    if (a.mode == Addr.Mode.BASE_OFFSET && a.register != null) {
                        Register base = map(a.register, rewrite);
                        return new MIPSInstruction(MIPSOp.LA, null, d, new Addr(a.constant, base));
                    } else {
                        // PC-relative label case
                        return new MIPSInstruction(MIPSOp.LA, null, d, a);
                    }
                }
                return inst;
            }
            case LW: {
                Register d = map((Register)inst.operands.get(MIPSInstruction.R_D_LW), rewrite);
                Addr a = (Addr)inst.operands.get(MIPSInstruction.ADDR_LW);
                Register base = map(a.register, rewrite);
                return new MIPSInstruction(MIPSOp.LW, null, d, new Addr(a.constant, base));
            }
            case SW: {
                Register s = map((Register)inst.operands.get(MIPSInstruction.R_S_SW), rewrite);
                Addr a = (Addr)inst.operands.get(MIPSInstruction.ADDR_SW);
                Register base = map(a.register, rewrite);
                return new MIPSInstruction(MIPSOp.SW, null, s, new Addr(a.constant, base));
            }
            case BEQ: case BNE: case BLT: case BGT: case BGE: {
                Register s = map((Register)inst.operands.get(MIPSInstruction.R_S_BR), rewrite);
                Register t = map((Register)inst.operands.get(MIPSInstruction.R_T_BR), rewrite);
                Addr lab = (Addr)inst.operands.get(MIPSInstruction.LABEL_BR);
                return new MIPSInstruction(inst.op, null, s, t, lab);
            }
            case JR: {
                Register r = map((Register)inst.operands.get(MIPSInstruction.R_S_JR), rewrite);
                return new MIPSInstruction(MIPSOp.JR, null, r);
            }
            case J: {
                return inst;
            }
            default:
                return inst;
        }
    }

    private Register map(Register r, Map<Register, Register> rewrite) {
        if (r == null) return null;
        return rewrite.getOrDefault(r, r);
    }

    private StackLayout buildStackLayout(IRFunction function) {
        StackLayout layout = new StackLayout();
        // Start at -8 to hold $ra (at -4) and old $fp (at -8)
        int currentOffset = -8;

        // Parameters: assign stack slots
        for (IRVariableOperand param : function.parameters) {
            if (IR2MIPS.isLocalVar(param, function)) {
                continue;
            }
            layout.offsets.put(param.getName(), currentOffset);
            currentOffset -= MIPSInstruction.WORD_SIZE;
        }

        // Locals: assign stack slots (including arrays)
        for (IRVariableOperand var : function.variables) {
            if (!IR2MIPS.isLocalVar(var, function)) {
                continue;
            }
            int actualSize = MIPSInstruction.WORD_SIZE;
            if (var.type instanceof IRArrayType) {
                actualSize *= ((IRArrayType)var.type).getSize();
            }
            layout.offsets.put(var.getName(), currentOffset);
            currentOffset -= actualSize;
        }

        layout.totalStackSize = -currentOffset;
        // Add space for $ra and $fp
        layout.totalStackSize += 8;
        if (layout.totalStackSize % 8 != 0) layout.totalStackSize += 4;
        return layout;
    }

    private List<MIPSInstruction> generatePrologue(String funcName, int stackSize) {
        List<MIPSInstruction> prologue = new ArrayList<>();
        prologue.add(new MIPSInstruction(MIPSOp.NOT_AN_OP_LABEL, funcName));
        prologue.add(new MIPSInstruction(MIPSOp.ADDI, null, SP, SP, Imm.Dec(-stackSize)));
        prologue.add(new MIPSInstruction(MIPSOp.SW, null, RA, new Addr(Imm.Dec(stackSize - 4), SP)));
        prologue.add(new MIPSInstruction(MIPSOp.SW, null, FP, new Addr(Imm.Dec(stackSize - 8), SP)));
        prologue.add(new MIPSInstruction(MIPSOp.ADDI, null, FP, SP, Imm.Dec(stackSize - 8)));
        return prologue;
    }

    private List<MIPSInstruction> generateEpilogue(int stackSize) {
        List<MIPSInstruction> epilogue = new ArrayList<>();
        epilogue.add(new MIPSInstruction(MIPSOp.LW, null, RA, new Addr(Imm.Dec(stackSize - 4), SP)));
        epilogue.add(new MIPSInstruction(MIPSOp.LW, null, FP, new Addr(Imm.Dec(stackSize - 8), SP)));
        epilogue.add(new MIPSInstruction(MIPSOp.ADDI, null, SP, SP, Imm.Dec(stackSize)));
        return epilogue;
    }
}