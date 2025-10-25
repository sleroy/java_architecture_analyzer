package com.analyzer.inspectors.core;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.inspector.InspectorTargetType;
import com.analyzer.core.model.ProjectFile;

/**
 * Base abstract class for inspectors that analyze ProjectFile objects.
 * This provides the foundation for the new ProjectFile-based architecture
 * while maintaining compatibility with the existing Inspector interface.
 * <p>
 * Concrete implementations should extend this class and implement the
 * analyzeProjectFile() method to perform their specific analysis.
 * <p>
 * This class handles:
 * - Type safety for ProjectFile objects
 * - Default support checking logic
 * - Template method pattern for analysis
 */
@InspectorDependencies(need = {  }, requires = {}, produces = {})
public abstract class AbstractProjectFileInspector implements Inspector<ProjectFile> {

    @Override
    public final void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator) {
        try {
            analyzeProjectFile(projectFile, decorator);
        } catch (Exception e) {
            decorator.error("Error analyzing project file: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // By default, support any ProjectFile - subclasses can override
        return projectFile != null;
    }

    @Override
    public InspectorTargetType getTargetType() {
        return InspectorTargetType.PROJECT_FILE;
    }

    /**
     * Template method implemented by concrete inspectors.
     * This method is called when a ProjectFile has been validated and
     * is ready for analysis.
     *
     * @param projectFile the ProjectFile to analyze
     * @param decorator   the decorator for setting properties and tags
     */
    protected abstract void analyzeProjectFile(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator);

    /**
     * Utility method to check if the ProjectFile has a specific tag.
     *
     * @param projectFile the ProjectFile to check
     * @param tagName     the name of the tag
     * @return true if the tag exists, false otherwise
     */
    protected boolean hasProperty(ProjectFile projectFile, String propertyName) {
        return projectFile.hasProperty(propertyName);
    }

    /**
     * Utility method to get a tag value with type safety.
     *
     * @param projectFile  the ProjectFile to get the tag from
     * @param tagName      the name of the tag
     * @param expectedType the expected type of the tag value
     * @param <T>          the type parameter
     * @return the tag value cast to the expected type, or null if not found or
     *         wrong type
     */
    protected <T> T getProperty(ProjectFile projectFile, String propertyName, Class<T> expectedType) {
        Object value = projectFile.getProperty(propertyName);
        if (value != null && expectedType.isInstance(value)) {
            return expectedType.cast(value);
        }
        return null;
    }

    /**
     * Utility method to check if the ProjectFile represents a Java class file.
     * Uses the centralized tag system instead of deprecated methods.
     *
     * @param projectFile the ProjectFile to check
     * @return true if this appears to be a Java class file
     */
    protected boolean isJavaClass(ProjectFile projectFile) {
        return projectFile.getBooleanProperty(InspectorTags.TAG_JAVA_IS_SOURCE, false) ||
                projectFile.getBooleanProperty(InspectorTags.TAG_JAVA_IS_BINARY, false);
    }

    /**
     * Utility method to get the fully qualified class name if available.
     * Uses the migration helper methods for backward compatibility.
     *
     * @param projectFile the ProjectFile to get the class name from
     * @return the fully qualified class name, or null if not available
     */
    protected String getClassName(ProjectFile projectFile) {
        return projectFile.getStringProperty(InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME);
    }
}
