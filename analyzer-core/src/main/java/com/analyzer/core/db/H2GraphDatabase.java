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
    private final GraphDatabaseSessionManager dbConfig;
    private final JsonSerializationService jsonSerializer;

    public H2GraphDatabase(final LoadOptions options, final JsonSerializationService jsonSerializer) {
        this.options = options;
        this.jsonSerializer = jsonSerializer;
        dbConfig = new GraphDatabaseSessionManager();
        h2Repository = new H2GraphStorageRepository(dbConfig);
    }

    @Override
    public void load() {
        try {
            // Use database path if provided, otherwise use JDBC URL
            if (options.getDatabasePath() != null) {
                final java.nio.file.Path dbPath = options.getDatabasePath();

                // Check if database file exists (H2 adds .mv.db extension)
                final java.nio.file.Path dbFile = java.nio.file.Paths.get(dbPath.toString() + ".mv.db");
                final boolean dbExists = java.nio.file.Files.exists(dbFile);

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
        } catch (final Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public GraphRepository snapshot() {
        final InMemoryGraphRepository memoryRepo = new InMemoryGraphRepository();
        loadDataIntoMemoryRepository(memoryRepo, options);
        return memoryRepo;
    }

    @Override
    public void persist(final GraphRepository graphRepository) {
        for (final GraphNode node : graphRepository.getNodes()) {
            h2Repository.saveNode(node);
        }
        for (final GraphEdge edge : graphRepository.getAllEdges()) {
            h2Repository.saveEdge(edge);
        }
    }

    /**
     * Loads data from H2 database into an existing GraphRepository.
     * This is useful for incremental analysis where you want to populate
     * an existing repository with previously analyzed data.
     *
     * Uses SessionManagedRepository to ensure the SqlSession remains open during
     * the entire operation. This prevents H2 CLOB lazy-loading issues where CLOB
     * data is streamed on demand and requires an active connection.
     *
     * @param targetRepo The GraphRepository to populate
     * @param options    Configuration options for loading (filters, project root,
     *                   etc.)
     */
    private void loadDataIntoMemoryRepository(
            final GraphRepository targetRepo,
            final LoadOptions options) {

        // Use SessionManagedRepository to keep session open for entire operation.
        // This prevents H2 CLOB lazy-loading issues where connection is closed
        // before CLOB data is fully read.
        try (final SessionManagedRepository repo = createSessionManagedRepository()) {
            // Load nodes with optional filtering
            logger.info("Loading nodes from database...");
            final List<GraphNodeEntity> nodeEntities = loadNodeEntities(repo, options);

            // Convert entities to ProjectFile nodes and add to repository
            final Map<String, GraphNode> nodeMap = convertAndAddNodes(
                    nodeEntities,
                    targetRepo,
                    options.getProjectRoot());

            logger.info("Loaded {} nodes into repository", nodeMap.size());

            // Load edges with optional filtering
            logger.info("Loading edges from database...");
            final List<GraphEdgeEntity> edgeEntities = loadEdgeEntities(repo);

            // Convert edge entities and add to repository
            final int edgesAdded = convertAndAddEdges(edgeEntities, nodeMap, targetRepo);

            logger.info("Loaded {} edges into repository", edgesAdded);

            // Repository (and its session) will be closed here, after all data
            // has been converted
        }
    }

    /**
     * Loads node entities from database based on options.
     * Uses the provided SessionManagedRepository to keep the connection open for
     * CLOB data access.
     */
    private List<GraphNodeEntity> loadNodeEntities(
            final SessionManagedRepository repo,
            final LoadOptions options) {

        final List<GraphNodeEntity> nodeEntities;

        if (options.shouldLoadAllNodes()) {
            // Load all nodes from database
            nodeEntities = repo.findAllNodes();
        } else if (options.getNodeTypeFilters() != null && !options.getNodeTypeFilters().isEmpty()) {
            // Load filtered node types
            nodeEntities = new ArrayList<>();
            for (final String nodeType : options.getNodeTypeFilters()) {
                nodeEntities.addAll(repo.findNodesByType(nodeType));
            }
            logger.info("Filtered to {} node types", options.getNodeTypeFilters().size());
        } else {
            // No filter specified - load all
            nodeEntities = repo.findAllNodes();
        }

        return nodeEntities;
    }

    /**
     * Loads edge entities from database based on options.
     * Uses the provided SessionManagedRepository to keep the connection open for
     * CLOB data access.
     */
    private List<GraphEdgeEntity> loadEdgeEntities(final SessionManagedRepository repo) {

        final List<GraphEdgeEntity> edgeEntities = new ArrayList<>();

        if (options.shouldLoadAllEdges()) {
            if (options.getEdgeTypeFilters() != null && !options.getEdgeTypeFilters().isEmpty()) {
                for (final String edgeType : options.getEdgeTypeFilters()) {
                    try {
                        edgeEntities.addAll(repo.findEdgesByType(edgeType));
                    } catch (final Exception e) {
                        logger.debug("No edges of type {} found: {}", edgeType, e.getMessage());
                    }
                }
            } else {
                edgeEntities.addAll(repo.findAllEdges());
            }
        } else if (null != options.getEdgeTypeFilters() && !options.getEdgeTypeFilters().isEmpty()) {
            // Load filtered edge types
            for (final String edgeType : options.getEdgeTypeFilters()) {
                try {
                    edgeEntities.addAll(repo.findEdgesByType(edgeType));
                } catch (final Exception e) {
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
            final List<GraphNodeEntity> nodeEntities,
            final GraphRepository targetRepo,
            final java.nio.file.Path projectRoot) {

        final Map<String, GraphNode> nodeMap = new HashMap<>();

        for (final GraphNodeEntity entity : nodeEntities) {
            try {
                // Use NodeTypeRegistry factory to create the appropriate node type
                // Tags are now loaded automatically by the factory from the JSON column
                final GraphNode node = com.analyzer.core.graph.NodeTypeRegistry.createFromEntity(
                        entity, jsonSerializer, projectRoot);

                // Add to repository
                targetRepo.addNode(node);
                nodeMap.put(entity.getId(), node);

            } catch (final Exception e) {
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
            final List<GraphEdgeEntity> edgeEntities,
            final Map<String, GraphNode> nodeMap,
            final GraphRepository targetRepo) {

        int edgesAdded = 0;

        for (final GraphEdgeEntity entity : edgeEntities) {
            final GraphNode source = nodeMap.get(entity.getSourceId());
            final GraphNode target = nodeMap.get(entity.getTargetId());

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

    /**
     * Create a session-managed repository for operations that need to access CLOB
     * fields.
     * The caller is responsible for closing the repository to release the database
     * session.
     * 
     * <p>
     * This is the recommended way to access the repository when loading large
     * datasets
     * from the database, as it prevents H2 CLOB lazy-loading issues by keeping the
     * SqlSession open until all data has been processed.
     * 
     * <p>
     * <b>Usage:</b>
     * 
     * <pre>{@code
     * try (SessionManagedRepository repo = database.createSessionManagedRepository()) {
     *     List<GraphNodeEntity> nodes = repo.findAllNodes();
     *     // Process nodes - CLOBs are accessible here
     *     convertToGraphNodes(nodes);
     * } // Session automatically closed
     * }</pre>
     *
     * @return A new SessionManagedRepository wrapping a SqlSession
     * @see SessionManagedRepository
     */
    public SessionManagedRepository createSessionManagedRepository() {
        final org.apache.ibatis.session.SqlSession session = dbConfig.openSession();
        return new SessionManagedRepository(h2Repository, session);
    }
}
