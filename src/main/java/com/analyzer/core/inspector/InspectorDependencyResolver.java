package com.analyzer.core.inspector;

import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Framework class that resolves inspector dependencies using the
 * annotation-based system.
 * This class automatically walks the inheritance chain and collects
 * dependencies from
 * all @InspectorDependencies annotations, eliminating the need for manual
 * super.depends() calls.
 * 
 * <p>
 * The resolver supports:
 * </p>
 * <ul>
 * <li>Automatic dependency inheritance through class hierarchy</li>
 * <li>Caching for performance optimization</li>
 * <li>Override and inheritance control via annotation parameters</li>
 * <li>Comprehensive error handling and validation</li>
 * </ul>
 */
public class InspectorDependencyResolver {

    /**
     * Cache for computed dependencies to avoid repeated reflection operations.
     * Key: Inspector class, Value: Computed RequiredTags
     */
    private static final Map<Class<?>, RequiredTags> DEPENDENCY_CACHE = new ConcurrentHashMap<>();

    /**
     * Gets all dependencies for an inspector instance, including inherited ones.
     * This method uses caching for performance and is thread-safe.
     * 
     * @param inspector the inspector instance to get dependencies for
     * @return RequiredTags containing all dependencies (own + inherited)
     * @throws IllegalArgumentException if inspector is null
     */
    public static RequiredTags getDependencies(Inspector<?> inspector) {
        if (inspector == null) {
            throw new IllegalArgumentException("Inspector cannot be null");
        }

        Class<?> inspectorClass = inspector.getClass();
        return DEPENDENCY_CACHE.computeIfAbsent(inspectorClass,
                InspectorDependencyResolver::computeDependencies);
    }

    /**
     * Gets dependencies for an inspector class (not instance).
     * Useful for static analysis and validation.
     * 
     * @param inspectorClass the inspector class to get dependencies for
     * @return RequiredTags containing all dependencies
     * @throws IllegalArgumentException if class is null or not an Inspector
     */
    public static RequiredTags getDependencies(Class<?> inspectorClass) {
        if (inspectorClass == null) {
            throw new IllegalArgumentException("Inspector class cannot be null");
        }

        if (!Inspector.class.isAssignableFrom(inspectorClass)) {
            throw new IllegalArgumentException("Class must implement Inspector interface: " + inspectorClass.getName());
        }

        return DEPENDENCY_CACHE.computeIfAbsent(inspectorClass,
                InspectorDependencyResolver::computeDependencies);
    }

    /**
     * Clears the dependency cache. Useful for testing or when inspectors are
     * dynamically modified.
     */
    public static void clearCache() {
        DEPENDENCY_CACHE.clear();
    }

    /**
     * Evaluates whether an inspector should run based on its dependencies.
     * 
     * @param inspector   the inspector to check
     * @param projectFile the project file to evaluate against
     * @return true if ALL dependencies are satisfied
     */
    public static boolean shouldRunInspector(Inspector<?> inspector, ProjectFile projectFile) {
        if (inspector == null || projectFile == null) {
            return false;
        }

        return shouldRunInspector(inspector.getClass(), projectFile);
    }

    /**
     * Evaluates whether an inspector class should run based on its dependencies.
     * 
     * @param inspectorClass the inspector class to check
     * @param projectFile    the project file to evaluate against
     * @return true if ALL dependencies are satisfied
     */
    public static boolean shouldRunInspector(Class<?> inspectorClass, ProjectFile projectFile) {
        if (inspectorClass == null || projectFile == null) {
            return false;
        }

        // Check tag-based dependencies
        RequiredTags dependencies = getDependencies(inspectorClass);
        for (String dependency : dependencies.toArray()) {
            if (!projectFile.hasTag(dependency)) {
                return false; // Dependency not satisfied
            }
        }

        return true; // All dependencies satisfied
    }

    /**
     * Validates that all dependencies referenced in annotations exist as constants
     * in InspectorTags.
     * 
     * @param inspectorClass the class to validate
     * @throws IllegalArgumentException if any dependency is not found in
     *                                  InspectorTags
     */
    public static void validateDependencies(Class<?> inspectorClass) {
        RequiredTags dependencies = getDependencies(inspectorClass);
        for (String dependency : dependencies.toArray()) {
            if (!isValidInspectorTag(dependency)) {
                throw new IllegalArgumentException(
                        "Invalid dependency '" + dependency + "' in class " + inspectorClass.getName() +
                                ". All dependencies must be constants from InspectorTags class.");
            }
        }
    }

