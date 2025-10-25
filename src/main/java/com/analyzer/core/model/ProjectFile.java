package com.analyzer.core.model;

import com.analyzer.core.graph.GraphNode;
import com.analyzer.core.graph.NodeTypeRegistry;
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

public class ProjectFile implements GraphNode {
    private final Path filePath;
    private final String relativePath;
    private final String fileName;
    private final String fileExtension;
    private final Map<String, Object> properties; // Renamed from tags
    private final Set<String> tags; // New field for simple string tags
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
    public ProjectFile(@JsonProperty("filePath") String filePathStr,
                       @JsonProperty("relativePath") String relativePath,
                       @JsonProperty("sourceJarPath") String sourceJarPath,
                       @JsonProperty("jarEntryPath") String jarEntryPath,
                       @JsonProperty("discoveredAt") Date discoveredAt,
                       @JsonProperty("properties") Map<String, Object> properties,
                       @JsonProperty("tags") Set<String> tags) {
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
        this.properties = properties != null ? new ConcurrentHashMap<>(properties) : new ConcurrentHashMap<>();
        this.tags = tags != null ? ConcurrentHashMap.newKeySet() : ConcurrentHashMap.newKeySet();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    private ProjectFile(Path filePath, Path projectRoot, String sourceJarPath, String jarEntryPath, Date discoveredAt) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        Objects.requireNonNull(projectRoot, "Project root cannot be null");

        this.sourceJarPath = sourceJarPath;
        this.jarEntryPath = jarEntryPath;
        this.isVirtual = (sourceJarPath != null);

        this.relativePath = projectRoot.relativize(filePath).toString();
        this.fileName = filePath.getFileName().toString();

        String name = fileName;
        int lastDot = name.lastIndexOf('.');
        this.fileExtension = lastDot != -1 ? name.substring(lastDot + 1).toLowerCase() : "";

        this.properties = new ConcurrentHashMap<>();
        this.tags = ConcurrentHashMap.newKeySet();
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

    @JsonIgnore
    public Path getFilePath() {
        return filePath;
    }

    @JsonProperty("filePath")
    public String getFilePathString() {
        return filePath.toString();
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

    public boolean isJarInternal() {
        return isVirtual;
    }

    // ==================== Property Methods ====================

    public void setProperty(String propertyName, Object value) {
        if (value == null) {
            properties.remove(propertyName);
        } else {
            properties.put(propertyName, value);
        }
    }

    public Object getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    public Object getProperty(String propertyName, Object defaultValue) {
        return properties.getOrDefault(propertyName, defaultValue);
    }

    public String getStringProperty(String propertyName) {
        Object value = getProperty(propertyName);
        return value != null ? value.toString() : null;
    }

    public String getStringProperty(String propertyName, String defaultValue) {
        Object value = getProperty(propertyName);
        return value != null ? value.toString() : defaultValue;
    }

    public Integer getIntegerProperty(String propertyName) {
        Object value = getProperty(propertyName);
        if (value == null)
            return null;
        if (value instanceof Integer)
            return (Integer) value;
        if (value instanceof Number)
            return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer getIntegerProperty(String propertyName, Integer defaultValue) {
        Integer value = getIntegerProperty(propertyName);
        return value != null ? value : defaultValue;
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

    public boolean hasProperty(String propertyName) {
        return properties.containsKey(propertyName);
    }

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

    @JsonProperty("properties")
    public Map<String, Object> getAllProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public void removeProperty(String propertyName) {
        properties.remove(propertyName);
    }

    // ==================== METRIC GETTERS (File-level only) ====================

    public long getFileSize() {
        return getLongProperty(InspectorTags.TAG_METRIC_FILE_SIZE, 0L);
    }

    public int getLinesOfCode() {
        return getIntegerProperty(InspectorTags.TAG_METRIC_LINES_OF_CODE, 0);
    }

    public int getCommentLines() {
        return getIntegerProperty(InspectorTags.TAG_METRIC_COMMENT_LINES, 0);
    }

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

    public void clearFileModificationTimeCache() {
        cachedFileModificationTime = null;
    }

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

    @Override
    public String getId() {
        return filePath.toString();
    }

    @Override
    public String getNodeType() {
        return NodeTypeRegistry.getTypeId(this);
    }

    @Override
    public Map<String, Object> getNodeProperties() {
        return getAllProperties();
    }

    @Override
    public String getDisplayLabel() {
        String fqn = getStringProperty(InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME);
        if (fqn != null) {
            return fqn;
        }
        return relativePath.length() < 50 ? relativePath : fileName;
    }

    @Override
    public void addTag(String tag) {
        this.tags.add(tag);
    }

    @Override
    public boolean hasTag(String tag) {
        return this.tags.contains(tag);
    }

    @Override
    @JsonProperty("tags")
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    @Override
    public void removeTag(String tag) {
        this.tags.remove(tag);
    }

    // ==================== Object Methods ====================

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
                ", properties=" + properties.size() +
                ", tags=" + tags.size() +
                '}';
    }

    public void setFullQualifiedName(String packageName, String className) {

        if (packageName.isEmpty()) {
            setProperty(InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME, className);
        } else {
            setProperty(InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME, packageName + "." + className);
        }

    }

    public boolean hasAllTags(String[] tags) {
        if (tags == null || tags.length == 0)
            return true;
        return Arrays.stream(tags).allMatch(this::hasTag);
    }
}
