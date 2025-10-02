package com.analyzer.core;

import com.analyzer.resource.ResourceLocation;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a Java class discovered during analysis.
 * Can be either from source files or compiled class files.
 */
public class Clazz {
    private final String className;
    private final String packageName;
    private final ClassType classType;
    private final ResourceLocation sourceLocation;
    private final ResourceLocation binaryLocation;
    private final Map<String, Object> inspectorResults;

    public Clazz(String className, String packageName, ClassType classType,
            ResourceLocation sourceLocation, ResourceLocation binaryLocation) {
        this.className = Objects.requireNonNull(className, "Class name cannot be null");
        this.packageName = packageName != null ? packageName : "";
        this.classType = Objects.requireNonNull(classType, "Class type cannot be null");
        this.sourceLocation = sourceLocation;
        this.binaryLocation = binaryLocation;
        this.inspectorResults = new HashMap<>();
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getFullyQualifiedName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    public ClassType getClassType() {
        return classType;
    }

    public ResourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public ResourceLocation getBinaryLocation() {
        return binaryLocation;
    }

    public boolean hasSourceCode() {
        return sourceLocation != null;
    }

    public boolean hasBinaryCode() {
        return binaryLocation != null;
    }

    public Map<String, Object> getInspectorResults() {
        return inspectorResults;
    }

    public void addInspectorResult(String inspectorName, Object result) {
        inspectorResults.put(inspectorName, result);
    }

    public Object getInspectorResult(String inspectorName) {
        return inspectorResults.get(inspectorName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Clazz clazz = (Clazz) o;
        return Objects.equals(className, clazz.className) &&
                Objects.equals(packageName, clazz.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, packageName);
    }

    @Override
    public String toString() {
        return "Clazz{" +
                "className='" + className + '\'' +
                ", packageName='" + packageName + '\'' +
                ", classType=" + classType +
                ", sourceLocation='" + sourceLocation + '\'' +
                ", binaryLocation='" + binaryLocation + '\'' +
                '}';
    }

    /**
     * Enumeration of different class types that can be discovered
     */
    public enum ClassType {
        SOURCE_ONLY, // Found only in source files
        BINARY_ONLY, // Found only in compiled class files
        BOTH // Found in both source and binary
    }
}
