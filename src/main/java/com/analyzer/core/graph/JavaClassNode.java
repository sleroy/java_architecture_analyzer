package com.analyzer.core.graph;

import com.analyzer.core.inspector.InspectorDependencies;

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

    // Node type constant
    public static final String NODE_TYPE = "java_class";

    // Source type constants
    public static final String SOURCE_TYPE_SOURCE = "source";
    public static final String SOURCE_TYPE_BINARY = "binary";

    /**
     * Creates a JavaClassNode with the specified fully qualified name.
     * The fully qualified name is used as both the node ID and as a property.
     * 
     * @param fullyQualifiedName Complete class name including package
     */
    public JavaClassNode(String fullyQualifiedName) {
        super(fullyQualifiedName, NODE_TYPE);
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

    /**
     * Sets a property value for this node.
     *
     * @param key   Property key
     * @param value Property value (null values remove the property)
     */
    public void setProperty(String key, Object value) {
        super.setProperty(key, value);
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
