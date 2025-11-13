import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext

/**
 * WORKING EXAMPLE: Visitor that finds all public methods in a class.
 * 
 * This is a complete, tested example showing correct OpenRewrite API usage.
 * Adapt this pattern for your specific search requirements.
 * 
 * Key elements demonstrated:
 * 1. Proper visitor class structure extending JavaIsoVisitor
 * 2. Result collection in List<Map<String, Object>>
 * 3. Location extraction using Cursor API
 * 4. Class context extraction
 * 5. Correct return statement and visitor execution
 */
class ExampleVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    @Override
    J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        // Example: Check if method is public
        def modifiers = method.modifiers.collect { it.type.toString() }
        
        if (modifiers.contains('Public')) {
            // Extract class context for additional information
            def classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class)
            
            // Build match object with all required fields
            def match = [
                nodeId: method.id.toString(),
                nodeType: 'MethodDeclaration',
                className: classDecl?.simpleName ?: 'Unknown',
                methodName: method.simpleName,
                fieldName: null,
                location: [
                    file: getCursor().firstEnclosingOrThrow(SourceFile.class).sourcePath.toString(),
                    line: method.prefix.coordinates.line,
                    column: method.prefix.coordinates.column
                ]
            ]
            matches.add(match)
        }
        
        // IMPORTANT: Always call super to continue traversal
        return super.visitMethodDeclaration(method, ctx)
    }
}

// Execute the visitor and return the matches list
def visitor = new ExampleVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
