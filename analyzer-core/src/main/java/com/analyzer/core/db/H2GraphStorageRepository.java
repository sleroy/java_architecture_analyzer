package com.analyzer.core.db;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.mapper.EdgeMapper;
import com.analyzer.core.db.mapper.NodeMapper;
import com.analyzer.core.db.validation.PropertiesValidator;
import com.analyzer.core.serialization.JsonSerializationService;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * H2 database storage repository for graph data persistence.
 * Handles all H2 database operations for storing and retrieving graph nodes,
 * edges, and tags.
 * This is separate from the in-memory GraphRepository used during analysis.
 */
public class H2GraphStorageRepository {

    private static final Logger logger = LoggerFactory.getLogger(H2GraphStorageRepository.class);

    private final GraphDatabaseSessionManager config;
    private final JsonSerializationService jsonSerializer;

    public H2GraphStorageRepository(GraphDatabaseSessionManager config) {
        this(config, new JsonSerializationService());
    }

    public H2GraphStorageRepository(GraphDatabaseSessionManager config, JsonSerializationService jsonSerializer) {
        this.config = config;
        this.jsonSerializer = jsonSerializer;
    }

    // ==================== NODE OPERATIONS ====================

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
     * Find all nodes in the database.
     *
     * @return List of all nodes
     */
    public List<GraphNodeEntity> findAll() {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findAll();
        }
    }

    /**
     * Find nodes by a single tag (optimized query).
     *
     * @param tag The tag to search for
     * @return List of nodes containing this tag
     */
    public List<GraphNodeEntity> findNodesByTag(String tag) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findByTag(tag);
        }
    }

    /**
     * Find nodes having any of the provided tags (optimized query).
     *
     * @param tags List of tags (OR condition)
     * @return List of nodes containing any of these tags
     */
    public List<GraphNodeEntity> findNodesByAnyTags(List<String> tags) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findByAnyTags(tags);
        }
    }

    /**
     * Find nodes having all of the provided tags (optimized query).
     *
     * @param tags List of tags (AND condition)
     * @return List of nodes containing all of these tags
     */
    public List<GraphNodeEntity> findNodesByAllTags(List<String> tags) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findByAllTags(tags);
        }
    }

    /**
     * Find nodes by type and having any of the provided tags (optimized query).
     *
     * @param nodeType The node type
     * @param tags     List of tags (OR condition)
     * @return List of nodes of this type containing any of these tags
     */
    public List<GraphNodeEntity> findNodesByTypeAndAnyTags(String nodeType, List<String> tags) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findByTypeAndAnyTags(nodeType, tags);
        }
    }

    /**
     * Find nodes by type and having all of the provided tags (optimized query).
     *
     * @param nodeType The node type
     * @param tags     List of tags (AND condition)
     * @return List of nodes of this type containing all of these tags
     */
    public List<GraphNodeEntity> findNodesByTypeAndAllTags(String nodeType, List<String> tags) {
        try (SqlSession session = config.openSession()) {
            NodeMapper mapper = session.getMapper(NodeMapper.class);
            return mapper.findByTypeAndAllTags(nodeType, tags);
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

            return jsonSerializer.deserializeProperties(node.getProperties());
        }
    }

    /**
     * Find nodes with a specific property value using JSON path query.
     *
     * @param jsonPath JSON path (e.g., '$.java.fullyQualifiedName')
     * @param value    Property value to match
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

            // Count nodes with tags from JSON column
            int tagCount = 0;
            List<GraphNodeEntity> nodes = nodeMapper.findAll();
            for (GraphNodeEntity node : nodes) {
                if (node.getTags() != null && !node.getTags().isEmpty() && !"[]".equals(node.getTags())) {
                    tagCount++;
                }
            }

            return new GraphStatistics(
                    nodeMapper.countNodes(),
                    edgeMapper.countEdges(),
                    tagCount);
        }
    }

    /**
     * Clear all data from the database.
     */
    public void clearAll() {
        try (SqlSession session = config.openSession()) {
            EdgeMapper edgeMapper = session.getMapper(EdgeMapper.class);
            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);

            // Delete in order (respecting foreign keys)
            edgeMapper.deleteAll();
            nodeMapper.deleteAll();

            session.commit();
            logger.info("Cleared all graph data");
        }
    }

    /**
     * Save a GraphNode with merge semantics (insert if not exists, update if
     * exists).
     * This method handles nodes, their properties, metrics, and tags.
     *
     * @param node The GraphNode to save
     */
    public void saveNode(GraphNode node) {
        try (SqlSession session = config.openSession()) {
            // Extract node data
            String nodeId = node.getId();
            String nodeType = node.getNodeType();
            String displayLabel = node.getDisplayLabel();
            Map<String, Object> properties = node.getNodeProperties();
            Set<String> tags = node.getTags();

            // Extract metrics separately - DO NOT include in properties
            Map<String, Double> metricsMap = null;
            if (node.getMetrics() != null) {
                metricsMap = node.getMetrics().getAllMetrics();
            }

            // Validate properties
            PropertiesValidator.validate(properties);

            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);

            // Serialize properties, metrics, and tags to JSON
            String propertiesJson = jsonSerializer.serializeProperties(properties);

            String metricsJson = null;
            if (metricsMap != null && !metricsMap.isEmpty()) {
                // Cast Map<String, Double> to Map<String, Object> for serialization
                Map<String, Object> metricsAsObjects = new HashMap<>(metricsMap);
                metricsJson = jsonSerializer.serializeProperties(metricsAsObjects);
            }

            String tagsJson = jsonSerializer.serializeTags(tags);

            // Use merge operation for atomic insert/update
            GraphNodeEntity nodeEntity = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson,
                    metricsJson, tagsJson);
            nodeMapper.mergeNode(nodeEntity);

            session.commit();
            logger.debug("Saved/merged node: {} (type: {}, {} tags, {} metrics)", nodeId, nodeType,
                    tags != null ? tags.size() : 0,
                    metricsMap != null ? metricsMap.size() : 0);
        }
    }

    /**
     * Save a GraphEdge with duplicate prevention.
     * Only creates the edge if it doesn't already exist.
     *
     * @param edge The GraphEdge to save
     */
    public void saveEdge(GraphEdge edge) {
        try (SqlSession session = config.openSession()) {
            EdgeMapper edgeMapper = session.getMapper(EdgeMapper.class);

            // Extract edge data
            String sourceId = edge.getSource().getId();
            String targetId = edge.getTarget().getId();
            String edgeType = edge.getEdgeType();
            Map<String, Object> properties = edge.getProperties();

            // Check if edge already exists
            GraphEdgeEntity existingEdge = edgeMapper.findEdge(sourceId, targetId, edgeType);
            if (existingEdge != null) {
                logger.debug("Edge already exists: {} -> {} (type: {})", sourceId, targetId, edgeType);
                return;
            }

            // Serialize properties to JSON metadata
            String metadataJson = null;
            if (properties != null && !properties.isEmpty()) {
                metadataJson = jsonSerializer.serializeProperties(properties);
            }

            // Create and insert edge
            GraphEdgeEntity edgeEntity = new GraphEdgeEntity(sourceId, targetId, edgeType);
            edgeEntity.setMetadataJson(metadataJson);

            edgeMapper.insertEdge(edgeEntity);
            session.commit();

            logger.debug("Saved edge: {} -> {} (type: {})", sourceId, targetId, edgeType);
        }
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
