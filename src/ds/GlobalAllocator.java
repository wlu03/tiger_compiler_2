package backend;

import ds.ControlFlowGraph;
import ir.IRFunction;
import ir.datatype.IRArrayType;
import ir.datatype.IRIntType;
import mips.MIPSInstruction;
import mips.MIPSOp;
import mips.operand.Addr;
import mips.operand.Imm;
import mips.operand.MIPSOperand;
import mips.operand.Register;
import opt.ChaitinBriggsColoring;
import opt.InterferenceGraph;
import opt.Liveness;

import java.util.*;

/**
 * Global register allocator: whole-function liveness, interference graph,
 * Chaitin–Briggs optimistic coloring, and MIPS rewrite with spills.
 */
public class GlobalAllocator {

    // Physical registers to color to (callee-saved $s0..$s7)
    private static final Register[] PHYS = new Register[] {
            new Register("$s0", false),
            new Register("$s1", false),
            new Register("$s2", false),
            new Register("$s3", false),
            new Register("$s4", false),
            new Register("$s5", false),
            new Register("$s6", false),
            new Register("$s7", false)
    };

    // Scratch registers used by the rewriter (caller-saved)
    private static final Register SCR1 = Register.T0; // for spilled reads (first)
    private static final Register SCR2 = Register.T1; // for spilled reads (second)
    private static final Register SCRW = Register.T2; // for spilled writes

