package com.analyzer.inspectors.core.detection;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Exhaustive inspector that marks files with "source_file" tag if they match
 * supported source file extensions defined in application.properties.
 * <p>
 * This inspector serves as the foundation for all source file analysis by:
 * 1. Reading supported extensions from analyzer.file.extensions configuration
 * 2. Reading supported filenames from analyzer.file.names configuration
 * 3. Tagging matching files with "source_file" tag
 * 4. Acting as a dependency for other AbstractSourceFileInspector
 * implementations
 * <p>
 * Other source inspectors should declare @InspectorDependencies(need =
 * {SourceFileTagDetector.class})
 * to ensure they only process validated source files.
 * <p>
 * Uses the new annotation-based dependency system - no manual dependency
 * management needed.
 */
@InspectorDependencies(produces = {SourceFileTagDetector.TAGS.TAG_SOURCE_FILE}) // No dependencies - fundamental
// detector
public class SourceFileTagDetector implements Inspector<ProjectFile> {

    private static final String CONFIG_FILE = "/application.properties";
    private static final String EXTENSIONS_KEY = "analyzer.file.extensions";
    private static final String FILENAMES_KEY = "analyzer.file.names";
    private final Set<String> supportedExtensions;
    private final Set<String> supportedFilenames;
    private final Properties config;
    public SourceFileTagDetector() {
        this.config = loadConfiguration();
        this.supportedExtensions = loadSupportedExtensions();
        this.supportedFilenames = loadSupportedFilenames();
    }

    /**
     * Constructor for testing with custom configuration
     */
    public SourceFileTagDetector(Properties config) {
        this.config = config;
        this.supportedExtensions = loadSupportedExtensions();
        this.supportedFilenames = loadSupportedFilenames();
    }

    @Override
    public String getName() {
        return "SourceFileTagDetector";
    }

    public boolean supports(ProjectFile projectFile) {
        if (projectFile == null) {
            return false;
        }

        String fileName = projectFile.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }

        // Check if the exact filename is in the supported filenames list
        if (supportedFilenames.contains(fileName.toLowerCase())) {
            return true;
        }

        // Check if the file extension is in the supported extensions list
        String fileExtension = projectFile.getFileExtension();
        if (fileExtension != null && !fileExtension.isEmpty()) {
            // Normalize extension to include the dot
            String normalizedExtension = fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
            return supportedExtensions.contains(normalizedExtension.toLowerCase());
        }

        return false;
    }

    @Override
    public void decorate(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        if (!supports(projectFile)) {
            projectFileDecorator.notApplicable();
            return;
        }

        try {
            // Tag the file as a source file using own TAGS constant
            projectFile.setTag(TAGS.TAG_SOURCE_FILE, true);


            // Also set file type specific tags for additional categorization
            String fileExtension = projectFile.getFileExtension();
            if (fileExtension != null && !fileExtension.isEmpty()) {
                String normalizedExtension = fileExtension.startsWith(".") ? fileExtension.substring(1) : fileExtension;
                setSourceTypeTag(projectFile, normalizedExtension);
            }

            // Tags are already set on projectFile, no need to call success()

        } catch (Exception e) {
            projectFileDecorator.error("Error tagging source file: " + e.getMessage());
        }
    }

    private void setSourceTypeTag(ProjectFile projectFile, String extension) {

        projectFile.setTag(extension.toLowerCase() + ".source_type", true);
    }

    /**
     * Load configuration from application.properties
     */
    private Properties loadConfiguration() {
        Properties props = new Properties();

        try (InputStream inputStream = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new RuntimeException("Configuration file not found: " + CONFIG_FILE);
            }
            props.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from " + CONFIG_FILE, e);
        }

        return props;
    }

    /**
     * Load supported file extensions from configuration
     */
    private Set<String> loadSupportedExtensions() {
        String extensionsValue = config.getProperty(EXTENSIONS_KEY, "");
        Set<String> extensions = new HashSet<>();

        if (!extensionsValue.trim().isEmpty()) {
            String[] extensionArray = extensionsValue.split(",");
            for (String ext : extensionArray) {
                String trimmed = ext.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    // Ensure extension starts with a dot
                    if (!trimmed.startsWith(".")) {
                        trimmed = "." + trimmed;
                    }
                    extensions.add(trimmed);
                }
            }
        }

        return extensions;
    }

    /**
     * Load supported specific filenames from configuration
     */
    private Set<String> loadSupportedFilenames() {
        String filenamesValue = config.getProperty(FILENAMES_KEY, "");
        Set<String> filenames = new HashSet<>();

        if (!filenamesValue.trim().isEmpty()) {
            String[] filenameArray = filenamesValue.split(",");
            for (String filename : filenameArray) {
                String trimmed = filename.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    filenames.add(trimmed);
                }
            }
        }

        return filenames;
    }

    /**
     * Get the list of supported extensions (for testing/debugging)
     */
    public Set<String> getSupportedExtensions() {
        return new HashSet<>(supportedExtensions);
    }

    /**
     * Get the list of supported filenames (for testing/debugging)
     */
    public Set<String> getSupportedFilenames() {
        return new HashSet<>(supportedFilenames);
    }

    /**
     * Get configuration details for debugging
     */
    public String getConfigurationSummary() {
        return String.format(
                "SourceFileTagDetector Configuration:\n" +
                        "  Supported Extensions (%d): %s\n" +
                        "  Supported Filenames (%d): %s",
                supportedExtensions.size(), supportedExtensions,
                supportedFilenames.size(), supportedFilenames);
    }

    @Override
    public String toString() {
        return String.format("%s{extensions=%d, filenames=%d}",
                getName(), supportedExtensions.size(), supportedFilenames.size());
    }

    public static class TAGS {
        public static final String TAG_SOURCE_FILE = "source_file";
    }
}
