import minijava.syntaxtree.*;
import minijava.visitor.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <file1> [file2] ...");
            System.exit(1);
        }

        for (String filename : args) {
            // parse the file
            MiniJavaParser parser = new MiniJavaParser(new FileInputStream(filename));
            Goal root = parser.Goal();

            SymbolTable table = new SymbolTable();

            // first pass: collect all declarations
            ClassCollectorVisitor collector = new ClassCollectorVisitor(table);
            root.accept(collector, null);

            // check hierarchy and types make sense
            table.validateHierarchy();
            table.validateTypes();

            // check overloading rules
            table.validateOverloading();

            // compute and print offsets
            table.computeOffsets();
            table.printOffsets();

            // second pass: type check everything
            TypeCheckVisitor checker = new TypeCheckVisitor(table);
            root.accept(checker, null);
        }
    }
}
