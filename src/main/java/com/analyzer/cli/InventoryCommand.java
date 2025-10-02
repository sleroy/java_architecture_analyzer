package com.analyzer.cli;

import com.analyzer.core.AnalysisEngine;
import com.analyzer.core.ClassCsvRecord;
import com.analyzer.core.Clazz;
import com.analyzer.core.CsvExporter;
import com.analyzer.core.CsvRecord;
import com.analyzer.core.InspectorRegistry;
import com.analyzer.core.Package;
import com.analyzer.core.PackageCsvRecord;
import com.analyzer.core.PackageFilter;
import com.analyzer.discovery.ClassDiscoveryEngine;
import com.analyzer.discovery.PackageDiscoveryEngine;
import com.analyzer.resource.CompositeResourceResolver;
import com.analyzer.resource.ResourceLocation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Option(names = { "--source" }, description = "Path to the source files (e.g., src/main/java or /path/to/src)")
    private String sourcePath;

    @Option(names = { "--binary" }, description = "Path to JAR files (e.g., lib/app.jar or /path/to/lib.jar)")
    private String binaryPath;

    @Option(names = { "--war" }, description = "Path to WAR files (e.g., target/app.war or /path/to/app.war)")
    private String warPath;

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
        System.out.println("Starting Java Architecture Analysis...");

        // Validate parameters
        if (!validateParameters()) {
            return 1;
        }

        // Convert paths to URIs
        String sourceUri = convertPathToUri(sourcePath);
        String binaryUri = convertPathToUri(binaryPath);
        String warUri = convertPathToUri(warPath);

        System.out.println("Configuration:");
        System.out.println("  Source path: " + sourcePath + " -> URI: " + sourceUri);
        System.out.println("  Binary path: " + binaryPath + " -> URI: " + binaryUri);
        System.out.println("  WAR path: " + warPath + " -> URI: " + warUri);
        System.out.println("  Output file: " + outputFile);
        System.out.println("  Encoding: " + encoding);
        System.out.println("  Java version: " + javaVersion);
        System.out.println("  Inspectors: " + inspectors);
        System.out.println("  Plugins directory: " + pluginsDirectory);
        System.out.println("  Package filters: " + packageFilters);

        try {
            // Initialize ResourceResolver system
            CompositeResourceResolver resolver = CompositeResourceResolver.createDefault();

            // Create ResourceLocations from URIs
            ResourceLocation sourceLocation = sourceUri != null ? new ResourceLocation(sourceUri) : null;
            List<ResourceLocation> binaryLocations = new ArrayList<>();

            if (binaryUri != null) {
                binaryLocations.add(new ResourceLocation(binaryUri));
            }
            if (warUri != null) {
                binaryLocations.add(new ResourceLocation(warUri));
            }

            // 1. Initialize Inspector Registry with plugins
            InspectorRegistry inspectorRegistry = new InspectorRegistry(pluginsDirectory, resolver);
            System.out.println(inspectorRegistry.getStatistics());

            // 2. Initialize Analysis Engine
            AnalysisEngine analysisEngine = new AnalysisEngine(inspectorRegistry, resolver);
            System.out.println(analysisEngine.getStatistics());

            // 3. Discover classes
            if (sourceLocation != null || !binaryLocations.isEmpty()) {
                ClassDiscoveryEngine classDiscovery = new ClassDiscoveryEngine(resolver);
                Map<String, Clazz> discoveredClasses = classDiscovery.discoverClasses(sourceLocation, binaryLocations);

                System.out.println("Discovered " + discoveredClasses.size() + " classes");

                // 4. Apply package filtering if specified
                Map<String, Clazz> filteredClasses = discoveredClasses;
                if (packageFilters != null && !packageFilters.isEmpty()) {
                    filteredClasses = PackageFilter.filterClasses(discoveredClasses, packageFilters);

                    PackageFilter.FilteringStatistics stats = PackageFilter.createStatistics(
                            0, 0, // Package stats not needed for new architecture
                            discoveredClasses.size(), filteredClasses.size(),
                            packageFilters);

                    System.out.println(stats);
                    System.out.println("After filtering: " + filteredClasses.size() + " classes");
                }

                // 5. Run inspector analysis on filtered classes
                Map<String, Clazz> analyzedClasses = analysisEngine.analyze(filteredClasses, inspectors);

                // 6. Generate CSV output with inspector results
                CsvExporter csvExporter = new CsvExporter(outputFile);
                List<String> inspectorNames = analysisEngine.getInspectorNames(inspectors);

                System.out.println(csvExporter.getStatistics(analyzedClasses, inspectorNames));
                csvExporter.exportToCsv(analyzedClasses, inspectorNames);
            }

            System.out.println("Analysis completed successfully!");
            System.out.println("Results written to: " + outputFile.getAbsolutePath());

            return 0;
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("Error converting path to URI: " + path + " - " + e.getMessage());
            return null;
        }
    }

    private boolean validateParameters() {
        // At least one input source must be specified
        if (sourcePath == null && binaryPath == null && warPath == null) {
            System.err.println("Error: At least one of --source, --binary, --war must be specified");
            return false;
        }

        // If source is used, Java version is mandatory
        if (sourcePath != null && javaVersion == null) {
            System.err.println("Error: --java_version is required when --source is specified");
            return false;
        }

        // Validate plugins directory
        if (pluginsDirectory != null && !pluginsDirectory.exists()) {
            System.out.println("Warning: Plugins directory does not exist: " + pluginsDirectory);
            System.out.println("Creating plugins directory...");
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

    public String getWarUri() {
        return convertPathToUri(warPath);
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getBinaryPath() {
        return binaryPath;
    }

    public String getWarPath() {
        return warPath;
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
