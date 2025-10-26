package com.analyzer.discovery;

import com.analyzer.core.model.Package;
import com.analyzer.core.model.ProjectFile;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine for discovering and building package hierarchy from discovered
 * classes.
 * Creates Package objects and establishes parent-child relationships.
 */
public class PackageDiscoveryEngine {

    private final Map<String, Package> discoveredPackages;

    public PackageDiscoveryEngine() {
        this.discoveredPackages = new ConcurrentHashMap<>();
    }

    /**
     * Build package hierarchy from the discovered classes.
     * 
     * @param discoveredClasses Map of fully qualified class names to ProjectFile
     *                          objects
     * @return Map of package names to Package objects
     */
    public Map<String, Package> discoverPackages(Map<String, ProjectFile> discoveredClasses) {
        // Clear previous results
        discoveredPackages.clear();

        // First pass: create all packages and collect their classes
        for (ProjectFile clazz : discoveredClasses.values()) {
            String packageName = clazz.getStringProperty("packageName", "");

            // Handle default package (empty package name)
            if (packageName.isEmpty()) {
                packageName = "<default>";
            }

            // Create package if it doesn't exist
            Package pkg = discoveredPackages.computeIfAbsent(packageName, this::createPackage);

            // Add class to package
            pkg.addClass(clazz);

            // Create parent packages if they don't exist
            createParentPackages(packageName);
        }

        return new HashMap<>(discoveredPackages);
    }

    /**
     * Create a new Package instance.
     * For URI-based system, we don't set a specific source location for packages
     * as they are logical groupings of classes.
     */
    private Package createPackage(String packageName) {
        return new Package(packageName, null); // Packages don't have a specific source location
    }

    /**
     * Create parent packages recursively if they don't exist.
     */
    private void createParentPackages(String packageName) {
        if (packageName.equals("<default>")) {
            return; // Default package has no parents
        }

        String parentPackageName = getParentPackageName(packageName);
        if (parentPackageName != null && !discoveredPackages.containsKey(parentPackageName)) {
            // Create parent package
            Package parentPackage = createPackage(parentPackageName);
            discoveredPackages.put(parentPackageName, parentPackage);

            // Recursively create grandparent packages
            createParentPackages(parentPackageName);
        }
    }

    /**
     * Get the parent package name for a given package.
     * 
     * @param packageName Full package name (e.g., "com.example.util")
     * @return Parent package name (e.g., "com.example") or null if no parent
     */
    private String getParentPackageName(String packageName) {
        if (packageName.equals("<default>")) {
            return null;
        }

        int lastDot = packageName.lastIndexOf('.');
        if (lastDot == -1) {
            return null; // No parent (top-level package)
        }

        return packageName.substring(0, lastDot);
    }

    /**
     * Get statistics about the package discovery process.
     */
    public PackageStatistics getStatistics() {
        long totalPackages = discoveredPackages.size();

        long emptyPackages = discoveredPackages.values().stream()
                .mapToLong(pkg -> pkg.getClasses().isEmpty() ? 1 : 0)
                .sum();

        OptionalInt maxDepth = discoveredPackages.keySet().stream()
                .filter(name -> !name.equals("<default>"))
                .mapToInt(this::getPackageDepth)
                .max();

        OptionalDouble avgClassesPerPackage = discoveredPackages.values().stream()
                .mapToInt(pkg -> pkg.getClasses().size())
                .average();

        return new PackageStatistics(totalPackages, emptyPackages,
                maxDepth.orElse(0), avgClassesPerPackage.orElse(0.0));
    }

    /**
     * Calculate the depth of a package (number of dots + 1).
     */
    private int getPackageDepth(String packageName) {
        if (packageName.equals("<default>")) {
            return 0;
        }
        return (int) packageName.chars().filter(ch -> ch == '.').count() + 1;
    }

    /**
     * Statistics about the package discovery process.
     */
    public static class PackageStatistics {
        private final long totalPackages;
        private final long emptyPackages;
        private final int maxDepth;
        private final double avgClassesPerPackage;

        public PackageStatistics(long totalPackages, long emptyPackages,
                int maxDepth, double avgClassesPerPackage) {
            this.totalPackages = totalPackages;
            this.emptyPackages = emptyPackages;
            this.maxDepth = maxDepth;
            this.avgClassesPerPackage = avgClassesPerPackage;
        }

        public long getTotalPackages() {
            return totalPackages;
        }

        public long getEmptyPackages() {
            return emptyPackages;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public double getAvgClassesPerPackage() {
            return avgClassesPerPackage;
        }

        @Override
        public String toString() {
            return String.format("Package Statistics: Total=%d, Empty=%d, Max-depth=%d, Avg-classes=%.1f",
                    totalPackages, emptyPackages, maxDepth, avgClassesPerPackage);
        }
    }
}
