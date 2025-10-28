package com.analyzer.core.inspector;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.model.Package;
import com.analyzer.core.model.ProjectFile;

/**
 * Enum representing the types of nodes that inspectors can process.
 * This allows for explicit, type-safe filtering of inspectors based on
 * what they analyze, avoiding fragile instanceof checks.
 * 
 * <p>Benefits of this approach:</p>
 * <ul>
 *   <li>Explicit declaration of inspector target type</li>
 *   <li>Easy filtering without reflection or instanceof</li>
 *   <li>Type-safe at compile time</li>
 *   <li>Clear separation of concerns between analysis phases</li>
 * </ul>
 * 
 * @since Phase 7 - Type-Safe Inspector Filtering
 */
public enum InspectorTargetType {
    /**
     * Inspector analyzes ProjectFile objects (Phase 3).
     * These inspectors work on file-level metadata and properties.
     */
    PROJECT_FILE(ProjectFile.class),
    
    /**
     * Inspector analyzes JavaClassNode objects (Phase 4).
     * These inspectors work on class-level metrics and relationships.
     */
    JAVA_CLASS_NODE(JavaClassNode.class),
    
    /**
     * Inspector analyzes Package objects.
     * These inspectors work on package-level aggregations.
     */
    PACKAGE(Package.class),
    
    /**
     * Inspector can analyze any GraphNode type.
     * Use sparingly - prefer specific types for better phase separation.
     */
    ANY(GraphNode.class);
    
    private final Class<? extends GraphNode> targetClass;
    
    InspectorTargetType(Class<? extends GraphNode> targetClass) {
        this.targetClass = targetClass;
    }
    
    /**
     * Gets the Java class represented by this target type.
     * @return the target class
     */
    public Class<? extends GraphNode> getTargetClass() {
        return targetClass;
    }
    
    /**
     * Checks if this target type matches or is compatible with a given class.
     * @param clazz the class to check
     * @return true if compatible, false otherwise
     */
    public boolean isCompatibleWith(Class<?> clazz) {
        return targetClass.isAssignableFrom(clazz);
    }
    
    /**
     * Determines the target type from a class.
     * @param clazz the class to determine the type for
     * @return the corresponding InspectorTargetType, or ANY if no specific match
     */
    public static InspectorTargetType fromClass(Class<?> clazz) {
        if (ProjectFile.class.isAssignableFrom(clazz)) {
            return PROJECT_FILE;
        } else if (JavaClassNode.class.isAssignableFrom(clazz)) {
            return JAVA_CLASS_NODE;
        } else if (Package.class.isAssignableFrom(clazz)) {
            return PACKAGE;
        }
        return ANY;
    }
}
