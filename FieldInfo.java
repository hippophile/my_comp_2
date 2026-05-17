/**
 * Represents a single field in a class.
 * Offset is computed later by SymbolTable.computeOffsets().
 */
public class FieldInfo {
    public String name;
    public String type;   // "int", "boolean", "int[]", or a class name
    public int offset;    // byte offset within the object; -1 until computed

    public FieldInfo(String name, String type) {
        this.name = name;
        this.type = type;
        this.offset = -1;
    }

    /** Returns the byte size of this field's type (no alignment padding in MiniJava). */
    public int sizeOf() {
        switch (type) {
            case "boolean": return 1;
            case "int":     return 4;
            default:        return 8;  // int[] and class references are 8-byte pointers
        }
    }
}
