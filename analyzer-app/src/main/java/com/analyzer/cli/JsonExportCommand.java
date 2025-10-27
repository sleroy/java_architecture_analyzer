package com.analyzer.cli;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.core.db.GraphDatabaseConfig;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.export.ProjectSerializer;
import com.analyzer.core.graph.InMemoryGraphRepository;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.serialization.JsonSerializationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * JSON Export command implementation.
 * Loads project data from the H2 database and exports it to JSON format.
 */
@Command(name = "json_export", description = "Export project data from database to JSON format")
public class JsonExportCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(JsonExportCommand.class);

    @Option(names = {
            "--project" }, description = "Path to the project directory containing the database", required = true)
    private String projectPath;

    @Option(names = {
            "--json-output" }, description = "Output directory for JSON files (relative or absolute path). Default: <project>/.analysis/json")
    private String jsonOutputPath;

    @Option(names = {
            "--node-types" }, description = "Comma-separated list of node types to export (e.g., java,xml). If not specified, all types are exported.", split = ",")
    private List<String> nodeTypeFilters;

    @Option(names = {
            "--edge-types" }, description = "Comma-separated list of edge types to export (e.g., depends_on,contains). If not specified, all types are exported.", split = ",")
    private List<String> edgeTypeFilters;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting JSON export from database...");

        // Validate parameters
        if (!validateParameters()) {
            return 1;
        }

        // Resolve project path (handle both relative and absolute)
        Path projectDir = resolveProjectPath(projectPath);

        // Resolve output path (handle both relative and absolute)
        Path outputDir = resolveOutputPath(projectDir, jsonOutputPath);

        logger.info("Configuration:");
        logger.info("  Project path: {}", projectDir);
        logger.info("  JSON output: {}", outputDir);
        logger.info("  Node type filters: {}", nodeTypeFilters != null ? nodeTypeFilters : "all");
        logger.info("  Edge type filters: {}", edgeTypeFilters != null ? edgeTypeFilters : "all");

        try {
            // Check if database exists
            Path dbPath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.GRAPH_DB_NAME + ".mv.db");
            if (!Files.exists(dbPath)) {
                logger.error("Database not found at: {}", dbPath);
                logger.error("Please run the 'inventory' command first to create the database.");
                return 1;
            }

            // Initialize database connection (H2 adds .mv.db automatically, so use base
            // path)
            Path dbBasePath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.GRAPH_DB_NAME);
            logger.info("Loading database from: {}", dbPath);
            GraphDatabaseConfig dbConfig = new GraphDatabaseConfig();
            dbConfig.initialize(dbBasePath);

            // Create H2 database repository to load data
            com.analyzer.core.db.repository.GraphRepository dbRepository = new com.analyzer.core.db.repository.GraphRepository(
                    dbConfig);

            // Get statistics
            var stats = dbRepository.getStatistics();
            logger.info("Database loaded: {}", stats);

            // Load data from H2 into in-memory repository
            GraphRepository inMemoryRepo = loadDataFromDatabase(dbRepository, nodeTypeFilters, edgeTypeFilters);

            // Create minimal project object for serialization
            Project project = createMinimalProject(projectDir, inMemoryRepo);

            // Export to JSON using ProjectSerializer
            File outputFile = outputDir.toFile();
            ProjectSerializer serializer = new ProjectSerializer(outputFile, inMemoryRepo);

            logger.info("Exporting to JSON...");
            serializer.serialize(project);

            // Get final statistics
            logger.info("Export completed successfully!");
            logger.info("JSON data written to: {}", outputDir.toAbsolutePath());
            logger.info("Exported: {} nodes, {} edges", inMemoryRepo.getNodeCount(), inMemoryRepo.getEdgeCount());

            return 0;
        } catch (Exception e) {
            logger.error("Error during JSON export: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Resolves the project path, handling both relative and absolute paths.
     */
    private Path resolveProjectPath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }

    /**
     * Resolves the output path, handling both relative and absolute paths.
     * If outputPath is null, uses default location.
     */
    private Path resolveOutputPath(Path projectDir, String outputPath) {
        if (outputPath == null) {
            // Default: <project>/.analysis/json
            return projectDir.resolve(AnalysisConstants.ANALYSIS_DIR).resolve("json");
        }

        Path p = Paths.get(outputPath);
        if (p.isAbsolute()) {
            return p;
        }
        // Relative path is resolved against current working directory
        return Paths.get(System.getProperty("user.dir")).resolve(outputPath).normalize();
    }

    /**
     * Load data from H2 database into an in-memory GraphRepository.
     * Applies optional node and edge type filters.
     */
    private GraphRepository loadDataFromDatabase(
            com.analyzer.core.db.repository.GraphRepository dbRepo,
            List<String> nodeTypeFilters,
            List<String> edgeTypeFilters) {

        InMemoryGraphRepository memoryRepo = new InMemoryGraphRepository();
        JsonSerializationService jsonSerializer = new JsonSerializationService();

        // Load nodes with optional filtering
        logger.info("Loading nodes from database...");
        List<GraphNodeEntity> nodeEntities;

        if (nodeTypeFilters != null && !nodeTypeFilters.isEmpty()) {
            nodeEntities = new ArrayList<>();
            for (String nodeType : nodeTypeFilters) {
                nodeEntities.addAll(dbRepo.findNodesByType(nodeType));
            }
            logger.info("Filtered to {} node types", nodeTypeFilters.size());
        } else {
            // Load all nodes - we need to get them by querying different types
            // For now, load common types (this should ideally query all types from DB)
            nodeEntities = new ArrayList<>();
            for (String type : Arrays.asList("java", "xml", "properties", "yaml", "json", "file")) {
                nodeEntities.addAll(dbRepo.findNodesByType(type));
            }
        }

        // Convert entities to ProjectFile nodes and add to memory repository
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
                ProjectFile projectFile = new ProjectFile(
                        java.nio.file.Paths.get(entity.getId()),
                        java.nio.file.Paths.get(".") // Placeholder parent path
                );

                // Set properties
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    projectFile.setProperty(entry.getKey(), entry.getValue());
                }

                // Add to memory repository
                memoryRepo.addNode(projectFile);
                nodeMap.put(entity.getId(), projectFile);

            } catch (Exception e) {
                logger.warn("Failed to load node {}: {}", entity.getId(), e.getMessage());
            }
        }

        logger.info("Loaded {} nodes into memory", nodeMap.size());

        // Load edges with optional filtering
        logger.info("Loading edges from database...");
        List<GraphEdgeEntity> edgeEntities;

        if (edgeTypeFilters != null && !edgeTypeFilters.isEmpty()) {
            edgeEntities = new ArrayList<>();
            for (String edgeType : edgeTypeFilters) {
                edgeEntities.addAll(dbRepo.findEdgesByType(edgeType));
            }
            logger.info("Filtered to {} edge types", edgeTypeFilters.size());
        } else {
            // This would ideally load all edges, but we don't have a method for that
            // in the current db repository API
            edgeEntities = new ArrayList<>();
            logger.warn("Cannot load all edges - GraphRepository.findEdgesByType needs all types");
        }

        // Convert edge entities and add to memory repository
        int edgesAdded = 0;
        for (GraphEdgeEntity entity : edgeEntities) {
            GraphNode source = nodeMap.get(entity.getSourceId());
            GraphNode target = nodeMap.get(entity.getTargetId());

            if (source != null && target != null) {
                memoryRepo.getOrCreateEdge(source, target, entity.getEdgeType());
                edgesAdded++;
            } else {
                logger.debug("Skipping edge - source or target node not found: {} -> {}",
                        entity.getSourceId(), entity.getTargetId());
            }
        }

        logger.info("Loaded {} edges into memory", edgesAdded);
        return memoryRepo;
    }

    /**
     * Creates a minimal Project object from database metadata for serialization
     * purposes.
     */
    private Project createMinimalProject(Path projectDir, GraphRepository repository) {
        // Get project name from directory
        String projectName = projectDir.getFileName().toString();

        // Create project with basic info (Path first, then String name)
        Project project = new Project(projectDir, projectName);

        // The ProjectSerializer will read nodes from the GraphRepository,
        // so we don't need to populate the project's file list

        return project;
    }

    private boolean validateParameters() {
        // Project path must be specified (already enforced by @Option required=true)
        if (projectPath == null || projectPath.trim().isEmpty()) {
            logger.error("Error: --project path must be specified");
            return false;
        }

        // Resolve and validate project path exists
        Path projectDir = resolveProjectPath(projectPath);
        if (!Files.exists(projectDir)) {
            logger.error("Error: Project directory does not exist: {}", projectDir);
            return false;
        }

        if (!Files.isDirectory(projectDir)) {
            logger.error("Error: Project path must be a directory: {}", projectDir);
            return false;
        }

        return true;
    }

    // Getters for testing
    public String getProjectPath() {
        return projectPath;
    }

    public String getJsonOutputPath() {
        return jsonOutputPath;
    }

    public List<String> getNodeTypeFilters() {
        return nodeTypeFilters;
    }

    public List<String> getEdgeTypeFilters() {
        return edgeTypeFilters;
    }
}
