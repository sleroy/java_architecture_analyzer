package com.analyzer.cli;
import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.engine.AnalysisEngine;
import com.analyzer.core.export.CsvExporter;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.graph.InMemoryGraphRepository;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorRegistry;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectDeserializer;
import com.analyzer.resource.CompositeResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Load command implementation.
 * Loads a previously saved project analysis from JSON and optionally exports to
 * CSV
 * or runs additional inspectors on the loaded data.
 */
@Command(name = "load", description = "Load a previously saved project analysis from JSON")
public class LoadCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(LoadCommand.class);

    @Option(names = {
            "--project-json" }, description = "Path to the JSON file containing saved project analysis", required = true)
    private String projectJsonPath;

    @Option(names = { "--output" }, description = "Output file for CSV export", defaultValue = "loaded-analysis.csv")
    private File outputFile;

    @Option(names = {
            "--validate-only" }, description = "Only validate the JSON file without loading", defaultValue = "false")
    private boolean validateOnly;

    @Option(names = {
            "--re-analyze" }, description = "Re-run specified inspectors on loaded project (comma-separated)", split = ",")
    private List<String> reAnalyzeInspectors;

    @Option(names = {
            "--plugins" }, description = "Directory containing inspector plugins (required if --re-analyze is used)", defaultValue = "plugins")
    private File pluginsDirectory;

    @Option(names = { "--include-graph" }, description = "Include graph data when loading", defaultValue = "true")
    private boolean includeGraph;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting Project Load Operation...");

        // Validate parameters
        if (!validateParameters()) {
            return 1;
        }

        logger.info("Configuration:");
        logger.info("  Project JSON: {}", projectJsonPath);
        logger.info("  Output file: {}", outputFile);
        logger.info("  Validate only: {}", validateOnly);
        logger.info("  Re-analyze inspectors: {}", reAnalyzeInspectors);
        logger.info("  Include graph: {}", includeGraph);

        try {
            Path jsonPath = Paths.get(projectJsonPath);
            ProjectDeserializer deserializer = new ProjectDeserializer();

            // If validate-only mode, just validate and return
            if (validateOnly) {
                return performValidation(deserializer, jsonPath);
            }

            // Load the project
            GraphRepository graphRepository = includeGraph ? new InMemoryGraphRepository() : null;
            Project project = deserializer.loadProject(jsonPath, graphRepository);

            logger.info("Successfully loaded project: {}", project.getProjectName());
            logger.info("Project contains {} files", project.getProjectFiles().size());

            // If re-analysis is requested, run additional inspectors
            if (reAnalyzeInspectors != null && !reAnalyzeInspectors.isEmpty()) {
                project = performReAnalysis(project, reAnalyzeInspectors, graphRepository);
            }

            // Export to CSV
            exportToCsv(project);

            logger.info("Load operation completed successfully!");
            logger.info("Results written to: {}", outputFile.getAbsolutePath());

            return 0;

        } catch (Exception e) {
            logger.error("Error during load operation: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Perform validation of the JSON file.
     */
    private Integer performValidation(ProjectDeserializer deserializer, Path jsonPath) {
        logger.info("Validating project analysis file...");

        ProjectDeserializer.ValidationResult result = deserializer.validateProjectFile(jsonPath);

        if (result.isValid()) {
            logger.info("✓ Validation successful: {}", result.getMessage());
            return 0;
        } else {
            logger.error("✗ Validation failed: {}", result.getMessage());
            return 1;
        }
    }

    /**
     * Re-run specified inspectors on the loaded project.
     */
    private Project performReAnalysis(Project project, List<String> inspectorNames, GraphRepository graphRepository)
            throws Exception {
        logger.info("Re-analyzing project with {} inspectors", inspectorNames.size());

        // Initialize ResourceResolver system
        CompositeResourceResolver resolver = CompositeResourceResolver.createDefault();

        // Initialize Inspector Registry
        InspectorRegistry inspectorRegistry = new InspectorRegistry(pluginsDirectory, resolver);
        logger.info("{}", inspectorRegistry.getStatistics());

        // Verify requested inspectors exist
        List<String> availableInspectors = new ArrayList<>();
        for (String inspectorName : inspectorNames) {
            Inspector inspector = inspectorRegistry.getInspector(inspectorName);
            if (inspector != null) {
                availableInspectors.add(inspectorName);
            } else {
                logger.warn("Inspector '{}' not found, skipping", inspectorName);
            }
        }

        if (availableInspectors.isEmpty()) {
            logger.warn("No valid inspectors found for re-analysis");
            return project;
        }

        // Create Analysis Engine for re-analysis
        AnalysisEngine analysisEngine = new AnalysisEngine(
                inspectorRegistry,
                new ArrayList<>(), // No file detection needed for re-analysis
                new ArrayList<>(), // No analyses needed
                graphRepository);

        // Note: For true re-analysis, we'd need to implement a method in AnalysisEngine
        // that can run specific inspectors on an existing project. For now, we'll just
        // log that the feature would be implemented here.
        logger.info("Re-analysis feature would run inspectors: {}", availableInspectors);
        logger.warn("Re-analysis implementation pending - returning original project");

        return project;
    }

    /**
     * Export project to CSV using the existing exporter.
     */
    private void exportToCsv(Project project) throws Exception {
        logger.info("Exporting loaded project to CSV...");

        CsvExporter csvExporter = new CsvExporter(outputFile);

        // Create empty inspector list since we're just exporting loaded data
        List<Inspector> inspectorList = new ArrayList<>();

        csvExporter.exportToCsv(project, inspectorList);

        // Log export statistics
        CsvExporter.ExportStatistics stats = csvExporter.getStatistics(
                project.getProjectFiles().values(),
                inspectorList);
        logger.info("Export statistics: {}", stats);

        // Log filtering statistics
        CsvExporter.FilteringStatistics filterStats = csvExporter.getFilteringStatistics(
                project.getProjectFiles().values());
        logger.info("Filtering statistics: {}", filterStats);

        if (!filterStats.getExcludedColumns().isEmpty()) {
            logger.info("Excluded columns (complex data): [{}]",
                    String.join(", ", filterStats.getExcludedColumns()));
        }
    }

    /**
     * Validate command line parameters.
     */
    private boolean validateParameters() {
        // Validate JSON file exists
        File jsonFile = new File(projectJsonPath);
        if (!jsonFile.exists()) {
            logger.error("Error: Project JSON file does not exist: {}", projectJsonPath);
            return false;
        }

        if (!jsonFile.isFile()) {
            logger.error("Error: Project JSON path must be a file: {}", projectJsonPath);
            return false;
        }

        // Validate plugins directory if re-analysis is requested
        if (reAnalyzeInspectors != null && !reAnalyzeInspectors.isEmpty()) {
            if (pluginsDirectory != null && !pluginsDirectory.exists()) {
                logger.warn("Warning: Plugins directory does not exist: {}", pluginsDirectory);
                logger.info("Creating plugins directory...");
                pluginsDirectory.mkdirs();
            }
        }

        return true;
    }

    // Getters for testing
    public String getProjectJsonPath() {
        return projectJsonPath;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public boolean isValidateOnly() {
        return validateOnly;
    }

    public List<String> getReAnalyzeInspectors() {
        return reAnalyzeInspectors;
    }

    public File getPluginsDirectory() {
        return pluginsDirectory;
    }

    public boolean isIncludeGraph() {
        return includeGraph;
    }
}
