package opt;

import ds.BasicBlock;
import ds.ControlFlowGraph;
import ds.GraphNode;
import ir.IRInstruction;
import ir.operand.IROperand;
import ir.operand.IRVariableOperand;

import java.util.*;

/**
 * Backward data-flow liveness analysis over the IR using the existing CFG.
 *
 * Block-level equations:
 *   IN[B] = USE[B] ∪ (OUT[B] − DEF[B])
 *   OUT[B] = ⋃ IN[S] for all successors S of B
 *
 * Optional instruction-level liveness can be computed per block by walking
 * the instructions backward once the block IN/OUT sets stabilize.
 */
public class Liveness {

    private final ControlFlowGraph cfg;

    // Per-block sets
    public final Map<BasicBlock, Set<String>> useB = new HashMap<>();
    public final Map<BasicBlock, Set<String>> defB = new HashMap<>();
    public final Map<BasicBlock, Set<String>> inB  = new HashMap<>();
    public final Map<BasicBlock, Set<String>> outB = new HashMap<>();

    // Optional per-instruction liveness
    public final Map<IRInstruction, Set<String>> liveInI  = new HashMap<>();
    public final Map<IRInstruction, Set<String>> liveOutI = new HashMap<>();

    public Liveness(ControlFlowGraph cfg) {
        this.cfg = cfg;
        computeUseDef();
        solveBlockLiveness();
        computeInstructionLiveness();
    }

