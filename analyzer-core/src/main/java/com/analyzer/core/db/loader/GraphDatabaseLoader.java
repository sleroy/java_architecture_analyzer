package com.analyzer.core.db.loader;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.repository.H2GraphStorageRepository;
import com.analyzer.core.graph.InMemoryGraphRepository;
import com.analyzer.core.serialization.JsonSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for loading graph data from H2 database into GraphRepository
 * instances.
 * Eliminates code duplication across command classes that need to load database
 * content.
 */
public class GraphDatabaseLoader {

    private static final Logger logger = LoggerFactory.getLogger(GraphDatabaseLoader.class);

    /**
     * Loads data from H2 database into a new InMemoryGraphRepository.
     *
     * @param dbRepo  The H2 database repository to read from
     * @param options Configuration options for loading (filters, project root,
     *                etc.)
     * @return A new InMemoryGraphRepository populated with database content
     */
    public static InMemoryGraphRepository loadFromDatabase(
            H2GraphStorageRepository dbRepo,
            LoadOptions options) {
        InMemoryGraphRepository memoryRepo = new InMemoryGraphRepository();
        loadIntoRepository(dbRepo, memoryRepo, options);
        return memoryRepo;
    }

    /**
     * Loads data from H2 database into an existing GraphRepository.
     * This is useful for incremental analysis where you want to populate
     * an existing repository with previously analyzed data.
     *
     * @param dbRepo     The H2 database repository to read from
     * @param targetRepo The GraphRepository to populate
     * @param options    Configuration options for loading (filters, project root,
     *                   etc.)
     */
    public static void loadIntoRepository(
            H2GraphStorageRepository dbRepo,
            GraphRepository targetRepo,
            LoadOptions options) {

        JsonSerializationService jsonSerializer = new JsonSerializationService();

        // Load nodes with optional filtering
        logger.info("Loading nodes from database...");
        List<GraphNodeEntity> nodeEntities = loadNodeEntities(dbRepo, options);

        // Convert entities to ProjectFile nodes and add to repository
        Map<String, GraphNode> nodeMap = convertAndAddNodes(
                nodeEntities,
                targetRepo,
                jsonSerializer,
                options.getProjectRoot());

        logger.info("Loaded {} nodes into repository", nodeMap.size());

        // Load edges with optional filtering
        logger.info("Loading edges from database...");
        List<GraphEdgeEntity> edgeEntities = loadEdgeEntities(dbRepo, options);

        // Convert edge entities and add to repository
        int edgesAdded = convertAndAddEdges(edgeEntities, nodeMap, targetRepo);

        logger.info("Loaded {} edges into repository", edgesAdded);
    }

    /**
     * Loads node entities from database based on options.
     */
    private static List<GraphNodeEntity> loadNodeEntities(
            H2GraphStorageRepository dbRepo,
            LoadOptions options) {

        List<GraphNodeEntity> nodeEntities;

        if (options.shouldLoadAllNodes()) {
            // Load all nodes from database
            nodeEntities = dbRepo.findAll();
        } else if (options.getNodeTypeFilters() != null && !options.getNodeTypeFilters().isEmpty()) {
            // Load filtered node types
            nodeEntities = new ArrayList<>();
            for (String nodeType : options.getNodeTypeFilters()) {
                nodeEntities.addAll(dbRepo.findNodesByType(nodeType));
            }
            logger.info("Filtered to {} node types", options.getNodeTypeFilters().size());
        } else {
            // No filter specified - load all
            nodeEntities = dbRepo.findAll();
        }

        return nodeEntities;
    }

    /**
     * Loads edge entities from database based on options.
     */
    private static List<GraphEdgeEntity> loadEdgeEntities(
            H2GraphStorageRepository dbRepo,
            LoadOptions options) {

        List<GraphEdgeEntity> edgeEntities = new ArrayList<>();

        if (options.shouldLoadAllEdges()) {
            // Note: Current H2GraphStorageRepository doesn't have a findAllEdges() method
            // So we fall back to loading by type if edge filters are provided
            if (options.getEdgeTypeFilters() != null && !options.getEdgeTypeFilters().isEmpty()) {
                for (String edgeType : options.getEdgeTypeFilters()) {
                    try {
                        edgeEntities.addAll(dbRepo.findEdgesByType(edgeType));
                    } catch (Exception e) {
                        logger.debug("No edges of type {} found: {}", edgeType, e.getMessage());
                    }
                }
            } else {
                logger.warn("Cannot load all edges - no edge type filters provided");
            }
        } else if (options.getEdgeTypeFilters() != null && !options.getEdgeTypeFilters().isEmpty()) {
            // Load filtered edge types
            for (String edgeType : options.getEdgeTypeFilters()) {
                try {
                    edgeEntities.addAll(dbRepo.findEdgesByType(edgeType));
                } catch (Exception e) {
                    logger.debug("No edges of type {} found: {}", edgeType, e.getMessage());
                }
            }
            logger.info("Filtered to {} edge types", options.getEdgeTypeFilters().size());
        }

        return edgeEntities;
    }

    /**
     * Converts node entities to GraphNode objects and adds them to the repository.
     * Uses the NodeTypeRegistry factory pattern to support all GraphNode types.
     * Returns a map of node IDs to GraphNode objects for edge creation.
     */
    private static Map<String, GraphNode> convertAndAddNodes(
            List<GraphNodeEntity> nodeEntities,
            GraphRepository targetRepo,
            JsonSerializationService jsonSerializer,
            java.nio.file.Path projectRoot) {

        Map<String, GraphNode> nodeMap = new HashMap<>();

        for (GraphNodeEntity entity : nodeEntities) {
            try {
                // Use NodeTypeRegistry factory to create the appropriate node type
                GraphNode node = com.analyzer.core.graph.NodeTypeRegistry.createFromEntity(
                        entity, jsonSerializer, projectRoot);

                // Add to repository
                targetRepo.addNode(node);
                nodeMap.put(entity.getId(), node);

            } catch (Exception e) {
                logger.warn("Failed to load node {}: {}", entity.getId(), e.getMessage(), e);
            }
        }

        return nodeMap;
    }

    /**
     * Converts edge entities and adds them to the repository.
     * Returns the number of edges successfully added.
     */
    private static int convertAndAddEdges(
            List<GraphEdgeEntity> edgeEntities,
            Map<String, GraphNode> nodeMap,
            GraphRepository targetRepo) {

        int edgesAdded = 0;

        for (GraphEdgeEntity entity : edgeEntities) {
            GraphNode source = nodeMap.get(entity.getSourceId());
            GraphNode target = nodeMap.get(entity.getTargetId());

            if (source != null && target != null) {
                targetRepo.getOrCreateEdge(source, target, entity.getEdgeType());
                edgesAdded++;
            } else {
                logger.debug("Skipping edge - source or target node not found: {} -> {}",
                        entity.getSourceId(), entity.getTargetId());
            }
        }

        return edgesAdded;
    }
}
