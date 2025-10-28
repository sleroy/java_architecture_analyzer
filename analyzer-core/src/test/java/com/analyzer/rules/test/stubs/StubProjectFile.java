package com.analyzer.rules.test.stubs;

import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ClassType;
import com.analyzer.core.model.ProjectFile;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stub implementation of ProjectFile for testing purposes.
 * Avoids Mockito dependency and provides simple, controllable behavior.
 * Updated to work with the new ProjectFile property and tag system.
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

        setupAsJavaFile(className, packageName, classType, binaryLocation);
    }

    private void setupAsJavaFile(String className, String packageName, ClassType classType, Object binaryLocation) {
        if (className != null) {
            setProperty(InspectorTags.PROP_JAVA_CLASS_NAME, className);
        }
        if (packageName != null) {
            setProperty(InspectorTags.TAG_JAVA_PACKAGE_NAME, packageName);
        }
        if (classType != null) {
            setProperty(InspectorTags.TAG_JAVA_CLASS_TYPE, classType.toString());
            setProperty("class_type", classType.toString());
        }

        setProperty(InspectorTags.TAG_LANGUAGE, InspectorTags.LANGUAGE_JAVA);
        addTag(InspectorTags.TAG_JAVA_DETECTED);

        if (classType == ClassType.BINARY_ONLY || binaryLocation != null) {
            addTag(InspectorTags.TAG_JAVA_IS_BINARY);
            setProperty(InspectorTags.JAVA_FORMAT, InspectorTags.FORMAT_BINARY);
            addTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY);
            addTag("resource.hasBinary");
        } else {
            addTag(InspectorTags.TAG_JAVA_IS_SOURCE);
            setProperty(InspectorTags.JAVA_FORMAT, InspectorTags.FORMAT_SOURCE);
            addTag(InspectorTags.TAG_SOURCE_FILE);
            addTag("resource.hasSource");
        }

        if (binaryLocation != null) {
            setProperty("resource.binaryLocation", binaryLocation.toString());
            addTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY);
        }
    }

    public void clearJavaTags() {
        removeProperty(InspectorTags.TAG_LANGUAGE);
        getTags().remove(InspectorTags.TAG_JAVA_DETECTED);
        getTags().remove(InspectorTags.TAG_JAVA_IS_SOURCE);
        getTags().remove(InspectorTags.TAG_JAVA_IS_BINARY);
        removeProperty(InspectorTags.JAVA_FORMAT);
        getTags().remove(InspectorTags.RESOURCE_HAS_JAVA_BINARY);
        getTags().remove(InspectorTags.TAG_SOURCE_FILE);
        removeProperty(InspectorTags.PROP_JAVA_CLASS_NAME);
        removeProperty(InspectorTags.TAG_JAVA_PACKAGE_NAME);
        removeProperty(InspectorTags.TAG_JAVA_CLASS_TYPE);
        removeProperty("class_type");
        getTags().remove("resource.hasSource");
        getTags().remove("resource.hasBinary");
    }

    private static Path createMockPath(String className, ClassType classType, Object binaryLocation) {
        String baseClassName = (className != null ? className.replace('.', '/') : "Test");

        if (classType == ClassType.BINARY_ONLY || binaryLocation != null) {
            return Paths.get("/test/project/target/classes/" + baseClassName + ".class");
        } else {
            return Paths.get("/test/project/src/main/java/" + baseClassName + ".java");
        }
    }

    public void setHasSourceCode(boolean hasSourceCode) {
        if (hasSourceCode) {
            addTag("resource.hasSource");
            addTag(InspectorTags.TAG_JAVA_IS_SOURCE);
        }
    }

    public void setHasBinaryCode(boolean hasBinaryCode) {
        if (hasBinaryCode) {
            addTag("resource.hasBinary");
            addTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY);
        }
    }

    public void setSourceLocationUri(String uri) {
        setProperty("resource.sourceUri", uri);
    }

    public void setBinaryLocationUri(String uri) {
        setProperty("resource.binaryUri", uri);
    }

    public boolean hasSourceCode() {
        return hasTag("resource.hasSource");
    }

    public boolean hasBinaryCode() {
        return hasTag("resource.hasBinary");
    }
}
