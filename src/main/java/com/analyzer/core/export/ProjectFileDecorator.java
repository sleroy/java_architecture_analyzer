package com.analyzer.core.export;
import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.inspector.InspectorResult;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ResultDecorator {
    private final ProjectFile projectFile;
    
    // Complexity levels in ascending order for comparison
    private static final List<String> COMPLEXITY_LEVELS = Arrays.asList(
        "NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL"
    );

    public ResultDecorator(ProjectFile projectFile) {
        this.projectFile = projectFile;
    }

    /**
     * Stores the result of an inspector into the ProjectFile.
     *
     * @param result the result to store
     */
    void store(InspectorResult result) {
        Objects.requireNonNull(result, "InspectorResult cannot be null");
        // Store the result using ProjectFile's addInspectorResult method
        if (result.isSuccessful()) {
            projectFile.addInspectorResult(result.getTagName(), result.getValue());
        } else if (result.isNotApplicable()) {
            // projectFile.addInspectorResult(inspector.getColumnName(), "N/A");
        } else if (result.isError()) {
            projectFile.addInspectorResult(result.getTagName(),
                    "ERROR: " + result.getErrorMessage());
        }
    }

    public void error(String errorMessage) {
        projectFile.addInspectorResult(InspectorTags.PROCESSING_ERROR, errorMessage);
    }

    public void notApplicable() {
        // Nothing to do
    }

    public void success(String columnName, Object value) {
        store(InspectorResult.success(columnName, value));
    }

    public void error(Throwable e) {
        store(InspectorResult.error(InspectorTags.PROCESSING_ERROR, e.getMessage()));
    }

    public void fromThrowable(String columnName, Exception e) {
        store(InspectorResult.fromThrowable(columnName, e));
    }
    
    // ========== Enhanced Aggregation Methods ==========
    
    /**
     * Sets a numeric tag to the maximum of current and new value.
     * Useful for tracking highest complexity scores, maximum method counts, etc.
     * 
     * @param tagName the tag name to set
     * @param newValue the new value to compare
     */
    public void setMax(String tagName, int newValue) {
        Integer currentValue = projectFile.getIntegerTag(tagName, 0);
        if (newValue > currentValue) {
            projectFile.setTag(tagName, newValue);
        }
    }
    
    /**
     * Sets a numeric tag to the maximum of current and new value.
     * Useful for tracking highest complexity scores, maximum values, etc.
     * 
     * @param tagName the tag name to set
     * @param newValue the new value to compare
     */
    public void setMax(String tagName, double newValue) {
        Double currentValue = projectFile.getDoubleTag(tagName, 0.0);
        if (newValue > currentValue) {
            projectFile.setTag(tagName, newValue);
        }
    }
    
    /**
     * Boolean OR operation: sets tag to true if either current OR new value is true.
     * Useful for aggregating detection flags across multiple inspectors.
     * Example: or("has_ejb_patterns", true) - marks as true if ANY inspector finds EJB patterns
     * 
     * @param tagName the tag name to set
     * @param newValue the new boolean value to OR with current
     */
    public void or(String tagName, boolean newValue) {
        boolean currentValue = projectFile.getBooleanTag(tagName, false);
        projectFile.setTag(tagName, currentValue || newValue);
    }
    
    /**
     * Boolean AND operation: sets tag to true only if both current AND new are true.
     * Useful for tracking requirements that must ALL be satisfied.
     * Example: and("fully_compatible", isCompatible) - only true if ALL checks pass
     * 
     * @param tagName the tag name to set
     * @param newValue the new boolean value to AND with current
     */
    public void and(String tagName, boolean newValue) {
        boolean currentValue = projectFile.getBooleanTag(tagName, true);
        projectFile.setTag(tagName, currentValue && newValue);
    }
    
    /**
     * Sets a String tag to the "higher" complexity level between current and new.
     * Supports standard complexity progression: NONE < LOW < MEDIUM < HIGH < CRITICAL
     * Useful for aggregating complexity assessments across multiple inspectors.
     * 
     * @param tagName the tag name to set
     * @param newComplexity the new complexity level
     */
    public void setMaxComplexity(String tagName, String newComplexity) {
        String currentComplexity = projectFile.getStringTag(tagName, "NONE");
        String maxComplexity = getMaxComplexity(currentComplexity, newComplexity);
        projectFile.setTag(tagName, maxComplexity);
    }
    
    /**
     * Determines the higher complexity level between two complexity strings.
     * 
     * @param current the current complexity level
     * @param newValue the new complexity level to compare
     * @return the higher complexity level
     */
    private String getMaxComplexity(String current, String newValue) {
        if (current == null) current = "NONE";
        if (newValue == null) newValue = "NONE";
        
        int currentIndex = COMPLEXITY_LEVELS.indexOf(current.toUpperCase());
        int newIndex = COMPLEXITY_LEVELS.indexOf(newValue.toUpperCase());
        
        // Handle unknown complexity levels by treating them as MEDIUM
        if (currentIndex == -1) currentIndex = 2; // MEDIUM
        if (newIndex == -1) newIndex = 2; // MEDIUM
        
        return COMPLEXITY_LEVELS.get(Math.max(currentIndex, newIndex));
    }
}
