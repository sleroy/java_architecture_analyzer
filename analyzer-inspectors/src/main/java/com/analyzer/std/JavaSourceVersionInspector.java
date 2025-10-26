package com.analyzer.rules.std;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Inspector that detects the Java version of Java files.
 * For binary .class files: reads the major version from bytecode.
 * For source .java files: attempts progressive parsing with different Java
 * versions.
 * Sets standardized tags for Java version information.
 */
@InspectorDependencies(requires = {InspectorTags.TAG_JAVA_DETECTED}, produces = {
        JavaSourceVersionInspector.TAGS.TAG_JAVA_VERSION,
        JavaSourceVersionInspector.TAGS.TAG_JAVA_MAJOR_VERSION,
        JavaSourceVersionInspector.TAGS.TAG_JAVA_VERSION_SOURCE})
public class JavaSourceVersionInspector implements Inspector<ProjectFile> {

    // Version source values
    public static final String VERSION_SOURCE_BYTECODE = "bytecode";
    public static final String VERSION_SOURCE_PARSER = "parser";
    public static final String VERSION_SOURCE_UNKNOWN = "unknown";
    private static final Logger logger = LoggerFactory.getLogger(JavaSourceVersionInspector.class);
    // Major version to Java version mapping
    private static final Map<Integer, String> MAJOR_VERSION_MAP = new HashMap<>();

    static {
        MAJOR_VERSION_MAP.put(45, "1.1");
        MAJOR_VERSION_MAP.put(46, "1.2");
        MAJOR_VERSION_MAP.put(47, "1.3");
        MAJOR_VERSION_MAP.put(48, "1.4");
        MAJOR_VERSION_MAP.put(49, "5");
        MAJOR_VERSION_MAP.put(50, "6");
        MAJOR_VERSION_MAP.put(51, "7");
        MAJOR_VERSION_MAP.put(52, "8");
        MAJOR_VERSION_MAP.put(53, "9");
        MAJOR_VERSION_MAP.put(54, "10");
        MAJOR_VERSION_MAP.put(55, "11");
        MAJOR_VERSION_MAP.put(56, "12");
        MAJOR_VERSION_MAP.put(57, "13");
        MAJOR_VERSION_MAP.put(58, "14");
        MAJOR_VERSION_MAP.put(59, "15");
        MAJOR_VERSION_MAP.put(60, "16");
        MAJOR_VERSION_MAP.put(61, "17");
        MAJOR_VERSION_MAP.put(62, "18");
        MAJOR_VERSION_MAP.put(63, "19");
        MAJOR_VERSION_MAP.put(64, "20");
        MAJOR_VERSION_MAP.put(65, "21");
        MAJOR_VERSION_MAP.put(66, "22");
        MAJOR_VERSION_MAP.put(67, "23");
    }

    private final GraphRepository graphRepository;

    @Inject
    public JavaSourceVersionInspector(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    // Default constructor for backward compatibility
    public JavaSourceVersionInspector() {
        this.graphRepository = null;
    }

    @Override
    public String getName() {
        return "JavaSourceVersionInspector";
    }

    @Override
    public boolean canProcess(ProjectFile objectToAnalyze) {
        return Inspector.super.canProcess(objectToAnalyze);
    }

    @Override
    public void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator) {
        try {
            String version = null;
            String versionSource = VERSION_SOURCE_UNKNOWN;

            if (projectFile.hasFileExtension("class")) {
                // Binary class file - read major version from bytecode
                version = detectBinaryVersion(projectFile);
                versionSource = VERSION_SOURCE_BYTECODE;
            } else if (projectFile.hasFileExtension("java")) {
                // Source file - try progressive parsing (placeholder for now)
                version = detectSourceVersion(projectFile);
                versionSource = VERSION_SOURCE_PARSER;
            }

            if (version != null) {
                decorator.setProperty(TAGS.TAG_JAVA_VERSION, version);
                decorator.setProperty(TAGS.TAG_JAVA_VERSION_SOURCE, versionSource);

                // Also set major version for binary files
                if (VERSION_SOURCE_BYTECODE.equals(versionSource)) {
                    Integer majorVersion = getMajorVersionForJavaVersion(version);
                    if (majorVersion != null) {
                        decorator.setProperty(TAGS.TAG_JAVA_MAJOR_VERSION, majorVersion);
                    }
                }

                logger.debug("Detected Java version {} for file: {} (source: {})",
                        version, projectFile.getFileName(), versionSource);
            } else {
                logger.debug("Could not detect Java version for file: {}", projectFile.getFileName());
            }

        } catch (Exception e) {
            logger.warn("Error detecting Java version for file {}: {}", projectFile.getFilePath(), e.getMessage());
            decorator.error("ERROR: " + e.getMessage());
        }
    }

