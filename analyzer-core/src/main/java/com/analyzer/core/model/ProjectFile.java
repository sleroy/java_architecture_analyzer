package com.analyzer.core.model;

import com.analyzer.api.graph.BaseGraphNode;
import com.analyzer.core.inspector.InspectorTags;
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

public class ProjectFile extends BaseGraphNode {
    private final Path filePath;
    private final String relativePath;
    private final String fileName;
    private final String fileExtension;
    private final Date discoveredAt;

    // JAR content support
    private final String sourceJarPath;
    private final String jarEntryPath;
    private final boolean isVirtual;

    @JsonIgnore
    private final Map<String, LocalDateTime> inspectorExecutionTimes = new ConcurrentHashMap<>();
    @JsonIgnore
    private LocalDateTime cachedFileModificationTime;

    public ProjectFile(Path filePath, Path projectRoot) {
        this(filePath, projectRoot, null, null);
    }

    public ProjectFile(Path filePath, Path projectRoot, String sourceJarPath, String jarEntryPath) {
        this(filePath, projectRoot, sourceJarPath, jarEntryPath, new Date());
    }

    @JsonCreator
    private ProjectFile(
            @JsonProperty("filePath") String filePathStr,
            @JsonProperty("relativePath") String relativePath,
            @JsonProperty("fileName") String fileName,
            @JsonProperty("fileExtension") String fileExtension,
            @JsonProperty("discoveredAt") Date discoveredAt,
            @JsonProperty("sourceJarPath") String sourceJarPath,
            @JsonProperty("jarEntryPath") String jarEntryPath,
            @JsonProperty("virtual") boolean isVirtual,
            @JsonProperty("tags") Set<String> tags,
            @JsonProperty("allProperties") Map<String, Object> allProperties) {
        super(Objects.requireNonNull(filePathStr, "File path cannot be null"), "file");

        this.filePath = Paths.get(filePathStr);
        this.relativePath = relativePath != null ? relativePath : "";
        this.fileName = fileName != null ? fileName : "";
        this.fileExtension = fileExtension != null ? fileExtension : "";
        this.discoveredAt = discoveredAt != null ? discoveredAt : new Date();
        this.sourceJarPath = sourceJarPath;
        this.jarEntryPath = jarEntryPath;
        this.isVirtual = isVirtual;

        // Restore tags from deserialization
        if (tags != null) {
            tags.forEach(this::addTag);
        }

        // Restore properties from deserialization
        if (allProperties != null) {
            allProperties.forEach(this::setProperty);
        }
    }

