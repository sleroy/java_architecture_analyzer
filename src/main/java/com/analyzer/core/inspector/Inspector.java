package com.analyzer.core.inspector;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.model.ProjectFile;

/**
 * Generic interface for all inspectors.
 * Inspectors analyze objects and return results that are stored in columns.
 *
 * @param <T> the type of object this inspector can analyze (ProjectFile,
 *            Package, or legacy ProjectFile)
 */
public interface Inspector<T> {

    /**
     * Analyzes the given object and returns a result.
     *
     * @param objectToAnalyze the object to analyze
     * @param projectFileDecorator the decorator to store the result
     * @return the result of the analysis
     */
    void decorate(T objectToAnalyze, ProjectFileDecorator projectFileDecorator);

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

        // For ProjectFile objects, check tag dependencies using new annotation system
        if (objectToAnalyze instanceof ProjectFile) {
            ProjectFile projectFile = (ProjectFile) objectToAnalyze;
            RequiredTags requiredTags = getDependencies(); // Uses annotation system!
            boolean allTags = projectFile.hasAllTags(requiredTags.toArray());
            return allTags && supports(objectToAnalyze);

        }
        return supports(objectToAnalyze);
    }

    default boolean supports(T objectToAnalyze) {
        return true; // Default implementation assumes support for all types
    }
}
