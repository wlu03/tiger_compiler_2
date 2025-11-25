package opt;

import ds.ControlFlowGraph;
import ir.IRFunction;
import ir.IRInstruction;
import ir.operand.IRVariableOperand;

import java.util.*;

/**
 * Chaitin–Briggs optimistic graph coloring for register allocation.
 *
 * Inputs:
 *  - InterferenceGraph (built from Liveness)
 *  - Number of colors (K)
 *  - Optional spill costs per variable (lower is cheaper to spill)
 *
 * Outputs:
 *  - colorOf: variable -> color index [0..K-1]
 *  - spilled: variables that could not be colored (require spilling)
 */
public class ChaitinBriggsColoring {

    private final InterferenceGraph ig;
    private final ControlFlowGraph cfg;
    private final int K;
    private final Map<String, Integer> spillCostProvided; // optional

    public final Map<String, Integer> colorOf = new HashMap<>();
    public final Set<String> spilled = new HashSet<>();

    public ChaitinBriggsColoring(InterferenceGraph ig, ControlFlowGraph cfg, int K) {
        this(ig, cfg, K, null);
    }

    public ChaitinBriggsColoring(InterferenceGraph ig, ControlFlowGraph cfg, int K, Map<String, Integer> spillCost) {
        this.ig = ig;
        this.cfg = cfg;
        this.K = K;
        this.spillCostProvided = spillCost;
        run();
    }

    private void run() {
        // Work structures
        Map<String, Integer> degree = new HashMap<>();
        Set<String> nodes = new HashSet<>(ig.adj.keySet());
        for (String v : nodes) {
            degree.put(v, ig.adj.getOrDefault(v, Collections.emptySet()).size());
        }

        Deque<String> selectStack = new ArrayDeque<>();
        Set<String> removed = new HashSet<>();

        // Simplify + Spill selection
        while (removed.size() < nodes.size()) {
            // Try to find a low-degree node
            String lowDegNode = null;
            for (String v : nodes) {
                if (removed.contains(v)) continue;
                Integer d = degree.get(v);
                if (d != null && d < K) {
                    lowDegNode = v;
                    break;
                }
            }

            if (lowDegNode != null) {
                // Simplify
                removeNodeAndUpdateDegrees(lowDegNode, degree, removed);
                selectStack.push(lowDegNode);
            } else {
                // Spill candidate selection (heuristic: min cost/degree)
                String candidate = chooseSpillCandidate(nodes, removed, degree);
                if (candidate == null) break; // should not happen
                removeNodeAndUpdateDegrees(candidate, degree, removed);
                selectStack.push(candidate);
            }
        }

        // Select (coloring)
        while (!selectStack.isEmpty()) {
            String v = selectStack.pop();
            Set<Integer> forbidden = new HashSet<>();
            for (String nei : ig.adj.getOrDefault(v, Collections.emptySet())) {
                Integer c = colorOf.get(nei);
                if (c != null) forbidden.add(c);
            }
            Integer chosen = null;
            for (int c = 0; c < K; c++) {
                if (!forbidden.contains(c)) {
                    chosen = c;
                    break;
                }
            }
            if (chosen != null) {
                colorOf.put(v, chosen);
            } else {
                spilled.add(v);
            }
        }
    }

    private void removeNodeAndUpdateDegrees(String v,
                                            Map<String, Integer> degree,
                                            Set<String> removed) {
        removed.add(v);
        for (String nei : ig.adj.getOrDefault(v, Collections.emptySet())) {
            if (!removed.contains(nei)) {
                degree.put(nei, Math.max(0, degree.getOrDefault(nei, 0) - 1));
            }
        }
    }

