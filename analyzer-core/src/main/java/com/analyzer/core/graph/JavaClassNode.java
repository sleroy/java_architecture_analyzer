package com.analyzer.core.graph;

import com.analyzer.core.inspector.InspectorDependencies;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph node representing a Java class discovered through source code or
 * bytecode analysis.
 * Extends BaseGraphNode to provide class-specific properties and behavior.
 * 
 * <p>
 * Key Properties:
 * </p>
 * <ul>
 * <li>Project File ID - Links to the ProjectFile that contains this class</li>
 * <li>Simple Name - Class name without package qualification</li>
 * <li>Package Name - Package containing this class</li>
 * <li>Fully Qualified Name - Complete class name including package</li>
 * <li>Class Type - Whether this is a class, interface, enum, etc.</li>
 * <li>Source Type - Whether discovered from source code or binary</li>
 * </ul>
 */
public class JavaClassNode extends BaseGraphNode {

    // Property keys for class-specific data
    public static final String PROP_PROJECT_FILE_ID = "projectFileId";
    public static final String PROP_SIMPLE_NAME = "simpleName";
    public static final String PROP_PACKAGE_NAME = "packageName";
    public static final String PROP_FULLY_QUALIFIED_NAME = "fullyQualifiedName";
    public static final String PROP_CLASS_TYPE = "classType";
    public static final String PROP_SOURCE_TYPE = "sourceType";
    public static final String PROP_SOURCE_FILE_PATH = "sourceFilePath";
    public static final String PROP_METHOD_COUNT = "methodCount";
    public static final String PROP_FIELD_COUNT = "fieldCount";
    public static final String PROP_CYCLOMATIC_COMPLEXITY = "cyclomaticComplexity";
    public static final String PROP_WEIGHTED_METHODS = "weightedMethods";
    public static final String PROP_AFFERENT_COUPLING = "afferentCoupling";
    public static final String PROP_EFFERENT_COUPLING = "efferentCoupling";

    // Source type constants
    public static final String SOURCE_TYPE_SOURCE = "source";
    public static final String SOURCE_TYPE_BINARY = "binary";

    // Inspector execution tracking for convergence detection
    @JsonIgnore
    private final Map<String, LocalDateTime> inspectorExecutionTimes = new ConcurrentHashMap<>();
    @JsonIgnore
    private LocalDateTime lastModified = LocalDateTime.now();

