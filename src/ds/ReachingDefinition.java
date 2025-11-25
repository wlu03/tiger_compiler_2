package ds;

import ir.*;
import java.util.*;

// MAY-REACHING DEFINITIONS ANALYSIS
// This implements "may-reaching" definitions: a definition MAY reach a point if there exists
// at least one path from the definition to that point without the variable being redefined.

// Initial values:
// IN[B] = empty set
// OUT[B] = GEN[B]

// Iterative update equations:
// IN[B] = Union of OUT[pred] for all predecessors (MAY analysis - union, not intersection)
// OUT[B] = GEN[B] ∪ (IN[B] - KILL[B])

// Set definitions:
// GEN[B] = definitions generated in block B (only the last definition of each variable)
// KILL[B] = all definitions of variables that are defined in block B (except those in GEN[B])
// 
// Notes on behavior:
// 1. EXIT may contain "old" definitions that are redefined later - this is correct for may-analysis
// 2. OUT[B] may contain definitions not generated in B - they flow through if not killed
// 3. A definition never kills itself (no self-kills in KILL sets)

public class ReachingDefinition {
    
    public static class Definition {
        public String variable; // variable that is defined
        public int lineNumber; // line number where the definition is defined
        public IRInstruction instruction; // instruction that defines the variable
        
        //constructor of def
        public Definition(String variable, int lineNumber, IRInstruction instruction) {
            this.variable = variable;
            this.lineNumber = lineNumber;
            this.instruction = instruction;
        }
        
        @Override
        public boolean equals(Object obj) {
            // two defs are equal if they have same variable and line number
            if (!(obj instanceof Definition)) return false;
            Definition other = (Definition) obj;
            return variable.equals(other.variable) && lineNumber == other.lineNumber;
        }
        
        @Override
        public int hashCode() {
            return variable.hashCode() + lineNumber;
        }
        
        @Override
        public String toString() {
            return variable + "@" + lineNumber;
        }
    }
    private ControlFlowGraph cfg;

    // explain above
    public Map<BasicBlock, Set<Definition>> in;
    public Map<BasicBlock, Set<Definition>> out;
    public Map<BasicBlock, Set<Definition>> gen;
    public Map<BasicBlock, Set<Definition>> kill;
    
    public ReachingDefinition(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.in = new HashMap<>();
        this.out = new HashMap<>();
        this.gen = new HashMap<>();
        this.kill = new HashMap<>();
        
        computeReachingDefinitions();
    }
    

    private void computeReachingDefinitions() {
        computeGenKillSets();
        initializeInOutSets(); //in and out are empty
        
        // Print initial state
        printInitialState();
        
        // iterative
        boolean changed = true;
        int iterations = 0;
        
         while (changed && iterations < 100) {
             changed = false;
             iterations++;
             
             System.out.println("ITERATION " + iterations);
             
             // Collect all new IN and OUT sets before updating
             Map<BasicBlock, Set<Definition>> newInSets = new HashMap<>();
             Map<BasicBlock, Set<Definition>> newOutSets = new HashMap<>();
             
             for (GraphNode<BasicBlock> node : cfg.getNodes()) {
                 BasicBlock block = node.getData();
                 String blockName = block.entryLabel != null ? block.entryLabel : "BB_" + block.id;
                 
                 // IN[B] = Union of OUT[pred] for all predecessors
                 Set<Definition> newIn = new HashSet<>();
                 List<GraphNode<BasicBlock>> predecessors = cfg.getPredecessors(node);
                 
                 // Print current state for each block
                System.out.println("Block " + blockName + ":");
                System.out.println("Current IN:  " + formatDefinitionSet(in.get(block)));
                System.out.println("Current OUT: " + formatDefinitionSet(out.get(block)));
                 
                 for (GraphNode<BasicBlock> predNode : predecessors) {
                     BasicBlock pred = predNode.getData();
                     newIn.addAll(out.get(pred)); // Use current OUT sets
                 }
                 
                 // OUT[block] = GEN[block] ∪ (IN[block] - KILL[block])
                 Set<Definition> newOut = new HashSet<>(gen.get(block));
                 Set<Definition> temp = new HashSet<>(newIn);
                 temp.removeAll(kill.get(block)); // remove all kill defs
                 newOut.addAll(temp);
                 
                 System.out.println("New IN: " + formatDefinitionSet(newIn));
                System.out.println("New OUT:" + formatDefinitionSet(newOut));
                 
                 newInSets.put(block, newIn);
                 newOutSets.put(block, newOut);
             }
             
             // Now check for changes and update all sets
             int blocksChanged = 0;
             for (GraphNode<BasicBlock> node : cfg.getNodes()) {
                 BasicBlock block = node.getData();
                 
                 Set<Definition> oldIn = in.get(block);
                 Set<Definition> oldOut = out.get(block);
                 Set<Definition> newIn = newInSets.get(block);
                 Set<Definition> newOut = newOutSets.get(block);
                 
                 // Check for changes
                 if (!newIn.equals(oldIn) || !newOut.equals(oldOut)) {
                     changed = true;
                     blocksChanged++;
                 }
                 
                 // Update the sets
                 in.put(block, newIn);
                 out.put(block, newOut);
             }
             
             System.out.println("End of Iteration " + iterations + " - Blocks changed: " + blocksChanged);
             System.out.println();
         }
        
        System.out.println("Reaching definitions converged after: " + iterations + " iterations");
    }
    

