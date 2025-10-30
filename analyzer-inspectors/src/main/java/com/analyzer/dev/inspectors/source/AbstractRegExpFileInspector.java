package com.analyzer.dev.inspectors.source;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.resource.ResourceResolver;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Abstract base class for source file inspectors that perform regex pattern
 * matching.
 * Returns boolean result indicating whether the pattern matches in the source
 * file.
 * 
 * Subclasses must implement getName() and getColumnName() methods and can
 * override
 * the pattern matching logic by providing a custom Pattern or overriding
 * matches().
 */
public abstract class AbstractRegExpFileInspector extends AbstractSourceFileInspector {

    private final Pattern pattern;

    /**
     * Creates a AbstractRegExpFileInspector with the specified regex pattern
     * string.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param regexPattern     the regex pattern string to compile and use for
     *                         matching
     * @throws IllegalArgumentException if regexPattern is null or invalid
     */
    protected AbstractRegExpFileInspector(ResourceResolver resourceResolver, String regexPattern, LocalCache localCache) {
        super(resourceResolver, localCache);
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Regex pattern cannot be null or empty");
        }
        this.pattern = Pattern.compile(regexPattern);
    }

    /**
     * Creates a AbstractRegExpFileInspector with the specified compiled Pattern.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param pattern          the compiled Pattern to use for matching
     * @throws IllegalArgumentException if pattern is null
     */
    protected AbstractRegExpFileInspector(ResourceResolver resourceResolver, Pattern pattern, LocalCache localCache) {
        super(resourceResolver, localCache);
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        this.pattern = pattern;
    }

    @Override
    protected final void analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation,
            NodeDecorator<ProjectFile> decorator)
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            boolean matchFound = matches(content);
            setMatchResult(clazz, matchFound, decorator);
        } catch (IOException e) {
            decorator.error("Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            decorator.error("Error matching pattern: " + e.getMessage());
        }
    }

    /**
     * Sets the match result on the project file.
     * Subclasses must implement this to set appropriate tags/properties.
     * 
     * @param projectFile the project file to set result on
     * @param matchFound  whether the pattern matched
     * @param decorator   the decorator for setting properties and tags
     */
    protected abstract void setMatchResult(ProjectFile projectFile, boolean matchFound,
            NodeDecorator<ProjectFile> decorator);

    protected abstract String getColumnName();

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
