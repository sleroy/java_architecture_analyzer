package com.analyzer.ejb2spring.openrewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.Map;
import java.util.Objects;

/**
 * OpenRewrite recipe for batch replacing Java annotations.
 * 
 * <p>
 * <b>Token Optimization</b>: This recipe handles annotation replacement
 * deterministically,
 * eliminating the need for AI to process these transformations. For N classes
 * with
 * annotation replacements, this saves N × 100 tokens.
 * </p>
 * 
 * <p>
 * <b>Example Usage</b>:
 * </p>
 * 
 * <pre>{@code
 * // In migration YAML:
 * - type: "OPENREWRITE"
 *   recipe: "com.analyzer.ejb2spring.openrewrite.AnnotationReplacementRecipe"
 *   options:
 *     annotationMappings:
 *       "@Stateless": "@Service"
 *       "@EJB": "@Autowired"
 *       "@Resource": "@Autowired"
 * }</pre>
 * 
 * <p>
 * <b>Token Savings</b>: 100% for covered annotations (no AI needed)
 * </p>
 * 
 * @since 1.0.0
 */
public class AnnotationReplacementRecipe extends Recipe {

    @Option(displayName = "Annotation mappings", description = "Map of old annotation names to new annotation names", example = "{'@Stateless': '@Service'}")
    private final Map<String, String> annotationMappings;

    @JsonCreator
    public AnnotationReplacementRecipe(
            @JsonProperty("annotationMappings") Map<String, String> annotationMappings) {
        this.annotationMappings = annotationMappings;
    }

    public Map<String, String> getAnnotationMappings() {
        return annotationMappings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AnnotationReplacementRecipe that = (AnnotationReplacementRecipe) o;
        return Objects.equals(annotationMappings, that.annotationMappings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationMappings);
    }

    @Override
    public String getDisplayName() {
        return "Replace Java annotations in batch";
    }

    @Override
    public String getDescription() {
        return "Replaces specified annotations with their mapped alternatives. " +
                "Useful for EJB to Spring migrations (@Stateless → @Service, @EJB → @Autowired).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AnnotationReplacementVisitor();
    }

    private class AnnotationReplacementVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);

            // Get annotation simple name
            String annotationName = ann.getSimpleName();

            // Check if we need to replace this annotation
            for (Map.Entry<String, String> mapping : annotationMappings.entrySet()) {
                String oldAnnotation = cleanAnnotationName(mapping.getKey());
                String newAnnotation = cleanAnnotationName(mapping.getValue());

                if (annotationName.equals(oldAnnotation)) {
                    // Create template for new annotation
                    JavaTemplate template = JavaTemplate.builder(newAnnotation)
                            .build();

                    // Replace the annotation
                    return template.apply(
                            getCursor(),
                            ann.getCoordinates().replace());
                }
            }

            return ann;
        }

        /**
         * Removes @ prefix if present to get simple name.
         */
        private String cleanAnnotationName(String name) {
            return name.startsWith("@") ? name.substring(1) : name;
        }
    }
}
