package backend;

import ir.IRFunction;
import ir.operand.IRVariableOperand;
import mips.MIPSInstruction;
import mips.MIPSOp;
import mips.operand.Addr;
import mips.operand.Imm;
import mips.operand.Register;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Intra-block greedy register allocator.
 * - Builds a simple stack layout for virtual variables (locals/params)
 * - Splits function into basic blocks
 * - For each block, greedily assigns most-used virtuals to physical temps ($t0-$t9)
 * - Loads at block entry on first use; stores on write and on block exit
 * - Falls back to naive load/use/store for unmapped virtuals within the block
 */
public class IntraBlockAllocator {

    private static final Register $fp = new Register("$fp");
    private static final Register $sp = new Register("$sp");
    private static final Register $ra = new Register("$ra");
    private static final Register $t0 = new Register("$t0");
    private static final Register $t1 = new Register("$t1");
    private static final Register $t2 = new Register("$t2");
    private static final Register $t3 = new Register("$t3");
    private static final Register $t4 = new Register("$t4");
    private static final Register $t5 = new Register("$t5");
    private static final Register $t6 = new Register("$t6");
    private static final Register $t7 = new Register("$t7");
    private static final Register $t8 = new Register("$t8");
    private static final Register $t9 = new Register("$t9");

    private static class StackLayout {
        Map<String, Integer> offsets = new HashMap<>();
        int totalStackSize = 0;
    }

    public MIPSTranslation allocate(MIPSTranslation translation) {
        StackLayout layout = buildStackLayout(translation.irFunction);
        List<MIPSInstruction> out = new ArrayList<>();

        // Prologue
        out.add(new MIPSInstruction(MIPSOp.NOT_AN_OP_LABEL, translation.functionName));
        out.add(new MIPSInstruction(MIPSOp.ADDI, null, $sp, $sp, Imm.Dec(-layout.totalStackSize)));
        out.add(new MIPSInstruction(MIPSOp.SW, null, $ra, new Addr(Imm.Dec(layout.totalStackSize - 4), $sp)));
        out.add(new MIPSInstruction(MIPSOp.SW, null, $fp, new Addr(Imm.Dec(layout.totalStackSize - 8), $sp)));
        out.add(new MIPSInstruction(MIPSOp.ADDI, null, $fp, $sp, Imm.Dec(layout.totalStackSize - 8)));

        // Basic block boundaries
        List<Integer> boundaries = computeBlockBoundaries(translation.mipsInstructions);
        int n = translation.mipsInstructions.size();
        for (int b = 0; b < boundaries.size() - 1; b++) {
            int start = boundaries.get(b);
            int end = boundaries.get(b + 1);

            // Count uses within block
            Map<String, Integer> useCount = new HashMap<>();
            for (int i = start; i < end; i++) {
                MIPSInstruction inst = translation.mipsInstructions.get(i);
                for (var r : inst.getReads()) {
                    if (isVirtual(r)) {
                        inc(useCount, virtualName(r));
                    }
                }
                Register w = inst.getWrite();
                if (w != null && isVirtual(w)) {
                    inc(useCount, virtualName(w));
                }
            }

            // Greedy pick up to 10 virtuals
            List<Register> pool = List.of($t0, $t1, $t2, $t3, $t4, $t5, $t6, $t7, $t8, $t9);
            Map<String, Register> alloc = greedyAssign(useCount, pool);
            HashSet<String> loaded = new HashSet<>();
            HashSet<String> dirty = new HashSet<>();

            // Emit rewritten block
            for (int i = start; i < end; i++) {
                MIPSInstruction inst = translation.mipsInstructions.get(i);

                if (inst.op == MIPSOp.NOT_AN_OP_LABEL) {
                    out.add(inst);
                    continue;
                }

                if (inst.op == MIPSOp.J || inst.op == MIPSOp.SYSCALL) {
                    out.add(inst);
                    continue;
                }

                // Ensure sources loaded (only for stack-backed locals/params)
                for (Register r : inst.getReads()) {
                    if (isStackBacked(r)) {
                        String v = virtualName(r);
                        if (alloc.containsKey(v) && !loaded.contains(v)) {
                            int off = layout.offsets.getOrDefault(v, 0);
                            out.add(new MIPSInstruction(MIPSOp.LW, null, alloc.get(v), new Addr(Imm.Dec(off), $fp)));
                            loaded.add(v);
                        }
                    }
                }

                // Rewrite operands
                out.add(rewrite(inst, alloc));

                // Track writes
                Register w = inst.getWrite();
                if (w != null && isStackBacked(w)) {
                    String v = virtualName(w);
                    if (alloc.containsKey(v)) {
                        dirty.add(v);
                    } else {
                        // spill immediate: store via temp t9
                        Register t = $t9;
                        int off = layout.offsets.getOrDefault(v, 0);
                        out.add(new MIPSInstruction(MIPSOp.SW, null, t, new Addr(Imm.Dec(off), $fp)));
                    }
                }
            }

            // Flush stores at block end
            for (String v : dirty) {
                int off = layout.offsets.getOrDefault(v, 0);
                out.add(new MIPSInstruction(MIPSOp.SW, null, alloc.get(v), new Addr(Imm.Dec(off), $fp)));
            }
        }

        // Epilogue (if function ends without JR path)
        out.add(new MIPSInstruction(MIPSOp.LW, null, $ra, new Addr(Imm.Dec(layout.totalStackSize - 4), $sp)));
        out.add(new MIPSInstruction(MIPSOp.LW, null, $fp, new Addr(Imm.Dec(layout.totalStackSize - 8), $sp)));
        out.add(new MIPSInstruction(MIPSOp.ADDI, null, $sp, $sp, Imm.Dec(layout.totalStackSize)));

        return new MIPSTranslation(translation.irFunction, out);
    }

