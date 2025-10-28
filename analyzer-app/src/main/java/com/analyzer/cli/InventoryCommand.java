package com.analyzer.cli;

import com.analyzer.api.analysis.Analysis;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.dev.collectors.CollectorBeanFactory;
import com.analyzer.core.db.GraphDatabaseConfig;
import com.analyzer.core.db.loader.GraphDatabaseLoader;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.db.repository.H2GraphStorageRepository;
import com.analyzer.core.db.serializer.GraphDatabaseSerializer;
import com.analyzer.core.engine.AnalysisEngine;
import com.analyzer.core.inspector.InspectorRegistry;
import com.analyzer.core.model.Project;
import com.analyzer.dev.detection.FileDetectionBeanFactory;
import com.analyzer.core.resource.CompositeResourceResolver;
import com.analyzer.rules.std.StdInspectorBeanFactory;
import com.analyzer.rules.ejb2spring.Ejb2SpringInspectorBeanFactory;
import com.analyzer.rules.graph.GraphInspectorBeanFactory;
import com.analyzer.rules.metrics.MetricsInspectorBeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Inventory command implementation.
 * Creates an inventory of Java classes and packages with inspector analysis
 * results using the URI-based ResourceResolver system.
 */
@Command(name = "inventory", description = "Create an inventory of Java classes and packages")
public class InventoryCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(InventoryCommand.class);

    @Option(names = { "--project" }, description = "Path to the project directory to analyze", required = true)
    private String projectPath;

    @Option(names = { "--encoding" }, description = "Default encoding to use for reading files")
    private String encoding = Charset.defaultCharset().name();

    @Option(names = {
            "--java_version" }, description = "Java version of the source files (required if --source is used)")
    private String javaVersion;

    @Option(names = { "--inspector" }, description = "List of inspectors to use (comma-separated)", split = ",")
    private List<String> inspectors;

    @Option(names = {
            "--packages" }, description = "Comma-separated list of package prefixes to include (e.g., com.example,com.company). Includes subpackages.", split = ",")
    private List<String> packageFilters;

    @Option(names = {
            "--max-passes" }, description = "Maximum number of analysis passes for convergence detection", defaultValue = "5")
    private int maxPasses;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting Project Architecture Analysis...");

        // Validate parameters
        if (!validateParameters()) {
            return 1;
        }

        logger.info("Configuration:");
        logger.info("  Project path: {}", projectPath);
        logger.info("  Encoding: {}", encoding);
        logger.info("  Java version: {}", javaVersion);
        logger.info("  Inspectors: {}", inspectors);
        logger.info("  Package filters: {}", packageFilters);
        logger.info("  Max passes: {}", maxPasses);

        try {
            // Initialize ResourceResolver system
            CompositeResourceResolver resolver = CompositeResourceResolver.createDefault();

            // 1. Initialize Inspector Registry
            InspectorRegistry inspectorRegistry = InspectorRegistry.newInspectorRegistry(resolver);
            inspectorRegistry.registerComponents(FileDetectionBeanFactory.class);
            inspectorRegistry.registerComponents(CollectorBeanFactory.class);
            inspectorRegistry.registerComponents(StdInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(Ejb2SpringInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(GraphInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(MetricsInspectorBeanFactory.class);

            logger.info("{}", inspectorRegistry.getStatistics());

            // 3. Create empty analysis list (will be enhanced later)
            List<Analysis> analyses = new ArrayList<>();

            // 4. Create fresh analysis container for this analysis run

            // 5. Get Analysis Engine from analysis container (all dependencies
            // auto-injected)
            AnalysisEngine analysisEngine = inspectorRegistry.getAnalysisEngine();
            if (analysisEngine == null) {
                logger.error("Failed to get AnalysisEngine from analysis container");
                return 1;
            }

            // Configure the engine with file detection inspectors and analyses
            analysisEngine.setAvailableAnalyses(analyses);

            logger.info("{}", analysisEngine.getStatistics());

            // Load existing database if it exists
            java.nio.file.Path projectDir = java.nio.file.Paths.get(projectPath);
            Project existingProject = loadExistingDatabase(projectDir, analysisEngine);

            // 5. Analyze the project using new architecture with multi-pass algorithm
            Project project = analysisEngine.analyzeProject(projectDir, inspectors, maxPasses, packageFilters);

            logger.info("Project analysis completed. Found {} files", project.getProjectFiles().size());

            // Serialize to H2 database (with preserved node IDs!)
            java.nio.file.Path dbPath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.GRAPH_DB_NAME);
            logger.info("Initializing H2 database at: {}", dbPath);
            GraphDatabaseConfig dbConfig = new GraphDatabaseConfig();
            dbConfig.initialize(dbPath);

            logger.info("Serializing project data to H2 database...");
            GraphDatabaseSerializer dbSerializer = new GraphDatabaseSerializer(dbConfig);
            dbSerializer.serialize(project);

            // Show database statistics
            H2GraphStorageRepository repo = new H2GraphStorageRepository(dbConfig);
            var stats = repo.getStatistics();
            logger.info("Database statistics: {}", stats);

            logger.info("Analysis completed successfully!");
            logger.info("H2 database created at: {}", dbPath);
            logger.info("  - Node IDs preserved as original file paths/FQNs");
            logger.info("  - {} total nodes stored", stats.nodeCount());
            logger.info("To export data:");
            logger.info("  - CSV: Use csv_export --project {} --output <file.csv>", projectPath);
            logger.info("  - JSON: Use json_export --project {} --json-output <output-dir>", projectPath);

            return 0;
        } catch (Exception e) {
            logger.error("Error during analysis: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Converts a file path to a URI string.
     * Handles both relative and absolute paths, and JAR/WAR files.
     */
    private String convertPathToUri(String path) {
        if (path == null) {
            return null;
        }

        try {
            File file = new File(path);

            // Convert to absolute path if relative
            if (!file.isAbsolute()) {
                file = file.getAbsoluteFile();
            }

            // For JAR and WAR files, use jar: protocol
            if (path.toLowerCase().endsWith(".jar") || path.toLowerCase().endsWith(".war")) {
                return "jar:" + file.toURI().toString() + "!/";
            } else {
                // For directories and regular files, use file: protocol
                return file.toURI().toString();
            }
        } catch (Exception e) {
            logger.error("Error converting path to URI: {} - {}", path, e.getMessage());
            return null;
        }
    }

    private boolean validateParameters() {
        // Project path must be specified (already enforced by @Option required=true)
        if (projectPath == null || projectPath.trim().isEmpty()) {
            logger.error("Error: --project path must be specified");
            return false;
        }

        // Validate project path exists and is a directory
        File projectDir = new File(projectPath);
        if (!projectDir.exists()) {
            logger.error("Error: Project directory does not exist: {}", projectPath);
            return false;
        }

        if (!projectDir.isDirectory()) {
            logger.error("Error: Project path must be a directory: {}", projectPath);
            return false;
        }

        return true;
    }

    // Getters for testing and internal use
    public String getProjectUri() {
        return convertPathToUri(projectPath);
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public List<String> getInspectors() {
        return inspectors;
    }

    public List<String> getPackageFilters() {
        return packageFilters;
    }

    public int getMaxPasses() {
        return maxPasses;
    }

    /**
     * Loads existing database if it exists and pre-populates the analysis engine's
     * graph repository.
     * This enables incremental analysis by starting with previously analyzed data.
     * 
     * @param projectDir     The project directory
     * @param analysisEngine The analysis engine to populate
     * @return Existing Project object if database found, null otherwise
     */
    private Project loadExistingDatabase(Path projectDir, AnalysisEngine analysisEngine) {
        try {
            // Check if database exists
            Path dbPath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.getCompleteDatabaseName());

            if (!Files.exists(dbPath)) {
                logger.info("No existing database found at: {}", dbPath);
                logger.info("Starting fresh analysis...");
                return null;
            }

            // Initialize database connection
            Path dbBasePath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.GRAPH_DB_NAME);
            logger.info("Found existing database at: {}", dbPath);
            logger.info("Loading existing data...");

            GraphDatabaseConfig dbConfig = new GraphDatabaseConfig();
            dbConfig.initialize(dbBasePath);

            // Create H2 database repository to load data
            H2GraphStorageRepository dbRepository = new H2GraphStorageRepository(dbConfig);

            // Get statistics
            var stats = dbRepository.getStatistics();
            logger.info("Existing database contains: {}", stats);

            // Load data from database into the analysis engine's graph repository
            loadDataIntoEngine(dbRepository, analysisEngine);

            logger.info("Existing database loaded successfully. Will update with new analysis results.");

            // Return a minimal project object (mainly for reference)
            return new Project(projectDir, projectDir.getFileName().toString());

        } catch (Exception e) {
            logger.warn("Failed to load existing database: {}", e.getMessage());
            logger.info("Continuing with fresh analysis...");
            return null;
        }
    }

    /**
     * Loads data from H2 database into the AnalysisEngine's graph repository.
     * 
     * @param dbRepo         The database repository to read from
     * @param analysisEngine The analysis engine whose graph repository to populate
     */
    private void loadDataIntoEngine(H2GraphStorageRepository dbRepo, AnalysisEngine analysisEngine) {
        // Get the graph repository from the analysis engine
        GraphRepository graphRepo = analysisEngine.getGraphRepository();
        if (graphRepo == null) {
            logger.warn("Cannot load data - AnalysisEngine has no GraphRepository");
            return;
        }

        // Use GraphDatabaseLoader to populate the repository
        LoadOptions loadOptions = LoadOptions.builder()
                .loadAllNodes()
                .withCommonEdgeTypes()
                .withProjectRoot(Paths.get("."))
                .build();

        GraphDatabaseLoader.loadIntoRepository(dbRepo, graphRepo, loadOptions);
    }
}
