package com.analyzer.core.db.repository;

import com.analyzer.core.db.GraphDatabaseConfig;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.entity.NodeTagEntity;
import com.analyzer.core.db.mapper.EdgeMapper;
import com.analyzer.core.db.mapper.NodeMapper;
import com.analyzer.core.db.mapper.TagMapper;
import com.analyzer.core.db.validation.PropertiesValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * High-level repository for graph database operations.
 * Provides convenient methods for working with nodes, edges, properties, and tags.
 */
public class GraphRepository {

    private static final Logger logger = LoggerFactory.getLogger(GraphRepository.class);

    private final GraphDatabaseConfig config;
    private final ObjectMapper jsonMapper;

    public GraphRepository(GraphDatabaseConfig config) {
        this.config = config;
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.findAndRegisterModules();
    }

    // ==================== NODE OPERATIONS ====================

    /**
     * Save a node with all its properties and tags.
     *
     * @param nodeId Node ID (file path or FQN)
     * @param nodeType Node type (java, xml, class, etc.)
     * @param displayLabel Human-readable label
     * @param properties Map of properties
     * @param tags Set of tags
     */
    public void saveNode(String nodeId, String nodeType, String displayLabel,
                         Map<String, Object> properties, Set<String> tags) {
        try (SqlSession session = config.openSession()) {
            // Validate properties
            PropertiesValidator.validate(properties);
            
            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);
            TagMapper tagMapper = session.getMapper(TagMapper.class);

            // Create node with JSON-serialized properties
            String propertiesJson = serializePropertiesToJson(properties);
            GraphNodeEntity node = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson);
            nodeMapper.insertNode(node);

            // Save tags
            if (tags != null && !tags.isEmpty()) {
                List<NodeTagEntity> tagEntities = tags.stream()
                        .map(tag -> new NodeTagEntity(nodeId, tag))
                        .collect(Collectors.toList());

                if (!tagEntities.isEmpty()) {
                    tagMapper.insertTags(tagEntities);
                }
            }