    private static String virtualName(Register r) {
        String n = r.name;
        if (n.startsWith("$v-local--")) return n.substring(10);
        if (n.startsWith("$v-param--")) return n.substring(10);
        return n;
    }

    private static boolean isVirtual(Register r) {
        String n = r.name;
        return n.startsWith("$v-local--") || n.startsWith("$v-param--") || n.startsWith("$v-temp--");
    }

    private static boolean isStackBacked(Register r) {
        String n = r.name;
        return n.startsWith("$v-local--") || n.startsWith("$v-param--");
    }

    private static void inc(Map<String,Integer> m, String k) {
        m.put(k, m.getOrDefault(k, 0) + 1);
    }

    private static Map<String, Register> greedyAssign(Map<String,Integer> useCount, List<Register> pool) {
        Map<String, Register> out = new HashMap<>();
        List<Map.Entry<String,Integer>> list = new ArrayList<>(useCount.entrySet());
        list.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        int i = 0;
        for (var e : list) {
            if (i >= pool.size()) break;
            out.put(e.getKey(), pool.get(i++));
        }
        return out;
    }

    private MIPSInstruction rewrite(MIPSInstruction inst, Map<String, Register> alloc) {
        // Replace virtual registers in operands with assigned physicals when available
        switch (inst.op) {
            case MOVE: {
                Register d = (Register) inst.operands.get(MIPSInstruction.R_D_MOV);
                Register s = (Register) inst.operands.get(MIPSInstruction.R_S_MOV);
                d = map(d, alloc);
                s = map(s, alloc);
                return new MIPSInstruction(MIPSOp.MOVE, null, d, s);
            }
            case ADD: case SUB: case MUL: case DIV: case AND: case OR: case ADDI: case SLL: {
                Register d = (Register) inst.operands.get(MIPSInstruction.R_D_BOP);
                Register s = (Register) inst.operands.get(MIPSInstruction.R_S_BOP);
                Object t = inst.operands.get(MIPSInstruction.R_T_BOP);
                d = map(d, alloc);
                s = map(s, alloc);
                if (t instanceof Register) {
                    t = map((Register)t, alloc);
                    return new MIPSInstruction(inst.op, null, d, s, (Register)t);
                } else {
                    return new MIPSInstruction(inst.op, null, d, s, (Imm)t);
                }
            }
            case LW: {
                Register d = (Register) inst.operands.get(MIPSInstruction.R_D_LW);
                Addr a = (Addr) inst.operands.get(MIPSInstruction.ADDR_LW);
                d = map(d, alloc);
                Register base = a.register;
                base = map(base, alloc);
                return new MIPSInstruction(MIPSOp.LW, null, d, new Addr(a.constant, base));
            }
            case SW: {
                Register s = (Register) inst.operands.get(MIPSInstruction.R_S_SW);
                Addr a = (Addr) inst.operands.get(MIPSInstruction.ADDR_SW);
                s = map(s, alloc);
                Register base = a.register;
                base = map(base, alloc);
                return new MIPSInstruction(MIPSOp.SW, null, s, new Addr(a.constant, base));
            }
            case LI: {
                Register d = (Register) inst.operands.get(MIPSInstruction.R_D_LI);
                Imm imm = (Imm) inst.operands.get(MIPSInstruction.IMM_LI);
                d = map(d, alloc);
                return new MIPSInstruction(MIPSOp.LI, null, d, imm);
            }
            case BEQ: case BNE: case BLT: case BGT: case BGE: {
                Register s = (Register) inst.operands.get(MIPSInstruction.R_S_BR);
                Register t = (Register) inst.operands.get(MIPSInstruction.R_T_BR);
                Addr lab = (Addr) inst.operands.get(MIPSInstruction.LABEL_BR);
                s = map(s, alloc);
                t = map(t, alloc);
                return new MIPSInstruction(inst.op, null, s, t, lab);
            }
            default:
                return inst;
        }
    }

