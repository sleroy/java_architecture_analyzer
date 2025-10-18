package com.analyzer.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.analyzer.core.graph.GraphNode;

/**
 * Represents a file in a project that has been detected by FileDetectors.
 * Replaces the previous ProjectFile class with a more general file-based
 * approach.
 * Uses a flexible tagging system to store file metadata and analysis results.
 * <p>
 * Supports both filesystem files and JAR-internal files (virtual files).
 * <p>
 * Implements GraphNode interface to participate directly in the analysis graph.
 */
public class ProjectFile implements GraphNode {
    private final Path filePath;
    private final String relativePath;
    private final String fileName;
    private final String fileExtension;
    private final Map<String, Object> tags;
    private final Date discoveredAt;

    // JAR content support
    private final String sourceJarPath; // Path to the JAR containing this file (null for filesystem files)
    private final String jarEntryPath; // Path within the JAR (null for filesystem files)
    private final boolean isVirtual; // True if this file exists inside a JAR

    // Inspector execution tracking for multi-pass algorithm
    @JsonIgnore
    private final Map<String, LocalDateTime> inspectorExecutionTimes = new ConcurrentHashMap<>();
    @JsonIgnore
    private LocalDateTime cachedFileModificationTime;

    /**
     * Constructor for filesystem files
     */
    public ProjectFile(Path filePath, Path projectRoot) {
        this(filePath, projectRoot, null, null);
    }

    /**
     * Constructor for JAR-internal files (virtual files)
     */
    public ProjectFile(Path filePath, Path projectRoot, String sourceJarPath, String jarEntryPath) {
        this(filePath, projectRoot, sourceJarPath, jarEntryPath, new Date());
    }
    
    /**
     * JSON deserialization constructor
     */
    @JsonCreator
    public ProjectFile(@JsonProperty("filePath") String filePathStr,
                      @JsonProperty("relativePath") String relativePath,
                      @JsonProperty("sourceJarPath") String sourceJarPath,
                      @JsonProperty("jarEntryPath") String jarEntryPath,
                      @JsonProperty("discoveredAt") Date discoveredAt,
                      @JsonProperty("tags") Map<String, Object> tags) {
        this.filePath = Paths.get(filePathStr);
        this.relativePath = relativePath;
        this.fileName = this.filePath.getFileName().toString();
        
        String name = fileName;
        int lastDot = name.lastIndexOf('.');
        this.fileExtension = lastDot != -1 ? name.substring(lastDot + 1).toLowerCase() : "";
        
        this.sourceJarPath = sourceJarPath;
        this.jarEntryPath = jarEntryPath;
        this.isVirtual = (sourceJarPath != null);
        this.discoveredAt = discoveredAt != null ? discoveredAt : new Date();
        this.tags = tags != null ? new ConcurrentHashMap<>(tags) : new ConcurrentHashMap<>();
    }
    
    /**
     * Internal constructor with discoveredAt parameter
     */
    private ProjectFile(Path filePath, Path projectRoot, String sourceJarPath, String jarEntryPath, Date discoveredAt) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        Objects.requireNonNull(projectRoot, "Project root cannot be null");

        // Initialize JAR-related fields
        this.sourceJarPath = sourceJarPath;
        this.jarEntryPath = jarEntryPath;
        this.isVirtual = (sourceJarPath != null);

        this.relativePath = projectRoot.relativize(filePath).toString();
        this.fileName = filePath.getFileName().toString();

        // Extract file extension
        String name = fileName;
        int lastDot = name.lastIndexOf('.');
        this.fileExtension = lastDot != -1 ? name.substring(lastDot + 1).toLowerCase() : "";

        this.tags = new ConcurrentHashMap<>();
        this.discoveredAt = discoveredAt;

        // Set basic file metadata as tags
        setTag(InspectorTags.TAG_FILE_NAME, fileName);
        setTag(InspectorTags.TAG_FILE_EXTENSION, fileExtension);
        setTag(InspectorTags.TAG_RELATIVE_PATH, relativePath);
        setTag(InspectorTags.TAG_ABSOLUTE_PATH, filePath.toString());

