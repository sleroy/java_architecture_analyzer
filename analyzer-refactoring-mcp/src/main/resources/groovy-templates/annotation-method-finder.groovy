import groovy.transform.CompileStatic
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaSourceFile
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext
import com.analyzer.refactoring.mcp.util.PositionTracker

/**
 * Template for finding methods with a specific annotation.
 * 
 * Parameters (passed via Groovy binding):
 * - annotationName: The annotation to search for (without @, e.g., "Override", "Transactional")
 * - compilationUnit: The OpenRewrite compilation unit to analyze
 */
@CompileStatic
class AnnotationMethodFinderVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    // Parameter from binding - the annotation to search for
    String targetAnnotation = annotationName as String
    
    @Override
    J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        // Check if method has the target annotation
        boolean hasAnnotation = method.leadingAnnotations.any { ann ->
            ann.annotationType.toString().contains(targetAnnotation)
        }
        
        if (hasAnnotation) {
            // Extract class context for additional information
            J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class)
            
            SourceFile sourceFile = getCursor().firstEnclosingOrThrow(SourceFile.class)
            Map<String, Integer> position = PositionTracker.getPosition((JavaSourceFile) sourceFile, method)
            
            // Extract all annotation names for context
            List<String> annotations = method.leadingAnnotations.collect { 
                it.annotationType.toString() 
            } as List<String>
            
            // Extract modifiers (public, static, synchronized, etc.)
            List<String> modifiers = method.modifiers.collect { 
                it.type.toString() 
            } as List<String>
            
            // Extract return type
            String returnType = method.returnTypeExpression?.toString() ?: 'void'
            
            // Extract parameter types
            List<String> parameterTypes = method.parameters.collect { param ->
                if (param instanceof J.VariableDeclarations) {
                    J.VariableDeclarations varDecl = (J.VariableDeclarations) param
                    return varDecl.typeExpression?.toString() ?: 'unknown'
                }
                return param.toString()
            } as List<String>
            
            Map<String, Object> match = [
                nodeId: method.id.toString(),
                nodeType: 'MethodDeclaration',
                className: classDecl?.simpleName ?: 'Unknown',
                methodName: method.simpleName,
                annotations: annotations,
                modifiers: modifiers,
                returnType: returnType,
                parameterTypes: parameterTypes,
                parameterCount: method.parameters.size(),
                location: [
                    file: sourceFile.sourcePath.toString(),
                    line: position.get('line'),
                    column: position.get('column')
                ]
            ]
            matches.add(match)
        }
        
        return super.visitMethodDeclaration(method, ctx)
    }
}

// Execute the visitor with the compilation unit from binding
def visitor = new AnnotationMethodFinderVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