    /**
     * Computes dependencies by walking the inheritance chain and processing
     * annotations.
     * This is the core algorithm that processes @InspectorDependencies annotations.
     * Handles both tag-based dependencies (requires) and inspector-class
     * based dependencies (need).
     * 
     * @param inspectorClass the class to compute dependencies for
     * @return RequiredTags containing all computed dependencies
     */
    private static RequiredTags computeDependencies(Class<?> inspectorClass) {
        List<Class<?>> inheritanceChain = buildInheritanceChain(inspectorClass);
        RequiredTags allDependencies = new RequiredTags();

        // Process inheritance chain from root to leaf for proper dependency
        // accumulation
        // Since we simplified the annotation, we process all dependencies from all
        // classes
        // in the inheritance chain (this maintains backward compatibility)
        for (Class<?> currentClass : inheritanceChain) {
            InspectorDependencies annotation = currentClass.getAnnotation(InspectorDependencies.class);

            if (annotation != null) {
                // Process tag-based dependencies
                for (String dependency : annotation.requires()) {
                    if (dependency != null && !dependency.trim().isEmpty()) {
                        allDependencies.requires(dependency);
                    }
                }

                // Process inspector-class based dependencies
                for (Class<? extends Inspector> inspectorDependency : annotation.need()) {
                    // Convert inspector class dependency to tag dependencies
                    // by finding what tags the dependency inspector produces
                    String[] producedTags = getProducedTags(inspectorDependency);
                    for (String tag : producedTags) {
                        if (tag != null && !tag.trim().isEmpty()) {
                            allDependencies.requires(tag);
                        }
                    }
                }
            }
        }

        return allDependencies;
    }

    /**
     * Builds the inheritance chain from Inspector interface down to the specific
     * class.
     * Returns classes in order from most general to most specific.
     * 
     * @param inspectorClass the leaf class to build chain for
     * @return list of classes from root to leaf
     */
    private static List<Class<?>> buildInheritanceChain(Class<?> inspectorClass) {
        List<Class<?>> chain = new ArrayList<>();
        Class<?> currentClass = inspectorClass;

        // Build chain from leaf to root
        while (currentClass != null && Inspector.class.isAssignableFrom(currentClass)) {
            // Only include classes that are Inspector implementations
            if (Inspector.class.isAssignableFrom(currentClass) && currentClass != Inspector.class) {
                chain.add(currentClass);
            }
            currentClass = currentClass.getSuperclass();
        }

        // Reverse to get root-to-leaf order for proper inheritance processing
        Collections.reverse(chain);
        return chain;
    }

    /**
     * Gets the tags produced by a specific inspector class.
     * Looks at the @InspectorDependencies annotation's produces field
     * to determine what tags the inspector creates when it runs.
     * 
     * @param inspectorClass the inspector class to examine
     * @return array of tags produced by this inspector
     */
    private static String[] getProducedTags(Class<? extends Inspector> inspectorClass) {
        if (inspectorClass == null) {
            return new String[0];
        }

        // Check the inspector class and its inheritance chain for produces declarations
        List<Class<?>> inheritanceChain = buildInheritanceChain(inspectorClass);
        Set<String> allProducedTags = new HashSet<>();

        for (Class<?> currentClass : inheritanceChain) {
            InspectorDependencies annotation = currentClass.getAnnotation(InspectorDependencies.class);
            if (annotation != null) {
                for (String tag : annotation.produces()) {
                    if (tag != null && !tag.trim().isEmpty()) {
                        allProducedTags.add(tag);
                    }
                }
            }
        }

        return allProducedTags.toArray(new String[0]);
    }

    /**
     * Validates that a dependency string is a valid InspectorTags constant.
     * This uses reflection to check if the dependency exists as a public static
     * final String
     * in the InspectorTags class.
     * 
     * @param dependency the dependency string to validate
     * @return true if the dependency is a valid InspectorTags constant
     */
    private static boolean isValidInspectorTag(String dependency) {
        if (dependency == null || dependency.trim().isEmpty()) {
            return false;
        }

        try {
            // Use reflection to check if this is a valid InspectorTags constant
            java.lang.reflect.Field[] fields = InspectorTags.class.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                        java.lang.reflect.Modifier.isFinal(field.getModifiers()) &&
                        field.getType() == String.class) {
                    try {
                        Object value = field.get(null);
                        if (dependency.equals(value)) {
                            return true;
                        }
                    } catch (IllegalAccessException e) {
                        // Skip this field if we can't access it
                    }
                }
            }
        } catch (Exception e) {
            // If reflection fails, be permissive and allow the dependency
            return true;
        }

        return false;
    }
}
