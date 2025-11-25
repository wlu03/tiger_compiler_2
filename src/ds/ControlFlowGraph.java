package ds;

import ir.*;
import java.util.*;
import java.util.function.Function;

/**
 * ControlFlowGraph
 * Leaders:
 *   1) First instruction in the function
 *   2) Target of any jump/branch label
 *   3) Instruction immediately following any branch/return (fall-through)
 *
 * Blocks are formed as [leader_k, leader_{k+1}) half-open ranges (LABELs are skipped inside blocks).
 * Successors:
 *   - GOTO: only target edge
 *   - BR<cond>: true edge to target, false edge to fall-through block (next non-label instr)
 *   - RETURN: no successors
 *   - Other: fall-through to next block (if any)
 */
public class ControlFlowGraph {

    // Terminators (end of a basic block)
    private static final EnumSet<IRInstruction.OpCode> BB_TERM_OPCODE = EnumSet.of(
        IRInstruction.OpCode.GOTO,
        IRInstruction.OpCode.BREQ,
        IRInstruction.OpCode.BRNEQ,
        IRInstruction.OpCode.BRLT,
        IRInstruction.OpCode.BRGT,
        IRInstruction.OpCode.BRGEQ,
        IRInstruction.OpCode.RETURN
    );

    // Conditional branches
    private static final EnumSet<IRInstruction.OpCode> BB_COND_JMP_OPCODE = EnumSet.of(
        IRInstruction.OpCode.BREQ,
        IRInstruction.OpCode.BRNEQ,
        IRInstruction.OpCode.BRLT,
        IRInstruction.OpCode.BRGT,
        IRInstruction.OpCode.BRGEQ
    );

    public IRFunction function; // function that the control flow graph is for
    public Graph<BasicBlock> cfg; // control flow graph
    
    public List<EdgeInfo> edgeMetadata; // list of edge metadata (conditions, source, destination)
    public Map<String, EdgeInfo> edgeMap;  // lookup map: "from_id->to_id" -> EdgeInfo

    /*
     * Constructor for the ControlFlowGraph class with given function
     */
    public ControlFlowGraph(IRFunction function) {
        this.function = function;
        this.cfg = new Graph<>();
        this.edgeMetadata = new ArrayList<>();
        this.edgeMap = new HashMap<>();
        buildCfg(function);
    }

    public void DbgPrintControlFlowGraph() {
        System.out.println("Control Flow Graph for function: " + function.name);
        for (var node : cfg.nodes.values()) {
            System.out.println("BASIC BLOCK");
            System.out.println("\tBLOCK: " + node.getName());
            for (var inst : node.getData().instructions.values()) {
                System.out.println("\t\t" + inst.toString());
            }
            var targets = cfg.getSuccessors(node);
            System.out.println("\tEDGE TARGETS");
            for (var target : targets) {
                System.out.println("\t\t" + target.getName());
            }
            System.out.println("END OF BLOCK");
        }
    }
    /**
     * Gets the number of basic blocks in the control flow graph
     * @return number of basic blocks
     */
    public int getNumberOfBlocks() {
        return cfg.nodes.size();
    }

    /**
     * Gets the nodes in the control flow graph
     * @return nodes in the control flow graph
     */
    public Iterable<GraphNode<BasicBlock>> getNodes() {
        return cfg.nodes.values();
    }

    /**
     * Gets the successors of a given node
     * @param node node to get successors of
     * @return successors of the node
     */
    public List<GraphNode<BasicBlock>> getSuccessors(GraphNode<BasicBlock> node) {
        return cfg.getSuccessors(node);
    }

    /**
     * Gets the predecessors of a given node
     * @param node node to get predecessors of
     * @return predecessors of the node
     */
    public List<GraphNode<BasicBlock>> getPredecessors(GraphNode<BasicBlock> node) {
        return cfg.getPredecessors(node);
    }

