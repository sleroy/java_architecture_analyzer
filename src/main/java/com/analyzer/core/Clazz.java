package com.analyzer.core;

import com.analyzer.resource.ResourceLocation;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a Java class discovered during analysis.
 * Can be either from source files or compiled class files.
 */
public class Clazz {
    private final String className;
    private final String packageName;
    private final ClassType classType;
    private final ResourceLocation sourceLocation;
    private final ResourceLocation binaryLocation;
    private final Map<String, Object> inspectorResults;

    public Clazz(String className, String packageName, ClassType classType,
            ResourceLocation sourceLocation, ResourceLocation binaryLocation) {
        this.className = Objects.requireNonNull(className, "Class name cannot be null");
        this.packageName = packageName != null ? packageName : "";
        this.classType = Objects.requireNonNull(classType, "Class type cannot be null");
        this.sourceLocation = sourceLocation;
        this.binaryLocation = binaryLocation;
        this.inspectorResults = new HashMap<>();
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getFullyQualifiedName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    public ClassType getClassType() {
        return classType;
    }

    public ResourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public ResourceLocation getBinaryLocation() {
        return binaryLocation;
    }

    public boolean hasSourceCode() {
        return sourceLocation != null;
    }

    public boolean hasBinaryCode() {
        return binaryLocation != null;
    }

    public Map<String, Object> getInspectorResults() {
        return inspectorResults;
    }

    public void addInspectorResult(String inspectorName, Object result) {
        // Smart merging: replace NA/null/error values with better values
        Object currentValue = inspectorResults.get(inspectorName);
        Object valueToStore = mergeInspectorResults(currentValue, result);
        inspectorResults.put(inspectorName, valueToStore);
    }

    /**
     * Merges two inspector results, preferring non-null, non-NA, non-error values.
     * Uses a priority system: valid values > default values > errors > N/A > null
     */
    private Object mergeInspectorResults(Object currentValue, Object newValue) {
        // If no current value, use the new value
        if (currentValue == null) {
            return newValue != null ? newValue : "N/A";
        }

        // If no new value, keep current
        if (newValue == null) {
            return currentValue;
        }

        String currentStr = currentValue.toString();
        String newStr = newValue.toString();

        // Priority levels (higher number = higher priority)
        int currentPriority = getValuePriority(currentStr);
        int newPriority = getValuePriority(newStr);

        // Use the value with higher priority
        return newPriority > currentPriority ? newValue : currentValue;
    }

    /**
     * Determines the priority of an inspector result value.
     * Higher values indicate better/more reliable results.
     */
    private int getValuePriority(String value) {
        if (value == null)
            return 0;

        // Convert to uppercase for case-insensitive comparison
        String upperValue = value.toUpperCase();

        // Lowest priority: null, empty, or N/A
        if (upperValue.isEmpty() || "N/A".equals(upperValue) || "NULL".equals(upperValue)) {
            return 1;
        }

        // Low priority: error messages
        if (upperValue.startsWith("ERROR:") || upperValue.startsWith("ERROR ")) {
            return 2;
        }

        // Medium priority: default/fallback values that might be replaced
        if ("UNKNOWN".equals(upperValue) || "DEFAULT".equals(upperValue) ||
                "BOTH".equals(upperValue) || "UNSPECIFIED".equals(upperValue)) {
            return 3;
        }

        // High priority: specific, meaningful values
        if (isSpecificValue(upperValue)) {
            return 5;
        }

        // Default priority: other values
        return 4;
    }

    /**
     * Checks if a value represents a specific, meaningful result.
     */
    private boolean isSpecificValue(String upperValue) {
        // Class types
        if ("CLASS".equals(upperValue) || "INTERFACE".equals(upperValue) ||
                "ENUM".equals(upperValue) || "ANNOTATION".equals(upperValue) ||
                "RECORD".equals(upperValue)) {
            return true;
        }

        // Numeric values (like line counts)
        try {
            Integer.parseInt(upperValue);
            return true;
        } catch (NumberFormatException e) {
            // Not a number, continue checking
        }

        // Other specific values can be added here
        return false;
    }

    public Object getInspectorResult(String inspectorName) {
        return inspectorResults.get(inspectorName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Clazz clazz = (Clazz) o;
        return Objects.equals(className, clazz.className) &&
                Objects.equals(packageName, clazz.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, packageName);
    }

    @Override
    public String toString() {
        return "Clazz{" +
                "className='" + className + '\'' +
                ", packageName='" + packageName + '\'' +
                ", classType=" + classType +
                ", sourceLocation='" + sourceLocation + '\'' +
                ", binaryLocation='" + binaryLocation + '\'' +
                '}';
    }

    /**
     * Enumeration of different class types that can be discovered
     */
    public enum ClassType {
        SOURCE_ONLY, // Found only in source files
        BINARY_ONLY, // Found only in compiled class files
        BOTH // Found in both source and binary
    }
}