    /**
     * Detect Java version from binary .class file by reading major version from
     * bytecode.
     */
    private String detectBinaryVersion(ProjectFile projectFile) {
        try (FileInputStream fis = new FileInputStream(projectFile.getFilePath().toFile())) {
            // Skip magic number (4 bytes) and minor version (2 bytes)
            byte[] buffer = new byte[8];
            int bytesRead = fis.read(buffer);

            if (bytesRead < 8) {
                logger.warn("Class file {} is too short to read version information", projectFile.getFileName());
                return null;
            }

            // Check magic number (0xCAFEBABE)
            int magic = ((buffer[0] & 0xFF) << 24) | ((buffer[1] & 0xFF) << 16) |
                    ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
            if (magic != 0xCAFEBABE) {
                logger.warn("File {} is not a valid Java class file (invalid magic number)", projectFile.getFileName());
                return null;
            }

            // Read major version (bytes 6-7)
            int majorVersion = ((buffer[6] & 0xFF) << 8) | (buffer[7] & 0xFF);

            String javaVersion = MAJOR_VERSION_MAP.get(majorVersion);
            if (javaVersion == null) {
                logger.warn("Unknown major version {} for class file {}", majorVersion, projectFile.getFileName());
                return "Unknown (" + majorVersion + ")";
            }

            return javaVersion;

        } catch (IOException e) {
            logger.warn("Error reading class file {}: {}", projectFile.getFilePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Detect Java version from source .java file using progressive parsing.
     * This is a placeholder implementation - would need JavaParser dependency for
     * full implementation.
     */
    private String detectSourceVersion(ProjectFile projectFile) {
        try {
            // For now, just return a default version
            // TODO: Implement progressive parsing with JavaParser
            String content = Files.readString(projectFile.getFilePath());

            // Simple heuristics for now
            if (content.contains("record ") || content.contains("sealed ") || content.contains("permits ")) {
                return "17"; // Records and sealed classes introduced in Java 17
            }
            if (content.contains("var ") && content.contains("->")) {
                return "11"; // Local variable type inference with lambdas
            }
            if (content.contains("->") || content.contains("::")) {
                return "8"; // Lambda expressions
            }

            // Default to Java 8 for source files if no specific features detected
            return "8";

        } catch (IOException e) {
            logger.warn("Error reading source file {}: {}", projectFile.getFilePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Get major version number for a given Java version string.
     */
    private Integer getMajorVersionForJavaVersion(String javaVersion) {
        for (Map.Entry<Integer, String> entry : MAJOR_VERSION_MAP.entrySet()) {
            if (entry.getValue().equals(javaVersion)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Tag constants owned by this inspector.
     * Other inspectors can reference these using JavaSourceVersionInspector.TAGS.TAG_*
     */
    public static class TAGS {
        public static final String TAG_JAVA_VERSION = "java.version";
        public static final String TAG_JAVA_MAJOR_VERSION = "java.major_version";
        public static final String TAG_JAVA_VERSION_SOURCE = "java.version_source";
    }

}