    private void computeGenKillSets() {
        Set<Definition> allDefinitions = getAllDefinitions(); // all defs in the program
        
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock block = node.getData();
            
            Set<Definition> blockGen = new HashSet<>();
            Set<Definition> blockKill = new HashSet<>();
            
             // Process instructions in sorted order by line number (not HashMap order!)
             List<IRInstruction> sortedInstructions = new ArrayList<>(block.instructions.values());
             sortedInstructions.sort((a, b) -> Integer.compare(a.irLineNumber, b.irLineNumber));
             
             // First pass: compute GEN set (only the last definition of each variable survives)
             for (IRInstruction inst : sortedInstructions) {
                 String definedVar = getDefinedVariable(inst);
                 
                 if (definedVar != null) {
                     // This instruction defines a variable
                     Definition newDef = new Definition(definedVar, inst.irLineNumber, inst);
                     
                     // Remove any previous definitions of the same variable from GEN
                     // (within this block, only the last definition survives)
                     blockGen.removeIf(def -> def.variable.equals(definedVar));
                     
                     // Add the new definition to GEN set
                     blockGen.add(newDef);
                 }
             }
             
             // Second pass: compute KILL set based on final GEN set
             // KILL = all definitions of variables that are defined in this block, EXCEPT the ones in GEN
             for (Definition genDef : blockGen) {
                 for (Definition def : allDefinitions) {
                     if (def.variable.equals(genDef.variable) && !def.equals(genDef)) {
                         blockKill.add(def);
                     }
                 }
             }
            
            gen.put(block, blockGen);
            kill.put(block, blockKill);
        }
    }
    
    // get all variable defs in the entire program
    private Set<Definition> getAllDefinitions() {
        Set<Definition> allDefs = new HashSet<>(); 
        
        // for a basic block
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock block = node.getData(); //get the data for the block
            // for each instruction in the block
            for (IRInstruction inst : block.instructions.values()) {
                String definedVar = getDefinedVariable(inst); // get the variable being defined by the instruction
                if (definedVar != null) {
                    allDefs.add(new Definition(definedVar, inst.irLineNumber, inst)); // add the definition to the set
                }
            }
        }
        
        return allDefs;
    }
    
    // get the variable being defined by an instruction
    private String getDefinedVariable(IRInstruction inst) {
        switch (inst.opCode) {
            case ASSIGN:
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
            case ARRAY_LOAD:
            case CALLR:
                // First operand is the target variable
                if (inst.operands.length > 0) {
                    return inst.operands[0].toString();
                }
                break;
            default:
                // Other instructions don't define variables
                break;
        }
        return null;
    }
    
     // initialize in and out sets
     private void initializeInOutSets() {
         for (GraphNode<BasicBlock> node : cfg.getNodes()) {
             BasicBlock block = node.getData();
             in.put(block, new HashSet<>());
             // Initialize OUT[B] = GEN[B] as per your comment
             out.put(block, new HashSet<>(gen.get(block)));
         }
     }
    
    // get defs that reach a specific instruction
    public Set<Definition> getReachingDefinitions(IRInstruction instruction) {
        /**
         * Example
         * B1
         * 1) assing x, 1
         * 2) assign y, 2
         * 3) goto B2
         * 
         * 
         * B2
         * 4) add, z, x, y
         * 5) return z
         * 
         * B1: 
         * GEN[B1] = { x@1, y@2 }
         * KILL[B1] = all other defs of x, y = {}
         * 
         * B2:
         * GEN[B2] = { z@4 }
         * KILL[B2] = all other defs of z = {}
         * 
         * IN[B1] = ∅ 
         * OUT[B1] = { x@1, y@2 }
         * IN[B2] = { x@1, y@2 }
         * OUT[B2] = { x@1, y@2, z@4 }
         * 
         * Example 1:
         * Reachingdefinition(4) (add, z, x, y)
         * 1. find block 2 (contains inst 4)
         * 2. Start with in.get(B2) = { x@1, y@2 } // at entry of B2 comes x@1 and y@2
         * 3. Process instruction befroe line 4 in B2 (none)
         * 4. Therefore, inst 4 has reaching definitions x@1, y@2
         * 
         * Example 2:
         * Reachingdefinition(5) (return, z)
         * 1. find block 2 (contains inst 5)
         * 2. start with in.get(B2) = { x@1, y@2 }
         * 3. Process instruction before line 5
         *    - line 4 defines z@4
         *       - kill any old defs of z
         *       - add new def z@4
         *          - reaching = { x@1, y@2, z@4 }
         */

        
        // find the basic block that contains the queried instruction
        for (GraphNode<BasicBlock> node : cfg.getNodes()) {
            BasicBlock block = node.getData();
            if (block.instructions.containsValue(instruction)) {

                // The starting set is ALL DEFINITIONS in IN[BLOCK] to reach the start of this block
                Set<Definition> reaching = new HashSet<>(in.get(block));
                
                // Simulate the blocks instructions up to the target instruction
                for (IRInstruction inst : block.instructions.values()) {
                    if (inst.irLineNumber >= instruction.irLineNumber) break; // stop if the instruction is after the given instruction
                    

                    String definedVar = getDefinedVariable(inst);
                    if (definedVar != null) {
                        // Remove old definitions of this variable
                        reaching.removeIf(def -> def.variable.equals(definedVar));
                        // Add new definition
                        reaching.add(new Definition(definedVar, inst.irLineNumber, inst));
                    }
                }
                
                return reaching;
            }
        }
        return new HashSet<>();
    }
    
    
    // check if a variable has a unique def at an instruction
    public boolean hasUniqueDefinition(IRInstruction instruction, String variable) {
        Set<Definition> reaching = getReachingDefinitions(instruction);
        long count = reaching.stream()
                           .filter(def -> def.variable.equals(variable))
                           .count();
        return count == 1;
    }
    
    // get the unique def of a variable at an instruction (if it exists)
    public Definition getUniqueDefinition(IRInstruction instruction, String variable) {
        if (!hasUniqueDefinition(instruction, variable)) return null;
        
        return getReachingDefinitions(instruction).stream()
                .filter(def -> def.variable.equals(variable))
                .findFirst()
                .orElse(null);
    }

     // Helper method to format definition sets for readable output
     private String formatDefinitionSet(Set<Definition> defs) {
         if (defs == null || defs.isEmpty()) {
             return "{}";
         }
         
         List<String> defStrings = new ArrayList<>();
         for (Definition def : defs) {
             defStrings.add(def.toString());
         }
         defStrings.sort(String::compareTo); // Sort for consistent output
         
         if (defStrings.size() <= 5) {
             return "{" + String.join(", ", defStrings) + "}";
         } else {
             // Show first 5 and count
             List<String> firstFive = defStrings.subList(0, 5);
             return "{" + String.join(", ", firstFive) + ", ...+" + (defStrings.size() - 5) + " more}";
         }
     }

     // Print initial state before iterations
     private void printInitialState() {
         System.out.println("First/Initial State");
         System.out.println("the GEN and KILL sets are computed, IN/OUT sets initialized");
         System.out.println();
         
         for (GraphNode<BasicBlock> node : cfg.getNodes()) {
             BasicBlock block = node.getData();
             String blockName = block.entryLabel != null ? block.entryLabel : "BB_" + block.id;
             
             System.out.println("Block " + blockName + ":");
             System.out.println("GEN: " + formatDefinitionSet(gen.get(block)));
             System.out.println("KILL: " + formatDefinitionSet(kill.get(block)));
             System.out.println("IN: " + formatDefinitionSet(in.get(block)));
             System.out.println("OUT: " + formatDefinitionSet(out.get(block)));
             System.out.println();
         }
         System.out.println();
     }

     public void printResults() {
         System.out.println("Reaching Definitions Analysis");
         System.out.println();
         
         for (GraphNode<BasicBlock> node : cfg.getNodes()) {
             BasicBlock block = node.getData();
             String blockName = block.entryLabel != null ? block.entryLabel : "BB_" + block.id;
             
             System.out.println("Block name: " + blockName);
             System.out.println("GEN set: " + gen.get(block));
             System.out.println("KILL set: " + kill.get(block));
             System.out.println("IN set: " + in.get(block));
             System.out.println("OUT set: " + out.get(block));
             System.out.println();
         }
     }
}