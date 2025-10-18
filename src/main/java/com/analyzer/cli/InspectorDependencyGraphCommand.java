package com.analyzer.cli;

import com.analyzer.core.InspectorDependencyGraphBuilder;
import com.analyzer.core.InspectorRegistry;
import com.analyzer.resource.CompositeResourceResolver;
import com.analyzer.resource.FileResourceResolver;
import com.analyzer.resource.JarResourceResolver;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * CLI command to generate inspector dependency graphs showing relationships
 * between
 * inspectors through their tag dependencies. This helps identify unused tags,
 * semantic duplications, and complex dependency chains.
 */
@Command(name = "inspector-graph", description = "Generate inspector dependency graph showing tag relationships", mixinStandardHelpOptions = true)
public class InspectorDependencyGraphCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(InspectorDependencyGraphCommand.class);

    @Parameters(paramLabel = "OUTPUT_FILE", description = "Output file path for the dependency graph (e.g., dependencies.graphml)")
    private File outputFile;

    @Option(names = { "-f",
            "--format" }, description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})", defaultValue = "graphml")
    private GraphFormat format = GraphFormat.graphml;

    @Option(names = { "-s", "--source" }, description = "Source directories to analyze (for context)", split = ",")
    private File[] sourceDirs = {};

    @Option(names = { "-b", "--binary" }, description = "Binary files/JARs to analyze (for context)", split = ",")
    private File[] binaryFiles = {};

    @Option(names = { "--plugins" }, description = "Directory containing plugin JAR files", defaultValue = "plugins")
    private File pluginsDir = new File("plugins");

    @Option(names = { "--include-unused" }, description = "Include analysis of unused tags", defaultValue = "true")
    private boolean includeUnusedTags = true;

    @Option(names = {
            "--include-semantics" }, description = "Include semantic duplication analysis", defaultValue = "true")
    private boolean includeSemanticsAnalysis = true;

    @Option(names = {
            "--min-chain-length" }, description = "Minimum dependency chain length to highlight (default: ${DEFAULT-VALUE})", defaultValue = "3")
    private int minChainLength = 3;

    @Option(names = { "--verbose", "-v" }, description = "Enable verbose output")
    private boolean verbose = false;

    public enum GraphFormat {
        graphml, dot, json
    }

    @Override
    public Integer call() throws Exception {
        logger.info("Starting inspector dependency graph generation...");

        if (verbose) {
            logger.info("Output file: {}", outputFile.getAbsolutePath());
            logger.info("Format: {}", format);
            logger.info("Include unused tags: {}", includeUnusedTags);
            logger.info("Include semantics analysis: {}", includeSemanticsAnalysis);
            logger.info("Min chain length: {}", minChainLength);
        }

        try {
            // Create resource resolver for context
            ResourceResolver resourceResolver = createResourceResolver();

            // Create inspector registry to load all inspectors
            InspectorRegistry inspectorRegistry = new InspectorRegistry(pluginsDir, resourceResolver);

            if (verbose) {
                logger.info("Loaded {} inspectors", inspectorRegistry.getInspectorCount());
                logger.info("  - Source inspectors: {}", inspectorRegistry.getSourceInspectorCount());
                logger.info("  - Binary inspectors: {}", inspectorRegistry.getBinaryInspectorCount());
            }

            // Create graph builder
            InspectorDependencyGraphBuilder graphBuilder = new InspectorDependencyGraphBuilder(
                    inspectorRegistry,
                    includeUnusedTags,
                    includeSemanticsAnalysis,
                    minChainLength);

            // Build the dependency graph
            logger.info("Analyzing inspector dependencies...");
            InspectorDependencyGraphBuilder.GraphAnalysisResult result = graphBuilder.buildDependencyGraph();

            if (verbose) {
                logger.info("Graph analysis completed:");
                logger.info("  - Total inspectors: {}", result.getTotalInspectors());
                logger.info("  - Total dependencies: {}", result.getTotalDependencies());
                logger.info("  - Unused tags: {}", result.getUnusedTags().size());
                logger.info("  - Potential duplicates: {}", result.getPotentialDuplicates().size());
                logger.info("  - Complex chains: {}", result.getComplexChains().size());
            }

            // Export to specified format
            logger.info("Exporting graph to: {}", outputFile.getAbsolutePath());
            exportGraph(result, outputFile, format);

            // Print summary
            printSummary(result);

            logger.info("Inspector dependency graph generation completed successfully!");
            return 0;

        } catch (Exception e) {
            logger.error("Error generating inspector dependency graph: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Creates a composite resource resolver for the given source and binary inputs.
     */
    private ResourceResolver createResourceResolver() {
        CompositeResourceResolver composite = new CompositeResourceResolver();

        // Register file and JAR resolvers
        composite.registerResolver("file", new FileResourceResolver());
        composite.registerResolver("jar", new JarResourceResolver());

        if (verbose) {
            logger.info("Registered file and JAR resource resolvers");
            for (File sourceDir : sourceDirs) {
                if (sourceDir.exists() && sourceDir.isDirectory()) {
                    logger.info("Source directory available: {}", sourceDir.getAbsolutePath());
                }
            }
            for (File binaryFile : binaryFiles) {
                if (binaryFile.exists()) {
                    logger.info("Binary file available: {}", binaryFile.getAbsolutePath());
                }
            }
        }

        return composite;
    }

    /**
     * Exports the graph analysis result to the specified file and format.
     */
    private void exportGraph(InspectorDependencyGraphBuilder.GraphAnalysisResult result,
            File outputFile, GraphFormat format) throws Exception {

        // Ensure output directory exists
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        switch (format) {
            case graphml:
                result.exportGraphML(outputFile);
                break;
            case dot:
                result.exportDot(outputFile);
                break;
            case json:
                result.exportJson(outputFile);
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Prints a summary of the analysis results to the console.
     */
    private void printSummary(InspectorDependencyGraphBuilder.GraphAnalysisResult result) {
        System.out.println();
        System.out.println("=== Inspector Dependency Graph Analysis ===");
        System.out.println();

        // Basic statistics
        System.out.printf("Total Inspectors: %d%n", result.getTotalInspectors());
        System.out.printf("Total Dependencies: %d%n", result.getTotalDependencies());
        System.out.printf("Unique Tags: %d%n", result.getUniqueTags().size());
        System.out.println();

        // Unused tags
        if (!result.getUnusedTags().isEmpty()) {
            System.out.printf("⚠️  Unused Tags (%d):%n", result.getUnusedTags().size());
            result.getUnusedTags().forEach(tag -> System.out.println("   - " + tag));
            System.out.println();
        }

        // Potential semantic duplicates
        if (!result.getPotentialDuplicates().isEmpty()) {
            System.out.printf("🔍 Potential Semantic Duplicates (%d groups):%n",
                    result.getPotentialDuplicates().size());
            result.getPotentialDuplicates().forEach((key, tags) -> {
                System.out.println("   " + key + ":");
                tags.forEach(tag -> System.out.println("     - " + tag));
            });
            System.out.println();
        }

        // Complex dependency chains
        if (!result.getComplexChains().isEmpty()) {
            System.out.printf("🔗 Complex Dependency Chains (%d):%n", result.getComplexChains().size());
            result.getComplexChains().forEach(chain -> {
                System.out.println("   Length " + chain.size() + ": " + String.join(" → ", chain));
            });
            System.out.println();
        }

        // Top producers and consumers
        var topProducers = result.getTopTagProducers(5);
        var topConsumers = result.getTopTagConsumers(5);

        if (!topProducers.isEmpty()) {
            System.out.println("🏭 Top Tag Producers:");
            topProducers.forEach((inspector, count) -> System.out.printf("   %s: %d tags%n", inspector, count));
            System.out.println();
        }

        if (!topConsumers.isEmpty()) {
            System.out.println("🔌 Top Tag Consumers:");
            topConsumers.forEach((inspector, count) -> System.out.printf("   %s: %d dependencies%n", inspector, count));
            System.out.println();
        }

        System.out.printf("Graph exported to: %s%n", outputFile.getAbsolutePath());
    }
}