    private static Register map(Register r, Map<String, Register> alloc) {
        if (r == null) return null;
        if (!isVirtual(r)) return r;
        String v = virtualName(r);
        return alloc.getOrDefault(v, r);
    }

    private StackLayout buildStackLayout(IRFunction function) {
        StackLayout layout = new StackLayout();
        int currentOffset = -8;

        for (IRVariableOperand param : function.parameters) {
            if (IR2MIPS.isLocalVar(param, function)) continue;
            layout.offsets.put(param.getName(), currentOffset);
            currentOffset -= MIPSInstruction.WORD_SIZE;
        }

        for (IRVariableOperand var : function.variables) {
            if (!IR2MIPS.isLocalVar(var, function)) continue;
            layout.offsets.put(var.getName(), currentOffset);
            currentOffset -= MIPSInstruction.WORD_SIZE;
        }

        layout.totalStackSize = -currentOffset + 8;
        if (layout.totalStackSize % 8 != 0) layout.totalStackSize += 4;
        return layout;
    }

    private List<Integer> computeBlockBoundaries(List<MIPSInstruction> insts) {
        List<Integer> b = new ArrayList<>();
        b.add(0);
        for (int i = 0; i < insts.size(); i++) {
            MIPSInstruction in = insts.get(i);
            if (in.op == MIPSOp.NOT_AN_OP_LABEL) {
                b.add(i);
            }
            if (in.op == MIPSOp.BEQ || in.op == MIPSOp.BNE || in.op == MIPSOp.BLT || in.op == MIPSOp.BGT || in.op == MIPSOp.BGE || in.op == MIPSOp.JR || in.op == MIPSOp.J || in.op == MIPSOp.SYSCALL) {
                b.add(i + 1);
            }
        }
        if (b.get(b.size() - 1) != insts.size()) b.add(insts.size());
        // normalize
        List<Integer> out = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
        for (int x : b) {
            if (!seen.contains(x)) { out.add(x); seen.add(x); }
        }
        out.sort(Integer::compareTo);
        return out;
    }
}


