import groovy.transform.CompileStatic
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaSourceFile
import org.openrewrite.java.tree.TypeTree  // CORRECT IMPORT - not J.TypeTree!
import org.openrewrite.SourceFile
import org.openrewrite.InMemoryExecutionContext
import com.analyzer.refactoring.mcp.util.PositionTracker

/**
 * Template for finding classes that implement a specific interface.
 * 
 * Parameters (passed via Groovy binding):
 * - interfaceName: The interface to search for (simple or FQN, e.g., "MessageListener" or "javax.jms.MessageListener")
 * - compilationUnit: The OpenRewrite compilation unit to analyze
 * 
 * IMPORTANT: This template uses the correct TypeTree import (not J.TypeTree)
 * which fixes the compilation error that was occurring with AI-generated scripts.
 */
@CompileStatic
class InterfaceFinderVisitor extends JavaIsoVisitor<ExecutionContext> {
    List<Map<String, Object>> matches = []
    
    // Parameter from binding - the interface to search for
    String targetInterface = interfaceName as String
    
    @Override
    J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        // Get the implements clause (returns List<TypeTree>, NOT List<J.TypeTree>!)
        List<TypeTree> implementsList = classDecl.implements
        
        // Check if class implements the target interface
        boolean implementsInterface = implementsList?.any { typeTree ->
            typeTree.toString().contains(targetInterface)
        } ?: false
        
        if (implementsInterface) {
            SourceFile sourceFile = getCursor().firstEnclosingOrThrow(SourceFile.class)
            Map<String, Integer> position = PositionTracker.getPosition((JavaSourceFile) sourceFile, classDecl)
            
            // Extract interface names
            List<String> interfaces = implementsList.collect { 
                it.toString() 
            } as List<String>
            
            // Extract modifiers (public, abstract, etc.)
            List<String> modifiers = classDecl.modifiers.collect { 
                it.type.toString() 
            } as List<String>
            
            // Extract annotations for additional context
            List<String> annotations = classDecl.leadingAnnotations.collect { 
                it.annotationType.toString() 
            } as List<String>
            
            // Check if class has extends clause
            String extendsClass = classDecl.extends_?.toString() ?: null
            
            Map<String, Object> match = [
                nodeId: classDecl.id.toString(),
                nodeType: 'ClassDeclaration',
                className: classDecl.simpleName,
                implements: interfaces,
                extends: extendsClass,
                modifiers: modifiers,
                annotations: annotations,
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
def visitor = new InterfaceFinderVisitor()
visitor.visit(compilationUnit, new InMemoryExecutionContext())
return visitor.matches
