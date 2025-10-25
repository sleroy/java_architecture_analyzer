package com.analyzer.core.graph;

import com.analyzer.core.model.ProjectFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for mapping GraphNode classes to their type identifiers.
 * Provides a single source of truth for node type classification.
 * 
 * <p>This registry pattern eliminates the need for hardcoded type strings
 * in individual node implementations and provides a centralized location
 * for managing all node type mappings.</p>
 */
public class NodeTypeRegistry {
    
    private static final Map<Class<? extends GraphNode>, String> TYPE_MAP = new HashMap<>();
    
    // Register all known node types
    static {
        register(ProjectFile.class, "file");
        register(JavaClassNode.class, "java_class");
        // Future node types can be registered here:
        // register(PackageNode.class, "package");
        // register(MethodNode.class, "method");
    }
    
    /**
     * Registers a node class with its type identifier.
     * 
     * @param nodeClass The GraphNode implementation class
     * @param typeId The string identifier for this node type
     */
    public static void register(Class<? extends GraphNode> nodeClass, String typeId) {
        TYPE_MAP.put(nodeClass, typeId);
    }
    
    /**
     * Gets the type identifier for a given node class.
     * 
     * @param nodeClass The GraphNode implementation class
     * @return The type identifier, or "unknown" if not registered
     */
    public static String getTypeId(Class<? extends GraphNode> nodeClass) {
        return TYPE_MAP.getOrDefault(nodeClass, "unknown");
    }
    
    /**
     * Gets the type identifier for a given node instance.
     * 
     * @param node The GraphNode instance
     * @return The type identifier, or "unknown" if not registered
     */
    public static String getTypeId(GraphNode node) {
        if (node == null) {
            return "unknown";
        }
        return getTypeId(node.getClass());
    }
    
    /**
     * Checks if a node class is registered.
     * 
     * @param nodeClass The GraphNode implementation class
     * @return true if the class is registered
     */
    public static boolean isRegistered(Class<? extends GraphNode> nodeClass) {
        return TYPE_MAP.containsKey(nodeClass);
    }
    
    /**
     * Gets all registered node types.
     * 
     * @return Map of class to type identifier
     */
    public static Map<Class<? extends GraphNode>, String> getAllTypes() {
        return new HashMap<>(TYPE_MAP);
    }
    
    /**
     * Finds the node class for a given type identifier.
     * 
     * @param typeId The type identifier to search for
     * @return Optional containing the class if found
     */
    @SuppressWarnings("unchecked")
    public static Optional<Class<? extends GraphNode>> findClassByTypeId(String typeId) {
        return (Optional<Class<? extends GraphNode>>) (Optional<?>) TYPE_MAP.entrySet().stream()
                .filter(entry -> entry.getValue().equals(typeId))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