    /**
     * Builds the control flow graph for a given function
     * @param function function to build the control flow graph for
     */
    public void buildCfg(IRFunction function) {
        final List<IRInstruction> instrs = function.instructions;
        if (instrs == null || instrs.isEmpty()) return;

        // Collect leaders
        // Map label -> index of the first non-LABEL instruction for that label
        Map<String, Integer> labelToIdx = new HashMap<>();
        // Leaders set (instruction indexes)
        Set<Integer> leaderIdx = new HashSet<>();
        leaderIdx.add(0); // first instruction is ALWAYS a leader

        // Record label landing indices (skip multiple stacked LABELs)
        for (int i = 0; i < instrs.size(); i++) {
            IRInstruction in = instrs.get(i);
            if (in.opCode == IRInstruction.OpCode.LABEL) {
                String lab = in.operands[0].toString();
                int j = i + 1;
                while (j < instrs.size() && instrs.get(j).opCode == IRInstruction.OpCode.LABEL) j++;
                labelToIdx.put(lab, j);
                if (j < instrs.size()) leaderIdx.add(j);
            }
        }

        // Add leaders for jump targets and fall-throughs after branches/returns
        for (int i = 0; i < instrs.size(); i++) {
            IRInstruction in = instrs.get(i);
            switch (in.opCode) {
                case GOTO:
                case BREQ:
                case BRNEQ:
                case BRLT:
                case BRGT:
                case BRGEQ: {
                    String target = in.operands[0].toString();
                    Integer tgtIdx = labelToIdx.get(target);
                    if (tgtIdx == null) {
                        throw new IllegalStateException("Undefined label: " + target);
                    }
                    leaderIdx.add(tgtIdx); // target is a leader

                    // fall-through leader (only for conditional branches)
                    if (BB_COND_JMP_OPCODE.contains(in.opCode)) {
                        int next = nextNonLabelIndex(instrs, i + 1);
                        if (next < instrs.size()) leaderIdx.add(next);
                    }
                    break;
                }
                case RETURN: {
                    int next = nextNonLabelIndex(instrs, i + 1);
                    if (next < instrs.size()) leaderIdx.add(next);
                    break;
                }
                default:
            }
        }

        // Sorted leaders
        List<Integer> leaders = new ArrayList<>(leaderIdx);
        Collections.sort(leaders);

        // forming basic blocks
        List<BasicBlock> blocks = new ArrayList<>();
        Map<Integer, BasicBlock> startIdxToBlock = new HashMap<>();
        Map<BasicBlock, Integer> blockToStartIdx = new HashMap<>();

        for (int li = 0; li < leaders.size(); li++) {
            int start = leaders.get(li);
            int end = (li + 1 < leaders.size()) ? leaders.get(li + 1) : instrs.size();

            BasicBlock bb = BasicBlock.CreateBasicBlock(function);

            // Assign entry label if any label maps to 'start'
            for (Map.Entry<String, Integer> e : labelToIdx.entrySet()) {
                if (e.getValue() == start) {
                    bb.setEntryLabel(e.getKey());
                    break;
                }
            }

            // Add all non-LABEL instructions in [start, end)
            for (int i = start; i < end; i++) {
                IRInstruction in = instrs.get(i);
                if (in.opCode != IRInstruction.OpCode.LABEL) {
                    bb.addInstruction(in);
                }
            }

            // Add node to graph & bookkeeping maps
            cfg.addNode(bb.toGraphNode());
            blocks.add(bb);
            startIdxToBlock.put(start, bb);
            blockToStartIdx.put(bb, start);
        }

        // Helper: given an instruction index, find the block by the greatest leader <= idx
        Function<Integer, BasicBlock> blockOfInstr = (Integer idx) -> {
            int pos = Collections.binarySearch(leaders, idx);
            if (pos < 0) {
                int ins = -pos - 2;
                if (ins < 0) ins = 0;
                return startIdxToBlock.get(leaders.get(ins));
            } else {
                return startIdxToBlock.get(leaders.get(pos));
            }
        };

        // adding edges
        for (BasicBlock bb : blocks) {
            if (bb.instructions.isEmpty()) continue;

            // last instruction in this block
            IRInstruction last = bb.instructions.get(bb.instructions.lastKey());

            switch (last.opCode) {
                case GOTO: {
                    String tgt = last.operands[0].toString();
                    Integer tgtIdx = labelToIdx.get(tgt);
                    BasicBlock succ = blockOfInstr.apply(tgtIdx);
                    addEdge(bb, succ);
                    break;
                }

                case BREQ:
                case BRNEQ:
                case BRLT:
                case BRGT:
                case BRGEQ: {
                    // true edge: target label
                    String tgt = last.operands[0].toString();
                    Integer tgtIdx = labelToIdx.get(tgt);
                    BasicBlock trueSucc = blockOfInstr.apply(tgtIdx);
                    addConditionalEdge(bb, trueSucc, EdgeInfo.EdgeType.TRUE_BRANCH, last);

                    // false edge: fall-through instruction after 'last'
                    int lastIdx = indexOfInstruction(instrs, last);
                    int nextIdx = nextNonLabelIndex(instrs, lastIdx + 1);
                    if (nextIdx < instrs.size()) {
                        BasicBlock falseSucc = blockOfInstr.apply(nextIdx);
                        addConditionalEdge(bb, falseSucc, EdgeInfo.EdgeType.FALSE_BRANCH, last);
                    }
                    break;
                }

                case RETURN:
                    // no successors
                    break;

                default: {
                    // implicit fall-through to the next block (if any)
                    Integer start = blockToStartIdx.get(bb);
                    int pos = Collections.binarySearch(leaders, start);
                    int nextPos = (pos >= 0) ? pos + 1 : (-pos - 1);
                    if (nextPos < leaders.size()) {
                        BasicBlock fallSucc = startIdxToBlock.get(leaders.get(nextPos));
                        addEdge(bb, fallSucc);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Adds an unconditional edge to the control flow graph
     * @param from source basic block
     * @param to destination basic block
     */
    private void addEdge(BasicBlock from, BasicBlock to) {
        addConditionalEdge(from, to, EdgeInfo.EdgeType.UNCONDITIONAL, null);
    }

    /**
     * Adds a conditional edge to the control flow graph
     * @param from source basic block
     * @param to destination basic block
     * @param type type of edge
     * @param inst instruction that caused the edge
     */
    private void addConditionalEdge(BasicBlock from, BasicBlock to, 
                                  EdgeInfo.EdgeType type, IRInstruction inst) {
        // Record in BasicBlock (optional)
        from.addResolvedJumpTarget(to);

        // Add to Graph (nodes already added)
        GraphNode<BasicBlock> fromNode = cfg.getNode(from.id);
        GraphNode<BasicBlock> toNode   = cfg.getNode(to.id);
        if (fromNode != null && toNode != null) {
            cfg.addEdge(fromNode, toNode);
            
            // Store edge condition metadata
            String condition = buildConditionString(inst, type);
            EdgeInfo info = new EdgeInfo(from, to, type, inst, condition);
            edgeMetadata.add(info);
            edgeMap.put(from.id + "->" + to.id, info);
        }
    }

    /**
     * Builds a condition string for a given instruction and edge type
     * @param inst instruction that caused the edge
     * @param type type of edge
     * @return condition string
     */
    private String buildConditionString(IRInstruction inst, EdgeInfo.EdgeType type) {
        if (type == EdgeInfo.EdgeType.UNCONDITIONAL || inst == null) {
            return "always";
        }
        
        String op1 = inst.operands[1].toString();
        String op2 = inst.operands[2].toString();
        
        switch (inst.opCode) {
            case BREQ:  return type == EdgeInfo.EdgeType.TRUE_BRANCH ? 
                              op1 + " == " + op2 : op1 + " != " + op2;
            case BRNEQ: return type == EdgeInfo.EdgeType.TRUE_BRANCH ? 
                              op1 + " != " + op2 : op1 + " == " + op2;
            case BRLT:  return type == EdgeInfo.EdgeType.TRUE_BRANCH ? 
                              op1 + " < " + op2 : op1 + " >= " + op2;
            case BRGEQ: return type == EdgeInfo.EdgeType.TRUE_BRANCH ? 
                              op1 + " >= " + op2 : op1 + " < " + op2;
            case BRGT:  return type == EdgeInfo.EdgeType.TRUE_BRANCH ? 
                              op1 + " > " + op2 : op1 + " <= " + op2;
            default:    return "unknown";
        }
    }

    /**
     * Finds the next non-label instruction index
     * @param list list of instructions
     * @param i index to start from
     * @return next non-label instruction index
     */
    private static int nextNonLabelIndex(List<IRInstruction> list, int i) {
        int j = i;
        while (j < list.size() && list.get(j).opCode == IRInstruction.OpCode.LABEL) j++;
        return j;
    }

    /**
     * Finds the index of a given instruction in a list of instructions
     * @param list list of instructions
     * @param x instruction to find
     * @return index of the instruction
     */
    private static int indexOfInstruction(List<IRInstruction> list, IRInstruction x) {
        // Assumes object identity (same reference) as in the builder loop
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == x) return i;
        }
        return -1;
    }


    // after optimizing, this function will add the optimized instruction back to the function's instruction List
    // call this method after optimization
    public void syncBasicBlocksToFunction() {
        // get all remaining instructions from all basic blocks (excluding labels)
        Set<IRInstruction> remainingInstructions = new HashSet<>();
        
        for (GraphNode<BasicBlock> node : this.getNodes()) {
            BasicBlock block = node.getData();
            remainingInstructions.addAll(block.instructions.values());
        }
        
        // filter the original function instruction list to keep
            // keep label instruciton
            // keep the optimized instruciton
        List<IRInstruction> optimizedInstructions = new ArrayList<>();
        
        for (IRInstruction inst : function.instructions) {
            if (inst.opCode == IRInstruction.OpCode.LABEL) {
                // always keep labels
                optimizedInstructions.add(inst);
            } else if (remainingInstructions.contains(inst)) {
                // keep non-label instructions that survived optimization
                optimizedInstructions.add(inst);
            }

        }
        
        function.instructions.clear();
        function.instructions.addAll(optimizedInstructions);
    }
}
