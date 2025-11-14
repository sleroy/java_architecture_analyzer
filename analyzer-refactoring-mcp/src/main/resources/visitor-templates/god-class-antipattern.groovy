import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaSourceFile
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext
import com.analyzer.refactoring.mcp.util.PositionTracker

/**
 * Detects God Class anti-pattern in Java code.
 * 
 * A God Class is identified by:
 * - High number of methods (>20)
 * - High number of fields (>15)
 * - High lines of code
 * - Often has "Manager", "Controller", "Handler" in name
 */
class GodClassAntiPatternVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    private static final int METHOD_THRESHOLD = 20
    private static final int FIELD_THRESHOLD = 15
    
    @Override
    J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        int methodCount = 0
        int fieldCount = 0
        
        // Count methods and fields
        classDecl.body.statements.each { statement ->
            if (statement instanceof J.MethodDeclaration) {
                def method = (J.MethodDeclaration) statement
                if (!method.isConstructor()) {
                    methodCount++
                }
            } else if (statement instanceof J.VariableDeclarations) {
                fieldCount++
            }
        }
        
        // Check if class exceeds thresholds
        if (methodCount > METHOD_THRESHOLD || fieldCount > FIELD_THRESHOLD) {
            // Extract accurate position using PositionTracker
            SourceFile sourceFile = getCursor().firstEnclosingOrThrow(SourceFile.class)
            Map<String, Integer> position = PositionTracker.getPosition((JavaSourceFile) sourceFile, classDecl)
            
            def match = [
                nodeId: classDecl.id.toString(),
                nodeType: 'ClassDeclaration',
                className: classDecl.simpleName,
                methodName: null,
                fieldName: null,
                metrics: [
                    methodCount: methodCount,
                    fieldCount: fieldCount,
                    severity: (methodCount > METHOD_THRESHOLD * 1.5 || fieldCount > FIELD_THRESHOLD * 1.5) ? 'HIGH' : 'MEDIUM'
                ],
                location: [
                    file: sourceFile.sourcePath.toString(),
                    line: position.get('line'),
                    column: position.get('column')
                ]
            ]
            matches.add(match)
        }
        
        return super.visitClassDeclaration(classDecl, ctx)
    }
}

def visitor = new GodClassAntiPatternVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
