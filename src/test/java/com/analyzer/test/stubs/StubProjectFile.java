package com.analyzer.test.stubs;


import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ClassType;
import com.analyzer.core.model.ProjectFile;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stub implementation of ProjectFile for testing purposes.
 * Avoids Mockito dependency and provides simple, controllable behavior.
 * Updated to work with the new ProjectFile tagging system and annotation-based
 * dependencies.
 */
public class StubProjectFile extends ProjectFile {

    public StubProjectFile(String className) {
        this(className, "com.test", ClassType.SOURCE_ONLY);
    }

    public StubProjectFile(String className, String packageName, ClassType classType) {
        this(className, packageName, classType, null, null);
    }

    public StubProjectFile(String className, String packageName, ClassType classType, Object unused,
                           Object binaryLocation) {
        super(createMockPath(className, classType, binaryLocation), Paths.get("/test/project"));

        // Only set Java tags for Java files - allow tests to override for non-Java
        // files
        setupAsJavaFile(className, packageName, classType, binaryLocation);
    }

    /**
     * Set up this stub as a Java file with proper tags for annotation-based
     * dependency system
     */
    private void setupAsJavaFile(String className, String packageName, ClassType classType, Object binaryLocation) {
        // Set up tags using InspectorTags constants for annotation-based dependency
        // system
        if (className != null) {
            setTag(InspectorTags.TAG_JAVA_CLASS_NAME, className);
        }
        if (packageName != null) {
            setTag(InspectorTags.TAG_JAVA_PACKAGE_NAME, packageName);
        }
        if (classType != null) {
            setTag(InspectorTags.TAG_JAVA_CLASS_TYPE, classType.toString());
            // Also set the class_type tag that TypeInspectorASMInspector checks for
            setTag("class_type", classType.toString());
        }

        // Set core language detection tags
        setTag(InspectorTags.TAG_LANGUAGE, InspectorTags.LANGUAGE_JAVA);
        setTag(InspectorTags.TAG_JAVA_DETECTED, true);

        // Set up file type and resource tags based on class type
        if (classType == ClassType.BINARY_ONLY || binaryLocation != null) {
            // Binary file tags
            setTag(InspectorTags.TAG_JAVA_IS_BINARY, true);
            setTag(InspectorTags.JAVA_FORMAT, InspectorTags.FORMAT_BINARY);
            setTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY, true);

            // Legacy tags for backward compatibility
            setTag("resource.hasBinary", true);
        } else {
            // Source file tags
            setTag(InspectorTags.TAG_JAVA_IS_SOURCE, true);
            setTag(InspectorTags.JAVA_FORMAT, InspectorTags.FORMAT_SOURCE);
            setTag(InspectorTags.TAG_JAVA_DETECTED, true);
            setTag(InspectorTags.SOURCE_FILE, true);

            // Legacy tags for backward compatibility
            setTag("resource.hasSource", true);
        }

        // Handle binary location if provided
        if (binaryLocation != null) {
            setTag("resource.binaryLocation", binaryLocation.toString());
            setTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY, true);
        }
    }

    /**
     * Clear Java-specific tags to allow this stub to represent a non-Java file
     */
    public void clearJavaTags() {
        removeTag(InspectorTags.TAG_LANGUAGE);
        removeTag(InspectorTags.TAG_JAVA_DETECTED);
        removeTag(InspectorTags.TAG_JAVA_IS_SOURCE);
        removeTag(InspectorTags.TAG_JAVA_IS_BINARY);
        removeTag(InspectorTags.JAVA_FORMAT);
        removeTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY);
        removeTag(InspectorTags.SOURCE_FILE);
        removeTag(InspectorTags.TAG_JAVA_CLASS_NAME);
        removeTag(InspectorTags.TAG_JAVA_PACKAGE_NAME);
        removeTag(InspectorTags.TAG_JAVA_CLASS_TYPE);
        removeTag("class_type");
        removeTag("resource.hasSource");
        removeTag("resource.hasBinary");
    }

    /**
     * Create a mock file path for testing - creates .class files for binary types
     */
    private static Path createMockPath(String className, ClassType classType, Object binaryLocation) {
        String baseClassName = (className != null ? className.replace('.', '/') : "Test");

        // If it's binary only or has binary location, create .class file path
        if (classType == ClassType.BINARY_ONLY || binaryLocation != null) {
            return Paths.get("/test/project/target/classes/" + baseClassName + ".class");
        } else {
            return Paths.get("/test/project/src/main/java/" + baseClassName + ".java");
        }
    }

    /**
     * Set whether this stub has source code (for backward compatibility)
     */
    public void setHasSourceCode(boolean hasSourceCode) {
        setTag("resource.hasSource", hasSourceCode);
        setTag(InspectorTags.TAG_JAVA_IS_SOURCE, hasSourceCode);
    }

    /**
     * Set whether this stub has binary code (for backward compatibility)
     */
    public void setHasBinaryCode(boolean hasBinaryCode) {
        setTag("resource.hasBinary", hasBinaryCode);
        setTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY, hasBinaryCode);
    }

    /**
     * Set the source location URI (for backward compatibility)
     */
    public void setSourceLocationUri(String uri) {
        setTag("resource.sourceUri", uri);
    }

    /**
     * Set the binary location URI (for backward compatibility)
     */
    public void setBinaryLocationUri(String uri) {
        setTag("resource.binaryUri", uri);
    }

    /**
     * Check if this stub has source code (for backward compatibility with tests)
     */
    public boolean hasSourceCode() {
        Object hasSource = getTag("resource.hasSource");
        return hasSource instanceof Boolean ? (Boolean) hasSource : false;
    }

    /**
     * Check if this stub has binary code (for backward compatibility with tests)
     */
    public boolean hasBinaryCode() {
        Object hasBinary = getTag("resource.hasBinary");
        return hasBinary instanceof Boolean ? (Boolean) hasBinary : false;
    }
}
