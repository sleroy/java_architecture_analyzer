package com.analyzer.core.inspector;

/**
 * Centralized constants for all inspector tag names used throughout the
 * application.
 * This class prevents tag name duplication and ensures consistency across all
 * inspectors.
 *
 * <p>
 * All inspectors should use these constants instead of hardcoded strings to
 * maintain
 * consistency and enable easy refactoring of tag names.
 * </p>
 *
 * <p>
 * Tag naming conventions:
 * <ul>
 * <li>Use dot notation for hierarchical organization (e.g.,
 * "java.className")</li>
 * <li>Use camelCase for the constant name</li>
 * <li>Group related tags together with comments</li>
 * </ul>
 * </p>
 */
public final class InspectorTags {

    public static final String TAG_JAVA_CLASSLOADER = "java.from.classloader";
    /**
     * Tag for Java class name (without package)
     */
    public static final String TAG_JAVA_CLASS_NAME = "java.className";

    // ==================== JAVA LANGUAGE TAGS ====================
    /**
     * Tag for Java package name
     */
    public static final String TAG_JAVA_PACKAGE_NAME = "java.packageName";
    /**
     * Tag for Java class type (SOURCE_ONLY, BINARY_ONLY, etc.)
     */
    public static final String TAG_JAVA_CLASS_TYPE = "java.classType";
    /**
     * Tag for Java fully qualified name
     */
    public static final String TAG_JAVA_FULLY_QUALIFIED_NAME = "java.fullyQualifiedName";
    /**
     * Tag indicating if this is a Java source file
     */
    public static final String TAG_JAVA_IS_SOURCE = "java.is_source";
    /**
     * Tag indicating if this is a Java binary file
     */
    public static final String TAG_JAVA_IS_BINARY = "java.is_binary";
    /**
     * Tag for Java file format (source, binary)
     */
    public static final String JAVA_FORMAT = "java.format";
    /**
     * Tag indicating Java language was detected
     */
    public static final String TAG_JAVA_DETECTED = "java.detected";
    /**
     * Tag indicating if Java binary is available for this resource
     */
    public static final String RESOURCE_HAS_JAVA_BINARY = "resource.has_java_binary";

    // ==================== FILE METADATA TAGS ====================
    public static final String TAG_JAVA_FORMAT = "java.format";
    /**
     * Tag for file name
     */
    public static final String TAG_FILE_NAME = "fileName";
    /**
     * Tag for file extension
     */
    public static final String TAG_FILE_EXTENSION = "fileExtension";
    /**
     * Tag for file type
     */
    public static final String TAG_FILE_TYPE = "fileType";
    /**
     * Tag for relative path from project root
     */
    public static final String TAG_RELATIVE_PATH = "relativePath";
    /**
     * Tag for absolute file path
     */
    public static final String TAG_ABSOLUTE_PATH = "absolutePath";
    /**
     * Tag for source JAR path (for virtual files)
     */
    public static final String TAG_JAR_SOURCE_PATH = "jar.sourceJarPath";

    // ==================== JAR METADATA TAGS ====================
    /**
     * Tag for JAR entry path (for virtual files)
     */
    public static final String TAG_JAR_ENTRY_PATH = "jar.entryPath";
    /**
     * Tag indicating if file is virtual (inside JAR)
     */
    public static final String TAG_JAR_IS_VIRTUAL = "jar.isVirtual";
    /**
     * Tag for programming language
     */
    public static final String TAG_LANGUAGE = "language";

    // ==================== FILE TYPE AND LANGUAGE TAGS ====================
    /**
     * Tag indicating this is a source file
     */
    public static final String SOURCE_FILE = "source_file";
    /**
     * Tag for processing errors
     */
    public static final String PROCESSING_ERROR = "processing.error";

    // ==================== PROCESSING AND ERROR TAGS ====================
    /**
     * Common language value: Java
     */
    public static final String LANGUAGE_JAVA = "java";

    // ==================== COMMON TAG VALUES ====================
    /**
     * Common format value: source
     */
    public static final String FORMAT_SOURCE = "source";
    /**
     * Common format value: binary
     */
    public static final String FORMAT_BINARY = "binary";

    // Prevent instantiation
    private InspectorTags() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

}
