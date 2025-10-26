package com.analyzer.api.inspector;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.inspector.InspectorDependencyResolver;
import com.analyzer.core.inspector.InspectorTargetType;
import com.analyzer.core.inspector.RequiredTags;

/**
 * Generic interface for all inspectors.
 * Inspectors analyze graph nodes and decorate them with properties and tags.
 *
 * @param <T> the type of GraphNode this inspector can analyze
 */
public interface Inspector<T extends GraphNode> {

    /**
     * Inspects and analyzes the given node, decorating it with properties and tags.
     *
     * @param node      the node to inspect
     * @param decorator the decorator for setting properties (with aggregation) and
     *                  tags
     */
    void inspect(T node, NodeDecorator<T> decorator);

    /**
     * Gets the unique name of this inspector.
     * This is used for configuration and logging.
     *
     * @return the inspector name
     */
    String getName();

    /**
     * Gets all required dependencies for this inspector using the annotation-based
     * system.
     * This method automatically resolves dependencies from @InspectorDependencies
     * annotations
     * throughout the inheritance chain, eliminating the need for manual
     * super.depends() calls.
     *
     * <p>
     * Dependencies are resolved by walking the class hierarchy and collecting all
     *
     * @return RequiredTags containing all dependencies (own + inherited)
     * @InspectorDependencies annotations. The resolver handles inheritance,
     * overrides,
     * and caching automatically.
     * </p>
     */
    default RequiredTags getDependencies() {
        return InspectorDependencyResolver.getDependencies(this);
    }

    /**
     * Enhanced support validation that combines dependency checking and file type
     * support.
     * This is the method that should be used by the analysis engine to determine
     * if an inspector can process a given object.
     *
     * <p>
     * This method now uses the annotation-based dependency system for improved
     * maintainability and reduced error potential.
     * </p>
     *
     * @param objectToAnalyze the object to check
     * @return true if all dependencies are satisfied AND the inspector supports the
     * object type
     */
    default boolean canProcess(T objectToAnalyze) {
        if (objectToAnalyze == null) {
            return false;
        }

        RequiredTags requiredTags = getDependencies(); // Uses annotation system!
        boolean allTags = objectToAnalyze.hasAllTags(requiredTags.toArray());
        return allTags && supports(objectToAnalyze);
    }

    default boolean supports(T objectToAnalyze) {
        return true; // Default implementation assumes support for all types
    }

    /**
     * Gets the target type that this inspector processes.
     * This enables type-safe filtering of inspectors without instanceof checks.
     *
     * <p>
     * Default implementation determines type from the inspector's class hierarchy.
     * Base classes should override this to return the specific target type.
     * </p>
     *
     * @return the target type this inspector processes
     * @since Phase 7 - Type-Safe Inspector Filtering
     */
    default InspectorTargetType getTargetType() {
        // Default implementation: try to determine from class hierarchy
        // Base classes should override for better performance
        return InspectorTargetType.ANY;
    }
}
