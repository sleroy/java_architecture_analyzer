package com.analyzer.core.export;

import com.analyzer.core.inspector.InspectorResult;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;

import java.util.Objects;

/**
 * Specialized decorator for ProjectFile that extends NodeDecorator.
 * Provides backward-compatible methods and InspectorResult handling.
 */
public class ProjectFileDecorator extends NodeDecorator<ProjectFile> {

    public ProjectFileDecorator(ProjectFile projectFile) {
        super(projectFile);
    }

    /**
     * Gets the ProjectFile being decorated.
     * 
     * @return the ProjectFile
     * @deprecated Use getNode() instead
     */
    @Deprecated
    public ProjectFile getProjectFile() {
        return getNode();
    }

    /**
     * Stores the result of an inspector into the ProjectFile.
     *
     * @param result the result to store
     */
    void store(InspectorResult result) {
        Objects.requireNonNull(result, "InspectorResult cannot be null");
        if (result.isSuccessful()) {
            getNode().setProperty(result.getTagName(), result.getValue());
        } else if (result.isError()) {
            getNode().setProperty(result.getTagName(), "ERROR: " + result.getErrorMessage());
        }
    }

    /**
     * Records a processing error.
     * 
     * @param errorMessage the error message
     */
    public void error(String errorMessage) {
        setProperty(InspectorTags.PROCESSING_ERROR, errorMessage);
    }

    /**
     * Marks the analysis as not applicable (no-op).
     */
    public void notApplicable() {
        // Nothing to do
    }

    /**
     * Sets a tag/property value.
     * 
     * @param columnName the column/property name
     * @param value      the value to set
     * @deprecated Use setProperty() instead
     */
    @Deprecated
    public void setTag(String columnName, Object value) {
        store(InspectorResult.success(columnName, value));
    }

    /**
     * Records an error from a throwable.
     * 
     * @param e the throwable
     */
    public void error(Throwable e) {
        store(InspectorResult.error(InspectorTags.PROCESSING_ERROR, e.getMessage()));
    }

    /**
     * Records an error with a specific column name.
     * 
     * @param columnName the column name
     * @param e          the exception
     */
    public void fromThrowable(String columnName, Exception e) {
        store(InspectorResult.fromThrowable(columnName, e));
    }

    // ========== Enhanced Aggregation Methods (Delegate to NodeDecorator)
    // ==========

    /**
     * Sets a numeric tag to the maximum of current and new value.
     * Useful for tracking highest complexity scores, maximum method counts, etc.
     *
     * @param tagName  the tag name to set
     * @param newValue the new value to compare
     * @deprecated Use setMaxProperty(tagName, newValue) instead
     */
    @Deprecated
    public void setMax(String tagName, int newValue) {
        setMaxProperty(tagName, newValue);
    }

    /**
     * Sets a numeric tag to the maximum of current and new value.
     * Useful for tracking highest complexity scores, maximum values, etc.
     *
     * @param tagName  the tag name to set
     * @param newValue the new value to compare
     * @deprecated Use setMaxProperty(tagName, newValue) instead
     */
    @Deprecated
    public void setMax(String tagName, double newValue) {
        setMaxProperty(tagName, newValue);
    }

    /**
     * Boolean OR operation: sets tag to true if either current OR new value is
     * true.
     * Useful for aggregating detection flags across multiple inspectors.
     * Example: or("has_ejb_patterns", true) - marks as true if ANY inspector finds
     * EJB patterns
     *
     * @param tagName  the tag name to set
     * @param newValue the new boolean value to OR with current
     * @deprecated Use orProperty(tagName, newValue) instead
     */
    @Deprecated
    public void or(String tagName, boolean newValue) {
        orProperty(tagName, newValue);
    }

    /**
     * Boolean AND operation: sets tag to true only if both current AND new are
     * true.
     * Useful for tracking requirements that must ALL be satisfied.
     * Example: and("fully_compatible", isCompatible) - only true if ALL checks pass
     *
     * @param tagName  the tag name to set
     * @param newValue the new boolean value to AND with current
     * @deprecated Use andProperty(tagName, newValue) instead
     */
    @Deprecated
    public void and(String tagName, boolean newValue) {
        andProperty(tagName, newValue);
    }

    /**
     * Sets a String tag to the "higher" complexity level between current and new.
     * Supports standard complexity progression: NONE < LOW < MEDIUM < HIGH <
     * CRITICAL
     * Useful for aggregating complexity assessments across multiple inspectors.
     *
     * @param tagName       the tag name to set
     * @param newComplexity the new complexity level
     * @deprecated Use setMaxComplexityProperty(tagName, newComplexity) instead
     */
    @Deprecated
    public void setMaxComplexity(String tagName, String newComplexity) {
        setMaxComplexityProperty(tagName, newComplexity);
    }
}
