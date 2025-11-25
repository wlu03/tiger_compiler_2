package ds;

import java.util.HashMap;
import java.util.Map;

public class VarIdentity {
    private static final Map<String, Integer> Var =  new HashMap<>();

    public String varname;
    public int number;

    private VarIdentity(String varname, int number) {
        this.varname = varname;
        this.number = number;
    }

    public static VarIdentity Variable(String varName)
    {
        if (VarIdentity.Var.containsKey(varName)) {
            VarIdentity.Var.merge(varName, 1, Integer::sum);
        }
        else {
            VarIdentity.Var.put(varName, 1);
        }
        var counter =  VarIdentity.Var.get(varName);
        return new VarIdentity(varName, counter);
    }

    @Override
    public int hashCode() {
        return varname.hashCode() + number;
    }

    @Override
    public boolean equals(Object obj) {
        return varname.equals(((VarIdentity)obj).varname) && number == ((VarIdentity)obj).number;
    }
}