    private String chooseSpillCandidate(Set<String> nodes,
                                        Set<String> removed,
                                        Map<String, Integer> degree) {
        String best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (String v : nodes) {
            if (removed.contains(v)) continue;
            int deg = Math.max(1, degree.getOrDefault(v, 0));
            int cost = getSpillCost(v);
            double score = (double) cost / (double) deg; // lower is "cheaper" to spill
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    private int getSpillCost(String v) {
        if (spillCostProvided != null && spillCostProvided.containsKey(v)) {
            return Math.max(1, spillCostProvided.get(v));
        }
        // Approximate: count appearances (uses + defs) across function
        return Math.max(1, approximateFrequencyInFunction(cfg.function, v));
    }

    private int approximateFrequencyInFunction(IRFunction f, String var) {
        int count = 0;
        for (IRInstruction inst : f.instructions) {
            if (inst.opCode == IRInstruction.OpCode.LABEL) continue;
            // uses
            switch (inst.opCode) {
                case ASSIGN: {
                    if (inst.operands.length == 2) {
                        if (inst.operands[1] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[1]).getName().equals(var)) count++;
                    } else if (inst.operands.length == 3) {
                        if (inst.operands[0] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[0]).getName().equals(var)) count++;
                        if (inst.operands[1] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[1]).getName().equals(var)) count++;
                        if (inst.operands[2] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[2]).getName().equals(var)) count++;
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
                        if (inst.operands[1] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[1]).getName().equals(var)) count++;
                        if (inst.operands[2] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[2]).getName().equals(var)) count++;
                    }
                    break;
                }
                case BREQ:
                case BRNEQ:
                case BRLT:
                case BRGT:
                case BRGEQ: {
                    if (inst.operands.length >= 3) {
                        if (inst.operands[1] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[1]).getName().equals(var)) count++;
                        if (inst.operands[2] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[2]).getName().equals(var)) count++;
                    }
                    break;
                }
                case RETURN: {
                    if (inst.operands.length >= 1
                            && inst.operands[0] instanceof IRVariableOperand
                            && ((IRVariableOperand) inst.operands[0]).getName().equals(var)) count++;
                    break;
                }
                case CALL: {
                    for (int i = 1; i < inst.operands.length; i++) {
                        if (inst.operands[i] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[i]).getName().equals(var)) count++;
                    }
                    break;
                }
                case CALLR: {
                    for (int i = 2; i < inst.operands.length; i++) {
                        if (inst.operands[i] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[i]).getName().equals(var)) count++;
                    }
                    break;
                }
                case ARRAY_STORE: {
                    if (inst.operands.length >= 3) {
                        if (inst.operands[0] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[0]).getName().equals(var)) count++;
                        if (inst.operands[1] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[1]).getName().equals(var)) count++;
                        if (inst.operands[2] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[2]).getName().equals(var)) count++;
                    }
                    break;
                }
                case ARRAY_LOAD: {
                    if (inst.operands.length >= 3) {
                        if (inst.operands[1] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[1]).getName().equals(var)) count++;
                        if (inst.operands[2] instanceof IRVariableOperand
                                && ((IRVariableOperand) inst.operands[2]).getName().equals(var)) count++;
                    }
                    break;
                }
                default:
                    break;
            }
            // defs
            switch (inst.opCode) {
                case ASSIGN:
                case ADD:
                case SUB:
                case MULT:
                case DIV:
                case AND:
                case OR:
                case CALLR:
                case ARRAY_LOAD: {
                    if (inst.operands.length >= 1
                            && inst.operands[0] instanceof IRVariableOperand
                            && ((IRVariableOperand) inst.operands[0]).getName().equals(var)) count++;
                    break;
                }
                default:
                    break;
            }
        }
        return count;
    }

    public void printResult() {
        System.out.println("Chaitin–Briggs Coloring (K=" + K + ")");
        List<String> vars = new ArrayList<>(ig.adj.keySet());
        vars.sort(String::compareTo);
        for (String v : vars) {
            if (spilled.contains(v)) {
                System.out.println("  " + v + " -> SPILL");
            } else {
                Integer c = colorOf.get(v);
                System.out.println("  " + v + " -> " + (c == null ? "uncolored" : ("R" + c)));
            }
        }
        System.out.println();
    }
}


