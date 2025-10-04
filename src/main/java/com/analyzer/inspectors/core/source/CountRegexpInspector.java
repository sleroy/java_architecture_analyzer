package com.analyzer.inspectors.core.source;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for source file inspectors that count regex pattern occurrences.
 * Returns integer count of how many times the pattern matches in the source file.
 * 
 * Subclasses must implement getName() and getColumnName() methods and can override
 * the counting logic by providing a custom Pattern or overriding countMatches().
 */
public abstract class CountRegexpInspector extends SourceFileInspector {

    private final Pattern pattern;

    /**
     * Creates a CountRegexpInspector with the specified regex pattern string.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param regexPattern the regex pattern string to compile and use for counting
     * @throws IllegalArgumentException if regexPattern is null or invalid
     */
    protected CountRegexpInspector(ResourceResolver resourceResolver, String regexPattern) {
        super(resourceResolver);
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Regex pattern cannot be null or empty");
        }
        this.pattern = Pattern.compile(regexPattern);
    }

    /**
     * Creates a CountRegexpInspector with the specified compiled Pattern.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param pattern the compiled Pattern to use for counting
     * @throws IllegalArgumentException if pattern is null
     */
    protected CountRegexpInspector(ResourceResolver resourceResolver, Pattern pattern) {
        super(resourceResolver);
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        this.pattern = pattern;
    }

    @Override
    protected final InspectorResult analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation)
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            int count = countMatches(content);
            return new InspectorResult(getColumnName(), count);
        } catch (IOException e) {
            return InspectorResult.error(getColumnName(), "Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "Error counting pattern matches: " + e.getMessage());
        }
    }

    /**
     * Counts the number of times the pattern matches in the given content.
     * Default implementation uses Matcher.find() to count all non-overlapping matches.
     * Subclasses can override this method to provide different counting logic.
     * 
     * @param content the file content to search in
     * @return the number of pattern matches found
     */
    protected int countMatches(String content) {
        if (content == null) {
            return 0;
        }
        
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Gets the compiled pattern used by this inspector.
     * 
     * @return the Pattern instance
     */
    protected Pattern getPattern() {
        return pattern;
    }
}
