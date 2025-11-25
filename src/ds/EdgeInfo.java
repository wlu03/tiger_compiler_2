package ds;

import ir.IRInstruction;

/**
 * Stores metadata about an edge in the CFG
 */
public class EdgeInfo {
    public enum EdgeType {
        UNCONDITIONAL,    // goto, return, fall-through
        TRUE_BRANCH,      // condition is true
        FALSE_BRANCH      // condition is false
    }
    
    public BasicBlock from;
    public BasicBlock to;
    public EdgeType type;
    public IRInstruction sourceInstruction;
    public String condition;
    
    public EdgeInfo(BasicBlock from, BasicBlock to, EdgeType type, 
                   IRInstruction sourceInst, String condition) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.sourceInstruction = sourceInst;
        this.condition = condition;
    }
    
    public boolean isTrueBranch() {
        return type == EdgeType.TRUE_BRANCH;
    }
    
    public boolean isFalseBranch() {
        return type == EdgeType.FALSE_BRANCH;
    }
    
    public boolean isConditional() {
        return type == EdgeType.TRUE_BRANCH || type == EdgeType.FALSE_BRANCH;
    }
    
    @Override
    public String toString() {
        return String.format("Edge{%s -> %s, type=%s, condition='%s'}", 
                           from.entryLabel != null ? from.entryLabel : "BB_" + from.id,
                           to.entryLabel != null ? to.entryLabel : "BB_" + to.id,
                           type, condition);
    }
}
