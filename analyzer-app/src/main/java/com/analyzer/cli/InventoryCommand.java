package com.analyzer.cli;

import com.analyzer.api.analysis.Analysis;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.core.db.H2GraphDatabase;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.engine.AnalysisEngine;
import com.analyzer.core.inspector.InspectorRegistry;
import com.analyzer.core.model.Project;
import com.analyzer.core.resource.CompositeResourceResolver;
import com.analyzer.core.serialization.JsonSerializationService;
import com.analyzer.dev.collectors.CollectorBeanFactory;
import com.analyzer.dev.detection.FileDetectionBeanFactory;
import com.analyzer.rules.ai.AIInspectorBeanFactory;
import com.analyzer.rules.ejb2spring.Ejb2SpringInspectorBeanFactory;
import com.analyzer.rules.graph.GraphInspectorBeanFactory;
import com.analyzer.rules.metrics.MetricsInspectorBeanFactory;
import com.analyzer.rules.std.StdInspectorBeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Inventory command implementation.
 * Creates an inventory of Java classes and packages with inspector analysis
 * results using the URI-based ResourceResolver system.
 */
@CommandLine.Command(name = "inventory", description = "Create an inventory of Java classes and packages")
public class InventoryCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(InventoryCommand.class);

    @CommandLine.Option(names = "--project", description = "Path to the project directory to analyze", required = true)
    private String projectPath;

    @CommandLine.Option(names = "--encoding", description = "Default encoding to use for reading files")
    private String encoding = Charset.defaultCharset().name();

    @CommandLine.Option(names = "--java_version", description = "Java version of the source files (required if --source is used)")
    private String javaVersion;

    @CommandLine.Option(names = "--inspector", description = "List of inspectors to use (comma-separated)", split = ",")
    private List<String> inspectors;

    @CommandLine.Option(names = "--packages", description = "Comma-separated list of package prefixes to include (e.g., com.example,com.company). Includes subpackages.", split = ",")
    private List<String> packageFilters;

    @CommandLine.Option(names = "--max-passes", description = "Maximum number of analysis passes for convergence detection", defaultValue = "5")
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
            final CompositeResourceResolver resolver = CompositeResourceResolver.createDefault();

            // 1. Initialize Inspector Registry
            final InspectorRegistry inspectorRegistry = InspectorRegistry.newInspectorRegistry(resolver);
            inspectorRegistry.registerComponents(FileDetectionBeanFactory.class);
            inspectorRegistry.registerComponents(CollectorBeanFactory.class);
            inspectorRegistry.registerComponents(StdInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(Ejb2SpringInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(GraphInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(MetricsInspectorBeanFactory.class);
            inspectorRegistry.registerComponents(AIInspectorBeanFactory.class);

            logger.info("{}", inspectorRegistry.getStatistics());

            // 3. Create empty analysis list (will be enhanced later)
            final List<Analysis> analyses = new ArrayList<>();

            // 4. Create fresh analysis container for this analysis run

            // 5. Get Analysis Engine from analysis container (all dependencies
            // auto-injected)
            final AnalysisEngine analysisEngine = inspectorRegistry.getAnalysisEngine();
            if (analysisEngine == null) {
                logger.error("Failed to get AnalysisEngine from analysis container");
                return 1;
            }

            // Configure the engine with file detection inspectors and analyses
            analysisEngine.setAvailableAnalyses(analyses);

            logger.info("{}", analysisEngine.getStatistics());

            // Initialize project directory
            final java.nio.file.Path projectDir = java.nio.file.Paths.get(projectPath);

            // Initialize and load H2 database at the beginning
            final java.nio.file.Path dbPath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                                                        .resolve(AnalysisConstants.GRAPH_DB_NAME);
            logger.info("Initializing H2 database at: {}", dbPath);

            final LoadOptions loadOptions = LoadOptions.builder()
                                                       .withProjectRoot(projectDir)
                                                       .withDatabasePath(dbPath)
                                                       .loadAllNodes()
                                                       .withCommonEdgeTypes()
                                                       .build();

            final H2GraphDatabase h2Database = new H2GraphDatabase(loadOptions, new JsonSerializationService());
            h2Database.load();

            // Load existing data from database into analysis engine (if database exists)
            logger.info("Loading existing data from database...");
            final var existingRepo = h2Database.snapshot();
            logger.info("Loaded {} existing nodes from database", existingRepo.getNodes().size());

            // Pre-populate the analysis engine's graph repository with existing data
            for (final var node : existingRepo.getNodes()) {
                analysisEngine.getGraphRepository().addNode(node);
            }
            for (final var edge : existingRepo.getAllEdges()) {
                analysisEngine.getGraphRepository().getOrCreateEdge(
                        edge.getSource(), edge.getTarget(), edge.getEdgeType());
            }

            // 5. Analyze the project using new architecture with multi-pass algorithm
            // This will add to or update the existing data
            final Project project = analysisEngine.analyzeProject(projectDir, inspectors, maxPasses, packageFilters);

            logger.info("Project analysis completed. Found {} files", project.getProjectFiles().size());

            // Persist updated graph repository back to H2 database
            logger.info("Persisting analysis results to H2 database...");
            h2Database.persist(analysisEngine.getGraphRepository());

            // Show database statistics
            final var stats = h2Database.getRepository().getStatistics();
            logger.info("Database statistics: {}", stats);

            logger.info("Analysis completed successfully!");
            logger.info("H2 database created at: {}", dbPath);
            logger.info("  - {} total nodes stored", stats.nodeCount());
            logger.info("  - {} total edges stored", stats.edgeCount());
            logger.info("To export data:");
            logger.info("  - CSV: Use csv_export --project {} --output <file.csv>", projectPath);

            return 0;
        } catch (final Exception e) {
            logger.error("Error during analysis: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Converts a file path to a URI string.
     * Handles both relative and absolute paths, and JAR/WAR files.
     */
    private String convertPathToUri(final String path) {
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
                return "jar:" + file.toURI() + "!/";
            } else {
                // For directories and regular files, use file: protocol
                return file.toURI().toString();
            }
        } catch (final Exception e) {
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
        final File projectDir = new File(projectPath);
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

}
