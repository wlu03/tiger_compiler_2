package ds;

import ds.BasicBlock;
import ds.ControlFlowGraph;
import ds.GraphNode;
import ir.IRInstruction;

import java.util.*;

/**
 * Computes conservative live ranges (intervals) for variables based on
 * per-instruction liveness computed by opt.Liveness.
 *
 * Intervals are formed over the linearized order of non-LABEL instructions
 * in the function (sorted by IR line number). This is sufficient for register
 * allocation heuristics that use interval length and overlap information.
 */
public class LiveRanges {

    public static class Interval {
        public final int startLine;
        public final int endLine;

        public Interval(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }

        @Override
        public String toString() {
            return "[" + startLine + ", " + endLine + "]";
        }
    }

    private final ControlFlowGraph cfg;
    private final Liveness liveness;

    // Map variable -> list of intervals (by IR line numbers)
    public final Map<String, List<Interval>> intervalsByVar = new HashMap<>();

    public LiveRanges(ControlFlowGraph cfg, Liveness liveness) {
        this.cfg = cfg;
        this.liveness = liveness;
        computeIntervals();
    }

    private void computeIntervals() {
        // Linearize all non-LABEL instructions (sorted by line number)
        List<IRInstruction> orderedInsts = new ArrayList<>();
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock bb = node.getData();
            for (IRInstruction inst : bb.instructions.values()) {
                if (inst.opCode != ir.IRInstruction.OpCode.LABEL) {
                    orderedInsts.add(inst);
                }
            }
        }
        orderedInsts.sort(Comparator.comparingInt(i -> i.irLineNumber));

        // Build variable -> list of instruction indices where it is live (liveIn)
        Map<String, List<Integer>> varToPositions = new HashMap<>();
        for (int i = 0; i < orderedInsts.size(); i++) {
            IRInstruction inst = orderedInsts.get(i);
            Set<String> liveIn = liveness.liveInI.getOrDefault(inst, Collections.emptySet());
            for (String v : liveIn) {
                varToPositions.computeIfAbsent(v, k -> new ArrayList<>()).add(i);
            }
        }

        // Coalesce contiguous positions into intervals mapped to IR line numbers
        for (Map.Entry<String, List<Integer>> e : varToPositions.entrySet()) {
            String var = e.getKey();
            List<Integer> positions = e.getValue();
            if (positions.isEmpty()) {
                continue;
            }
            // positions already in increasing order
            List<Interval> ranges = new ArrayList<>();
            int startPos = positions.get(0);
            int prevPos = startPos;
            for (int k = 1; k < positions.size(); k++) {
                int pos = positions.get(k);
                if (pos == prevPos + 1) {
                    prevPos = pos;
                } else {
                    int startLine = orderedInsts.get(startPos).irLineNumber;
                    int endLine = orderedInsts.get(prevPos).irLineNumber;
                    ranges.add(new Interval(startLine, endLine));
                    startPos = pos;
                    prevPos = pos;
                }
            }
            // close last interval
            int startLine = orderedInsts.get(startPos).irLineNumber;
            int endLine = orderedInsts.get(prevPos).irLineNumber;
            ranges.add(new Interval(startLine, endLine));

            intervalsByVar.put(var, ranges);
        }
    }

    public void printIntervals() {
        System.out.println("Live Ranges (by IR lines)");
        List<String> vars = new ArrayList<>(intervalsByVar.keySet());
        vars.sort(String::compareTo);
        for (String v : vars) {
            System.out.println("  " + v + ": " + intervalsByVar.get(v));
        }
        System.out.println();
    }
}


