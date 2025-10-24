package backend;

import mips.MIPSInstruction;
import mips.MIPSOp;
import mips.operand.Addr;
import mips.operand.Imm;
import mips.operand.Register;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NaiveRegisterAllocator {

    public static List<MIPSInstruction> apply(List<MIPSInstruction> original,
                                              Map<String, Integer> localVarToFpOffset)
    {
        // Prepass: assign stable spill slots for all virtual locals encountered
        if (localVarToFpOffset == null) throw new IllegalArgumentException("offset map cannot be null");
        int[] nextOffset = new int[]{ computeNextOffset(localVarToFpOffset) };
        for (MIPSInstruction inst : original) {
            collectVirtualsForOffsets(inst, localVarToFpOffset, nextOffset);
        }

        List<MIPSInstruction> out = new ArrayList<>();

        for (MIPSInstruction inst : original) {
            // Per-instruction temp pool
            TempPool temps = new TempPool();

            // Load all virtual reads into temps and rewrite operands (including Addr bases)
            rewriteReadsWithLoads(inst, localVarToFpOffset, temps, out);

            // Determine destination rewrite (if any)
            Register write = inst.getWrite();
            Register writeTemp = null;
            if (write != null && isVirtualLocal(write)) {
                writeTemp = temps.acquire();
                // replace the write register in operands with the temp
                replaceRegisterOperand(inst, write, writeTemp);
            }

            // Emit the rewritten original instruction
            out.add(inst);

            // Store-back for a virtual write
            if (write != null && isVirtualLocal(write)) {
                int offset = localVarToFpOffset.get(write.name);
                out.add(new MIPSInstruction(MIPSOp.SW, null, writeTemp, new Addr(Imm.Dec(-offset), Register.$fp)));
            }
        }

        return out;
    }

    private static void collectVirtualsForOffsets(MIPSInstruction inst,
                                                  Map<String,Integer> map,
                                                  int[] nextOffsetBox) {
        // Registers in operand list
        for (int i = 0; i < inst.operands.size(); i++) {
            var op = inst.operands.get(i);
            if (op instanceof Register) {
                var r = (Register) op;
                if (isVirtualLocal(r) && !map.containsKey(r.name)) {
                    map.put(r.name, nextOffsetBox[0]);
                    nextOffsetBox[0] += 4;
                }
            } else if (op instanceof Addr) {
                var a = (Addr) op;
                if (a.register != null && isVirtualLocal(a.register) && !map.containsKey(a.register.name)) {
                    map.put(a.register.name, nextOffsetBox[0]);
                    nextOffsetBox[0] += 4;
                }
            }
        }
    }

    private static int computeNextOffset(Map<String,Integer> map) {
        if (map.isEmpty()) return 4;
        int max = 0;
        for (int off : map.values()) max = Math.max(max, off);
        return max + 4;
    }

    private static void rewriteReadsWithLoads(MIPSInstruction inst,
                                              Map<String,Integer> localVarToFpOffset,
                                              TempPool temps,
                                              List<MIPSInstruction> out) {
        // Track which virtuals we’ve already loaded to which temp in this instruction
        Map<String, Register> vToTemp = new java.util.HashMap<>();

        // Helper to get or create a temp for a virtual and emit a load
        java.util.function.Function<Register, Register> ensureLoaded = (virt) -> {
            Register have = vToTemp.get(virt.name);
            if (have != null) return have;
            Register t = temps.acquire();
            int off = localVarToFpOffset.get(virt.name);
            out.add(new MIPSInstruction(MIPSOp.LW, null, t, new Addr(Imm.Dec(-off), Register.$fp)));
            vToTemp.put(virt.name, t);
            return t;
        };

        // Rewrite plain register operands
        for (int i = 0; i < inst.operands.size(); i++) {
            var op = inst.operands.get(i);
            if (op instanceof Register) {
                var r = (Register) op;
                if (isVirtualLocal(r)) {
                    Register t = ensureLoaded.apply(r);
                    inst.operands.set(i, t);
                }
            } else if (op instanceof Addr) {
                var a = (Addr) op;
                if (a.register != null && isVirtualLocal(a.register)) {
                    Register t = ensureLoaded.apply(a.register);
                    inst.operands.set(i, new Addr(a.constant, t));
                }
            }
        }
    }

    private static void replaceRegisterOperand(MIPSInstruction inst, Register from, Register to) {
        for (int i = 0; i < inst.operands.size(); i++) {
            var op = inst.operands.get(i);
            if (op instanceof Register) {
                var r = (Register) op;
                if (r.equals(from)) inst.operands.set(i, to);
            } else if (op instanceof Addr) {
                var a = (Addr) op;
                if (a.register != null && a.register.equals(from)) {
                    inst.operands.set(i, new Addr(a.constant, to));
                }
            }
        }
    }

    private static boolean isVirtualLocal(Register r) {
        if (r == null) return false;
        String n = r.name;
        // Treat $vlocal* as compiler virtual locals that need spilling
        if (n.startsWith("$v-local--")) return true;
        return false;
    }

    private static class TempPool {
        private final Register[] pool = new Register[]{ Register.T0, Register.T1, Register.T2, Register.T3 };
        private int idx = 0;
        Register acquire() { return pool[Math.min(idx++, pool.length - 1)]; }
    }
}


