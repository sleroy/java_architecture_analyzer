package com.analyzer.core.db.loader;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.serialization.JsonSerializationService;

import java.nio.file.Path;
import java.util.Map;

/**
 * Abstract base factory that provides common initialization logic for all
 * GraphNode types.
 * This factory handles the standard operations for loading nodes from database
 * entities:
 * - Deserializing properties from JSON
 * - Deserializing metrics from JSON
 * - Applying properties to the node
 * - Applying metrics to the node
 * 
 * Subclasses only need to implement the createNode() method to provide
 * type-specific
 * node instantiation logic.
 *
 * @param <T> The specific GraphNode type this factory creates
 */
public abstract class DefaultGraphNodeFactory<T extends GraphNode> implements NodeFactory {

    /**
     * Template method that orchestrates the node creation and initialization
     * process.
     * This method is final to ensure consistent behavior across all node types.
     *
     * @param entity         The database entity containing node data
     * @param jsonSerializer Service for deserializing JSON properties
     * @param projectRoot    The project root path for relative path resolution
     * @return A fully initialized GraphNode instance
     * @throws Exception if node creation or initialization fails
     */
    @Override
    public final GraphNode createFromEntity(
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer,
            Path projectRoot) throws Exception {

        // Step 1: Create the type-specific node instance (delegated to subclass)
        T node = createNode(entity, projectRoot);

        // Step 2: Apply common properties from entity to node
        applyProperties(node, entity, jsonSerializer);

        // Step 3: Apply metrics from entity to node
        applyMetrics(node, entity, jsonSerializer);

        // Step 4: Apply tags from entity to node
        applyTags(node, entity, jsonSerializer);

        return node;
    }

    /**
     * Creates a type-specific GraphNode instance from the entity.
     * This is the only method subclasses must implement.
     *
     * @param entity      The database entity containing basic node data
     * @param projectRoot The project root path for relative path resolution
     * @return A new instance of the specific node type
     * @throws Exception if node creation fails
     */
    protected abstract T createNode(GraphNodeEntity entity, Path projectRoot) throws Exception;

    /**
     * Deserializes and applies properties from the entity to the node.
     * Can be overridden by subclasses for specialized property handling.
     *
     * @param node           The node to apply properties to
     * @param entity         The database entity containing properties as JSON
     * @param jsonSerializer Service for deserializing JSON
     */
    protected void applyProperties(
            GraphNode node,
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer) {

        // Deserialize properties from JSON string
        Map<String, Object> properties = jsonSerializer.deserializeProperties(entity.getProperties());

        // Apply each property to the node
        // Note: All GraphNode implementations extend BaseGraphNode which has
        // setProperty
        if (node instanceof com.analyzer.api.graph.BaseGraphNode) {
            com.analyzer.api.graph.BaseGraphNode baseNode = (com.analyzer.api.graph.BaseGraphNode) node;
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                baseNode.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Deserializes and applies metrics from the entity to the node.
     * Can be overridden by subclasses for specialized metrics handling.
     *
     * @param node           The node to apply metrics to
     * @param entity         The database entity containing metrics as JSON
     * @param jsonSerializer Service for deserializing JSON
     */
    protected void applyMetrics(
            GraphNode node,
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer) {

        String metricsMapStr = entity.getMetricsMap();

        // Only process if metrics exist
        if (metricsMapStr != null && !metricsMapStr.isEmpty()) {
            // Deserialize metrics from JSON string
            Map<String, Object> metrics = jsonSerializer.deserializeProperties(metricsMapStr);

            // Apply each metric to the node
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                // Cast to Number as metrics are always numeric values
                Object value = entry.getValue();
                if (value instanceof Number) {
                    node.getMetrics().setMetric(entry.getKey(), (Number) value);
                }
            }
        }
    }

    /**
     * Deserializes and applies tags from the entity to the node.
     * Tags are stored as JSON array in the database.
     *
     * @param node           The node to apply tags to
     * @param entity         The database entity containing tags as JSON
     * @param jsonSerializer Service for deserializing JSON
     */
    protected void applyTags(
            GraphNode node,
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer) {

        String tagsJson = entity.getTags();

        // Only process if tags exist
        if (tagsJson != null && !tagsJson.isEmpty()) {
            // Deserialize tags from JSON string
            java.util.Set<String> tags = jsonSerializer.deserializeTags(tagsJson);

            // Apply each tag to the node
            for (String tag : tags) {
                node.enableTag(tag);
            }
        }
    }
}
