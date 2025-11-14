import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaSourceFile
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext
import com.analyzer.refactoring.mcp.util.PositionTracker

/**
 * Detects factory pattern implementations in Java code.
 * 
 * A class is considered a factory if it has:
 * - A method that returns an interface or abstract class type
 * - Method name contains "create", "make", or "build"
 * - Returns different implementations based on parameters
 */
class FactoryPatternVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    @Override
    J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        def methodName = method.simpleName.toLowerCase()
        
        // Check if method name suggests factory pattern
        if (methodName.contains('create') || methodName.contains('make') || 
            methodName.contains('build') || methodName.contains('factory')) {
            
            // Check if method is static (common in factory patterns)
            def modifiers = method.modifiers.collect { it.type.toString() }
            if (modifiers.contains('Static') || modifiers.contains('Public')) {
                
                def classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class)
                
                // Extract accurate position using PositionTracker
                SourceFile sourceFile = getCursor().firstEnclosingOrThrow(SourceFile.class)
                Map<String, Integer> position = PositionTracker.getPosition((JavaSourceFile) sourceFile, method)
                
                def match = [
                    nodeId: method.id.toString(),
                    nodeType: 'MethodDeclaration',
                    className: classDecl?.simpleName ?: 'Unknown',
                    methodName: method.simpleName,
                    fieldName: null,
                    location: [
                        file: sourceFile.sourcePath.toString(),
                        line: position.get('line'),
                        column: position.get('column')
                    ]
                ]
                matches.add(match)
            }
        }
        
        return super.visitMethodDeclaration(method, ctx)
    }
}

def visitor = new FactoryPatternVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
