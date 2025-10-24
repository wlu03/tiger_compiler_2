import backend.*;
import ir.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

public class Test {
    public static void main(String[] args)
            throws IOException, IRException
    {
//        var inputProgram = "public_test_cases/prime/prime.ir";
        var inputProgram = "public_test_cases/quicksort/quicksort.ir";

        IRReader irReader = new IRReader();
        IRProgram irProgram = irReader.parseIRFile(inputProgram);

        var mipsTranslations = IR2MIPSISelect.selectMipsInstructions(irProgram);

        var mipsUnAllocatedText = IR2MIPSISelect.mipsTranslationToText(mipsTranslations);
        try (PrintStream out = new PrintStream("output_unallocated.s")) {
            out.println(mipsUnAllocatedText);
        }

        // Hardcode allocator choice: true => IntraBlockAllocatorV3, false => NaiveRegisterAllocatorV2
        boolean useBlock = true;

//         we want to assign registers now
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
        // write output to file
        try (PrintStream out = new PrintStream("output_quicksort.s")) {
            out.println(mipsProgramText);
        }
    }
}
