import groovy.transform.CompileStatic
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaSourceFile
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext
import com.analyzer.refactoring.mcp.util.PositionTracker

/**
 * Template for finding classes with a specific annotation.
 * 
 * Parameters (passed via Groovy binding):
 * - annotationName: The annotation to search for (without @, e.g., "Stateless", "Service")
 * - compilationUnit: The OpenRewrite compilation unit to analyze
 */
@CompileStatic
class AnnotationClassFinderVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    // Parameter from binding - the annotation to search for
    String targetAnnotation = annotationName as String
    
    @Override
    J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        // Check if class has the target annotation
        boolean hasAnnotation = classDecl.leadingAnnotations.any { ann ->
            ann.annotationType.toString().contains(targetAnnotation)
        }
        
        if (hasAnnotation) {
            SourceFile sourceFile = getCursor().firstEnclosingOrThrow(SourceFile.class)
            Map<String, Integer> position = PositionTracker.getPosition((JavaSourceFile) sourceFile, classDecl)
            
            // Extract all annotation names for context
            List<String> annotations = classDecl.leadingAnnotations.collect { 
                it.annotationType.toString() 
            } as List<String>
            
            // Extract modifiers (public, abstract, etc.)
            List<String> modifiers = classDecl.modifiers.collect { 
                it.type.toString() 
            } as List<String>
            
            // Check if class has extends clause
            String extendsClass = classDecl.extends_?.toString() ?: null
            
            // Check if class implements interfaces
            List<String> implementsInterfaces = classDecl.implements?.collect { 
                it.toString() 
            } as List<String> ?: []
            
            Map<String, Object> match = [
                nodeId: classDecl.id.toString(),
                nodeType: 'ClassDeclaration',
                className: classDecl.simpleName,
                annotations: annotations,
                modifiers: modifiers,
                extends: extendsClass,
                implements: implementsInterfaces,
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

// Execute the visitor with the compilation unit from binding
def visitor = new AnnotationClassFinderVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
