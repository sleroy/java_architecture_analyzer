package com.analyzer.inspectors.core;

import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorResult;
import com.analyzer.core.ProjectFile;

/**
 * Base abstract class for inspectors that analyze ProjectFile objects.
 * This provides the foundation for the new ProjectFile-based architecture
 * while maintaining compatibility with the existing Inspector interface.
 * 
 * Concrete implementations should extend this class and implement the
 * analyzeProjectFile() method to perform their specific analysis.
 * 
 * This class handles:
 * - Type safety for ProjectFile objects
 * - Default support checking logic
 * - Template method pattern for analysis
 */
public abstract class ProjectFileInspector implements Inspector<ProjectFile> {

    @Override
    public final InspectorResult decorate(ProjectFile projectFile) {
        if (!supports(projectFile)) {
            return InspectorResult.notApplicable(getColumnName());
        }

        try {
            return analyzeProjectFile(projectFile);
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(),
                    "Error analyzing project file: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // By default, support any ProjectFile - subclasses can override
        return projectFile != null;
    }

    /**
     * Template method implemented by concrete inspectors.
     * This method is called when a ProjectFile has been validated and
     * is ready for analysis.
     * 
     * @param projectFile the ProjectFile to analyze
     * @return the result of the analysis
     */
    protected abstract InspectorResult analyzeProjectFile(ProjectFile projectFile);

    /**
     * Utility method to check if the ProjectFile has a specific tag.
     * 
     * @param projectFile the ProjectFile to check
     * @param tagName     the name of the tag
     * @return true if the tag exists, false otherwise
     */
    protected boolean hasTag(ProjectFile projectFile, String tagName) {
        return projectFile.hasTag(tagName);
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
    protected <T> T getTag(ProjectFile projectFile, String tagName, Class<T> expectedType) {
        Object value = projectFile.getTag(tagName);
        if (value != null && expectedType.isInstance(value)) {
            return expectedType.cast(value);
        }
        return null;
    }

    /**
     * Utility method to check if the ProjectFile represents a Java class file.
     * Uses the migration helper methods for backward compatibility.
     * 
     * @param projectFile the ProjectFile to check
     * @return true if this appears to be a Java class file
     */
    protected boolean isJavaClass(ProjectFile projectFile) {
        return projectFile.hasSourceCode() || projectFile.hasBinaryCode();
    }

    /**
     * Utility method to get the fully qualified class name if available.
     * Uses the migration helper methods for backward compatibility.
     * 
     * @param projectFile the ProjectFile to get the class name from
     * @return the fully qualified class name, or null if not available
     */
    protected String getClassName(ProjectFile projectFile) {
        return projectFile.getFullyQualifiedName();
    }
}
