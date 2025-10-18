package com.analyzer.core.filter;
import com.analyzer.core.inspector.InspectorDependencies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;

/**
 * Filter for ignoring files during analysis based on gitignore-style patterns.
 * Reads ignore patterns from application.properties and provides filtering
 * functionality.
 */
public class FileIgnoreFilter {

    private static final Logger logger = LoggerFactory.getLogger(FileIgnoreFilter.class);

    private static final String IGNORE_PATTERNS_PROPERTY = "analyzer.ignore.patterns";

    private final List<PathMatcher> ignoreMatchers;
    private final Set<String> ignorePatterns;


    /**
     * Create a FileIgnoreFilter with specified patterns.
     *
     * @param patterns the ignore patterns to use (gitignore-style)
     */
    public FileIgnoreFilter(Collection<String> patterns) {
        this.ignorePatterns = new LinkedHashSet<>();
        this.ignoreMatchers = new ArrayList<>();

        if (patterns != null) {
            for (String pattern : patterns) {
                addPattern(pattern);
            }
        }

        logger.debug("Created FileIgnoreFilter with {} patterns: {}", ignorePatterns.size(), ignorePatterns);
    }

    /**
     * Create a FileIgnoreFilter by loading patterns from application.properties.
     * Falls back to default patterns if loading fails.
     *
     * @return configured FileIgnoreFilter
     */
    public static FileIgnoreFilter fromApplicationProperties() {
        try {
            Properties props = loadApplicationProperties();
            String patternsProperty = props.getProperty(IGNORE_PATTERNS_PROPERTY);

            if (patternsProperty != null && !patternsProperty.trim().isEmpty()) {
                // Split on comma and clean up patterns
                String[] patterns = patternsProperty.split(",");
                List<String> cleanPatterns = new ArrayList<>();

                for (String pattern : patterns) {
                    String clean = pattern.trim();
                    if (!clean.isEmpty()) {
                        cleanPatterns.add(clean);
                    }
                }

                logger.info("Loaded {} ignore patterns from application.properties", cleanPatterns.size());
                return new FileIgnoreFilter(cleanPatterns);
            } else {
                logger.info("No ignore patterns found in application.properties, using defaults");
                return new FileIgnoreFilter(List.of());
            }
        } catch (Exception e) {
            logger.warn("Failed to load ignore patterns from application.properties: {}, using defaults",
                    e.getMessage());
            return new FileIgnoreFilter(List.of());
        }
    }

    /**
     * Load application.properties from the classpath.
     *
     * @return loaded Properties object
     * @throws IOException if loading fails
     */
    private static Properties loadApplicationProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream is = FileIgnoreFilter.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                logger.debug("application.properties not found on classpath");
            }
        }
        return props;
    }

    /**
     * Add a single ignore pattern.
     *
     * @param pattern the pattern to add (gitignore-style)
     */
    public void addPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return;
        }

        String cleanPattern = pattern.trim();

        // Skip comments and empty lines
        if (cleanPattern.startsWith("#") || cleanPattern.isEmpty()) {
            return;
        }

        ignorePatterns.add(cleanPattern);

        try {
            // Convert gitignore-style pattern to glob pattern
            String globPattern = convertToGlobPattern(cleanPattern);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
            ignoreMatchers.add(matcher);

            logger.debug("Added ignore pattern: '{}' -> glob: '{}'", cleanPattern, globPattern);
        } catch (Exception e) {
            logger.warn("Failed to compile ignore pattern '{}': {}", cleanPattern, e.getMessage());
        }
    }

    /**
     * Convert gitignore-style pattern to glob pattern.
     *
     * @param gitignorePattern the gitignore pattern
     * @return equivalent glob pattern
     */
    private String convertToGlobPattern(String gitignorePattern) {
        String pattern = gitignorePattern;

        // Handle directory patterns (ending with /)
        if (pattern.endsWith("/")) {
            pattern = pattern.substring(0, pattern.length() - 1) + "/**";
        }

        // Handle patterns starting with /
        if (pattern.startsWith("/")) {
            pattern = pattern.substring(1);
        }

        // If pattern doesn't contain / and doesn't start with **, make it match
        // anywhere
        if (!pattern.contains("/") && !pattern.startsWith("**")) {
            pattern = "**/" + pattern;
        }

        return pattern;
    }

    /**
     * Check if a file should be ignored based on the configured patterns.
     *
     * @param filePath    the absolute file path
     * @param projectRoot the project root path (for relative path calculation)
     * @return true if the file should be ignored
     */
    public boolean shouldIgnore(Path filePath, Path projectRoot) {
        if (filePath == null || projectRoot == null) {
            return false;
        }

        try {
            // Get relative path from project root
            Path relativePath = projectRoot.relativize(filePath);
            String relativePathString = relativePath.toString().replace('\\', '/');

            // Check against all matchers
            for (PathMatcher matcher : ignoreMatchers) {
                if (matcher.matches(relativePath)) {
                    logger.debug("File ignored by pattern: {}", relativePathString);
                    return true;
                }
            }

            // Also check against the absolute path string for patterns that might need it
            String absolutePathString = filePath.toString().replace('\\', '/');
            for (PathMatcher matcher : ignoreMatchers) {
                if (matcher.matches(filePath)) {
                    logger.debug("File ignored by absolute pattern: {}", absolutePathString);
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            logger.debug("Error checking ignore status for {}: {}", filePath, e.getMessage());
            return false; // Don't ignore on error
        }
    }

    /**
     * Get all configured ignore patterns.
     *
     * @return unmodifiable set of ignore patterns
     */
    public Set<String> getIgnorePatterns() {
        return Collections.unmodifiableSet(ignorePatterns);
    }

    /**
     * Get the number of configured patterns.
     *
     * @return pattern count
     */
    public int getPatternCount() {
        return ignorePatterns.size();
    }

    /**
     * Check if any patterns are configured.
     *
     * @return true if patterns are configured
     */
    public boolean hasPatterns() {
        return !ignorePatterns.isEmpty();
    }

    @Override
    public String toString() {
        return "FileIgnoreFilter{" +
                "patterns=" + ignorePatterns.size() +
                ", patterns=" + ignorePatterns +
                '}';
    }
}
