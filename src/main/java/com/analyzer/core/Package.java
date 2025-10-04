package com.analyzer.core;

import com.analyzer.resource.ResourceLocation;
import java.util.*;

/**
 * Represents a Java package discovered during analysis.
 * Contains all classes within this package and inspector results.
 */
public class Package {
    private final String packageName;
    private final ResourceLocation sourceLocation;
    private final List<ProjectFile> classes;
    private final Map<String, Object> inspectorResults;

    public Package(String packageName, ResourceLocation sourceLocation) {
        this.packageName = Objects.requireNonNull(packageName, "Package name cannot be null");
        this.sourceLocation = sourceLocation;
        this.classes = new ArrayList<>();
        this.inspectorResults = new HashMap<>();
    }

    public String getPackageName() {
        return packageName;
    }

    public ResourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public List<ProjectFile> getClasses() {
        return Collections.unmodifiableList(classes);
    }

    public void addClass(ProjectFile clazz) {
        if (clazz != null && !classes.contains(clazz)) {
            classes.add(clazz);
        }
    }

    public void addClasses(Collection<ProjectFile> classesToAdd) {
        if (classesToAdd != null) {
            for (ProjectFile clazz : classesToAdd) {
                addClass(clazz);
            }
        }
    }

    public int getClassCount() {
        return classes.size();
    }

    public boolean isEmpty() {
        return classes.isEmpty();
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

    /**
     * Gets the depth of this package (number of dots + 1)
     * For example: "com.analyzer.core" has depth 3
     */
    public int getDepth() {
        if (packageName.isEmpty()) {
            return 0; // Default package
        }
        return packageName.split("\\.").length;
    }

    /**
     * Gets the parent package name.
     * For example: "com.analyzer.core" returns "com.analyzer"
     */
    public String getParentPackageName() {
        if (packageName.isEmpty()) {
            return null; // Default package has no parent
        }
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot == -1) {
            return ""; // Parent is default package
        }
        return packageName.substring(0, lastDot);
    }

    /**
     * Gets the simple name of this package (last component).
     * For example: "com.analyzer.core" returns "core"
     */
    public String getSimpleName() {
        if (packageName.isEmpty()) {
            return ""; // Default package
        }
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot == -1) {
            return packageName; // Top-level package
        }
        return packageName.substring(lastDot + 1);
    }

    /**
     * Checks if this package is a subpackage of the given package.
     */
    public boolean isSubpackageOf(String parentPackage) {
        if (parentPackage == null || parentPackage.isEmpty()) {
            return !packageName.isEmpty(); // All non-default packages are subpackages of default
        }
        return packageName.startsWith(parentPackage + ".");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Package aPackage = (Package) o;
        return Objects.equals(packageName, aPackage.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName);
    }

    @Override
    public String toString() {
        return "Package{" +
                "packageName='" + packageName + '\'' +
                ", sourceLocation='" + sourceLocation + '\'' +
                ", classCount=" + classes.size() +
                '}';
    }
}
