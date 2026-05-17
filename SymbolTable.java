import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Top-level symbol table for a MiniJava program.
 * Stores all class declarations in declaration order.
 * Provides validation (hierarchy, types, overloading) and offset computation.
 */
public class SymbolTable {
    // Classes in the order they were declared in the source file.
    public LinkedHashMap<String, ClassInfo> classes;
    // The name of the main class (the one with public static void main).
    // It is excluded from offset output.
    public String mainClassName;

    public SymbolTable() {
        this.classes = new LinkedHashMap<>();
        this.mainClassName = null;
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    /**
     * Checks that every parent class is declared, and that there are no
     * inheritance cycles.  Must be called after the full collection pass.
     */
    public void validateHierarchy() {
        for (ClassInfo ci : classes.values()) {
            if (ci.parentName == null) continue;

            // Parent must be declared
            if (!classes.containsKey(ci.parentName)) {
                error("Class '" + ci.name + "' extends undefined class '" + ci.parentName + "'");
            }

            // Detect cycles by walking the parent chain
            Set<String> visited = new HashSet<>();
            String cur = ci.name;
            while (cur != null) {
                if (!visited.add(cur)) {
                    error("Inheritance cycle detected involving class '" + cur + "'");
                }
                ClassInfo curInfo = classes.get(cur);
                cur = (curInfo != null) ? curInfo.parentName : null;
            }
        }
    }

    /**
     * Checks that every type name used in field declarations, method return
     * types, and parameter/local types is either a primitive or a known class.
     */
    public void validateTypes() {
        for (ClassInfo ci : classes.values()) {
            // Validate field types
            for (FieldInfo fi : ci.fields.values()) {
                checkTypeExists(fi.type, "field '" + fi.name + "' of class '" + ci.name + "'");
            }
            // Validate method return types, param types, and local types
            for (MethodInfo mi : ci.methods.values()) {
                checkTypeExists(mi.returnType,
                    "return type of method '" + mi.name + "' in class '" + ci.name + "'");
                for (String paramType : mi.params.values()) {
                    checkTypeExists(paramType,
                        "parameter of method '" + mi.name + "' in class '" + ci.name + "'");
                }
                for (String localType : mi.locals.values()) {
                    checkTypeExists(localType,
                        "local var in method '" + mi.name + "' in class '" + ci.name + "'");
                }
            }
        }
    }

    private void checkTypeExists(String type, String context) {
        if (type.equals("int") || type.equals("boolean") || type.equals("int[]")) return;
        if (!classes.containsKey(type)) {
            error("Unknown type '" + type + "' used in " + context);
        }
    }

    /**
     * Enforces MiniJava override rules for methods that share a name across
     * the inheritance hierarchy.  Must be called after validateHierarchy().
     *
     * Rules:
     *  - A child method with the same name as a parent method is an override.
     *  - An override must have exactly the same parameter types and return type.
     *  - Overloading (same name, different param count) is NOT allowed in MiniJava.
     */
    public void validateOverloading() {
        for (ClassInfo ci : classes.values()) {
            if (ci.parentName == null) continue;

            for (MethodInfo mi : ci.methods.values()) {
                // Walk up the ancestor chain to see if any ancestor declares this name
                MethodInfo inherited = lookupMethodInAncestors(ci.parentName, mi.name);
                if (inherited == null) continue;  // brand-new method, no conflict

                // Found same name in ancestor — must be a valid override
                List<String> childParams  = mi.paramTypes();
                List<String> parentParams = inherited.paramTypes();

                if (childParams.size() != parentParams.size()) {
                    error("Method '" + mi.name + "' in class '" + ci.name +
                          "' has different parameter count than parent's version (overloading not allowed)");
                }
                for (int i = 0; i < childParams.size(); i++) {
                    if (!childParams.get(i).equals(parentParams.get(i))) {
                        error("Method '" + mi.name + "' in class '" + ci.name +
                              "' overrides parent method with different parameter types (overloading not allowed)");
                    }
                }
                if (!mi.returnType.equals(inherited.returnType)) {
                    error("Method '" + mi.name + "' in class '" + ci.name +
                          "' overrides parent method but return type differs: expected '" +
                          inherited.returnType + "', got '" + mi.returnType + "'");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Offset Computation
    // -----------------------------------------------------------------------

    /**
     * Computes field and method vtable offsets for all classes.
     * Classes are processed in topological order (parents before children)
     * so that inherited offsets are ready when needed.
     */
    public void computeOffsets() {
        // Build a topologically-ordered list (parents first)
        List<String> order = topologicalOrder();

        for (String className : order) {
            ClassInfo ci = classes.get(className);
            String parent = ci.parentName;

            // ---- Field offsets ----
            // Start right after the last byte of the parent's fields
            int fieldOffset = (parent != null) ? totalFieldSize(parent) : 0;
            for (FieldInfo fi : ci.fields.values()) {
                fi.offset = fieldOffset;
                fieldOffset += fi.sizeOf();
            }

            // ---- Method vtable offsets ----
            // Start right after the last slot in the parent's vtable
            int methodOffset = (parent != null) ? totalVtableSize(parent) : 0;
            for (MethodInfo mi : ci.methods.values()) {
                // Check whether this method overrides a parent method
                MethodInfo inherited = (parent != null)
                    ? lookupMethodInAncestors(parent, mi.name) : null;
                if (inherited != null) {
                    // Override: reuse the parent's vtable slot
                    mi.offset = inherited.offset;
                } else {
                    // New method: append a new slot
                    mi.offset = methodOffset;
                    methodOffset += 8;  // each vtable slot is 8 bytes
                }
            }
        }
    }

    /** Returns the total byte size of all fields (own + inherited) in a class. */
    private int totalFieldSize(String className) {
        ClassInfo ci = classes.get(className);
        if (ci == null) return 0;
        int size = (ci.parentName != null) ? totalFieldSize(ci.parentName) : 0;
        for (FieldInfo fi : ci.fields.values()) {
            size += fi.sizeOf();
        }
        return size;
    }

    /** Returns the total number of vtable bytes used by a class (own + inherited). */
    private int totalVtableSize(String className) {
        ClassInfo ci = classes.get(className);
        if (ci == null) return 0;
        int size = (ci.parentName != null) ? totalVtableSize(ci.parentName) : 0;
        for (MethodInfo mi : ci.methods.values()) {
            // Only count methods that are NOT overrides (i.e., they add a new slot)
            MethodInfo inherited = (ci.parentName != null)
                ? lookupMethodInAncestors(ci.parentName, mi.name) : null;
            if (inherited == null) {
                size += 8;
            }
        }
        return size;
    }

    /**
     * Produces a stable topological ordering of classes (parents before children).
     * Relies on the fact that validateHierarchy() already caught cycles.
     */
    private List<String> topologicalOrder() {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String name : classes.keySet()) {
            topoVisit(name, visited, result);
        }
        return result;
    }

    private void topoVisit(String name, Set<String> visited, List<String> result) {
        if (visited.contains(name)) return;
        visited.add(name);
        ClassInfo ci = classes.get(name);
        if (ci != null && ci.parentName != null) {
            topoVisit(ci.parentName, visited, result);
        }
        result.add(name);
    }

    // -----------------------------------------------------------------------
    // Offset Printing
    // -----------------------------------------------------------------------

    /**
     * Prints field and method offsets for all non-main classes, in declaration order.
     * Format matches the reference output exactly.
     */
    public void printOffsets() {
        for (ClassInfo ci : classes.values()) {
            // Main class has no fields/methods of interest — skip it
            if (ci.name.equals(mainClassName)) continue;

            System.out.println("-----------Class " + ci.name + "-----------");
            System.out.println("--Variables---");
            for (FieldInfo fi : ci.fields.values()) {
                System.out.println(ci.name + "." + fi.name + " : " + fi.offset);
            }
            System.out.println("---Methods---");
            for (MethodInfo mi : ci.methods.values()) {
                // Overriding methods are NOT printed (they don't add a new slot)
                MethodInfo inherited = (ci.parentName != null)
                    ? lookupMethodInAncestors(ci.parentName, mi.name) : null;
                if (inherited == null) {
                    System.out.println(ci.name + "." + mi.name + " : " + mi.offset);
                }
            }
            System.out.println();
        }
    }

    // -----------------------------------------------------------------------
    // Lookup helpers (used by TypeCheckVisitor and internal validation)
    // -----------------------------------------------------------------------

    /**
     * Looks up a method by name in the given class or any of its ancestors.
     * Returns the first match found (closest to the child), or null.
     */
    public MethodInfo lookupMethod(String className, String methodName) {
        if (className == null) return null;
        ClassInfo ci = classes.get(className);
        if (ci == null) return null;
        if (ci.methods.containsKey(methodName)) return ci.methods.get(methodName);
        return lookupMethod(ci.parentName, methodName);
    }

    /** Same as lookupMethod but starts from the *parent* of the given class. */
    private MethodInfo lookupMethodInAncestors(String parentName, String methodName) {
        return lookupMethod(parentName, methodName);
    }

    /**
     * Looks up a field by name in the given class or any of its ancestors.
     * Returns the first match found (closest to the child), or null.
     */
    public FieldInfo lookupField(String className, String fieldName) {
        if (className == null) return null;
        ClassInfo ci = classes.get(className);
        if (ci == null) return null;
        if (ci.fields.containsKey(fieldName)) return ci.fields.get(fieldName);
        return lookupField(ci.parentName, fieldName);
    }

    /**
     * Returns true if typeA is a subtype of typeB.
     * Primitives must match exactly. For class types, walks the parent chain.
     */
    public boolean isSubtype(String typeA, String typeB) {
        if (typeA.equals(typeB)) return true;
        // Primitives have no subtype relationships with other types
        if (isPrimitive(typeA) || isPrimitive(typeB)) return false;
        // Walk up typeA's parent chain looking for typeB
        ClassInfo ci = classes.get(typeA);
        while (ci != null && ci.parentName != null) {
            if (ci.parentName.equals(typeB)) return true;
            ci = classes.get(ci.parentName);
        }
        return false;
    }

    private boolean isPrimitive(String type) {
        return type.equals("int") || type.equals("boolean") || type.equals("int[]");
    }

    // -----------------------------------------------------------------------
    // Error reporting
    // -----------------------------------------------------------------------

    public static void error(String msg) {
        System.err.println("Error: " + msg);
        System.exit(1);
    }
}