            session.commit();
            logger.debug("Saved node: {} (type: {})", nodeId, nodeType);
        }
    }

    /**
     * Find a node by its ID.
     *
     * @param nodeId The node ID
     * @return GraphNodeEntity or null if not found
     */
    public GraphNodeEntity findNodeById(String nodeId) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findById(nodeId);
        }
    }

    /**
     * Find all nodes of a specific type.
     *
     * @param nodeType The node type
     * @return List of nodes
     */
    public List<GraphNodeEntity> findNodesByType(String nodeType) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findByType(nodeType);
        }
    }

    /**
     * Check if a node exists.
     *
     * @param nodeId The node ID
     * @return true if exists
     */
    public boolean nodeExists(String nodeId) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.exists(nodeId);
        }
    }

    /**
     * Delete a node and all its associated data.
     *
     * @param nodeId The node ID
     */
    public void deleteNode(String nodeId) {
        try (SqlSession session = config.openSession()) {
            // Cascade delete will handle properties, tags, and edges
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            mapper.deleteNode(nodeId);
            session.commit();
            logger.debug("Deleted node: {}", nodeId);
        }
    }

    // ==================== EDGE OPERATIONS ====================

    /**
     * Create an edge between two nodes.
     *
     * @param sourceId Source node ID
     * @param targetId Target node ID
     * @param edgeType Edge type (depends_on, contains, etc.)
     */
    public void createEdge(String sourceId, String targetId, String edgeType) {
        createEdge(sourceId, targetId, edgeType, null);
    }

    /**
     * Create an edge with metadata.
     *
     * @param sourceId Source node ID
     * @param targetId Target node ID
     * @param edgeType Edge type
     * @param metadata Optional metadata as JSON string
     */
    public void createEdge(String sourceId, String targetId, String edgeType, String metadata) {
        try (SqlSession session = config.openSession()) {
            EdgeMapper mapper = session.getMapper(EdgeMapper.class);

            GraphEdgeEntity edge = new GraphEdgeEntity(sourceId, targetId, edgeType);
            edge.setMetadataJson(metadata);

            mapper.insertEdge(edge);
            session.commit();
            logger.debug("Created edge: {} -> {} (type: {})", sourceId, targetId, edgeType);
        }
    }

    /**
     * Find all outgoing edges from a node.
     *
     * @param nodeId Source node ID
     * @return List of edges
     */
    public List<GraphEdgeEntity> findOutgoingEdges(String nodeId) {
        try (SqlSession session = config.openSession()) {
            EdgeMapper mapper = session.getMapper(EdgeMapper.class);
            return mapper.findBySourceId(nodeId);
        }
    }

    /**
     * Find all incoming edges to a node.
     *
     * @param nodeId Target node ID
     * @return List of edges
     */
    public List<GraphEdgeEntity> findIncomingEdges(String nodeId) {
        try (SqlSession session = config.openSession()) {
            EdgeMapper mapper = session.getMapper(EdgeMapper.class);
            return mapper.findByTargetId(nodeId);
        }
    }

    /**
     * Find all edges of a specific type.
     *
     * @param edgeType Edge type
     * @return List of edges
     */
    public List<GraphEdgeEntity> findEdgesByType(String edgeType) {
        try (SqlSession session = config.openSession()) {
            EdgeMapper mapper = session.getMapper(EdgeMapper.class);
            return mapper.findByType(edgeType);
        }
    }

    // ==================== TAG OPERATIONS ====================

    /**
     * Get all tags for a node.
     *
     * @param nodeId The node ID
     * @return Set of tags
     */
    public Set<String> getNodeTags(String nodeId) {
        try (SqlSession session = config.openSession()) {
            TagMapper mapper = session.getMapper(TagMapper.class);
            return mapper.findByNodeId(nodeId).stream()
                    .map(NodeTagEntity::getTag)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Find all nodes with a specific tag.
     *
     * @param tag The tag to search for
     * @return List of node IDs
     */
    public List<String> findNodesByTag(String tag) {
        try (SqlSession session = config.openSession()) {
            TagMapper mapper = session.getMapper(TagMapper.class);
            return mapper.findByTag(tag).stream()
                    .map(NodeTagEntity::getNodeId)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get all unique tags in the database.
     *
     * @return List of unique tags
     */
    public List<String> getAllTags() {
        try (SqlSession session = config.openSession()) {
            TagMapper mapper = session.getMapper(TagMapper.class);
            return mapper.findAllUniqueTags();
        }
    }

    // ==================== PROPERTY OPERATIONS ====================

    /**
     * Get all properties for a node.
     *
     * @param nodeId The node ID
     * @return Map of property key to value
     */
    public Map<String, Object> getNodeProperties(String nodeId) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            GraphNodeEntity node = mapper.findById(nodeId);
            if (node == null || node.getProperties() == null) {
                return Map.of();
            }
            
            try {
                return jsonMapper.readValue(node.getProperties(), 
                    new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                logger.error("Failed to parse properties JSON for node: {}", nodeId, e);
                return Map.of();
            }
        }
    }

    /**
     * Find nodes with a specific property value using JSON path query.
     *
     * @param jsonPath JSON path (e.g., '$.java.fullyQualifiedName')
     * @param value Property value to match
     * @return List of matching nodes
     */
    public List<GraphNodeEntity> findNodesByPropertyValue(String jsonPath, String value) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findByPropertyValue(jsonPath, value);
        }
    }

    // ==================== STATISTICS ====================

    /**
     * Get graph statistics.
     *
     * @return Statistics object
     */
    public GraphStatistics getStatistics() {
        try (SqlSession session = config.openSession()) {
            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);
            EdgeMapper edgeMapper = session.getMapper(EdgeMapper.class);
            TagMapper tagMapper = session.getMapper(TagMapper.class);

            return new GraphStatistics(
                    nodeMapper.countNodes(),
                    edgeMapper.countEdges(),
                    tagMapper.countTags()
            );
        }
    }

    /**
     * Clear all data from the database.
     */
    public void clearAll() {
        try (SqlSession session = config.openSession()) {
            EdgeMapper edgeMapper = session.getMapper(EdgeMapper.class);
            TagMapper tagMapper = session.getMapper(TagMapper.class);
            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);

            // Delete in order (respecting foreign keys)
            edgeMapper.deleteAll();
            tagMapper.deleteAll();
            nodeMapper.deleteAll();

            session.commit();
            logger.info("Cleared all graph data");
        }
    }

    // ==================== HELPER METHODS ====================

    private String serializePropertiesToJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        try {
            // Use Jackson to serialize
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(properties);
        } catch (Exception e) {
            logger.error("Failed to serialize properties to JSON", e);
            return "{}";
        }
    }

    private String determineValueType(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String) {
            String str = (String) value;
            if (str.trim().startsWith("{") || str.trim().startsWith("[")) {
                return "json";
            }
        }
        return "string";
    }

    /**
     * Statistics record for graph metrics.
     */
    public record GraphStatistics(int nodeCount, int edgeCount, int tagCount) {
        @Override
        public String toString() {
            return String.format("Graph Statistics: %d nodes, %d edges, %d tags",
                    nodeCount, edgeCount, tagCount);
        }
    }
}
