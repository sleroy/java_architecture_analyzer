package com.analyzer.core.db.loader;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.repository.H2GraphStorageRepository;
import com.analyzer.core.graph.InMemoryGraphRepository;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.serialization.JsonSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
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
     * Converts node entities to ProjectFile objects and adds them to the
     * repository.
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
                // Deserialize properties from JSON
                Map<String, Object> properties = jsonSerializer.deserializeProperties(entity.getProperties());

                // Load metrics from database and add with "metrics." prefix
                String metricsMapStr = entity.getMetricsMap();
                if (metricsMapStr != null && !metricsMapStr.isEmpty()) {
                    Map<String, Object> metrics = jsonSerializer.deserializeProperties(metricsMapStr);
                    // Add metrics back with prefix
                    for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                        properties.put("metrics." + entry.getKey(), entry.getValue());
                    }
                }

                // Create ProjectFile from entity data
                // Check if this is a JAR-internal file by looking for JAR properties
                String sourceJarPath = (String) properties.get("source_jar_path");
                String jarEntryPath = (String) properties.get("jar_entry_path");

                ProjectFile projectFile;
                java.nio.file.Path filePath = Paths.get(entity.getId());

                // Check if paths are from different filesystem types
                boolean isDifferentFilesystem = !filePath.getFileSystem().equals(projectRoot.getFileSystem());

                if (isDifferentFilesystem || sourceJarPath != null) {
                    // For JAR-internal files or different filesystems, use alternative construction
                    projectFile = createProjectFileFromProperties(entity.getId(), projectRoot, properties);
                } else {
                    // Regular filesystem path - use standard constructor
                    projectFile = new ProjectFile(filePath, projectRoot, sourceJarPath, jarEntryPath);
                }

                // Set properties
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    projectFile.setProperty(entry.getKey(), entry.getValue());
                }

                // Add to repository
                targetRepo.addNode(projectFile);
                nodeMap.put(entity.getId(), projectFile);

            } catch (Exception e) {
                logger.warn("Failed to load node {}: {}", entity.getId(), e.getMessage());
            }
        }

        return nodeMap;
    }

    /**
     * Creates a ProjectFile from stored properties when direct Path construction
     * fails.
     * This handles cases like JAR-internal files or paths from different
     * filesystems.
     */
    private static ProjectFile createProjectFileFromProperties(
            String nodeId,
            java.nio.file.Path projectRoot,
            Map<String, Object> properties) {

        // Extract key properties that were stored
        String relativePath = (String) properties.get("relative_path");
        String fileName = (String) properties.get("file_name");
        String fileExtension = (String) properties.get("file_extension");
        String sourceJarPath = (String) properties.get("source_jar_path");
        String jarEntryPath = (String) properties.get("jar_entry_path");

        // Use the JSON creator constructor which doesn't call relativize()
        return new ProjectFile(
                nodeId,
                relativePath != null ? relativePath : nodeId,
                fileName != null ? fileName : new java.io.File(nodeId).getName(),
                fileExtension != null ? fileExtension : "",
                new java.util.Date(),
                sourceJarPath,
                jarEntryPath,
                sourceJarPath != null,
                new java.util.HashSet<>(), // tags will be set separately
                new java.util.HashMap<>() // properties will be set separately
        );
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
