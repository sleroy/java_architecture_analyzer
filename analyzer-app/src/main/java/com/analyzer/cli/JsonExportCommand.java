package com.analyzer.cli;

import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.core.db.GraphDatabaseConfig;
import com.analyzer.core.db.loader.GraphDatabaseLoader;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.export.ProjectSerializer;
import com.analyzer.core.model.Project;
import com.analyzer.core.db.repository.H2GraphStorageRepository;
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
            Path dbFileNamePath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.getCompleteDatabaseName());
            if (!Files.exists(dbFileNamePath)) {
                logger.error("Database not found at: {}", dbFileNamePath);
                logger.error("Please run the 'inventory' command first to create the database.");
                return 1;
            }

            // Initialize database connection (H2 adds .mv.db automatically, so use base
            // path)
            Path dbPath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR).resolve(AnalysisConstants.GRAPH_DB_NAME);
            logger.info("Loading database from: {}", dbPath);
            GraphDatabaseConfig dbConfig = new GraphDatabaseConfig();
            dbConfig.initialize(dbPath);

            // Create H2 database repository to load data
            var dbRepository = new H2GraphStorageRepository(
                    dbConfig);

            // Get statistics
            var stats = dbRepository.getStatistics();
            logger.info("Database loaded: {}", stats);

            // Load data from H2 into in-memory repository using GraphDatabaseLoader
            LoadOptions.Builder optionsBuilder = LoadOptions.builder()
                    .withProjectRoot(projectDir);

            if (nodeTypeFilters != null && !nodeTypeFilters.isEmpty()) {
                optionsBuilder.withNodeTypeFilters(nodeTypeFilters);
            } else {
                optionsBuilder.loadAllNodes();
            }

            if (edgeTypeFilters != null && !edgeTypeFilters.isEmpty()) {
                optionsBuilder.withEdgeTypeFilters(edgeTypeFilters);
            } else {
                optionsBuilder.withCommonEdgeTypes();
            }

            LoadOptions loadOptions = optionsBuilder.build();
            GraphRepository inMemoryRepo = GraphDatabaseLoader.loadFromDatabase(dbRepository, loadOptions);

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
