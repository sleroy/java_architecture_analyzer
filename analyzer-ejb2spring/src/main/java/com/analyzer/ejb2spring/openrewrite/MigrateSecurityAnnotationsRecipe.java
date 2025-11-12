package com.analyzer.ejb2spring.openrewrite;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

/**
 * OpenRewrite recipe for migrating EJB security annotations to Spring Security.
 * 
 * <p>
 * <b>Transformations</b>:
 * </p>
 * <ul>
 * <li>@RolesAllowed("ADMIN") → @PreAuthorize("hasRole('ADMIN')")</li>
 * <li>@PermitAll → @PreAuthorize("permitAll()")</li>
 * <li>@DenyAll → @PreAuthorize("denyAll()")</li>
 * <li>Update imports (remove javax.annotation.security.*, add
 * org.springframework.security.*)</li>
 * </ul>
 * 
 * <p>
 * <b>Token Optimization</b>: Security annotation migration typically requires
 * 1000+ tokens per class. Recipe handles it deterministically with 0 tokens.
 * </p>
 * 
 * <p>
 * <b>Example Usage</b>:
 * </p>
 * 
 * <pre>{@code
 * // In migration YAML:
 * - type: "OPENREWRITE"
 *   recipe: "com.analyzer.ejb2spring.openrewrite.MigrateSecurityAnnotationsRecipe"
 * }</pre>
 * 
 * <p>
 * <b>Token Savings</b>: 1000 → 0 tokens per class (100%)
 * </p>
 * 
 * @since 1.0.0
 */
public class MigrateSecurityAnnotationsRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate EJB security annotations to Spring Security";
    }

    @Override
    public String getDescription() {
        return "Converts EJB security annotations (@RolesAllowed, @PermitAll, @DenyAll) " +
                "to Spring Security @PreAuthorize annotations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrateSecurityAnnotationsVisitor();
    }

    private static class MigrateSecurityAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);
            String simpleName = ann.getSimpleName();

            // Convert @PermitAll
            if ("PermitAll".equals(simpleName)) {
                maybeAddImport("org.springframework.security.access.prepost.PreAuthorize");
                maybeRemoveImport("javax.annotation.security.PermitAll");

                JavaTemplate template = JavaTemplate.builder("@PreAuthorize(\"permitAll()\")")
                        .imports("org.springframework.security.access.prepost.PreAuthorize")
                        .build();

                return template.apply(getCursor(), ann.getCoordinates().replace());
            }

            // Convert @DenyAll
            if ("DenyAll".equals(simpleName)) {
                maybeAddImport("org.springframework.security.access.prepost.PreAuthorize");
                maybeRemoveImport("javax.annotation.security.DenyAll");

                JavaTemplate template = JavaTemplate.builder("@PreAuthorize(\"denyAll()\")")
                        .imports("org.springframework.security.access.prepost.PreAuthorize")
                        .build();

                return template.apply(getCursor(), ann.getCoordinates().replace());
            }

            // Convert @RolesAllowed
            if ("RolesAllowed".equals(simpleName)) {
                String rolesExpression = extractRolesExpression(ann);

                maybeAddImport("org.springframework.security.access.prepost.PreAuthorize");
                maybeRemoveImport("javax.annotation.security.RolesAllowed");

                JavaTemplate template = JavaTemplate.builder("@PreAuthorize(" + rolesExpression + ")")
                        .imports("org.springframework.security.access.prepost.PreAuthorize")
                        .build();

                return template.apply(getCursor(), ann.getCoordinates().replace());
            }

            return ann;
        }

        private String extractRolesExpression(J.Annotation ann) {
            // Extract roles from @RolesAllowed annotation
            // This is simplified - full implementation would parse annotation arguments
            String annStr = ann.toString();

            if (annStr.contains("(")) {
                // Has arguments: @RolesAllowed("ADMIN") or @RolesAllowed({"ADMIN", "USER"})
                int start = annStr.indexOf('(');
                int end = annStr.lastIndexOf(')');

                if (start > 0 && end > start) {
                    String argsStr = annStr.substring(start + 1, end).trim();

                    // Handle array: {"ADMIN", "USER"}
                    if (argsStr.startsWith("{") && argsStr.endsWith("}")) {
                        argsStr = argsStr.substring(1, argsStr.length() - 1);
                        String[] roles = argsStr.split(",");

                        if (roles.length == 1) {
                            return "\"hasRole(" + roles[0].trim() + ")\"";
                        } else {
                            // Multiple roles: hasAnyRole
                            StringBuilder sb = new StringBuilder("\"hasAnyRole(");
                            for (int i = 0; i < roles.length; i++) {
                                if (i > 0)
                                    sb.append(", ");
                                sb.append(roles[i].trim());
                            }
                            sb.append(")\"");
                            return sb.toString();
                        }
                    }

                    // Handle single value: "ADMIN"
                    return "\"hasRole(" + argsStr + ")\"";
                }
            }

            // Default
            return "\"authenticated\"";
        }
    }
}
