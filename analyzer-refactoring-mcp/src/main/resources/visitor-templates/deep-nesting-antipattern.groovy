import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext

/**
 * Detects deep nesting anti-pattern in Java code.
 * 
 * Deep nesting is identified by:
 * - Nested if/else statements >3 levels deep
 * - Nested loops >2 levels deep
 * - Makes code hard to read and maintain
 */
class DeepNestingAntiPatternVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    private static final int NESTING_THRESHOLD = 3
    private int currentDepth = 0
    private int maxDepth = 0
    private J.MethodDeclaration currentMethod = null
    
    @Override
    J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        currentMethod = method
        currentDepth = 0
        maxDepth = 0
        
        def result = super.visitMethodDeclaration(method, ctx)
        
        // If max depth exceeded threshold, record it
        if (maxDepth > NESTING_THRESHOLD) {
            def classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class)
            
            def match = [
                nodeId: method.id.toString(),
                nodeType: 'MethodDeclaration',
                className: classDecl?.simpleName ?: 'Unknown',
                methodName: method.simpleName,
                fieldName: null,
                metrics: [
                    nestingDepth: maxDepth,
                    severity: maxDepth > NESTING_THRESHOLD * 2 ? 'HIGH' : 'MEDIUM'
                ],
                location: [
                    file: getCursor().firstEnclosingOrThrow(SourceFile.class).sourcePath.toString(),
                    line: method.prefix.coordinates.line,
                    column: method.prefix.coordinates.column
                ]
            ]
            matches.add(match)
        }
        
        currentMethod = null
        return result
    }
    
    @Override
    J.If visitIf(J.If iff, ExecutionContext ctx) {
        currentDepth++
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth
        }
        def result = super.visitIf(iff, ctx)
        currentDepth--
        return result
    }
    
    @Override
    J.ForLoop visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
        currentDepth++
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth
        }
        def result = super.visitForLoop(forLoop, ctx)
        currentDepth--
        return result
    }
    
    @Override
    J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, ExecutionContext ctx) {
        currentDepth++
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth
        }
        def result = super.visitWhileLoop(whileLoop, ctx)
        currentDepth--
        return result
    }
}

def visitor = new DeepNestingAntiPatternVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
