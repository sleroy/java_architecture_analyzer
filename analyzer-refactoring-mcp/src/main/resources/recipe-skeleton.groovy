import groovy.transform.CompileStatic
import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J

/**
 * WORKING EXAMPLE: Recipe that renames methods matching a pattern.
 * 
 * This is a complete, tested example showing correct OpenRewrite Recipe usage.
 * Adapt this pattern for your specific transformation requirements.
 * 
 * Key elements demonstrated:
 * 1. Proper Recipe class structure extending Recipe
 * 2. Use of @CompileStatic for type safety and performance
 * 3. getDisplayName() for recipe description
 * 4. getVisitor() returning a JavaIsoVisitor
 * 5. Transformation logic that modifies AST nodes
 * 6. Use of withXXX() methods for immutable updates
 */
@CompileStatic
class ExampleRenameMethodRecipe extends Recipe {
    
    @Override
    String getDisplayName() {
        return "Example: Rename methods"
    }
    
    @Override
    String getDescription() {
        return "Renames methods matching a specific pattern"
    }
    
    @Override
    TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            
            @Override
            J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // Example: Rename methods that start with "old"
                if (method.simpleName.startsWith("old")) {
                    String newName = method.simpleName.replace("old", "new")
                    
                    // Use withSimpleName to create modified copy
                    return method.withSimpleName(newName)
                }
                
                // IMPORTANT: Always call super to continue traversal
                return super.visitMethodDeclaration(method, ctx)
            }
        }
    }
}

// Return the recipe instance
return new ExampleRenameMethodRecipe()
