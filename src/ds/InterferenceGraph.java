package opt;

import ds.BasicBlock;
import ds.ControlFlowGraph;
import ds.GraphNode;
import ir.IRInstruction;
import ir.operand.IRVariableOperand;
import ir.IRFunction;

import java.util.*;

/**
 * Undirected interference graph constructed from liveness:
 * For each instruction, for each defined variable d and each v in liveOut(inst),
 * add edge (d, v), v != d. For move-like instructions x := y, optionally skip
 * adding edge (x, y) to preserve coalescing opportunities.
 */
public class InterferenceGraph {

    private final ControlFlowGraph cfg;
    private final Liveness liveness;

    // adjacency: var -> set of interfering vars
    public final Map<String, Set<String>> adj = new HashMap<>();

    public InterferenceGraph(ControlFlowGraph cfg, Liveness liveness) {
        this.cfg = cfg;
        this.liveness = liveness;
        seedAllVariables(cfg.function);
        build();
    }

    private void seedAllVariables(IRFunction function) {
        if (function.parameters != null) {
            for (var p : function.parameters) {
                addNode(p.getName());
            }
        }
        if (function.variables != null) {
            for (var v : function.variables) {
                addNode(v.getName());
            }
        }
    }

    private void addNode(String v) {
        adj.computeIfAbsent(v, k -> new HashSet<>());
    }

    private void addEdge(String a, String b) {
        if (a.equals(b)) return;
        addNode(a);
        addNode(b);
        adj.get(a).add(b);
        adj.get(b).add(a);
    }

    private void build() {
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock bb = node.getData();

            List<IRInstruction> insts = new ArrayList<>(bb.instructions.values());
            insts.sort(Comparator.comparingInt(i -> i.irLineNumber));

            // Add clique edges for variables simultaneously live at instruction entry (liveIn)
            for (IRInstruction inst : insts) {
                Set<String> liveIn = liveness.liveInI.getOrDefault(inst, Collections.emptySet());
                // Build undirected edges among all pairs in liveIn
                List<String> vars = new ArrayList<>(liveIn);
                for (int i = 0; i < vars.size(); i++) {
                    for (int j = i + 1; j < vars.size(); j++) {
                        String a = vars.get(i);
                        String b = vars.get(j);
                        addEdge(a, b);
                    }
                }
            }

            for (IRInstruction inst : insts) {
                Set<String> out = liveness.liveOutI.getOrDefault(inst, Collections.emptySet());
                Set<String> defs = defsOf(inst);
                MoveInfo move = moveInfo(inst); // detect copies for coalescing
                for (String d : defs) {
                    for (String v : out) {
                        if (move.isMove && move.dest.equals(d) && move.src != null && move.src.equals(v)) {
                            // Skip interference between move dest/src to allow coalescing
                            continue;
                        }
                        addEdge(d, v);
                    }
                }
            }
        }
    }

    private static class MoveInfo {
        boolean isMove;
        String dest;
        String src;
    }

    // Detect move-like instruction: ASSIGN x, y
    private MoveInfo moveInfo(IRInstruction inst) {
        MoveInfo mi = new MoveInfo();
        if (inst.opCode == IRInstruction.OpCode.ASSIGN && inst.operands.length == 2) {
            mi.isMove = (inst.operands[0] instanceof IRVariableOperand);
            mi.dest = (inst.operands[0] instanceof IRVariableOperand)
                    ? ((IRVariableOperand) inst.operands[0]).getName() : null;
            mi.src = (inst.operands[1] instanceof IRVariableOperand)
                    ? ((IRVariableOperand) inst.operands[1]).getName() : null;
        } else {
            mi.isMove = false;
        }
        return mi;
    }

    // Minimal defs calculator (mirrors opt.Liveness.defsOf)
    private Set<String> defsOf(IRInstruction inst) {
        Set<String> defs = new HashSet<>();
        switch (inst.opCode) {
            case ASSIGN: {
                if (inst.operands.length == 2) {
                    if (inst.operands[0] instanceof IRVariableOperand) {
                        defs.add(((IRVariableOperand) inst.operands[0]).getName());
                    }
                }
                break;
            }
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR: {
                if (inst.operands.length >= 1 && inst.operands[0] instanceof IRVariableOperand) {
                    defs.add(((IRVariableOperand) inst.operands[0]).getName());
                }
                break;
            }
            case CALLR: {
                if (inst.operands.length >= 1 && inst.operands[0] instanceof IRVariableOperand) {
                    defs.add(((IRVariableOperand) inst.operands[0]).getName());
                }
                break;
            }
            case ARRAY_LOAD: {
                if (inst.operands.length >= 1 && inst.operands[0] instanceof IRVariableOperand) {
                    defs.add(((IRVariableOperand) inst.operands[0]).getName());
                }
                break;
            }
            default:
                break;
        }
        return defs;
    }

    public void printSummary() {
        System.out.println("Interference Graph");
        List<String> vars = new ArrayList<>(adj.keySet());
        vars.sort(String::compareTo);
        for (String v : vars) {
            List<String> nei = new ArrayList<>(adj.get(v));
            nei.sort(String::compareTo);
            System.out.println("  " + v + " : degree=" + nei.size() + " -> " + nei);
        }
        System.out.println();
    }
}


