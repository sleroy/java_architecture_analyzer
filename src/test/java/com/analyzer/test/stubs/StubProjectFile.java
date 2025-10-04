package com.analyzer.test.stubs;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.ClassType;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stub implementation of ProjectFile for testing purposes.
 * Avoids Mockito dependency and provides simple, controllable behavior.
 * Updated to work with the new ProjectFile tagging system.
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

        // Set up tags to simulate the old Clazz behavior
        if (className != null) {
            setTag("java.className", className);
        }
        if (packageName != null) {
            setTag("java.packageName", packageName);
        }
        if (classType != null) {
            setTag("java.classType", classType.toString());
            // Also set the class_type tag that TypeInspector checks for
            setTag("class_type", classType.toString());
        }

        // Set up file type tags based on class type
        if (classType == ClassType.BINARY_ONLY || binaryLocation != null) {
            setTag("file.type", "java_binary");
            setTag("language", "java");
            setTag("resource.hasBinary", true);
        } else {
            setTag("file.type", "java_source");
            setTag("language", "java");
            setTag("resource.hasSource", true);
        }

        // Handle binary location if provided
        if (binaryLocation != null) {
            setTag("resource.binaryLocation", binaryLocation.toString());
            setTag("resource.hasBinary", true);
        }
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
    }

    /**
     * Set whether this stub has binary code (for backward compatibility)
     */
    public void setHasBinaryCode(boolean hasBinaryCode) {
        setTag("resource.hasBinary", hasBinaryCode);
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
     * Override hasSourceCode to use tags
     */
    @Override
    public boolean hasSourceCode() {
        Object hasSource = getTag("resource.hasSource");
        return hasSource instanceof Boolean ? (Boolean) hasSource : super.hasSourceCode();
    }

    /**
     * Override hasBinaryCode to use tags
     */
    @Override
    public boolean hasBinaryCode() {
        Object hasBinary = getTag("resource.hasBinary");
        return hasBinary instanceof Boolean ? (Boolean) hasBinary : super.hasBinaryCode();
    }
}