    private ProjectFile(Path filePath, Path projectRoot, String sourceJarPath, String jarEntryPath, Date discoveredAt) {
        super(Objects.requireNonNull(filePath, "File path cannot be null").toString(), "file");
        Objects.requireNonNull(projectRoot, "Project root cannot be null");

        this.filePath = filePath;
        this.sourceJarPath = sourceJarPath;
        this.jarEntryPath = jarEntryPath;
        this.isVirtual = (sourceJarPath != null);

        this.relativePath = projectRoot.relativize(filePath).toString();
        this.fileName = filePath.getFileName().toString();

        String name = fileName;
        int lastDot = name.lastIndexOf('.');
        this.fileExtension = lastDot != -1 ? name.substring(lastDot + 1).toLowerCase() : "";

        this.discoveredAt = discoveredAt;

        // Set basic file metadata as properties
        setProperty(InspectorTags.TAG_FILE_NAME, fileName);
        setProperty(InspectorTags.TAG_FILE_EXTENSION, fileExtension);
        setProperty(InspectorTags.TAG_RELATIVE_PATH, relativePath);
        setProperty(InspectorTags.TAG_ABSOLUTE_PATH, filePath.toString());

        if (isVirtual) {
            setProperty(InspectorTags.TAG_JAR_SOURCE_PATH, sourceJarPath);
            setProperty(InspectorTags.TAG_JAR_ENTRY_PATH, jarEntryPath);
            setProperty(InspectorTags.TAG_JAR_IS_VIRTUAL, true);
        }
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public boolean hasFileExtension(String extension) {
        if (extension == null) {
            return fileExtension.isEmpty();
        }
        return fileExtension.equalsIgnoreCase(extension);
    }

    public Date getDiscoveredAt() {
        return new Date(discoveredAt.getTime());
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public String getSourceJarPath() {
        return sourceJarPath;
    }

    public String getJarEntryPath() {
        return jarEntryPath;
    }

    @JsonIgnore
    public boolean isJarInternal() {
        return isVirtual;
    }

    // ==================== Property Methods ====================
    // Note: setProperty() and basic getProperty() inherited from BaseGraphNode

    public String getStringProperty(String propertyName) {
        return getStringProperty(propertyName, null);
    }

    public Integer getIntegerProperty(String propertyName) {
        return getIntProperty(propertyName, null);
    }

    public Integer getIntegerProperty(String propertyName, Integer defaultValue) {
        return getIntProperty(propertyName, defaultValue);
    }

    private Integer getIntProperty(String propertyName, Integer defaultValue) {
        Object value = getProperty(propertyName);
        if (value == null)
            return defaultValue;
        if (value instanceof Integer)
            return (Integer) value;
        if (value instanceof Number)
            return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Long getLongProperty(String propertyName) {
        Object value = getProperty(propertyName);
        if (value == null)
            return null;
        if (value instanceof Long)
            return (Long) value;
        if (value instanceof Number)
            return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long getLongProperty(String propertyName, Long defaultValue) {
        Long value = getLongProperty(propertyName);
        return value != null ? value : defaultValue;
    }

    public Double getDoubleProperty(String propertyName) {
        Object value = getProperty(propertyName);
        if (value == null)
            return null;
        if (value instanceof Double)
            return (Double) value;
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Double getDoubleProperty(String propertyName, Double defaultValue) {
        Double value = getDoubleProperty(propertyName);
        return value != null ? value : defaultValue;
    }

    public Boolean getBooleanProperty(String propertyName) {
        Object value = getProperty(propertyName);
        if (value == null)
            return null;
        if (value instanceof Boolean)
            return (Boolean) value;
        String stringValue = value.toString().toLowerCase().trim();
        if ("true".equals(stringValue) || "1".equals(stringValue) || "yes".equals(stringValue))
            return true;
        if ("false".equals(stringValue) || "0".equals(stringValue) || "no".equals(stringValue))
            return false;
        return null;
    }

    public Boolean getBooleanProperty(String propertyName, Boolean defaultValue) {
        Boolean value = getBooleanProperty(propertyName);
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getListProperty(String propertyName) {
        Object value = getProperty(propertyName);
        if (value == null)
            return null;
        if (value instanceof List)
            return (List<T>) value;
        if (value instanceof Collection)
            return new ArrayList<>((Collection<T>) value);
        return null;
    }

    public <T> List<T> getListProperty(String propertyName, List<T> defaultValue) {
        List<T> value = getListProperty(propertyName);
        return value != null ? value : defaultValue;
    }

    // hasProperty() inherited from BaseGraphNode

    public boolean hasAllProperties(String... propertyNames) {
        if (propertyNames == null || propertyNames.length == 0)
            return true;
        for (String propertyName : propertyNames) {
            if (!hasProperty(propertyName)) {
                return false;
            }
        }
        return true;
    }

    @JsonProperty("allProperties")
    public Map<String, Object> getAllProperties() {
        return getNodeProperties();
    }

    @JsonProperty("allProperties")
    public void setAllProperties(Map<String, Object> props) {
        if (props != null) {
            props.forEach(this::setProperty);
        }
    }

    public void removeProperty(String propertyName) {
        setProperty(propertyName, null);
    }

    // ==================== METRIC GETTERS (File-level only) ====================

    @JsonIgnore
    public long getFileSize() {
        return getLongProperty(InspectorTags.TAG_METRIC_FILE_SIZE, 0L);
    }

    @JsonIgnore
    public int getLinesOfCode() {
        return getIntegerProperty(InspectorTags.TAG_METRIC_LINES_OF_CODE, 0);
    }

    @JsonIgnore
    public int getCommentLines() {
        return getIntegerProperty(InspectorTags.TAG_METRIC_COMMENT_LINES, 0);
    }

    @JsonIgnore
    public int getBlankLines() {
        return getIntegerProperty(InspectorTags.TAG_METRIC_BLANK_LINES, 0);
    }

    // ==================== INSPECTOR EXECUTION TRACKING ====================

    public void markInspectorExecuted(String inspectorName) {
        markInspectorExecuted(inspectorName, LocalDateTime.now());
    }

    public void markInspectorExecuted(String inspectorName, LocalDateTime executionTime) {
        inspectorExecutionTimes.put(inspectorName, executionTime);
    }

    public Optional<LocalDateTime> getInspectorExecutionTime(String inspectorName) {
        return Optional.ofNullable(inspectorExecutionTimes.get(inspectorName));
    }

    public boolean isInspectorUpToDate(String inspectorName) {
        Optional<LocalDateTime> executionTime = getInspectorExecutionTime(inspectorName);
        if (executionTime.isEmpty())
            return false;
        LocalDateTime fileModTime = getFileModificationTime();
        return executionTime.get().isAfter(fileModTime) || executionTime.get().isEqual(fileModTime);
    }

    @JsonIgnore
    public LocalDateTime getFileModificationTime() {
        if (cachedFileModificationTime == null) {
            try {
                cachedFileModificationTime = Files.getLastModifiedTime(filePath)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (IOException e) {
                cachedFileModificationTime = LocalDateTime.now();
            }
        }
        return cachedFileModificationTime;
    }

    @JsonIgnore
    public void clearFileModificationTimeCache() {
        cachedFileModificationTime = null;
    }

    @JsonIgnore
    public Map<String, LocalDateTime> getAllInspectorExecutionTimes() {
        return Collections.unmodifiableMap(inspectorExecutionTimes);
    }

    public boolean hasAnyInspectorBeenExecuted() {
        return !inspectorExecutionTimes.isEmpty();
    }

    public void clearInspectorExecutionTimes() {
        inspectorExecutionTimes.clear();
    }

    // ==================== GRAPH NODE INTERFACE IMPLEMENTATION ====================
    // getId(), getNodeType(), getNodeProperties(), addTag(), hasTag(), getTags(),
    // removeTag()
    // inherited from BaseGraphNode

    @Override
    @JsonIgnore
    public String getDisplayLabel() {
        String fqn = getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME);
        if (fqn != null) {
            return fqn;
        }
        return relativePath.length() < 50 ? relativePath : fileName;
    }

    // ==================== Object Methods ====================
    // equals(), hashCode() inherited from BaseGraphNode (based on ID)

    @Override
    public String toString() {
        return "ProjectFile{" +
                "relativePath='" + relativePath + '\'' +
                ", properties=" + getNodeProperties().size() +
                ", tags=" + getTags().size() +
                '}';
    }

    public void setFullQualifiedName(String packageName, String className) {

        if (packageName.isEmpty()) {
            setProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME, className);
        } else {
            setProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME, packageName + "." + className);
        }

    }

}