    /**
     * Creates a JavaClassNode with the specified fully qualified name.
     * The fully qualified name is used as both the node ID and as a property.
     * 
     * @param fullyQualifiedName Complete class name including package
     */
    public JavaClassNode(String fullyQualifiedName) {
        super(fullyQualifiedName, NodeTypeRegistry.getTypeId(JavaClassNode.class));
        setProperty(PROP_FULLY_QUALIFIED_NAME, fullyQualifiedName);

        // Extract and set simple name and package name
        int lastDotIndex = fullyQualifiedName.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            setProperty(PROP_PACKAGE_NAME, fullyQualifiedName.substring(0, lastDotIndex));
            setProperty(PROP_SIMPLE_NAME, fullyQualifiedName.substring(lastDotIndex + 1));
        } else {
            setProperty(PROP_PACKAGE_NAME, ""); // Default package
            setProperty(PROP_SIMPLE_NAME, fullyQualifiedName);
        }
    }

    /**
     * Gets the project file ID that contains this class.
     * 
     * @return Project file ID, or null if not set
     */
    public String getProjectFileId() {
        return getStringProperty(PROP_PROJECT_FILE_ID, null);
    }

    /**
     * Sets the project file ID that contains this class.
     * 
     * @param projectFileId Project file identifier
     */
    public void setProjectFileId(String projectFileId) {
        setProperty(PROP_PROJECT_FILE_ID, projectFileId);
    }

    /**
     * Gets the simple class name (without package).
     * 
     * @return Simple class name
     */
    public String getSimpleName() {
        return getStringProperty(PROP_SIMPLE_NAME, "");
    }

    /**
     * Gets the package name containing this class.
     * 
     * @return Package name, or empty string for default package
     */
    public String getPackageName() {
        return getStringProperty(PROP_PACKAGE_NAME, "");
    }

    /**
     * Gets the fully qualified class name.
     * 
     * @return Complete class name including package
     */
    public String getFullyQualifiedName() {
        return getStringProperty(PROP_FULLY_QUALIFIED_NAME, getId());
    }

    /**
     * Gets the class type (class, interface, enum, annotation, record).
     * 
     * @return Class type, or "class" as default
     */
    public String getClassType() {
        return getStringProperty(PROP_CLASS_TYPE, "class");
    }

    /**
     * Sets the class type.
     * 
     * @param classType Type of class (class, interface, enum, annotation, record)
     */
    public void setClassType(String classType) {
        setProperty(PROP_CLASS_TYPE, classType);
    }

    /**
     * Gets how this class was discovered (source or binary analysis).
     * 
     * @return Source type
     */
    public String getSourceType() {
        return getStringProperty(PROP_SOURCE_TYPE, SOURCE_TYPE_SOURCE);
    }

    /**
     * Sets how this class was discovered.
     * 
     * @param sourceType SOURCE_TYPE_SOURCE or SOURCE_TYPE_BINARY
     */
    public void setSourceType(String sourceType) {
        setProperty(PROP_SOURCE_TYPE, sourceType);
    }

    /**
     * Gets the source file path if available.
     * 
     * @return Source file path or null
     */
    public String getSourceFilePath() {
        return getStringProperty(PROP_SOURCE_FILE_PATH, null);
    }

    /**
     * Sets the source file path.
     * 
     * @param sourceFilePath Path to the source file containing this class
     */
    public void setSourceFilePath(String sourceFilePath) {
        setProperty(PROP_SOURCE_FILE_PATH, sourceFilePath);
    }

    public int getMethodCount() {
        return getIntProperty(PROP_METHOD_COUNT, 0);
    }

    public void setMethodCount(int methodCount) {
        setProperty(PROP_METHOD_COUNT, methodCount);
    }

    public int getFieldCount() {
        return getIntProperty(PROP_FIELD_COUNT, 0);
    }

    public void setFieldCount(int fieldCount) {
        setProperty(PROP_FIELD_COUNT, fieldCount);
    }

    public int getCyclomaticComplexity() {
        return getIntProperty(PROP_CYCLOMATIC_COMPLEXITY, 0);
    }

    public void setCyclomaticComplexity(int cyclomaticComplexity) {
        setProperty(PROP_CYCLOMATIC_COMPLEXITY, cyclomaticComplexity);
    }

    public int getWeightedMethods() {
        return getIntProperty(PROP_WEIGHTED_METHODS, 0);
    }

    public void setWeightedMethods(int weightedMethods) {
        setProperty(PROP_WEIGHTED_METHODS, weightedMethods);
    }

    public int getAfferentCoupling() {
        return getIntProperty(PROP_AFFERENT_COUPLING, 0);
    }

    public void setAfferentCoupling(int afferentCoupling) {
        setProperty(PROP_AFFERENT_COUPLING, afferentCoupling);
    }

    public int getEfferentCoupling() {
        return getIntProperty(PROP_EFFERENT_COUPLING, 0);
    }

    public void setEfferentCoupling(int efferentCoupling) {
        setProperty(PROP_EFFERENT_COUPLING, efferentCoupling);
    }

    /**
     * Checks if this class is in the default package.
     * 
     * @return true if in default package (no package declaration)
     */
    public boolean isInDefaultPackage() {
        String packageName = getPackageName();
        return packageName == null || packageName.trim().isEmpty();
    }

    /**
     * Checks if this class was discovered from source code analysis.
     * 
     * @return true if discovered from source code
     */
    public boolean isFromSource() {
        return SOURCE_TYPE_SOURCE.equals(getSourceType());
    }

    /**
     * Checks if this class was discovered from binary analysis.
     * 
     * @return true if discovered from binary code
     */
    public boolean isFromBinary() {
        return SOURCE_TYPE_BINARY.equals(getSourceType());
    }

    // ==================== INSPECTOR EXECUTION TRACKING ====================

    /**
     * Marks that an inspector has been executed on this class node.
     * Updates lastModified timestamp to trigger re-analysis in dependent
     * inspectors.
     * 
     * @param inspectorName Name of the inspector that executed
     */
    public void markInspectorExecuted(String inspectorName) {
        markInspectorExecuted(inspectorName, LocalDateTime.now());
    }

    /**
     * Marks that an inspector has been executed on this class node at a specific
     * time.
     * Does NOT update lastModified - that should only happen when the node's data
     * actually changes.
     * 
     * @param inspectorName Name of the inspector that executed
     * @param executionTime Time when the inspector executed
     */
    public void markInspectorExecuted(String inspectorName, LocalDateTime executionTime) {
        inspectorExecutionTimes.put(inspectorName, executionTime);
        // REMOVED: lastModified = LocalDateTime.now();
        // lastModified should only be updated when node data changes, not when an
        // inspector runs
    }

    /**
     * Gets the time when a specific inspector was last executed on this node.
     * 
     * @param inspectorName Name of the inspector
     * @return Optional containing execution time, or empty if inspector hasn't run
     */
    public Optional<LocalDateTime> getInspectorExecutionTime(String inspectorName) {
        return Optional.ofNullable(inspectorExecutionTimes.get(inspectorName));
    }

    /**
     * Checks if an inspector's results are up-to-date for this node.
     * An inspector is up-to-date if it has executed after the node's last
     * modification.
     * 
     * @param inspectorName Name of the inspector to check
     * @return true if inspector results are current, false otherwise
     */
    public boolean isInspectorUpToDate(String inspectorName) {
        Optional<LocalDateTime> executionTime = getInspectorExecutionTime(inspectorName);
        if (executionTime.isEmpty()) {
            return false;
        }
        return executionTime.get().isAfter(lastModified) || executionTime.get().isEqual(lastModified);
    }

    /**
     * Gets the time this node was last modified.
     * 
     * @return Last modification timestamp
     */
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    /**
     * Gets all inspector execution times for this node.
     * 
     * @return Unmodifiable map of inspector names to execution times
     */
    public Map<String, LocalDateTime> getAllInspectorExecutionTimes() {
        return Collections.unmodifiableMap(inspectorExecutionTimes);
    }

    /**
     * Checks if any inspector has been executed on this node.
     * 
     * @return true if at least one inspector has run
     */
    public boolean hasAnyInspectorBeenExecuted() {
        return !inspectorExecutionTimes.isEmpty();
    }

    /**
     * Clears all inspector execution times. Used for testing or reset scenarios.
     */
    public void clearInspectorExecutionTimes() {
        inspectorExecutionTimes.clear();
    }

    @Override
    public String getDisplayLabel() {
        String simpleName = getSimpleName();
        String classType = getClassType();
        String sourceType = getSourceType();

        if (isInDefaultPackage()) {
            return String.format("%s (%s, %s)", simpleName, classType, sourceType);
        } else {
            return String.format("%s (%s, %s, %s)", simpleName, classType, sourceType, getPackageName());
        }
    }

    /**
     * Creates a JavaClassNode from analysis results with all properties set.
     * 
     * @param fullyQualifiedName Complete class name
     * @param classType          Type of class (class, interface, etc.)
     * @param sourceType         How it was discovered (source or binary)
     * @param projectFileId      Associated project file ID
     * @param sourceFilePath     Path to source file (if available)
     * @return Configured JavaClassNode instance
     */
    public static JavaClassNode create(String fullyQualifiedName, String classType,
            String sourceType, String projectFileId, String sourceFilePath) {
        JavaClassNode node = new JavaClassNode(fullyQualifiedName);
        node.setClassType(classType);
        node.setSourceType(sourceType);
        node.setProjectFileId(projectFileId);
        if (sourceFilePath != null) {
            node.setSourceFilePath(sourceFilePath);
        }
        return node;
    }
}
