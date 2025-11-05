package com.analyzer.migration.template;

import com.analyzer.migration.context.MigrationContext;

import java.util.function.Function;

/**
 * Represents a property that contains template expressions requiring
 * resolution.
 * This class provides lazy evaluation of template strings using
 * MigrationContext.
 * 
 * @param <T> The type of the resolved property value
 */
public class TemplateProperty<T> {
    private final String template;
    private final Function<String, T> resolver;
    private T resolvedValue;
    private boolean resolved = false;

    /**
     * Creates a new template property.
     * 
     * @param template The template string containing variable expressions
     * @param resolver Function to convert resolved string to target type
     */
    public TemplateProperty(String template, Function<String, T> resolver) {
        this.template = template;
        this.resolver = resolver;
    }

    /**
     * Resolves the template using the provided context.
     * Result is cached for subsequent calls.
     * 
     * @param context The migration context with variables
     * @return The resolved value
     * @throws RuntimeException if resolution fails
     */
    public T resolve(MigrationContext context) {
        if (!resolved) {
            try {
                String resolvedTemplate = context.substituteVariables(template);
                resolvedValue = resolver.apply(resolvedTemplate);
                resolved = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve template: " + template, e);
            }
        }
        return resolvedValue;
    }

    /**
     * Gets the raw template string without resolution.
     * 
     * @return The unresolved template string
     */
    public String getTemplate() {
        return template;
    }

    /**
     * Checks if this property has been resolved.
     * 
     * @return true if resolve() has been called successfully
     */
    public boolean isResolved() {
        return resolved;
    }

    /**
     * Gets the resolved value without triggering resolution.
     * 
     * @return The resolved value if available, null otherwise
     */
    public T getResolvedValue() {
        return resolvedValue;
    }

    /**
     * Clears the resolved value, forcing re-resolution on next access.
     */
    public void clearResolved() {
        resolved = false;
        resolvedValue = null;
    }

    /**
     * Checks if the template contains variable expressions.
     * 
     * @return true if the template contains ${...} expressions
     */
    public boolean hasVariables() {
        return template != null && template.contains("${");
    }

    @Override
    public String toString() {
        return "TemplateProperty{" +
                "template='" + template + '\'' +
                ", resolved=" + resolved +
                '}';
    }
}
