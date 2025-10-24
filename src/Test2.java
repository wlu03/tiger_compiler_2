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
            System.err.println("Usage: java Test2 <path/to/program.ir> [--naive|--block]");
            System.exit(1);
        }

        var inputProgram = args[0];
        var mode = args.length == 2 ? args[1] : "--naive";

        boolean useBlock;
        if (mode.equalsIgnoreCase("--block") || mode.equalsIgnoreCase("--greedy")) {
            useBlock = true;
        } else if (mode.equalsIgnoreCase("--naive")) {
            useBlock = false;
        } else {
            System.err.println("Unknown mode: " + mode + ". Use --naive or --block");
            System.exit(1);
            return;
        }

        IRReader irReader = new IRReader();
        IRProgram irProgram = irReader.parseIRFile(inputProgram);

        var mipsTranslations = IR2MIPSISelect.selectMipsInstructions(irProgram);

        var mipsUnAllocatedText = IR2MIPSISelect.mipsTranslationToText(mipsTranslations);
        try (PrintStream out = new PrintStream("output_unallocated.s")) {
            out.println(mipsUnAllocatedText);
        }

        var regAlloc = new ArrayList<MIPSTranslation>();
        for (MIPSTranslation trans : mipsTranslations) {
            MIPSTranslation allocated;
            if (useBlock) {
                var regAllocer = new IntraBlockAllocatorV3();
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


