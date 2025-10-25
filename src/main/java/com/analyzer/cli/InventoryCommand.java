package com.analyzer.cli;

import com.analyzer.analysis.Analysis;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.core.collector.CollectorBeanFactory;
import com.analyzer.core.engine.AnalysisEngine;
import com.analyzer.core.export.CsvExporter;
import com.analyzer.core.export.ProjectSerializer;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorRegistry;
import com.analyzer.core.model.Project;
import com.analyzer.inspectors.core.detection.FileDetectionBeanFactory;
import com.analyzer.resource.CompositeResourceResolver;
import com.analyzer.rules.ejb2spring.Ejb2SpringInspectorBeanFactory;
import com.analyzer.rules.graph.GraphInspectorBeanFactory;
import com.analyzer.rules.metrics.MetricsInspectorBeanFactory;
import com.analyzer.rules.std.StdInspectorBeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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
@Command(name = "inventory", description = "Create an inventory of Java classes and packages")
public class InventoryCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(InventoryCommand.class);

    @Option(names = { "--project" }, description = "Path to the project directory to analyze", required = true)
    private String projectPath;

    @Option(names = {
            "--output" }, description = "Output file for the analysis results (CSV format)", defaultValue = "out/inventory.csv")
    private File outputFile;

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
        logger.info("  Output file: {}", outputFile);
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

            // 5. Analyze the project using new architecture with multi-pass algorithm
            java.nio.file.Path projectDir = java.nio.file.Paths.get(projectPath);
            Project project = analysisEngine.analyzeProject(projectDir, inspectors, maxPasses);

            logger.info("Project analysis completed. Found {} files", project.getProjectFiles().size());

            // 6. Generate CSV output with inspector results (will need to be updated for
            // ProjectFile)
            CsvExporter csvExporter = new CsvExporter(outputFile);
            List<Inspector> inspectorList = analysisEngine.getInspectors(inspectors);

            logger.info("Exporting results to CSV...");
            csvExporter.exportToCsv(project, inspectorList);

            // Export project data as JSON - compute path relative to project directory
            File jsonOutputDir = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.PROJECT_DATA_DIR).toFile();
            ProjectSerializer projectSerializer = new ProjectSerializer(jsonOutputDir);
            logger.info("Serializing project data to JSON...");
            projectSerializer.serialize(project);

            logger.info("Analysis completed successfully!");
            logger.info("Results written to: {}", outputFile.getAbsolutePath());
            logger.info("Project data serialized to: {}", jsonOutputDir.getAbsolutePath());

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

    public File getOutputFile() {
        return outputFile;
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
