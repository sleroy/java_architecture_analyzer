package com.analyzer.core.filter;
import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.model.Package;
import com.analyzer.core.model.ProjectFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for filtering packages and classes based on package name
 * patterns.
 * Supports exact package matching and subpackage inclusion.
 */
public class PackageFilter {

    /**
     * Filters packages based on the provided package filter list.
     * If filters is null or empty, all packages are returned.
     * 
     * @param packages       The map of packages to filter
     * @param packageFilters List of package prefixes to include (e.g.,
     *                       "com.example", "org.springframework")
     * @return Filtered map containing only packages that match any of the filters
     */
    public static Map<String, Package> filterPackages(Map<String, Package> packages, List<String> packageFilters) {
        if (packages == null || packages.isEmpty()) {
            return new HashMap<>();
        }

        // If no filters specified, return all packages
        if (packageFilters == null || packageFilters.isEmpty()) {
            return new HashMap<>(packages);
        }

        // Clean and normalize filters (remove empty strings, trim whitespace)
        List<String> cleanFilters = packageFilters.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(filter -> !filter.isEmpty())
                .collect(Collectors.toList());

        if (cleanFilters.isEmpty()) {
            return new HashMap<>(packages);
        }

        Map<String, Package> filteredPackages = new HashMap<>();

        for (Map.Entry<String, Package> entry : packages.entrySet()) {
            String packageName = entry.getKey();
            Package pkg = entry.getValue();

            if (matchesAnyFilter(packageName, cleanFilters)) {
                filteredPackages.put(packageName, pkg);
            }
        }

        return filteredPackages;
    }

    /**
     * Filters classes based on their package names using the provided package
     * filter list.
     * If filters is null or empty, all classes are returned.
     * 
     * @param classes        The map of classes to filter
     * @param packageFilters List of package prefixes to include
     * @return Filtered map containing only classes from packages that match any of
     *         the filters
     */
    public static Map<String, ProjectFile> filterClasses(Map<String, ProjectFile> classes,
            List<String> packageFilters) {
        if (classes == null || classes.isEmpty()) {
            return new HashMap<>();
        }

        // If no filters specified, return all classes
        if (packageFilters == null || packageFilters.isEmpty()) {
            return new HashMap<>(classes);
        }

        // Clean and normalize filters
        List<String> cleanFilters = packageFilters.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(filter -> !filter.isEmpty())
                .collect(Collectors.toList());

        if (cleanFilters.isEmpty()) {
            return new HashMap<>(classes);
        }

        Map<String, ProjectFile> filteredClasses = new HashMap<>();

        for (Map.Entry<String, ProjectFile> entry : classes.entrySet()) {
            ProjectFile clazz = entry.getValue();
            String packageName = clazz.getPackageName();

            if (matchesAnyFilter(packageName, cleanFilters)) {
                filteredClasses.put(entry.getKey(), clazz);
            }
        }

        return filteredClasses;
    }

    /**
     * Checks if a package name matches any of the provided filters.
     * A package matches if:
     * 1. It exactly equals one of the filters
     * 2. It starts with a filter followed by a dot (subpackage match)
     * 
     * @param packageName The package name to check
     * @param filters     List of package filter patterns
     * @return true if the package matches any filter, false otherwise
     */
    public static boolean matchesAnyFilter(String packageName, List<String> filters) {
        if (packageName == null || filters == null || filters.isEmpty()) {
            return filters == null || filters.isEmpty(); // No filtering if no filters
        }

        for (String filter : filters) {
            if (filter == null || filter.trim().isEmpty()) {
                continue;
            }

            String trimmedFilter = filter.trim();

            // Exact match
            if (packageName.equals(trimmedFilter)) {
                return true;
            }

            // Subpackage match: package starts with filter + "."
            if (packageName.startsWith(trimmedFilter + ".")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates filtering statistics for reporting purposes.
     * 
     * @param originalPackageCount Number of packages before filtering
     * @param filteredPackageCount Number of packages after filtering
     * @param originalClassCount   Number of classes before filtering
     * @param filteredClassCount   Number of classes after filtering
     * @param appliedFilters       List of filters that were applied
     * @return FilteringStatistics object with the results
     */
    public static FilteringStatistics createStatistics(
            int originalPackageCount,
            int filteredPackageCount,
            int originalClassCount,
            int filteredClassCount,
            List<String> appliedFilters) {
        return new FilteringStatistics(
                originalPackageCount,
                filteredPackageCount,
                originalClassCount,
                filteredClassCount,
                appliedFilters != null ? new ArrayList<>(appliedFilters) : new ArrayList<>());
    }

    /**
     * Statistics about filtering operations.
     */
    public static class FilteringStatistics {
        private final int originalPackageCount;
        private final int filteredPackageCount;
        private final int originalClassCount;
        private final int filteredClassCount;
        private final List<String> appliedFilters;

        public FilteringStatistics(int originalPackageCount, int filteredPackageCount,
                int originalClassCount, int filteredClassCount,
                List<String> appliedFilters) {
            this.originalPackageCount = originalPackageCount;
            this.filteredPackageCount = filteredPackageCount;
            this.originalClassCount = originalClassCount;
            this.filteredClassCount = filteredClassCount;
            this.appliedFilters = appliedFilters;
        }

        public int getOriginalPackageCount() {
            return originalPackageCount;
        }

        public int getFilteredPackageCount() {
            return filteredPackageCount;
        }

        public int getOriginalClassCount() {
            return originalClassCount;
        }

        public int getFilteredClassCount() {
            return filteredClassCount;
        }

        public List<String> getAppliedFilters() {
            return Collections.unmodifiableList(appliedFilters);
        }

        public int getPackagesRemoved() {
            return originalPackageCount - filteredPackageCount;
        }

        public int getClassesRemoved() {
            return originalClassCount - filteredClassCount;
        }

        public boolean wasFilteringApplied() {
            return appliedFilters != null && !appliedFilters.isEmpty();
        }

        @Override
        public String toString() {
            if (!wasFilteringApplied()) {
                return "No package filtering applied";
            }

            return String.format(
                    "Package filtering applied: %s → Packages: %d→%d (-%d), Classes: %d→%d (-%d)",
                    appliedFilters,
                    originalPackageCount, filteredPackageCount, getPackagesRemoved(),
                    originalClassCount, filteredClassCount, getClassesRemoved());
        }
    }
}
