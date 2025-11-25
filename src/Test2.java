import backend.*;
import ir.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

public class Test2 {
    public static void main(String[] args)
            throws IOException, IRException
    {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java Test2 <path/to/program.ir> [--naive|--greedy|--opt]");
            System.exit(1);
        }

        var inputProgram = args[0];
        var mode = args.length == 2 ? args[1] : "--naive";

        enum Mode { NAIVE, BLOCK, GLOBAL }
        Mode selected;
        if (mode.equalsIgnoreCase("--block") || mode.equalsIgnoreCase("--greedy")) {
            selected = Mode.BLOCK;
        } else if (mode.equalsIgnoreCase("--opt") || mode.equalsIgnoreCase("-o")) {
            selected = Mode.GLOBAL;
        } else if (mode.equalsIgnoreCase("--naive")) {
            selected = Mode.NAIVE;
        } else {
            System.err.println("Unknown mode: " + mode + ". Use --naive, --greedy, or --opt");
            System.exit(1);
            return;
        }

        IRReader irReader = new IRReader();
        IRProgram irProgram = irReader.parseIRFile(inputProgram);

        var mipsTranslations = IR2MIPSISelect.selectMipsInstructions(irProgram);

//        var mipsUnAllocatedText = IR2MIPSISelect.mipsTranslationToText(mipsTranslations);
//        try (PrintStream out = new PrintStream("output_unallocated.s")) {
//            out.println(mipsUnAllocatedText);
//        }

        var regAlloc = new ArrayList<MIPSTranslation>();
        for (MIPSTranslation trans : mipsTranslations) {
            MIPSTranslation allocated;
            if (selected == Mode.BLOCK) {
                var regAllocer = new IntraBlockAllocatorV3();
                allocated = regAllocer.allocate(trans);
            } else if (selected == Mode.GLOBAL) {
                var regAllocer = new GlobalAllocator();
                allocated = regAllocer.allocate(trans);
            } else {
                var regAllocer = new NaiveRegisterAllocatorV2();
                allocated = regAllocer.allocate(trans);
            }
            regAlloc.add(allocated);
        }

        var mipsProgramText = IR2MIPSISelect.mipsTranslationToText(regAlloc);
        try (PrintStream out = new PrintStream("out.s")) {
            out.println(mipsProgramText);
        }
    }
}


