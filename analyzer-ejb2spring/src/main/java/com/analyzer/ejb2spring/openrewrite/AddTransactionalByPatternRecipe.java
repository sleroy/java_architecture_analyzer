package com.analyzer.ejb2spring.openrewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Objects;

/**
 * OpenRewrite recipe for adding @Transactional annotations to methods by name
 * pattern.
 * 
 * <p>
 * <b>Token Optimization</b>: Automatically adds @Transactional to methods
 * matching patterns (save*, update*, delete*, etc.), eliminating AI token
 * consumption for this common task.
 * </p>
 * 
 * <p>
 * <b>Example Usage</b>:
 * </p>
 * 
 * <pre>{@code
 * // In migration YAML:
 * - type: "OPENREWRITE"
 *   recipe: "com.analyzer.ejb2spring.openrewrite.AddTransactionalByPatternRecipe"
 *   options:
 *     methodPatterns: ["save*", "update*", "delete*", "create*"]
 *     readOnlyPatterns: ["find*", "get*", "list*"]
 * }</pre>
 * 
 * <p>
 * <b>Token Savings</b>: 1500 → 0 tokens per class (100%)
 * </p>
 * 
 * @since 1.0.0
 */
public class AddTransactionalByPatternRecipe extends Recipe {

    @Option(displayName = "Method patterns", description = "Method name patterns that should have @Transactional", example = "['save*', 'update*', 'delete*']")
    private final List<String> methodPatterns;

    @Option(displayName = "Read-only patterns", description = "Method name patterns that should have @Transactional(readOnly=true)", example = "['find*', 'get*']")
    private final List<String> readOnlyPatterns;

    @JsonCreator
    public AddTransactionalByPatternRecipe(
            @JsonProperty("methodPatterns") List<String> methodPatterns,
            @JsonProperty("readOnlyPatterns") List<String> readOnlyPatterns) {
        this.methodPatterns = methodPatterns != null ? methodPatterns : List.of();
        this.readOnlyPatterns = readOnlyPatterns != null ? readOnlyPatterns : List.of();
    }

    public List<String> getMethodPatterns() {
        return methodPatterns;
    }

    public List<String> getReadOnlyPatterns() {
        return readOnlyPatterns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AddTransactionalByPatternRecipe that = (AddTransactionalByPatternRecipe) o;
        return Objects.equals(methodPatterns, that.methodPatterns)
                && Objects.equals(readOnlyPatterns, that.readOnlyPatterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodPatterns, readOnlyPatterns);
    }

    @Override
    public String getDisplayName() {
        return "Add @Transactional to methods by pattern";
    }

    @Override
    public String getDescription() {
        return "Adds @Transactional annotation to public methods matching specified name patterns. " +
                "Supports read-only transactions for query methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddTransactionalVisitor();
    }

    private class AddTransactionalVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

            // Only process public methods
            if (!md.hasModifier(J.Modifier.Type.Public)) {
                return md;
            }

            // Check if method already has @Transactional
            boolean hasTransactional = md.getLeadingAnnotations().stream()
                    .anyMatch(ann -> "Transactional".equals(ann.getSimpleName()));

            if (hasTransactional) {
                return md;
            }

            String methodName = md.getSimpleName();

            // Check if method matches write patterns
            if (matchesAnyPattern(methodName, methodPatterns)) {
                maybeAddImport("org.springframework.transaction.annotation.Transactional");

                JavaTemplate template = JavaTemplate.builder("@Transactional")
                        .imports("org.springframework.transaction.annotation.Transactional")
                        .build();

                return template.apply(
                        getCursor(),
                        md.getCoordinates().addAnnotation(java.util.Comparator.comparing(
                                a -> a.getSimpleName())));
            }

            // Check if method matches read-only patterns
            if (matchesAnyPattern(methodName, readOnlyPatterns)) {
                maybeAddImport("org.springframework.transaction.annotation.Transactional");

                JavaTemplate template = JavaTemplate.builder("@Transactional(readOnly = true)")
                        .imports("org.springframework.transaction.annotation.Transactional")
                        .build();

                return template.apply(
                        getCursor(),
                        md.getCoordinates().addAnnotation(java.util.Comparator.comparing(
                                a -> a.getSimpleName())));
            }

            return md;
        }

        private boolean matchesAnyPattern(String methodName, List<String> patterns) {
            for (String pattern : patterns) {
                if (matchesPattern(methodName, pattern)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesPattern(String methodName, String pattern) {
            // Simple glob pattern matching (* at end)
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                return methodName.startsWith(prefix);
            }
            // Exact match
            return methodName.equals(pattern);
        }
    }
}
