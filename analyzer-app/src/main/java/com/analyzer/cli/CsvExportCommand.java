package com.analyzer.cli;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.core.db.GraphDatabaseConfig;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.engine.AnalysisEngine;
import com.analyzer.core.export.CsvExporter;
import com.analyzer.core.graph.InMemoryGraphRepository;
import com.analyzer.core.inspector.InspectorRegistry;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.CompositeResourceResolver;
import com.analyzer.core.serialization.JsonSerializationService;
import com.analyzer.dev.collectors.CollectorBeanFactory;
import com.analyzer.dev.detection.FileDetectionBeanFactory;
import com.analyzer.rules.ejb2spring.Ejb2SpringInspectorBeanFactory;
import com.analyzer.rules.graph.GraphInspectorBeanFactory;
import com.analyzer.rules.metrics.MetricsInspectorBeanFactory;
import com.analyzer.rules.std.StdInspectorBeanFactory;
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
 * CSV Export command implementation.
 * Loads project data from the H2 database and exports it to CSV format.
 */
@Command(name = "csv_export", description = "Export project data from database to CSV format")
public class CsvExportCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(CsvExportCommand.class);

    @Option(names = {
            "--project" }, description = "Path to the project directory containing the database", required = true)
    private String projectPath;

    @Option(names = {
            "--output" }, description = "Output file for CSV export", defaultValue = "out/inventory.csv")
    private File outputFile;

    @Option(names = { "--inspector" }, description = "List of inspectors to use (comma-separated)", split = ",")
    private List<String> inspectors;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting CSV export from database...");

        // Validate parameters
        if (!validateParameters()) {
            return 1;
        }

        // Resolve project path (handle both relative and absolute)
        Path projectDir = resolveProjectPath(projectPath);

        logger.info("Configuration:");
        logger.info("  Project path: {}", projectDir);
        logger.info("  CSV output: {}", outputFile.getAbsolutePath());
        logger.info("  Inspectors: {}", inspectors);

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
            GraphRepository inMemoryRepo = loadDataFromDatabase(dbRepository);

            // Create minimal project object for serialization
            Project project = createMinimalProject(projectDir, inMemoryRepo);

            // Initialize Inspector Registry to get inspector list
            CompositeResourceResolver resolver = CompositeResourceResolver.createDefault();
            InspectorRegistry inspectorRegistry = InspectorRegistry.newInspectorRegistry(resolver);
            inspectorRegistry.registerComponents(FileDetectionBeanFactory.class);
            inspectorRegistry.registerComponents(CollectorBeanFactory.class);
            inspectorRegistry.registerComponents(StdInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(Ejb2SpringInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(GraphInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(MetricsInspectorBeanFactory.class);

            AnalysisEngine analysisEngine = inspectorRegistry.getAnalysisEngine();
            List<Inspector> inspectorList = analysisEngine.getInspectors(inspectors);

            // Export to CSV using CsvExporter
            CsvExporter csvExporter = new CsvExporter(outputFile);

            logger.info("Exporting to CSV...");
            csvExporter.exportToCsv(project, inspectorList);

            // Get final statistics
            logger.info("Export completed successfully!");
            logger.info("CSV data written to: {}", outputFile.getAbsolutePath());
            logger.info("Exported: {} files", project.getProjectFiles().size());

            return 0;
        } catch (Exception e) {
            logger.error("Error during CSV export: {}", e.getMessage(), e);
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
     * Load data from H2 database into an in-memory GraphRepository.
     */
    private GraphRepository loadDataFromDatabase(com.analyzer.core.db.repository.GraphRepository dbRepo) {
        InMemoryGraphRepository memoryRepo = new InMemoryGraphRepository();
        JsonSerializationService jsonSerializer = new JsonSerializationService();

        // Load nodes from database
        logger.info("Loading nodes from database...");
        List<GraphNodeEntity> nodeEntities = new ArrayList<>();

        // Load common node types
        for (String type : Arrays.asList("java", "xml", "properties", "yaml", "json", "file")) {
            nodeEntities.addAll(dbRepo.findNodesByType(type));
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
                        Paths.get(entity.getId()),
                        Paths.get(".") // Placeholder parent path
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

        // Load edges from database
        logger.info("Loading edges from database...");
        List<GraphEdgeEntity> edgeEntities = new ArrayList<>();

        // Note: We would need to query for all edge types here
        // For now, load common edge types
        for (String type : Arrays.asList("depends_on", "contains", "imports", "extends", "implements")) {
            try {
                edgeEntities.addAll(dbRepo.findEdgesByType(type));
            } catch (Exception e) {
                logger.debug("No edges of type {}: {}", type, e.getMessage());
            }
        }

        // Convert edge entities and add to memory repository
        int edgesAdded = 0;
        for (GraphEdgeEntity entity : edgeEntities) {
            GraphNode source = nodeMap.get(entity.getSourceId());
            GraphNode target = nodeMap.get(entity.getTargetId());

            if (source != null && target != null) {
                memoryRepo.getOrCreateEdge(source, target, entity.getEdgeType());
                edgesAdded++;
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

        // Create project with basic info
        Project project = new Project(projectDir, projectName);

        // Add all nodes from repository to project
        for (GraphNode node : repository.getNodes()) {
            if (node instanceof ProjectFile) {
                ProjectFile projectFile = (ProjectFile) node;
                project.addProjectFile(projectFile);
            }
        }

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

    public File getOutputFile() {
        return outputFile;
    }

    public List<String> getInspectors() {
        return inspectors;
    }
}
