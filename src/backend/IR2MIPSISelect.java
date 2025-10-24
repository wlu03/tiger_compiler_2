package backend;

import ir.IRFunction;
import ir.IRInstruction;
import ir.IRProgram;
import ir.datatype.IRArrayType;
import ir.datatype.IRIntType;
import mips.*;
import mips.operand.Imm;
import mips.operand.Register;

import java.util.*;

public class IR2MIPSISelect {
    public static List<MIPSTranslation> selectMipsInstructions(IRProgram program)
    {
        var translation = new ArrayList<MIPSTranslation>();
        var nextAddress = MemLayout.TEXT;

        for (IRFunction function : program.functions) {
            Map<Integer, MIPSInstruction> instructions = new HashMap<>();

            // start of function, insert label of function name
            var fnLabel = function.name;
//            instructions.put(nextAddress, new MIPSInstruction(MIPSOp.NOT_AN_OP_LABEL, fnLabel));
//            nextAddress += MIPSInstruction.WORD_SIZE;
//            labels.put(fnLabel, nextAddress);

            // for some reason all functions have
            // move $fp, $sp at the beginning
//            instructions.put(nextAddress, new MIPSInstruction(MIPSOp.MOVE, null, Register.$fp, Register.$sp));
//            nextAddress += MIPSInstruction.WORD_SIZE;

            // do a virtual copy from all parameters
            for (int i = 0; i < function.parameters.size(); i++) {
                // emit move
                var param = function.parameters.get(i);

                if (i < 4)
                {
                    instructions.put(nextAddress, new MIPSInstruction(MIPSOp.MOVE, null,
                            IR2MIPS.regVarMan("param--" + param.getName()), new Register("$a" + i)));
                }
                else
                {
                    instructions.put(nextAddress, new MIPSInstruction(MIPSOp.MOVE, null,
                            IR2MIPS.regVarMan("param--" + param.getName()), IR2MIPS.regVarMan("stack-arg--" + (i - 4))));
                }
                nextAddress += MIPSInstruction.WORD_SIZE;
            }

            // compute and allocate stack for variables
//            final int PADDING = 60;
//            int size = 0;
//            for (int i = 0; i < function.variables.size(); i++) {
//                var variable = function.variables.get(i);
//                if (variable.type instanceof IRArrayType) {
//                    if (IR2MIPS.isLocalVar(variable, function))
//                    {
//                        size += ((IRArrayType)variable.type).getSize() * 4;
//                    }
//                    else
//                    {
//                        size += 4;
//                    }
//                }
//                else if (variable.type instanceof IRIntType) {
//                    size += 4;
//                }
//            }
//
//            var allo = IR2MIPS.allocateStackSpace(size + PADDING);
//            for (var instruction : allo) {
//                instructions.put(nextAddress, instruction);
//                nextAddress += MIPSInstruction.WORD_SIZE;
//            }

            for (IRInstruction instruction : function.instructions) {
                // check for label
                if (instruction.opCode == IRInstruction.OpCode.LABEL) {
                    // extract label name
                    var label = IR2MIPS.labelOp(instruction.operands[0], function).label;
//                    labels.put(label, nextAddress);
                    instructions.put(nextAddress, new MIPSInstruction(MIPSOp.NOT_AN_OP_LABEL, label));
                    nextAddress += MIPSInstruction.WORD_SIZE;
                    continue;
                }

                var mipsInstructions = IR2MIPS.translate(instruction, function);
                for (var mipsInstruction : mipsInstructions) {
                    instructions.put(nextAddress, mipsInstruction);
                    nextAddress += MIPSInstruction.WORD_SIZE;
                }
            }

            // are we in main function, if yes, return means syscall exit
            if (function.name.equals("main")) {
                var v0_sysexit = new MIPSInstruction(MIPSOp.LI, null, Register.$v0, Imm.Dec(10));
                var syscallExit = new MIPSInstruction(MIPSOp.SYSCALL, null);
                instructions.put(nextAddress, v0_sysexit);
                instructions.put(nextAddress + MIPSInstruction.WORD_SIZE, syscallExit);
                nextAddress += MIPSInstruction.WORD_SIZE * 2;
            } else {
                // pop stack
//                var pop = IR2MIPS.deallocateStackSpace(size + PADDING);
//                for (var instruction : pop) {
//                    instructions.put(nextAddress, instruction);
//                    nextAddress += MIPSInstruction.WORD_SIZE;
//                }
                if (function.returnType == null) {
                    var epilogue = new MIPSInstruction(MIPSOp.JR, null, Register.$ra);
                    instructions.put(nextAddress, epilogue);
                    nextAddress += MIPSInstruction.WORD_SIZE;
                }
            }

            final boolean DO_REGISTER_ALLOCATION = false;

            var sortedKeys = instructions.keySet().stream().sorted().toList();
            var sortedInstructions = new ArrayList<MIPSInstruction>();
            for (var key : sortedKeys) {
                sortedInstructions.add(instructions.get(key));
            }

            var transl = new MIPSTranslation(function, sortedInstructions);

            translation.add(transl);
        }

        return translation;
    }

    public static String mipsTranslationToText(List<MIPSTranslation> translation)
    {
        var labels = new HashMap<String, Integer>();
        var nextInst = MemLayout.TEXT;

        StringBuilder text = new StringBuilder();
        text.append(".text\n");

        // order translation so main function always come first
        var reorderedTranslation = new ArrayList<MIPSTranslation>();
        for (MIPSTranslation trans : translation) {
            if (trans.functionName.equals("main")) {
                reorderedTranslation.add(trans);
            }
        }
        for (MIPSTranslation trans : translation) {
            if (!trans.functionName.equals("main")) {
                reorderedTranslation.add(trans);
            }
        }

        for (MIPSTranslation trans : reorderedTranslation) {
            for (MIPSInstruction inst : trans.mipsInstructions) {
                if (inst.op == MIPSOp.NOT_AN_OP_LABEL)
                {
                    text.append(String.format("%s:\n", inst.label));
                    continue;
                }

                text.append(String.format("  %s\n", inst.toString()));
            }
        }
        return text.toString();
    }
}
