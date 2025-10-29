package com.analyzer.core.graph;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.loader.GenericNodeFactory;
import com.analyzer.core.db.loader.JavaClassNodeFactory;
import com.analyzer.core.db.loader.NodeFactory;
import com.analyzer.core.db.loader.ProjectFileFactory;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.serialization.JsonSerializationService;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for mapping GraphNode classes to their type identifiers
 * and node factories.
 * Provides a single source of truth for node type classification and creation.
 * 
 * <p>
 * This registry pattern eliminates the need for hardcoded type strings
 * in individual node implementations and provides a centralized location
 * for managing all node type mappings and factory logic.
 * </p>
 */
public class NodeTypeRegistry {

    private static final Map<Class<? extends GraphNode>, String> TYPE_MAP = new HashMap<>();
    private static final Map<String, NodeFactory> FACTORY_MAP = new HashMap<>();

    // Register all known node types with their factories
    static {
        register(ProjectFile.class, "file", new ProjectFileFactory());
        register(JavaClassNode.class, "java_class", new JavaClassNodeFactory());
        // Future node types can be registered here:
        // register(PackageNode.class, "package", new PackageNodeFactory());
        // register(MethodNode.class, "method", new MethodNodeFactory());
    }

    /**
     * Registers a node class with its type identifier and factory.
     * 
     * @param nodeClass The GraphNode implementation class
     * @param typeId    The string identifier for this node type
     * @param factory   The factory for creating instances of this node type
     */
    public static void register(Class<? extends GraphNode> nodeClass, String typeId, NodeFactory factory) {
        TYPE_MAP.put(nodeClass, typeId);
        FACTORY_MAP.put(typeId, factory);
    }

    /**
     * Registers a node type identifier with its factory.
     * Use this when you only need to register a factory without a class mapping.
     * 
     * @param typeId  The string identifier for this node type
     * @param factory The factory for creating instances of this node type
     */
    public static void registerFactory(String typeId, NodeFactory factory) {
        FACTORY_MAP.put(typeId, factory);
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

    /**
     * Creates a GraphNode instance from a database entity using the registered
     * factory.
     * 
     * @param entity         The database entity containing node data
     * @param jsonSerializer Service for deserializing JSON properties
     * @param projectRoot    The project root path for relative path resolution
     * @return A fully constructed GraphNode instance
     * @throws Exception if node creation fails
     */
    public static GraphNode createFromEntity(
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer,
            Path projectRoot) throws Exception {

        NodeFactory factory = FACTORY_MAP.get(entity.getNodeType());
        if (factory == null) {
            // Use generic factory as fallback
            factory = new GenericNodeFactory();
        }
        return factory.createFromEntity(entity, jsonSerializer, projectRoot);
    }
}
