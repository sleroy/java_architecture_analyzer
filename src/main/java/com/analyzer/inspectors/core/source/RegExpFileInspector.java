package com.analyzer.inspectors.core.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Abstract base class for source file inspectors that perform regex pattern matching.
 * Returns boolean result indicating whether the pattern matches in the source file.
 * 
 * Subclasses must implement getName() and getColumnName() methods and can override
 * the pattern matching logic by providing a custom Pattern or overriding matches().
 */
public abstract class RegExpFileInspector extends SourceFileInspector {

    private final Pattern pattern;

    /**
     * Creates a RegExpFileInspector with the specified regex pattern string.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param regexPattern the regex pattern string to compile and use for matching
     * @throws IllegalArgumentException if regexPattern is null or invalid
     */
    protected RegExpFileInspector(ResourceResolver resourceResolver, String regexPattern) {
        super(resourceResolver);
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Regex pattern cannot be null or empty");
        }
        this.pattern = Pattern.compile(regexPattern);
    }

    /**
     * Creates a RegExpFileInspector with the specified compiled Pattern.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param pattern the compiled Pattern to use for matching
     * @throws IllegalArgumentException if pattern is null
     */
    protected RegExpFileInspector(ResourceResolver resourceResolver, Pattern pattern) {
        super(resourceResolver);
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        this.pattern = pattern;
    }

    @Override
    protected final InspectorResult analyzeSourceFile(Clazz clazz, ResourceLocation sourceLocation) 
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            boolean matches = matches(content);
            return new InspectorResult(getColumnName(), matches);
        } catch (IOException e) {
            return InspectorResult.error(getColumnName(), "Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "Error analyzing pattern: " + e.getMessage());
        }
    }

    /**
     * Determines if the pattern matches in the given content.
     * Default implementation uses Pattern.matcher().find() to check for any match.
     * Subclasses can override this method to provide different matching logic.
     * 
     * @param content the file content to search in
     * @return true if the pattern matches, false otherwise
     */
    protected boolean matches(String content) {
        if (content == null) {
            return false;
        }
        return pattern.matcher(content).find();
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
