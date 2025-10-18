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
     * Cache for complex conditions to avoid repeated reflection operations.
     * Key: Inspector class, Value: Array of TagConditions
     */
    private static final Map<Class<?>, TagCondition[]> COMPLEX_CONDITIONS_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Tag condition evaluator for processing complex dependencies.
     */
    private static final TagConditionEvaluator CONDITION_EVALUATOR = new TagConditionEvaluator();

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

        // Process inheritance chain from root to leaf for proper dependency accumulation
        // Since we simplified the annotation, we process all dependencies from all classes
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
    
    // ========== Complex Condition Support ==========
    
    /**
     * Evaluates whether an inspector should run based on both simple and complex dependencies.
     * This is the main entry point for hybrid dependency checking.
     * 
     * @param inspector the inspector to check
     * @param projectFile the project file to evaluate against
     * @return true if ALL dependencies and conditions are satisfied
     */
    public static boolean shouldRunInspector(Inspector<?> inspector, ProjectFile projectFile) {
        if (inspector == null || projectFile == null) {
            return false;
        }
        
        return shouldRunInspector(inspector.getClass(), projectFile);
    }
    
    /**
     * Evaluates whether an inspector class should run based on both simple and complex dependencies.
     * 
     * @param inspectorClass the inspector class to check
     * @param projectFile the project file to evaluate against
     * @return true if ALL dependencies and conditions are satisfied
     */
    public static boolean shouldRunInspector(Class<?> inspectorClass, ProjectFile projectFile) {
        if (inspectorClass == null || projectFile == null) {
            return false;
        }
        
        // Check simple tag-based dependencies first (faster)
        RequiredTags simpleDependencies = getDependencies(inspectorClass);
        for (String dependency : simpleDependencies.toArray()) {
            if (!projectFile.hasTag(dependency)) {
                return false; // Simple dependency not satisfied
            }
        }
        
        // Check complex conditions
        TagCondition[] complexConditions = getComplexConditions(inspectorClass);
        if (complexConditions.length > 0) {
            return CONDITION_EVALUATOR.evaluateAll(complexConditions, projectFile);
        }
        
        return true; // All conditions satisfied
    }
    
    /**
     * Gets all complex conditions for an inspector class, including inherited ones.
     * This method uses caching for performance and is thread-safe.
     * 
     * @param inspectorClass the inspector class to get conditions for
     * @return array of TagConditions (may be empty but never null)
     */
    public static TagCondition[] getComplexConditions(Class<?> inspectorClass) {
        if (inspectorClass == null) {
            return new TagCondition[0];
        }
        
        return COMPLEX_CONDITIONS_CACHE.computeIfAbsent(inspectorClass,
                InspectorDependencyResolver::computeComplexConditions);
    }
    
    /**
     * Clears all caches (both dependencies and complex conditions).
     * Useful for testing or when inspectors are dynamically modified.
     */
    public static void clearAllCaches() {
        DEPENDENCY_CACHE.clear();
        COMPLEX_CONDITIONS_CACHE.clear();
    }
    
    /**
     * Validates both simple dependencies and complex conditions for an inspector class.
     * 
     * @param inspectorClass the class to validate
     * @throws IllegalArgumentException if any dependency or condition is invalid
     */
    public static void validateAllDependencies(Class<?> inspectorClass) {
        // Validate simple dependencies
        validateDependencies(inspectorClass);
        
        // Validate complex conditions
        TagCondition[] conditions = getComplexConditions(inspectorClass);
        for (TagCondition condition : conditions) {
            validateComplexCondition(condition, inspectorClass);
        }
    }
    
    /**
     * Computes complex conditions by walking the inheritance chain and processing annotations.
     */
    private static TagCondition[] computeComplexConditions(Class<?> inspectorClass) {
        List<Class<?>> inheritanceChain = buildInheritanceChain(inspectorClass);
        List<TagCondition> allConditions = new ArrayList<>();
        
        // Process inheritance chain from root to leaf
        for (Class<?> currentClass : inheritanceChain) {
            InspectorDependencies annotation = currentClass.getAnnotation(InspectorDependencies.class);
            
            if (annotation != null) {
                TagCondition[] conditions = annotation.complexRequires();
                if (conditions != null && conditions.length > 0) {
                    Collections.addAll(allConditions, conditions);
                }
            }
        }
        
        return allConditions.toArray(new TagCondition[0]);
    }
    
    /**
     * Validates a single complex condition for correctness.
     */
    private static void validateComplexCondition(TagCondition condition, Class<?> inspectorClass) {
        if (condition == null) {
            throw new IllegalArgumentException("TagCondition cannot be null in class " + inspectorClass.getName());
        }
        
        if (condition.tag() == null || condition.tag().trim().isEmpty()) {
            throw new IllegalArgumentException("TagCondition tag cannot be null or empty in class " + inspectorClass.getName());
        }
        
        if (condition.value() == null) {
            throw new IllegalArgumentException("TagCondition value cannot be null in class " + inspectorClass.getName() + 
                " for tag '" + condition.tag() + "'");
        }
        
        // Validate operator compatibility with data type
        TagCondition.TagOperator operator = condition.operator();
        TagCondition.TagDataType dataType = condition.dataType();
        
        if (dataType == TagCondition.TagDataType.BOOLEAN && 
            (operator == TagCondition.TagOperator.GREATER_THAN || 
             operator == TagCondition.TagOperator.LESS_THAN ||
             operator == TagCondition.TagOperator.CONTAINS)) {
            throw new IllegalArgumentException("Operator " + operator + " is not compatible with BOOLEAN data type " +
                "in class " + inspectorClass.getName() + " for tag '" + condition.tag() + "'");
        }
        
        // Validate regex patterns if using MATCHES operator
        if (operator == TagCondition.TagOperator.MATCHES) {
            try {
                java.util.regex.Pattern.compile(condition.value());
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern '" + condition.value() + "' " +
                    "in class " + inspectorClass.getName() + " for tag '" + condition.tag() + "': " + e.getMessage());
            }
        }
    }
}
