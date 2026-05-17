import minijava.syntaxtree.*;
import minijava.visitor.GJDepthFirst;
import java.util.Enumeration;

/**
 * First-pass visitor.  Collects all class, field, and method declarations
 * into the SymbolTable without doing any type checking.
 *
 * Why a separate pass: MiniJava allows forward references, so class B can
 * use class A even if A is declared after B in the source file.
 *
 * Uses GJDepthFirst<String, Void>: the String return value is used to
 * propagate type names up from Type/Identifier nodes.
 */
public class ClassCollectorVisitor extends GJDepthFirst<String, Void> {

    private SymbolTable symbolTable;

    // Track where we are during traversal so VarDeclaration/FormalParameter
    // know whether they are inside a class body or a method body.
    private String currentClass;   // name of the class being visited
    private String currentMethod;  // name of the method being visited, or null

    public ClassCollectorVisitor(SymbolTable table) {
        this.symbolTable = table;
    }

    // -----------------------------------------------------------------------
    // Top-level structure
    // -----------------------------------------------------------------------

    /**
     * MainClass: class X { public static void main(String[] args) { ... } }
     * We register the main class name so printOffsets() can skip it.
     * We do NOT collect methods or fields from the main class.
     */
    @Override
    public String visit(MainClass n, Void argu) {
        String className = n.f1.f0.tokenImage;
        symbolTable.mainClassName = className;
        // The main class is registered as a class so type checks against it work,
        // but it gets no fields or methods (MiniJava spec: main class is special).
        if (symbolTable.classes.containsKey(className)) {
            SymbolTable.error("Duplicate class name: '" + className + "'");
        }
        symbolTable.classes.put(className, new ClassInfo(className, null));
        return null;
    }

    /**
     * ClassDeclaration: class X { fields... methods... }
     */
    @Override
    public String visit(ClassDeclaration n, Void argu) {
        String className = n.f1.f0.tokenImage;
        if (symbolTable.classes.containsKey(className)) {
            SymbolTable.error("Duplicate class name: '" + className + "'");
        }

        ClassInfo ci = new ClassInfo(className, null);
        symbolTable.classes.put(className, ci);

        currentClass = className;
        currentMethod = null;

        // Visit field declarations and method declarations
        n.f3.accept(this, argu);   // ( VarDeclaration() )*
        n.f4.accept(this, argu);   // ( MethodDeclaration() )*

        currentClass = null;
        return null;
    }

    /**
     * ClassExtendsDeclaration: class X extends Y { fields... methods... }
     */
    @Override
    public String visit(ClassExtendsDeclaration n, Void argu) {
        String className  = n.f1.f0.tokenImage;
        String parentName = n.f3.f0.tokenImage;

        if (symbolTable.classes.containsKey(className)) {
            SymbolTable.error("Duplicate class name: '" + className + "'");
        }

        ClassInfo ci = new ClassInfo(className, parentName);
        symbolTable.classes.put(className, ci);

        currentClass = className;
        currentMethod = null;

        n.f5.accept(this, argu);   // ( VarDeclaration() )*
        n.f6.accept(this, argu);   // ( MethodDeclaration() )*

        currentClass = null;
        return null;
    }

    // -----------------------------------------------------------------------
    // Field and local variable declarations
    // -----------------------------------------------------------------------

    /**
     * VarDeclaration: Type Identifier ";"
     * Used both for class fields and for method-local variables.
     * We tell them apart by checking whether currentMethod is set.
     */
    @Override
    public String visit(VarDeclaration n, Void argu) {
        String typeName = typeToString(n.f0);
        String varName  = n.f1.f0.tokenImage;
        ClassInfo ci    = symbolTable.classes.get(currentClass);

        if (currentMethod == null) {
            // Class field declaration
            if (ci.fields.containsKey(varName)) {
                SymbolTable.error("Duplicate field '" + varName +
                                  "' in class '" + currentClass + "'");
            }
            ci.fields.put(varName, new FieldInfo(varName, typeName));
        } else {
            // Local variable inside a method
            MethodInfo mi = ci.methods.get(currentMethod);
            if (mi.locals.containsKey(varName)) {
                SymbolTable.error("Duplicate local variable '" + varName +
                                  "' in method '" + currentMethod +
                                  "' of class '" + currentClass + "'");
            }
            // A local var can shadow a param — that is valid in MiniJava
            mi.locals.put(varName, typeName);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Method declarations
    // -----------------------------------------------------------------------

    /**
     * MethodDeclaration: public Type Identifier ( ParamList? ) { locals... stmts... return expr; }
     */
    @Override
    public String visit(MethodDeclaration n, Void argu) {
        String returnType = typeToString(n.f1);
        String methodName = n.f2.f0.tokenImage;
        ClassInfo ci      = symbolTable.classes.get(currentClass);

        if (ci.methods.containsKey(methodName)) {
            SymbolTable.error("Duplicate method '" + methodName +
                              "' in class '" + currentClass + "'");
        }

        MethodInfo mi = new MethodInfo(methodName, returnType);
        ci.methods.put(methodName, mi);

        currentMethod = methodName;

        // Collect parameters (optional)
        n.f4.accept(this, argu);   // ( FormalParameterList() )?

        // Collect local variable declarations
        n.f7.accept(this, argu);   // ( VarDeclaration() )*

        // Do NOT visit statement bodies — we only collect declarations here.

        currentMethod = null;
        return null;
    }

    /**
     * FormalParameter: Type Identifier
     */
    @Override
    public String visit(FormalParameter n, Void argu) {
        String typeName  = typeToString(n.f0);
        String paramName = n.f1.f0.tokenImage;
        ClassInfo ci     = symbolTable.classes.get(currentClass);
        MethodInfo mi    = ci.methods.get(currentMethod);

        if (mi.params.containsKey(paramName)) {
            SymbolTable.error("Duplicate parameter '" + paramName +
                              "' in method '" + currentMethod +
                              "' of class '" + currentClass + "'");
        }
        mi.params.put(paramName, typeName);
        return null;
    }

    // FormalParameterList/Tail/Term: let the default depth-first walk handle them.

    // -----------------------------------------------------------------------
    // Type helper
    // -----------------------------------------------------------------------

    /**
     * Converts a Type AST node into a plain String type name.
     * The grammar choice is:  ArrayType | BooleanType | IntegerType | Identifier
     */
    private String typeToString(Type t) {
        Node choice = t.f0.choice;
        if (choice instanceof ArrayType)   return "int[]";
        if (choice instanceof BooleanType) return "boolean";
        if (choice instanceof IntegerType) return "int";
        if (choice instanceof Identifier)  return ((Identifier) choice).f0.tokenImage;
        SymbolTable.error("Unknown type node: " + choice.getClass().getName());
        return null;  // unreachable
    }
}