    public MIPSTranslation allocate(MIPSTranslation translation) {
        IRFunction function = translation.irFunction;

        // Build analysis
        ControlFlowGraph cfg = new ControlFlowGraph(function);
        Liveness liveness = new Liveness(cfg);
        InterferenceGraph ig = new InterferenceGraph(cfg, liveness);
        ChaitinBriggsColoring coloring = new ChaitinBriggsColoring(ig, cfg, PHYS.length);

        // Map variable -> physical register
        Map<String, Register> varToReg = new HashMap<>();
        for (Map.Entry<String, Integer> e : coloring.colorOf.entrySet()) {
            varToReg.put(e.getKey(), PHYS[e.getValue()]);
        }
        Set<String> spilledVars = new HashSet<>(coloring.spilled);

        // Assign spill slots (offsets from $sp after prologue)
        Map<String, Integer> spillOffset = new HashMap<>();
        int numSavedS = usedSRegisters(varToReg).size();
        int localVarSize = computeLocalVarSize(function);
        int spillCount = spilledVars.size();
        // Layout (stack grows down):
        //  [spills + saved $s regs] below $sp
        //  $sp .. $fp .. (locals addressed from $fp downward via array offset)
        // saved area: 0 .. (numSavedS-1)*4
        int spillBaseOffset = numSavedS * MIPSInstruction.WORD_SIZE;
        int i = 0;
        for (String v : spilledVars) {
            spillOffset.put(v, spillBaseOffset + i * MIPSInstruction.WORD_SIZE);
            i++;
        }
        int frameSize = roundUpTo(spillBaseOffset + spillCount * MIPSInstruction.WORD_SIZE + localVarSize, 8);

        // Rewrite instructions
        List<MIPSInstruction> out = new ArrayList<>();
        // If first instruction is the function entry label, emit it before prologue
        int startIdx = 0;
        if (!translation.mipsInstructions.isEmpty()) {
            MIPSInstruction first = translation.mipsInstructions.get(0);
            if (first.op == MIPSOp.NOT_AN_OP_LABEL && function.name.equals(first.label)) {
                out.add(first);
                startIdx = 1;
            } else {
                // Ensure function has an entry label
                out.add(new MIPSInstruction(MIPSOp.NOT_AN_OP_LABEL, function.name));
            }
        } else {
            out.add(new MIPSInstruction(MIPSOp.NOT_AN_OP_LABEL, function.name));
        }
        // Prologue after function label
        out.addAll(generatePrologue(frameSize, usedSRegisters(varToReg)));

        for (int idx = startIdx; idx < translation.mipsInstructions.size(); idx++) {
            MIPSInstruction inst = translation.mipsInstructions.get(idx);
            if (inst.op == MIPSOp.NOT_AN_OP_LABEL) {
                out.add(inst);
                continue;
            }

            Map<Register, Register> rewrite = new HashMap<>();
            List<MIPSInstruction> pre = new ArrayList<>();
            List<MIPSInstruction> post = new ArrayList<>();

            // Prepare reads (including base of LW/SW)
            Register[] reads = inst.getReads();
            int scrUsed = 0;
            for (Register r : reads) {
                if (isStackBacked(r)) {
                    String v = trueName(r);
                    if (varToReg.containsKey(v)) {
                        rewrite.put(r, varToReg.get(v));
                    } else if (spilledVars.contains(v)) {
                        Register scr = (scrUsed == 0) ? SCR1 : SCR2;
                        if (scrUsed < 2) scrUsed++;
                        Integer off = spillOffset.get(v);
                        pre.add(new MIPSInstruction(MIPSOp.LW, null, scr, new Addr(Imm.Dec(off), Register.$sp)));
                        rewrite.put(r, scr);
                    }
                } else if (isTempVirtual(r)) {
                    rewrite.put(r, getTempAssignment(r));
                }
            }

            // Prepare write
            Register w = inst.getWrite();
            boolean spillDef = false;
            String spillDefVar = null;
            if (w != null) {
                if (isStackBacked(w)) {
                    String v = trueName(w);
                    if (varToReg.containsKey(v)) {
                        rewrite.put(w, varToReg.get(v));
                    } else if (spilledVars.contains(v)) {
                        spillDef = true;
                        spillDefVar = v;
                        rewrite.put(w, SCRW);
                    }
                } else if (isTempVirtual(w)) {
                    rewrite.put(w, getTempAssignment(w));
                }
            }

            // Emit
            out.addAll(pre);
            MIPSInstruction rewritten = rewriteInst(inst, rewrite);
            // Drop self move
            if (rewritten.op == MIPSOp.MOVE &&
                    rewritten.operands.get(MIPSInstruction.R_D_MOV).equals(rewritten.operands.get(MIPSInstruction.R_S_MOV))) {
                // skip
            } else {
                out.add(rewritten);
            }

            if (spillDef) {
                int off = spillOffset.get(spillDefVar);
                post.add(new MIPSInstruction(MIPSOp.SW, null, SCRW, new Addr(Imm.Dec(off), Register.$sp)));
            }
            out.addAll(post);
        }

        // Epilogue insert: before each JR $ra or before main syscall exit
        List<MIPSInstruction> finalOut = new ArrayList<>();
        for (int k = 0; k < out.size(); k++) {
            MIPSInstruction inst = out.get(k);
            boolean isExit = (inst.op == MIPSOp.JR && inst.operands.get(MIPSInstruction.R_S_JR).equals(Register.$ra));
            boolean isMainExit = isMainExitSequence(out, k);
            if (isExit || isMainExit) {
                // restore and dealloc frame before exit
                finalOut.addAll(generateEpilogue(frameSize, usedSRegisters(varToReg)));
            }
            finalOut.add(inst);
        }

        return new MIPSTranslation(function, finalOut);
    }

    private static Set<Register> usedSRegisters(Map<String, Register> varToReg) {
        Set<Register> used = new LinkedHashSet<>();
        for (Register r : varToReg.values()) used.add(r);
        return used;
    }

    private static int roundUpTo(int n, int multiple) {
        int rem = n % multiple;
        return rem == 0 ? n : n + (multiple - rem);
    }

    private static boolean isStackBacked(Register r) {
        if (r == null) return false;
        String n = r.name;
        return n.startsWith("$v-local--") || n.startsWith("$v-param--");
    }

    private static String trueName(Register r) {
        if (r.name.startsWith("$v-local--")) return r.name.substring(10);
        if (r.name.startsWith("$v-param--")) return r.name.substring(10);
        return r.name;
    }

    private static boolean isTempVirtual(Register r) {
        return r != null && r.name.startsWith("$v-temp--");
    }

    private static Register getTempAssignment(Register v) {
        String purpose = v.name.substring(9); // after "$v-temp--"
        // Use distinct temporaries to avoid clobbering address computation:
        //  - tmp  -> $t9
        //  - off  -> $t8
        //  - base -> $t7
        //  - addr -> $t6
        if (purpose.equals("tmp")) return Register.T9;
        if (purpose.equals("off")) return Register.T8;
        if (purpose.equals("base")) return Register.T7;
        if (purpose.equals("addr")) return Register.T6;
        return Register.T8;
    }

