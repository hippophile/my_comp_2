import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single method declaration.
 * Params and locals are stored in declaration order via LinkedHashMap.
 * Offset is the vtable slot position (8 bytes per slot); -1 until computed.
 */
public class MethodInfo {
    public String name;
    public String returnType;
    public LinkedHashMap<String, String> params;   // param name -> type, in order
    public LinkedHashMap<String, String> locals;   // local var name -> type, in order
    public int offset;   // vtable byte offset; -1 until computed

    public MethodInfo(String name, String returnType) {
        this.name = name;
        this.returnType = returnType;
        this.params = new LinkedHashMap<>();
        this.locals = new LinkedHashMap<>();
        this.offset = -1;
    }

    /** Returns the ordered list of parameter types (used for signature comparison). */
    public List<String> paramTypes() {
        return new ArrayList<>(params.values());
    }
}
