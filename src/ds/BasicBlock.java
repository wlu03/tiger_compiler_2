package ds;

import ir.*;
import java.util.*;

/**
 * Represents a basic block in the control flow graph for an IR
 * This class stores the instructions, control-flow targets, and metadata
 */
public class BasicBlock {
    public static int blockIdGen = 0; // Global ID generator for unqiuely identifying basic blocks
    public final int id; // Unique ID for the basic block
    public IRFunction function; // function that the basic block is part of
    public TreeMap<Integer, IRInstruction> instructions; // instructions in the basic block
    public String entryLabel; // the associated label (could be null)
    public List<String> jumpTargetLabels; // labels of jump targets 
    public List<BasicBlock> jumpTargets; // resolved jump targets (successors basic blocks)

    /**
     * Constructor for the BasicBlock class with given id and parent function
     * @param id
     * @param function
     */
    public BasicBlock(int id, IRFunction function) {
        this.id = id;
        this.function = function;
        this.instructions = new TreeMap<>();
        this.jumpTargetLabels = new ArrayList<>();
        this.jumpTargets = new ArrayList<>();
    }

    /**
     * Sets the entry label for the basic block
     * @param label label string to associate with the basic block
     */
    public void setEntryLabel(String label) {
        this.entryLabel = label;
    }

    /**
     * Adds an instruction to the basic block
     * @param instr instruction to add
     */
    public void addInstruction(IRInstruction instr) {
        instructions.put(instr.irLineNumber, instr);
    }

    /**
     * Adds a resolved jump target to the basic block
     * @param bb jump target basic block (successor)
     */
    public void addResolvedJumpTarget(BasicBlock bb)
    {
        jumpTargets.add(bb);
    }

    /**
     * Adds an unresolved jump target to the basic block
     * @param label label string to associate with the basic block
     */
    public void addUnresolvedLabelTarget(String label)
    {
        jumpTargetLabels.add(label);
    }

    /**
     * Converts the basic block to a graph node for the control flow graph
     * @return graph node
     */
    public GraphNode<BasicBlock> toGraphNode() {
        String name = entryLabel != null ? entryLabel : "BB_" + blockIdGen;
        return new GraphNode<>(id, name, this);
    }

    /**
     * Checks if the basic block is empty
     * @return true if the basic block is empty, false otherwise
     */
    public boolean isEmpty() {
        return instructions.size() == 0;
    }

    /**
     * Converts the basic block to a string representation
     * @return string representation
     */
    @Override
    public String toString() {
        return "BasicBlock{" +
                "id=" + id +
                ", function=" + function +
                ", instructions=" + instructions +
                ", entryLabel='" + entryLabel + '\'' +
                ", jumpTargetLabels=" + jumpTargetLabels +
                ", jumpTargets=" + jumpTargets +
                '}';
    }

    /**
     * Creates a new basic block with a given function
     * @param function function to associate with the basic block
     * @return new basic block
     */
    public static BasicBlock CreateBasicBlock(IRFunction function) {
        return new BasicBlock(
            blockIdGen++,
            function
        );
    }
}
