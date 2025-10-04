package com.analyzer.core;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a file in a project that has been detected by FileDetectors.
 * Replaces the previous ProjectFile class with a more general file-based
 * approach.
 * Uses a flexible tagging system to store file metadata and analysis results.
 */
public class ProjectFile {
    private final Path filePath;
    private final String relativePath;
    private final String fileName;
    private final String fileExtension;
    private final Map<String, Object> tags;
    private final Date discoveredAt;

    public ProjectFile(Path filePath, Path projectRoot) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        Objects.requireNonNull(projectRoot, "Project root cannot be null");

        this.relativePath = projectRoot.relativize(filePath).toString();
        this.fileName = filePath.getFileName().toString();

        // Extract file extension
        String name = fileName;
        int lastDot = name.lastIndexOf('.');
        this.fileExtension = lastDot != -1 ? name.substring(lastDot + 1).toLowerCase() : "";

        this.tags = new ConcurrentHashMap<>();
        this.discoveredAt = new Date();

        // Set basic file metadata as tags
        setTag("fileName", fileName);
        setTag("fileExtension", fileExtension);
        setTag("relativePath", relativePath);
        setTag("absolutePath", filePath.toString());
    }

    /**
     * Get the absolute file path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Get the relative path from project root
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * Get just the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Get the file extension (without the dot)
     */
    public String getFileExtension() {
        return fileExtension;
    }

    /**
     * Get when this file was discovered
     */
    public Date getDiscoveredAt() {
        return new Date(discoveredAt.getTime());
    }

    /**
     * Set a tag value for this file
     */
    public void setTag(String tagName, Object value) {
        if (value == null) {
            tags.remove(tagName);
        } else {
            tags.put(tagName, value);
        }
    }

    /**
     * Get a tag value
     */
    public Object getTag(String tagName) {
        return tags.get(tagName);
    }

    /**
     * Get a tag value with a default if not present
     */
    public Object getTag(String tagName, Object defaultValue) {
        return tags.getOrDefault(tagName, defaultValue);
    }

    /**
     * Get a tag value as a String
     */
    public String getStringTag(String tagName) {
        Object value = getTag(tagName);
        return value != null ? value.toString() : null;
    }

    /**
     * Get a tag value as a String with default
     */
    public String getStringTag(String tagName, String defaultValue) {
        Object value = getTag(tagName);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Get a tag value as an Integer
     */
    public Integer getIntegerTag(String tagName) {
        Object value = getTag(tagName);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get a tag value as an Integer with default
     */
    public Integer getIntegerTag(String tagName, Integer defaultValue) {
        Integer value = getIntegerTag(tagName);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a tag value as a Long
     */
    public Long getLongTag(String tagName) {
        Object value = getTag(tagName);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get a tag value as a Long with default
     */
    public Long getLongTag(String tagName, Long defaultValue) {
        Long value = getLongTag(tagName);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a tag value as a Double
     */
    public Double getDoubleTag(String tagName) {
        Object value = getTag(tagName);
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get a tag value as a Double with default
     */
    public Double getDoubleTag(String tagName, Double defaultValue) {
        Double value = getDoubleTag(tagName);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a tag value as a Boolean
     */
    public Boolean getBooleanTag(String tagName) {
        Object value = getTag(tagName);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String stringValue = value.toString().toLowerCase().trim();
        if ("true".equals(stringValue) || "1".equals(stringValue) || "yes".equals(stringValue)) {
            return true;
        }
        if ("false".equals(stringValue) || "0".equals(stringValue) || "no".equals(stringValue)) {
            return false;
        }
        return null;
    }

    /**
     * Get a tag value as a Boolean with default
     */
    public Boolean getBooleanTag(String tagName, Boolean defaultValue) {
        Boolean value = getBooleanTag(tagName);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a tag value as a List (if the tag contains a List or Collection)
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getListTag(String tagName) {
        Object value = getTag(tagName);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return (List<T>) value;
        }
        if (value instanceof Collection) {
            return new ArrayList<>((Collection<T>) value);
        }
        return null;
    }

    /**
     * Get a tag value as a List with default
     */
    public <T> List<T> getListTag(String tagName, List<T> defaultValue) {
        List<T> value = getListTag(tagName);
        return value != null ? value : defaultValue;
    }

    /**
     * Check if a tag exists
     */
    public boolean hasTag(String tagName) {
        return tags.containsKey(tagName);
    }

    /**
     * Get all tags
     */
    public Map<String, Object> getAllTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Remove a tag
     */
    public void removeTag(String tagName) {
        tags.remove(tagName);
    }

    /**
     * Add inspector result - with smart merging similar to original ProjectFile
     */
    public void addInspectorResult(String inspectorName, Object result) {
        // Smart merging: replace NA/null/error values with better values
        Object currentValue = tags.get(inspectorName);
        Object valueToStore = mergeInspectorResults(currentValue, result);
        tags.put(inspectorName, valueToStore);
    }

    /**
     * Merges two inspector results, preferring non-null, non-NA, non-error values.
     * Uses a priority system: valid values > default values > errors > N/A > null
     * (Migrated from original ProjectFile class)
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
     * (Migrated from original ProjectFile class)
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
     * (Migrated from original ProjectFile class)
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

    /**
     * Get inspector result by name
     */
    public Object getInspectorResult(String inspectorName) {
        return tags.get(inspectorName);
    }

    /**
     * Migration helper: Check if this file represents a Java class (migrated from
     * ProjectFile.hasSourceCode())
     */
    public boolean hasSourceCode() {
        Object hasSource = getTag("resource.hasSource");
        return hasSource instanceof Boolean ? (Boolean) hasSource : false;
    }

    /**
     * Migration helper: Check if this file has binary representation (migrated from
     * ProjectFile.hasBinaryCode())
     */
    public boolean hasBinaryCode() {
        Object hasBinary = getTag("resource.hasBinary");
        return hasBinary instanceof Boolean ? (Boolean) hasBinary : false;
    }

    /**
     * Migration helper: Get class name for Java files (migrated from
     * ProjectFile.getClassName())
     */
    public String getClassName() {
        Object className = getTag("java.className");
        return className instanceof String ? (String) className : null;
    }

    /**
     * Migration helper: Get package name for Java files (migrated from
     * ProjectFile.getPackageName())
     */
    public String getPackageName() {
        Object packageName = getTag("java.packageName");
        return packageName instanceof String ? (String) packageName : "";
    }

    /**
     * Migration helper: Get fully qualified name for Java files (migrated from
     * ProjectFile.getFullyQualifiedName())
     */
    public String getFullyQualifiedName() {
        String className = getClassName();
        String packageName = getPackageName();

        if (className == null) {
            return null;
        }

        return packageName != null && !packageName.isEmpty()
                ? packageName + "." + className
                : className;
    }

    /**
     * Migration helper: Get source location - returns the file path as string
     * (migrated from old ProjectFile.getSourceLocation())
     */
    public String getSourceLocation() {
        return filePath.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ProjectFile that = (ProjectFile) o;
        return Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    @Override
    public String toString() {
        return "ProjectFile{" +
                "relativePath='" + relativePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileExtension='" + fileExtension + '\'' +
                ", tags=" + tags.size() +
                '}';
    }
}