    private static boolean isMainExitSequence(List<MIPSInstruction> list, int idx) {
        if (idx + 1 >= list.size()) return false;
        MIPSInstruction a = list.get(idx);
        MIPSInstruction b = list.get(idx + 1);
        if (a.op != MIPSOp.LI || b.op != MIPSOp.SYSCALL) return false;
        // Ensure it's specifically: li $v0, 10
        Register dest = (Register) a.operands.get(MIPSInstruction.R_D_LI);
        Imm imm = (Imm) a.operands.get(MIPSInstruction.IMM_LI);
        return "$v0".equals(dest.name) && imm.getInt() == 10;
    }

    private static List<MIPSInstruction> generatePrologue(int frameSize, Set<Register> usedS) {
        List<MIPSInstruction> pro = new ArrayList<>();
        // Set frame pointer to caller's SP (top-of-frame) first to match array addressing
        pro.add(new MIPSInstruction(MIPSOp.MOVE, null, Register.$fp, Register.$sp));
        if (frameSize > 0) {
            pro.add(new MIPSInstruction(MIPSOp.ADDI, null, Register.$sp, Register.$sp, Imm.Dec(-frameSize)));
        }
        int off = 0;
        for (Register s : usedS) {
            pro.add(new MIPSInstruction(MIPSOp.SW, null, s, new Addr(Imm.Dec(off), Register.$sp)));
            off += MIPSInstruction.WORD_SIZE;
        }
        return pro;
    }

    private static List<MIPSInstruction> generateEpilogue(int frameSize, Set<Register> usedS) {
        List<MIPSInstruction> epi = new ArrayList<>();
        int off = 0;
        for (Register s : usedS) {
            epi.add(new MIPSInstruction(MIPSOp.LW, null, s, new Addr(Imm.Dec(off), Register.$sp)));
            off += MIPSInstruction.WORD_SIZE;
        }
        if (frameSize > 0) {
            epi.add(new MIPSInstruction(MIPSOp.ADDI, null, Register.$sp, Register.$sp, Imm.Dec(frameSize)));
        }
        return epi;
    }

    private static int computeLocalVarSize(IRFunction function) {
        if (function == null || function.variables == null) return 0;
        int size = 0;
        for (var variable : function.variables) {
            // skip parameters
            if (function.parameters != null && function.parameters.contains(variable)) {
                continue;
            }
            if (variable.type instanceof IRArrayType) {
                size += ((IRArrayType) variable.type).getSize() * MIPSInstruction.WORD_SIZE;
            } else if (variable.type instanceof IRIntType) {
                size += MIPSInstruction.WORD_SIZE;
            }
        }
        return size;
    }

    private static MIPSInstruction rewriteInst(MIPSInstruction inst, Map<Register, Register> rewrite) {
        switch (inst.op) {
            case MOVE: {
                Register d = map((Register)inst.operands.get(MIPSInstruction.R_D_MOV), rewrite);
                Register s = map((Register)inst.operands.get(MIPSInstruction.R_S_MOV), rewrite);
                return new MIPSInstruction(MIPSOp.MOVE, null, d, s);
            }
            case ADD: case SUB: case MUL: case DIV: case AND: case OR: case ADDI: case ANDI: case ORI: case SLL: {
                Register d = map((Register)inst.operands.get(MIPSInstruction.R_D_BOP), rewrite);
                Register s = map((Register)inst.operands.get(MIPSInstruction.R_S_BOP), rewrite);
                MIPSOperand t = inst.operands.get(MIPSInstruction.R_T_BOP);
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
                MIPSOperand second = inst.operands.get(1);
                if (second instanceof Addr) {
                    Addr a = (Addr)second;
                    Register base = map(a.register, rewrite);
                    return new MIPSInstruction(MIPSOp.LA, null, d, new Addr(a.constant, base));
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
            case J:
            case SYSCALL:
            case NOT_AN_OP_LABEL:
            default:
                return inst;
        }
    }

    private static Register map(Register r, Map<Register, Register> rewrite) {
        if (r == null) return null;
        return rewrite.getOrDefault(r, r);
    }
}


