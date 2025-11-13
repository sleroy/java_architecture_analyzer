import groovy.transform.CompileStatic
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
 * 2. Use of @CompileStatic for type safety and performance
 * 3. Explicit type declarations for static compilation
 * 4. Result collection in List<Map<String, Object>>
 * 5. Location extraction using Cursor API
 * 6. Class context extraction
 * 7. Correct return statement and visitor execution
 */
@CompileStatic
class ExampleVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    @Override
    J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        // Example: Check if method is public
        // Use explicit typing for static compilation
        List<String> modifiers = method.modifiers.collect { it.type.toString() } as List<String>
        
        if (modifiers.contains('Public')) {
            // Extract class context for additional information
            J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class)
            
            // Build match object with all required fields
            Map<String, Object> match = [
                nodeId: method.id.toString(),
                nodeType: 'MethodDeclaration',
                className: classDecl?.simpleName ?: 'Unknown',
                methodName: method.simpleName,
                fieldName: null,
                location: [
                    file: getCursor().firstEnclosingOrThrow(SourceFile.class).sourcePath.toString(),
                        // Not supported by OpenRewrite
                    line: 0,
                    column: 0,
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
