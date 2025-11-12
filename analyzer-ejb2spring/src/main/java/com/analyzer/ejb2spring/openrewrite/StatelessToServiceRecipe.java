package com.analyzer.ejb2spring.openrewrite;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

/**
 * OpenRewrite recipe for migrating @Stateless EJBs to Spring @Service.
 * 
 * <p>
 * <b>Transformations</b>:
 * </p>
 * <ul>
 * <li>Replace @Stateless with @Service</li>
 * <li>Remove @Local annotation</li>
 * <li>Remove @Remote annotation</li>
 * <li>Update imports (remove javax.ejb.*, add org.springframework.*)</li>
 * </ul>
 * 
 * <p>
 * <b>Token Optimization</b>: Handles the most common EJB migration pattern
 * deterministically with 0 tokens. For 100 stateless beans, this saves 250,000
 * tokens.
 * </p>
 * 
 * <p>
 * <b>Example Usage</b>:
 * </p>
 * 
 * <pre>{@code
 * // In migration YAML:
 * - type: "OPENREWRITE"
 *   recipe: "com.analyzer.ejb2spring.openrewrite.StatelessToServiceRecipe"
 * }</pre>
 * 
 * @since 1.0.0
 */
public class StatelessToServiceRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate @Stateless to @Service";
    }

    @Override
    public String getDescription() {
        return "Converts EJB @Stateless session beans to Spring @Service components. " +
                "Replaces @Stateless with @Service and removes @Local/@Remote annotations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new StatelessToServiceVisitor();
    }

    private static class StatelessToServiceVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);
            String simpleName = ann.getSimpleName();

            // Replace @Stateless with @Service
            if ("Stateless".equals(simpleName)) {
                maybeAddImport("org.springframework.stereotype.Service");
                maybeRemoveImport("javax.ejb.Stateless");

                JavaTemplate template = JavaTemplate.builder("@Service")
                        .build();

                return template.apply(
                        getCursor(),
                        ann.getCoordinates().replace());
            }

            // Remove @Local and @Remote
            if ("Local".equals(simpleName) || "Remote".equals(simpleName)) {
                if ("Local".equals(simpleName)) {
                    maybeRemoveImport("javax.ejb.Local");
                } else {
                    maybeRemoveImport("javax.ejb.Remote");
                }

                // Return null to remove the annotation
                return null;
            }

            return ann;
        }
    }
}
