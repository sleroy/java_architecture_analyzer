package com.analyzer.cli;

import com.analyzer.core.AnalysisEngine;
import com.analyzer.core.Clazz;
import com.analyzer.core.CsvExporter;
import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorRegistry;
import com.analyzer.core.PackageFilter;
import com.analyzer.discovery.ClassDiscoveryEngine;
import com.analyzer.resource.CompositeResourceResolver;
import com.analyzer.resource.ResourceLocation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Inventory command implementation.
 * Creates an inventory of Java classes and packages with inspector analysis
 * results using the URI-based ResourceResolver system.
 */
@Command(name = "inventory", description = "Create an inventory of Java classes and packages")
public class InventoryCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(InventoryCommand.class);

    @Option(names = { "--source" }, description = "Path to the source files (e.g., src/main/java or /path/to/src)")
    private String sourcePath;

    @Option(names = {
            "--binary" }, description = "Path to binary archive files or directory. Supports JAR, WAR, EAR, ZIP files. For directories, scans recursively for all archive files.")
    private String binaryPath;

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
            "--plugins" }, description = "Directory containing inspector plugins (JAR files)", defaultValue = "plugins")
    private File pluginsDirectory;

    @Option(names = {
            "--packages" }, description = "Comma-separated list of package prefixes to include (e.g., com.example,com.company). Includes subpackages.", split = ",")
    private List<String> packageFilters;

    @Override
    public Integer call() throws Exception {
        logger.info("Starting Java Architecture Analysis...");

        // Validate parameters
        if (!validateParameters()) {
            return 1;
        }

        // Convert paths to URIs
        String sourceUri = convertPathToUri(sourcePath);
        String binaryUri = convertPathToUri(binaryPath);

        logger.debug("Converting paths to URIs");
        logger.debug("sourcePath = {}", sourcePath);
        logger.debug("binaryPath = {}", binaryPath);
        logger.debug("sourceUri = {}", sourceUri);
        logger.debug("binaryUri = {}", binaryUri);

        logger.info("Configuration:");
        logger.info("  Source path: {} -> URI: {}", sourcePath, sourceUri);
        logger.info("  Binary path: {} -> URI: {}", binaryPath, binaryUri);
        logger.info("  Output file: {}", outputFile);
        logger.info("  Encoding: {}", encoding);
        logger.info("  Java version: {}", javaVersion);
        logger.info("  Inspectors: {}", inspectors);
        logger.info("  Plugins directory: {}", pluginsDirectory);
        logger.info("  Package filters: {}", packageFilters);

        try {
            // Initialize ResourceResolver system
            CompositeResourceResolver resolver = CompositeResourceResolver.createDefault();

            // Create ResourceLocations from URIs
            ResourceLocation sourceLocation = sourceUri != null ? new ResourceLocation(sourceUri) : null;
            List<ResourceLocation> binaryLocations = new ArrayList<>();

            logger.debug("Creating ResourceLocations");
            logger.debug("sourceLocation = {}", sourceLocation);

            if (binaryUri != null) {
                logger.debug("Adding binaryUri to binaryLocations: {}", binaryUri);
                binaryLocations.add(new ResourceLocation(binaryUri));
            } else {
                logger.debug("binaryUri is null, not adding to binaryLocations");
            }

            logger.debug("binaryLocations.size() = {}", binaryLocations.size());
            logger.debug("binaryLocations.isEmpty() = {}", binaryLocations.isEmpty());

            // 1. Initialize Inspector Registry with plugins
            InspectorRegistry inspectorRegistry = new InspectorRegistry(pluginsDirectory, resolver);
            logger.info("{}", inspectorRegistry.getStatistics());

            // 2. Initialize Analysis Engine
            AnalysisEngine analysisEngine = new AnalysisEngine(inspectorRegistry);
            logger.info("{}", analysisEngine.getStatistics());

            // 3. Discover classes
            logger.debug("About to check condition for class discovery");
            logger.debug("sourceLocation != null = {}", (sourceLocation != null));
            logger.debug("!binaryLocations.isEmpty() = {}", (!binaryLocations.isEmpty()));
            logger.debug("Overall condition = {}", (sourceLocation != null || !binaryLocations.isEmpty()));

            if (sourceLocation != null || !binaryLocations.isEmpty()) {
                logger.debug("Condition passed, creating ClassDiscoveryEngine");
                ClassDiscoveryEngine classDiscovery = new ClassDiscoveryEngine(resolver);
                logger.debug("Calling classDiscovery.discoverClasses()");
                Map<String, Clazz> discoveredClasses = classDiscovery.discoverClasses(sourceLocation, binaryLocations);

                logger.info("Discovered {} classes", discoveredClasses.size());

                // 4. Apply package filtering if specified
                Map<String, Clazz> filteredClasses = discoveredClasses;
                if (packageFilters != null && !packageFilters.isEmpty()) {
                    filteredClasses = PackageFilter.filterClasses(discoveredClasses, packageFilters);

                    PackageFilter.FilteringStatistics stats = PackageFilter.createStatistics(
                            0, 0, // Package stats not needed for new architecture
                            discoveredClasses.size(), filteredClasses.size(),
                            packageFilters);

                    logger.info("{}", stats);
                    logger.info("After filtering: {} classes", filteredClasses.size());
                }

                // 5. Run inspector analysis on filtered classes
                Map<String, Clazz> analyzedClasses = analysisEngine.analyze(filteredClasses, inspectors);

                // 6. Generate CSV output with inspector results
                CsvExporter csvExporter = new CsvExporter(outputFile);
                List<Inspector> inspectorList = analysisEngine.getInspectors(inspectors);

                logger.info("{}", csvExporter.getStatistics(analyzedClasses, inspectorList));
                csvExporter.exportToCsv(analyzedClasses, inspectorList);
            } else {
                logger.warn("No source or binary locations found - skipping class discovery");
            }

            logger.info("Analysis completed successfully!");
            logger.info("Results written to: {}", outputFile.getAbsolutePath());

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
        // At least one input source must be specified
        if (sourcePath == null && binaryPath == null) {
            logger.error("Error: At least one of --source or --binary must be specified");
            return false;
        }

        // If source is used, Java version is mandatory
        if (sourcePath != null && javaVersion == null) {
            logger.error("Error: --java_version is required when --source is specified");
            return false;
        }

        // Validate plugins directory
        if (pluginsDirectory != null && !pluginsDirectory.exists()) {
            logger.warn("Warning: Plugins directory does not exist: {}", pluginsDirectory);
            logger.info("Creating plugins directory...");
            pluginsDirectory.mkdirs();
        }

        return true;
    }

    // Getters for testing and internal use
    public String getSourceUri() {
        return convertPathToUri(sourcePath);
    }

    public String getBinaryUri() {
        return convertPathToUri(binaryPath);
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getBinaryPath() {
        return binaryPath;
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

    public File getPluginsDirectory() {
        return pluginsDirectory;
    }

    public List<String> getPackageFilters() {
        return packageFilters;
    }
}
