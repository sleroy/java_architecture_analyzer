package com.analyzer.core.db;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.graph.InMemoryGraphRepository;
import com.analyzer.core.serialization.JsonSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class H2GraphDatabase implements GraphDatabase {
    private static final Logger logger = LoggerFactory.getLogger(H2GraphDatabase.class);
    private final LoadOptions options;
    private final H2GraphStorageRepository h2Repository;
    private GraphDatabaseSessionManager dbConfig;
    private JsonSerializationService jsonSerializer;

    public H2GraphDatabase(LoadOptions options, JsonSerializationService jsonSerializer) {
        this.options = options;
        this.jsonSerializer = jsonSerializer;
        this.dbConfig = new GraphDatabaseSessionManager();
        this.h2Repository = new H2GraphStorageRepository(dbConfig);
    }

    @Override
    public void load() {
        try {
            // Use database path if provided, otherwise use JDBC URL
            if (options.getDatabasePath() != null) {
                java.nio.file.Path dbPath = options.getDatabasePath();

                // Check if database file exists (H2 adds .mv.db extension)
                java.nio.file.Path dbFile = java.nio.file.Paths.get(dbPath.toString() + ".mv.db");
                boolean dbExists = java.nio.file.Files.exists(dbFile);

                if (!dbExists) {
                    logger.info("Database file not found at: {}", dbFile);
                    logger.info("Creating new empty database...");
                }

                // Initialize database (creates new empty database if it doesn't exist)
                dbConfig.initialize(dbPath);

                if (!dbExists) {
                    logger.info("New empty database created and schema initialized successfully");
                }
            } else if (options.getJdbcUrl() != null) {
                throw new IllegalArgumentException(
                        "Direct JDBC URL initialization not yet supported. Please provide a database path.");
            } else {
                throw new IllegalArgumentException("Either database path or JDBC URL must be provided in LoadOptions");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public GraphRepository snapshot() {
        InMemoryGraphRepository memoryRepo = new InMemoryGraphRepository();
        loadDataIntoMemoryRepository(memoryRepo, options);
        return memoryRepo;
    }

    @Override
    public void persist(GraphRepository graphRepository) {
        for (GraphNode node : graphRepository.getNodes()) {
            h2Repository.saveNode(node);
        }
        for (GraphEdge edge : graphRepository.getAllEdges()) {
            h2Repository.saveEdge(edge);
        }
    }

    /**
     * Loads data from H2 database into an existing GraphRepository.
     * This is useful for incremental analysis where you want to populate
     * an existing repository with previously analyzed data.
     *
     * @param targetRepo The GraphRepository to populate
     * @param options    Configuration options for loading (filters, project root,
     *                   etc.)
     */
    private void loadDataIntoMemoryRepository(
            GraphRepository targetRepo,
            LoadOptions options) {

        // Load nodes with optional filtering
        logger.info("Loading nodes from database...");
        List<GraphNodeEntity> nodeEntities = loadNodeEntities(options);

        // Convert entities to ProjectFile nodes and add to repository
        Map<String, GraphNode> nodeMap = convertAndAddNodes(
                nodeEntities,
                targetRepo,
                options.getProjectRoot());

        logger.info("Loaded {} nodes into repository", nodeMap.size());

        // Load edges with optional filtering
        logger.info("Loading edges from database...");
        List<GraphEdgeEntity> edgeEntities = loadEdgeEntities();

        // Convert edge entities and add to repository
        int edgesAdded = convertAndAddEdges(edgeEntities, nodeMap, targetRepo);

        logger.info("Loaded {} edges into repository", edgesAdded);
    }

    /**
     * Loads node entities from database based on options.
     */
    private List<GraphNodeEntity> loadNodeEntities(
            LoadOptions options) {

        List<GraphNodeEntity> nodeEntities;

        if (options.shouldLoadAllNodes()) {
            // Load all nodes from database
            nodeEntities = h2Repository.findAll();
        } else if (options.getNodeTypeFilters() != null && !options.getNodeTypeFilters().isEmpty()) {
            // Load filtered node types
            nodeEntities = new ArrayList<>();
            for (String nodeType : options.getNodeTypeFilters()) {
                nodeEntities.addAll(h2Repository.findNodesByType(nodeType));
            }
            logger.info("Filtered to {} node types", options.getNodeTypeFilters().size());
        } else {
            // No filter specified - load all
            nodeEntities = h2Repository.findAll();
        }

        return nodeEntities;
    }

    /**
     * Loads edge entities from database based on options.
     */
    private List<GraphEdgeEntity> loadEdgeEntities() {

        List<GraphEdgeEntity> edgeEntities = new ArrayList<>();

        if (options.shouldLoadAllEdges()) {
            // Note: Current H2GraphStorageRepository doesn't have a findAllEdges() method
            // So we fall back to loading by type if edge filters are provided
            if (options.getEdgeTypeFilters() != null && !options.getEdgeTypeFilters().isEmpty()) {
                for (String edgeType : options.getEdgeTypeFilters()) {
                    try {
                        edgeEntities.addAll(h2Repository.findEdgesByType(edgeType));
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
                    edgeEntities.addAll(h2Repository.findEdgesByType(edgeType));
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
    private Map<String, GraphNode> convertAndAddNodes(
            List<GraphNodeEntity> nodeEntities,
            GraphRepository targetRepo,
            java.nio.file.Path projectRoot) {

        Map<String, GraphNode> nodeMap = new HashMap<>();

        for (GraphNodeEntity entity : nodeEntities) {
            try {
                // Use NodeTypeRegistry factory to create the appropriate node type
                // Tags are now loaded automatically by the factory from the JSON column
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
    private int convertAndAddEdges(
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

    public H2GraphStorageRepository getRepository() {
        return h2Repository;
    }
}