    private void computeUseDef() {
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock block = node.getData();

            Set<String> use = new HashSet<>();
            Set<String> def = new HashSet<>();

            List<IRInstruction> sorted = new ArrayList<>(block.instructions.values());
            sorted.sort(Comparator.comparingInt(i -> i.irLineNumber));

            for (IRInstruction inst : sorted) {
                Set<String> uses = usesOf(inst);
                Set<String> defs = defsOf(inst);

                for (String v : uses) {
                    if (!def.contains(v)) {
                        use.add(v);
                    }
                }
                def.addAll(defs);
            }

            useB.put(block, use);
            defB.put(block, def);
            inB.put(block, new HashSet<>());
            outB.put(block, new HashSet<>());
        }
    }

    private void solveBlockLiveness() {
        boolean changed;
        int guard = 0;
        do {
            changed = false;
            guard++;
            for (GraphNode<BasicBlock> node : cfg.getNodes()) {
                BasicBlock block = node.getData();

                Set<String> newOut = new HashSet<>();
                for (GraphNode<BasicBlock> succ : cfg.getSuccessors(node)) {
                    newOut.addAll(inB.get(succ.getData()));
                }

                Set<String> newIn = new HashSet<>(useB.get(block));
                Set<String> temp = new HashSet<>(newOut);
                temp.removeAll(defB.get(block));
                newIn.addAll(temp);

                if (!newOut.equals(outB.get(block)) || !newIn.equals(inB.get(block))) {
                    outB.put(block, newOut);
                    inB.put(block, newIn);
                    changed = true;
                }
            }
        } while (changed && guard < 1000);
    }

    private void computeInstructionLiveness() {
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock block = node.getData();

            List<IRInstruction> insts = new ArrayList<>(block.instructions.values());
            insts.sort(Comparator.comparingInt(i -> i.irLineNumber));

            Set<String> live = new HashSet<>(outB.get(block));

            for (int i = insts.size() - 1; i >= 0; --i) {
                IRInstruction inst = insts.get(i);

                Set<String> uses = usesOf(inst);
                Set<String> defs = defsOf(inst);

                Set<String> out = new HashSet<>(live);
                Set<String> in  = new HashSet<>(uses);
                out.removeAll(defs);
                in.addAll(out);

                liveInI.put(inst, in);
                liveOutI.put(inst, new HashSet<>(live));
                live = in;
            }
        }
    }

    private boolean isVarOperand(IROperand op) {
        return (op instanceof IRVariableOperand);
    }

    private String asVarName(IROperand op) {
        return ((IRVariableOperand) op).getName();
    }

    private Set<String> usesOf(IRInstruction inst) {
        Set<String> uses = new HashSet<>();
        switch (inst.opCode) {
            case ASSIGN: {
                // Two forms:
                //  - x, y   -> def x; use y (if var)
                //  - a, i, v  (array form) -> use a,i,v (if var); no defs
                if (inst.operands.length == 2) {
                    if (isVarOperand(inst.operands[1])) {
                        uses.add(asVarName(inst.operands[1]));
                    }
                } else if (inst.operands.length == 3) {
                    if (isVarOperand(inst.operands[0])) uses.add(asVarName(inst.operands[0])); // array base
                    if (isVarOperand(inst.operands[1])) uses.add(asVarName(inst.operands[1])); // index
                    if (isVarOperand(inst.operands[2])) uses.add(asVarName(inst.operands[2])); // value
                }
                break;
            }
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR: {
                if (inst.operands.length >= 3) {
                    if (isVarOperand(inst.operands[1])) uses.add(asVarName(inst.operands[1]));
                    if (isVarOperand(inst.operands[2])) uses.add(asVarName(inst.operands[2]));
                }
                break;
            }
            case BREQ:
            case BRNEQ:
            case BRLT:
            case BRGT:
            case BRGEQ: {
                // operands: [label, op1, op2]
                if (inst.operands.length >= 3) {
                    if (isVarOperand(inst.operands[1])) uses.add(asVarName(inst.operands[1]));
                    if (isVarOperand(inst.operands[2])) uses.add(asVarName(inst.operands[2]));
                }
                break;
            }
            case RETURN: {
                if (inst.operands.length >= 1 && isVarOperand(inst.operands[0])) {
                    uses.add(asVarName(inst.operands[0]));
                }
                break;
            }
            case CALL: {
                // operands: [func, a1, a2, ...]
                for (int i = 1; i < inst.operands.length; i++) {
                    if (isVarOperand(inst.operands[i])) uses.add(asVarName(inst.operands[i]));
                }
                break;
            }
            case CALLR: {
                // operands: [dest, func, a1, a2, ...]
                for (int i = 2; i < inst.operands.length; i++) {
                    if (isVarOperand(inst.operands[i])) uses.add(asVarName(inst.operands[i]));
                }
                break;
            }
            case ARRAY_STORE: {
                // operands: [value, array, index]
                if (inst.operands.length >= 3) {
                    if (isVarOperand(inst.operands[0])) uses.add(asVarName(inst.operands[0])); // value
                    if (isVarOperand(inst.operands[1])) uses.add(asVarName(inst.operands[1])); // array
                    if (isVarOperand(inst.operands[2])) uses.add(asVarName(inst.operands[2])); // index
                }
                break;
            }
            case ARRAY_LOAD: {
                // operands: [dest, array, index]
                if (inst.operands.length >= 3) {
                    if (isVarOperand(inst.operands[1])) uses.add(asVarName(inst.operands[1])); // array
                    if (isVarOperand(inst.operands[2])) uses.add(asVarName(inst.operands[2])); // index
                }
                break;
            }
            case GOTO:
            case LABEL:
                break;
            default:
                break;
        }
        return uses;
    }

    private Set<String> defsOf(IRInstruction inst) {
        Set<String> defs = new HashSet<>();
        switch (inst.opCode) {
            case ASSIGN: {
                if (inst.operands.length == 2) {
                    if (isVarOperand(inst.operands[0])) defs.add(asVarName(inst.operands[0]));
                }
                // array-form of ASSIGN defines memory (modeled as no SSA variable def)
                break;
            }
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR: {
                if (inst.operands.length >= 1 && isVarOperand(inst.operands[0])) {
                    defs.add(asVarName(inst.operands[0]));
                }
                break;
            }
            case CALLR: {
                // operands: [dest, func, ...]
                if (inst.operands.length >= 1 && isVarOperand(inst.operands[0])) {
                    defs.add(asVarName(inst.operands[0]));
                }
                break;
            }
            case ARRAY_LOAD: {
                // operands: [dest, array, index]
                if (inst.operands.length >= 1 && isVarOperand(inst.operands[0])) {
                    defs.add(asVarName(inst.operands[0]));
                }
                break;
            }
            default:
                break;
        }
        return defs;
    }

    public void printBlockResults() {
        System.out.println("Liveness Analysis (per block)");
        System.out.println();
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock block = node.getData();
            String blockName = block.entryLabel != null ? block.entryLabel : "BB_" + block.id;
            System.out.println("Block " + blockName + ":");
            System.out.println("  USE: " + sorted(useB.get(block)));
            System.out.println("  DEF: " + sorted(defB.get(block)));
            System.out.println("  IN : " + sorted(inB.get(block)));
            System.out.println("  OUT: " + sorted(outB.get(block)));
            System.out.println();
        }
    }

    private String sorted(Set<String> s) {
        if (s == null || s.isEmpty()) return "{}";
        List<String> list = new ArrayList<>(s);
        list.sort(String::compareTo);
        return "{" + String.join(", ", list) + "}";
    }
}


