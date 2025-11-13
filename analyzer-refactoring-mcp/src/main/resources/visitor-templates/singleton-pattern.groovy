import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext

/**
 * Detects singleton pattern implementations in Java code.
 * 
 * A class is considered a singleton if it has:
 * - A private static instance field
 * - A private constructor
 * - A public static getInstance() method
 */
class SingletonPatternVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    @Override
    J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        // Check if class has singleton characteristics
        boolean hasPrivateStaticInstance = false
        boolean hasPrivateConstructor = false
        boolean hasGetInstanceMethod = false
        
        // Check fields for private static instance
        classDecl.body.statements.each { statement ->
            if (statement instanceof J.VariableDeclarations) {
                def varDecl = (J.VariableDeclarations) statement
                def modifiers = varDecl.modifiers.collect { it.type.toString() }
                
                if (modifiers.contains('Private') && modifiers.contains('Static')) {
                    // Check if type matches the class
                    if (varDecl.typeExpression?.toString()?.contains(classDecl.simpleName)) {
                        hasPrivateStaticInstance = true
                    }
                }
            }
            
            // Check for private constructor
            if (statement instanceof J.MethodDeclaration) {
                def method = (J.MethodDeclaration) statement
                if (method.isConstructor()) {
                    def modifiers = method.modifiers.collect { it.type.toString() }
                    if (modifiers.contains('Private')) {
                        hasPrivateConstructor = true
                    }
                }
                
                // Check for getInstance method
                if (method.simpleName == 'getInstance') {
                    def modifiers = method.modifiers.collect { it.type.toString() }
                    if (modifiers.contains('Public') && modifiers.contains('Static')) {
                        hasGetInstanceMethod = true
                    }
                }
            }
        }
        
        // If all singleton characteristics are present, record it
        if (hasPrivateStaticInstance && hasPrivateConstructor && hasGetInstanceMethod) {
            def match = [
                nodeId: classDecl.id.toString(),
                nodeType: 'ClassDeclaration',
                className: classDecl.simpleName,
                methodName: null,
                fieldName: null,
                location: [
                    file: getCursor().firstEnclosingOrThrow(SourceFile.class).sourcePath.toString(),
                    line: 0,
                    column: 0
                ]
            ]
            matches.add(match)
        }
        
        return super.visitClassDeclaration(classDecl, ctx)
    }
}

def visitor = new SingletonPatternVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
