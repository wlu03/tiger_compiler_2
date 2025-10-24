package mips.operand;

public class Register extends MIPSOperand {
    public static final Register ZERO = new Register("$0");

    // frame pointer register
    public static final Register $fp = new Register("$fp");
    // stack pointer register
    public static final Register $sp = new Register("$sp");

    // return address register
    public static final Register $ra = new Register("$ra");
    
    // return value register
    public static final Register $v0 = new Register("$v0");

    // argument registers
    public static final Register $a0 = new Register("$a0");
    public static final Register $a1 = new Register("$a1");
    public static final Register $a2 = new Register("$a2");
    public static final Register $a3 = new Register("$a3");

    // Tmp register 0 - 9
    public static final Register T0 = new Register("$t0");
    public static final Register T1 = new Register("$t1");
    public static final Register T2 = new Register("$t2");
    public static final Register T3 = new Register("$t3");
    public static final Register T4 = new Register("$t4");
    public static final Register T5 = new Register("$t5");
    public static final Register T6 = new Register("$t6");
    public static final Register T7 = new Register("$t7");
    public static final Register T8 = new Register("$t8");
    public static final Register T9 = new Register("$t9");

    public static final Register VIRTAL_TMP = new Register("$tmp", false);

    public String name;
    public boolean isVirtual;

    public Register(String name) {
        this(name, true);
    }

    public Register(String name, boolean isVirtual) {
        this.name = name;
        this.isVirtual = isVirtual;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (!(other instanceof Register)) {
            return false;
        }

        if (((Register)other).name.equals(name)) {
            return true;
        } else {
            return false;
        }
    }
}
