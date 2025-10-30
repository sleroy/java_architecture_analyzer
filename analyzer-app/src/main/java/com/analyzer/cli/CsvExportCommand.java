package com.analyzer.cli;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.core.db.H2GraphDatabase;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.serialization.JsonSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CSV Export command implementation.
 * Loads project data from the H2 database and exports it to CSV format,
 * creating one CSV file per node type with dynamic columns for tags and
 * metrics.
 */
@Command(name = "csv_export", description = "Export project data from database to CSV format")
public class CsvExportCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(CsvExportCommand.class);

    @Option(names = {
            "--project" }, description = "Path to the project directory containing the database", required = true)
    private String projectPath;

    @Option(names = {
            "--output-dir" }, description = "Output directory for CSV files (default: <project>/.analysis/csv)")
    private String outputDir;

    @Option(names = {
            "--node-types" }, description = "Comma-separated list of node types to export (e.g., file,java_class). If not specified, all types are exported.", split = ",")
    private List<String> nodeTypeFilters;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting CSV export from database...");

        // Validate parameters
        if (!validateParameters()) {
            return 1;
        }

        // Resolve project path (handle both relative and absolute)
        Path projectDir = resolveProjectPath(projectPath);

        // Resolve output path
        Path csvOutputDir = resolveOutputPath(projectDir, outputDir);

        logger.info("Configuration:");
        logger.info("  Project path: {}", projectDir);
        logger.info("  CSV output: {}", csvOutputDir);
        logger.info("  Node type filters: {}", nodeTypeFilters != null ? nodeTypeFilters : "all");

        try {
            // Check if database exists
            Path dbFileNamePath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.getCompleteDatabaseName());
            if (!Files.exists(dbFileNamePath)) {
                logger.error("Database not found at: {}", dbFileNamePath);
                logger.error("Please run the 'inventory' command first to create the database.");
                return 1;
            }

            // Initialize database connection
            Path dbPath = projectDir.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.GRAPH_DB_NAME);
            logger.info("Loading database from: {}", dbPath);

            // Load data from H2 into in-memory repository
            LoadOptions.Builder optionsBuilder = LoadOptions.builder()
                    .withProjectRoot(projectDir)
                    .withDatabasePath(dbPath);

            if (nodeTypeFilters != null && !nodeTypeFilters.isEmpty()) {
                optionsBuilder.withNodeTypeFilters(nodeTypeFilters);
            } else {
                optionsBuilder.loadAllNodes();
            }

            // Load all edges to get complete context
            optionsBuilder.withCommonEdgeTypes();

            LoadOptions loadOptions = optionsBuilder.build();
            H2GraphDatabase h2DB = new H2GraphDatabase(loadOptions, new JsonSerializationService());
            h2DB.load();

            // Get statistics
            var stats = h2DB.getRepository().getStatistics();
            logger.info("Database loaded: {}", stats);
            GraphRepository repository = h2DB.snapshot();

            // Create output directory if it doesn't exist
            Files.createDirectories(csvOutputDir);

            // Group nodes by type
            Map<String, List<GraphNode>> nodesByType = repository.getNodes().stream()
                    .collect(Collectors.groupingBy(GraphNode::getNodeType));

            logger.info("Found {} node types to export", nodesByType.size());

            // Export each node type to a separate CSV file
            int totalExported = 0;
            for (Map.Entry<String, List<GraphNode>> entry : nodesByType.entrySet()) {
                String nodeType = entry.getKey();
                List<GraphNode> nodes = entry.getValue();

                String csvFileName = nodeType + "_nodes.csv";
                Path csvFile = csvOutputDir.resolve(csvFileName);

                logger.info("Exporting {} nodes of type '{}' to {}", nodes.size(), nodeType, csvFileName);
                exportNodesToCsv(nodes, nodeType, csvFile);
                totalExported += nodes.size();
            }

            logger.info("Export completed successfully!");
            logger.info("CSV files written to: {}", csvOutputDir.toAbsolutePath());
            logger.info("Exported: {} nodes across {} node types", totalExported, nodesByType.size());

            return 0;
        } catch (Exception e) {
            logger.error("Error during CSV export: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Exports a list of nodes to a CSV file.
     * Creates dynamic columns based on tags and metrics present in the nodes.
     */
    private void exportNodesToCsv(List<GraphNode> nodes, String nodeType, Path csvFile) throws IOException {
        if (nodes.isEmpty()) {
            logger.warn("No nodes to export for type: {}", nodeType);
            return;
        }

        // Collect all unique tags and metrics across all nodes
        Set<String> allTags = new TreeSet<>();
        Set<String> allMetrics = new TreeSet<>();

        for (GraphNode node : nodes) {
            allTags.addAll(node.getTags());
            if (node.getMetrics() != null) {
                allMetrics.addAll(node.getMetrics().getAllMetrics().keySet());
            }
        }

        // Write CSV file
        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            // Write header
            writeHeader(writer, allTags, allMetrics);

            // Write data rows
            for (GraphNode node : nodes) {
                writeNodeRow(writer, node, allTags, allMetrics);
            }
        }

        logger.debug("Wrote {} rows to {}", nodes.size(), csvFile.getFileName());
    }

    /**
     * Writes the CSV header with fixed columns followed by dynamic tag and metric
     * columns.
     */
    private void writeHeader(BufferedWriter writer, Set<String> allTags, Set<String> allMetrics) throws IOException {
        List<String> headers = new ArrayList<>();

        // Fixed columns
        headers.add("node_id");
        headers.add("node_type");
        headers.add("display_label");

        // Tag columns (prefixed with "tag:")
        for (String tag : allTags) {
            headers.add("tag:" + tag);
        }

        // Metric columns (prefixed with "metric:")
        for (String metric : allMetrics) {
            headers.add("metric:" + metric);
        }

        writer.write(String.join(",", headers));
        writer.newLine();
    }

    /**
     * Writes a single node as a CSV row.
     */
    private void writeNodeRow(BufferedWriter writer, GraphNode node, Set<String> allTags, Set<String> allMetrics)
            throws IOException {
        List<String> values = new ArrayList<>();

        // Fixed columns
        values.add(escapeCsv(node.getId()));
        values.add(escapeCsv(node.getNodeType()));
        values.add(escapeCsv(node.getDisplayLabel()));

        // Tag columns (true/false)
        for (String tag : allTags) {
            values.add(node.hasTag(tag) ? "true" : "false");
        }

        // Metric columns (numeric values or empty)
        for (String metric : allMetrics) {
            String value = "";
            if (node.getMetrics() != null) {
                Number metricValue = node.getMetrics().getMetric(metric);
                if (metricValue != null) {
                    value = metricValue.toString();
                }
            }
            values.add(value);
        }

        writer.write(String.join(",", values));
        writer.newLine();
    }

    /**
     * Escapes a string value for CSV format.
     * Handles quotes and commas by wrapping in quotes.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        // If value contains comma, quote, or newline, wrap in quotes and escape
        // internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
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
            // Default: <project>/.analysis/csv
            return projectDir.resolve(AnalysisConstants.ANALYSIS_DIR).resolve("csv");
        }

        Path p = Paths.get(outputPath);
        if (p.isAbsolute()) {
            return p;
        }
        // Relative path is resolved against current working directory
        return Paths.get(System.getProperty("user.dir")).resolve(outputPath).normalize();
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

    public String getOutputDir() {
        return outputDir;
    }

    public List<String> getNodeTypeFilters() {
        return nodeTypeFilters;
    }
}