        // Set JAR-specific metadata if this is a virtual file
        if (isVirtual) {
            setTag(InspectorTags.TAG_JAR_SOURCE_PATH, sourceJarPath);
            setTag(InspectorTags.TAG_JAR_ENTRY_PATH, jarEntryPath);
            setTag(InspectorTags.TAG_JAR_IS_VIRTUAL, true);
        }
    }

    /**
     * Get the absolute file path
     */
    @JsonIgnore
    public Path getFilePath() {
        return filePath;
    }
    
    /**
     * Get the absolute file path as string for JSON serialization
     */
    @JsonProperty("filePath")
    public String getFilePathString() {
        return filePath.toString();
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
     * Check if the file has a specific extension (case-insensitive)
     *
     * @param extension the extension to check (without the dot)
     * @return true if the file has the specified extension
     */
    public boolean hasFileExtension(String extension) {
        if (extension == null) {
            return fileExtension.isEmpty();
        }
        return fileExtension.equalsIgnoreCase(extension);
    }

    /**
     * Get when this file was discovered
     */
    public Date getDiscoveredAt() {
        return new Date(discoveredAt.getTime());
    }

    // ==================== JAR CONTENT SUPPORT ====================

    /**
     * Check if this file is virtual (exists inside a JAR)
     */
    public boolean isVirtual() {
        return isVirtual;
    }

    /**
     * Get the path to the JAR containing this file (null for filesystem files)
     */
    public String getSourceJarPath() {
        return sourceJarPath;
    }

    /**
     * Get the path within the JAR (null for filesystem files)
     */
    public String getJarEntryPath() {
        return jarEntryPath;
    }

    /**
     * Check if this file exists inside a JAR
     */
    public boolean isJarInternal() {
        return isVirtual;
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
     * Check if all specified tags exist
     *
     * @param tagNames array of tag names to check
     * @return true if all tags exist, false otherwise
     */
    public boolean hasAllTags(String... tagNames) {
        if (tagNames == null || tagNames.length == 0) {
            return true; // No dependencies means always satisfied
        }

        for (String tagName : tagNames) {
            if (!hasTag(tagName)) {
                return false;
            }
        }
        return true;
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
    public void addInspectorResult(String tagName, Object result) {
        // Smart merging: replace NA/null/error values with better values
        Object currentValue = tags.get(tagName);
        Object valueToStore = mergeInspectorResults(currentValue, result);
        tags.put(tagName, valueToStore);
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
     * Migration helper: Get class name for Java files (migrated from
     * ProjectFile.getClassName())
     */
    public String getClassName() {
        Object className = getTag(InspectorTags.TAG_JAVA_CLASS_NAME);
        return className instanceof String ? (String) className : null;
    }

    /**
     * Migration helper: Get package name for Java files (migrated from
     * ProjectFile.getPackageName())
     */
    public String getPackageName() {
        Object packageName = getTag(InspectorTags.TAG_JAVA_PACKAGE_NAME);
        return packageName instanceof String ? (String) packageName : "";
    }

    public void setFullQualifiedName(String packageName, String className) {
        if (className != null && !className.isEmpty()) {
            setTag(InspectorTags.TAG_JAVA_CLASS_NAME, className);
        }
        if (packageName != null) {
            setTag(InspectorTags.TAG_JAVA_PACKAGE_NAME, packageName);
        }
        if (className != null && !className.isEmpty()) {
            String fqName = (packageName != null && !packageName.isEmpty())
                    ? packageName + "." + className
                    : className;
            setTag(InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME, fqName);
        }
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

    // ==================== INSPECTOR EXECUTION TRACKING ====================

    /**
     * Mark that an inspector has been executed on this file at the current time.
     * Used by the multi-pass algorithm to track execution state.
     *
     * @param inspectorName the name of the inspector that was executed
     */
    public void markInspectorExecuted(String inspectorName) {
        markInspectorExecuted(inspectorName, LocalDateTime.now());
    }

    /**
     * Mark that an inspector has been executed on this file at a specific time.
     * Used by the multi-pass algorithm to track execution state.
     *
     * @param inspectorName the name of the inspector that was executed
     * @param executionTime when the inspector was executed
     */
    public void markInspectorExecuted(String inspectorName, LocalDateTime executionTime) {
        inspectorExecutionTimes.put(inspectorName, executionTime);
    }

    /**
     * Get the last execution time for a specific inspector.
     *
     * @param inspectorName the name of the inspector
     * @return the last execution time, or empty if never executed
     */
    public Optional<LocalDateTime> getInspectorExecutionTime(String inspectorName) {
        return Optional.ofNullable(inspectorExecutionTimes.get(inspectorName));
    }

    /**
     * Check if an inspector is up to date (execution time is after file
     * modification time).
     * Used by the multi-pass algorithm to determine if re-execution is needed.
     *
     * @param inspectorName the name of the inspector to check
     * @return true if the inspector has been executed since the file was last
     *         modified
     */
    public boolean isInspectorUpToDate(String inspectorName) {
        Optional<LocalDateTime> executionTime = getInspectorExecutionTime(inspectorName);
        if (executionTime.isEmpty()) {
            return false; // Never executed
        }

        LocalDateTime fileModTime = getFileModificationTime();
        return executionTime.get().isAfter(fileModTime) || executionTime.get().isEqual(fileModTime);
    }

    /**
     * Get the file modification time, with caching for performance.
     *
     * @return the file modification time as LocalDateTime
     */
    public LocalDateTime getFileModificationTime() {
        if (cachedFileModificationTime == null) {
            try {
                cachedFileModificationTime = Files.getLastModifiedTime(filePath)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (IOException e) {
                // If we can't read modification time, use current time as fallback
                cachedFileModificationTime = LocalDateTime.now();
            }
        }
        return cachedFileModificationTime;
    }

    /**
     * Clear the cached file modification time. Should be called if the file might
     * have been modified.
     */
    public void clearFileModificationTimeCache() {
        cachedFileModificationTime = null;
    }

    /**
     * Get all inspector execution times.
     *
     * @return unmodifiable map of inspector names to execution times
     */
    public Map<String, LocalDateTime> getAllInspectorExecutionTimes() {
        return Collections.unmodifiableMap(inspectorExecutionTimes);
    }

    /**
     * Check if any inspector has been executed on this file.
     *
     * @return true if at least one inspector has been executed
     */
    public boolean hasAnyInspectorBeenExecuted() {
        return !inspectorExecutionTimes.isEmpty();
    }

    /**
     * Clear all inspector execution tracking. Used for testing or forcing
     * re-execution.
     */
    public void clearInspectorExecutionTimes() {
        inspectorExecutionTimes.clear();
    }

    // ==================== GRAPH NODE INTERFACE IMPLEMENTATION ====================

    @Override
    public String getId() {
        // Use the absolute file path as the unique identifier
        // This ensures that each file has a unique ID in the graph
        return filePath.toString();
    }

    @Override
    public String getNodeType() {
        // Determine node type based on file extension and content analysis
        if (hasTag(InspectorTags.TAG_JAVA_CLASS_NAME)) {
            return "java_class";
        } else if (hasFileExtension("java")) {
            return "java_file";
        } else if (hasFileExtension("class")) {
            return "class_file";
        } else if (hasFileExtension("jar")) {
            return "jar_file";
        } else if (hasFileExtension("xml")) {
            return "xml_file";
        } else if (hasFileExtension("properties")) {
            return "properties_file";
        } else {
            return "file";
        }
    }

    @Override
    public Map<String, Object> getNodeProperties() {
        // Return the tags as node properties for graph analysis
        return getAllTags();
    }

    @Override
    public String getDisplayLabel() {
        // Create a human-readable label for graph visualization
        String className = getClassName();
        if (className != null) {
            String packageName = getPackageName();
            if (packageName != null && !packageName.isEmpty()) {
                return className + " (" + packageName + ")";
            } else {
                return className;
            }
        }

        // For non-Java files, use filename with relative path
        if (relativePath.length() < 50) {
            return relativePath;
        } else {
            // For very long paths, show just the filename
            return fileName;
        }
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
