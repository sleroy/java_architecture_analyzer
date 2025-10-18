package com.analyzer.inspectors.core.source;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.model.ProjectFile;
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
public abstract class AbstractCountRegexpInspector extends AbstractSourceFileInspector {

    private final Pattern pattern;

    /**
     * Creates a AbstractCountRegexpInspector with the specified regex pattern string.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param regexPattern the regex pattern string to compile and use for counting
     * @throws IllegalArgumentException if regexPattern is null or invalid
     */
    protected AbstractCountRegexpInspector(ResourceResolver resourceResolver, String regexPattern) {
        super(resourceResolver);
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Regex pattern cannot be null or empty");
        }
        this.pattern = Pattern.compile(regexPattern);
    }

    /**
     * Creates a AbstractCountRegexpInspector with the specified compiled Pattern.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param pattern the compiled Pattern to use for counting
     * @throws IllegalArgumentException if pattern is null
     */
    protected AbstractCountRegexpInspector(ResourceResolver resourceResolver, Pattern pattern) {
        super(resourceResolver);
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        this.pattern = pattern;
    }

    @Override
    protected final void analyzeSourceFile(ProjectFile projectFile, ResourceLocation sourceLocation, ProjectFileDecorator projectFileDecorator)
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            int count = countMatches(content);
            
            // Subclasses should override this method to set appropriate tags
            setCountResult(projectFile, count);
            
        } catch (IOException e) {
            projectFileDecorator.error("Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            projectFileDecorator.error("Error counting pattern matches: " + e.getMessage());
        }
    }

    /**
     * Sets the count result on the project file.
     * Subclasses must implement this to set appropriate tags using their TAGS constants.
     * 
     * @param projectFile the project file to set tags on
     * @param count the pattern match count
     */
    protected abstract void setCountResult(ProjectFile projectFile, int count);

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
