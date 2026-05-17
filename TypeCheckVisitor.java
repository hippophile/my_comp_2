import minijava.syntaxtree.*;
import minijava.visitor.GJDepthFirst;

/**
 * Second-pass visitor: type checks all expressions and statements.
 * TODO: implement Days 6-8.
 */
public class TypeCheckVisitor extends GJDepthFirst<String, Void> {

    private SymbolTable symbolTable;

    public TypeCheckVisitor(SymbolTable table) {
        this.symbolTable = table;
    }

    // Full implementation comes in the next phase (Days 6-8).
}
