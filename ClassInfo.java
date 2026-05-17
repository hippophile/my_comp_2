import java.util.LinkedHashMap;

/**
 * Represents a single class declaration.
 * Fields and methods are stored in declaration order via LinkedHashMap.
 * Only *own* (not inherited) fields and methods are stored here.
 */
public class ClassInfo {
    public String name;
    public String parentName;  // null if no parent class
    public LinkedHashMap<String, FieldInfo> fields;    // own fields only
    public LinkedHashMap<String, MethodInfo> methods;  // own methods only

    public ClassInfo(String name, String parentName) {
        this.name = name;
        this.parentName = parentName;
        this.fields = new LinkedHashMap<>();
        this.methods = new LinkedHashMap<>();
    }
}
