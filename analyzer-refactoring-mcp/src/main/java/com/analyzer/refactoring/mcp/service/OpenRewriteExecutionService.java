package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool.PatternMatch;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool.LocationInfo;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.SourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for executing Groovy-based OpenRewrite visitors on Java source files.
 * 
 * This service:
 * - Parses Java source files using OpenRewrite's JavaParser
 * - Executes compiled Groovy visitor scripts on the parsed AST
 * - Extracts pattern matches with accurate location information
 * - Handles parsing and execution errors gracefully
 */
@Service
public class OpenRewriteExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(OpenRewriteExecutionService.class);

    /**
     * Execute a visitor script on a Java project.
     * 
     * @param script      The compiled visitor script
     * @param projectPath Absolute path to the project root
     * @param filePaths   Optional list of relative file paths to process (null =
     *                    all files)
     * @return List of pattern matches found
     * @throws ExecutionException if execution fails
     */
    public List<PatternMatch> executeVisitorOnProject(
            OpenRewriteVisitorScript script,
            String projectPath,
            List<String> filePaths) throws ExecutionException {

        logger.info("Executing visitor on project: {}", projectPath);

        try {
            Path projectRoot = Paths.get(projectPath);

            // Determine which files to process
            List<Path> javaFiles = resolveJavaFiles(projectRoot, filePaths);

            if (javaFiles.isEmpty()) {
                logger.warn("No Java files found to process in: {}", projectPath);
                return new ArrayList<>();
            }

            logger.info("Processing {} Java files", javaFiles.size());

            // Parse all Java files
            List<SourceFile> parsedFiles = parseJavaFiles(javaFiles);

            // Execute visitor on each file and collect matches
            List<PatternMatch> allMatches = new ArrayList<>();
            for (SourceFile sourceFile : parsedFiles) {
                if (sourceFile instanceof J.CompilationUnit) {
                    List<PatternMatch> fileMatches = executeVisitorOnFile(
                            script, (J.CompilationUnit) sourceFile);
                    allMatches.addAll(fileMatches);
                }
            }

            logger.info("Execution completed: {} matches found", allMatches.size());
            return allMatches;

        } catch (IOException e) {
            throw new ExecutionException("Failed to read Java files: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExecutionException("Failed to execute visitor: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a visitor script on a single compilation unit.
     * 
     * @param script          The compiled visitor script
     * @param compilationUnit The parsed Java file
     * @return List of pattern matches in this file
     * @throws ExecutionException if execution fails
     */
    private List<PatternMatch> executeVisitorOnFile(
            OpenRewriteVisitorScript script,
            J.CompilationUnit compilationUnit) throws ExecutionException {

        try {
            // Execute the Groovy script with the compilation unit
            CompiledScript compiledScript = script.getCompiledScript();

            // The script expects 'compilationUnit' variable
            javax.script.ScriptEngine engine = compiledScript.getEngine();
            engine.put("compilationUnit", compilationUnit);

            // Execute and get result
            Object result = compiledScript.eval();

            // Extract matches from the result
            return extractMatches(compilationUnit, result);

        } catch (ScriptException e) {
            String filePath = compilationUnit.getSourcePath().toString();
            logger.error("Script execution failed for file: {}", filePath, e);
            throw new ExecutionException(
                    "Script execution failed for " + filePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parse Java files using OpenRewrite's JavaParser.
     * 
     * @param javaFiles List of Java file paths to parse
     * @return List of parsed SourceFile objects
     * @throws IOException if file reading fails
     */
    private List<SourceFile> parseJavaFiles(List<Path> javaFiles) throws IOException {
        logger.debug("Parsing {} Java files", javaFiles.size());

        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            logger.warn("Parse error encountered", t);
        });

        // Use JavaParser with Java 21 support from rewrite-java-21 dependency
        JavaParser parser = JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(false)
                .build();

        List<SourceFile> result = parser.parse(javaFiles, null, ctx)
                .collect(Collectors.toList());

        logger.debug("Successfully parsed {} files", result.size());
        return result;
    }

    /**
     * Resolve which Java files to process.
     * 
     * @param projectRoot The project root directory
     * @param filePaths   Optional list of relative file paths (null = all files)
     * @return List of absolute paths to Java files
     * @throws IOException if directory traversal fails
     */
    private List<Path> resolveJavaFiles(Path projectRoot, List<String> filePaths)
            throws IOException {

        if (filePaths != null && !filePaths.isEmpty()) {
            // Process specified files only
            return filePaths.stream()
                    .map(relativePath -> projectRoot.resolve(relativePath))
                    .filter(Files::exists)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } else {
            // Find all Java files recursively
            try (Stream<Path> walk = Files.walk(projectRoot)) {
                return walk
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }
        }
    }

    /**
     * Extract pattern matches from visitor execution result.
     * 
     * The Groovy visitor script is expected to return a List of Maps,
     * where each Map represents a match with structure:
     * {
     * nodeId: String,
     * nodeType: String,
     * className: String (optional),
     * methodName: String (optional),
     * fieldName: String (optional),
     * location: {
     * file: String,
     * line: int,
     * column: int
     * }
     * }
     * 
     * @param compilationUnit The source file being processed
     * @param result          The result from visitor execution
     * @return List of PatternMatch objects
     */
    @SuppressWarnings("unchecked")
    private List<PatternMatch> extractMatches(
            J.CompilationUnit compilationUnit,
            Object result) {

        List<PatternMatch> matches = new ArrayList<>();

        if (result == null) {
            logger.debug("Visitor returned null result for: {}",
                    compilationUnit.getSourcePath());
            return matches;
        }

        if (!(result instanceof List)) {
            logger.warn("Visitor returned unexpected type: {} for: {}",
                    result.getClass().getName(), compilationUnit.getSourcePath());
            return matches;
        }

        List<?> resultList = (List<?>) result;
        String sourceFile = compilationUnit.getSourcePath().toString();

        for (Object item : resultList) {
            try {
                if (item instanceof Map) {
                    PatternMatch match = extractMatchFromMap(
                            (Map<String, Object>) item, sourceFile);
                    if (match != null) {
                        matches.add(match);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to extract match from item: {}", item, e);
            }
        }

        logger.debug("Extracted {} matches from: {}", matches.size(), sourceFile);
        return matches;
    }

    /**
     * Convert a Map representing a match into a PatternMatch object.
     */
    @SuppressWarnings("unchecked")
    private PatternMatch extractMatchFromMap(Map<String, Object> map, String defaultFile) {
        PatternMatch match = new PatternMatch();

        // Extract basic fields
        match.setNodeId((String) map.get("nodeId"));
        match.setNodeType((String) map.get("nodeType"));
        match.setClassName((String) map.get("className"));
        match.setMethodName((String) map.get("methodName"));
        match.setFieldName((String) map.get("fieldName"));

        // Extract location
        Object locationObj = map.get("location");
        if (locationObj instanceof Map) {
            Map<String, Object> locMap = (Map<String, Object>) locationObj;

            String file = (String) locMap.get("file");
            if (file == null || file.isEmpty()) {
                file = defaultFile;
            }

            Integer line = getIntValue(locMap, "line");
            Integer column = getIntValue(locMap, "column");

            LocationInfo location = new LocationInfo(
                    file,
                    line != null ? line : 0,
                    column != null ? column : 0);
            match.setLocation(location);
        } else {
            // Default location if not provided
            match.setLocation(new LocationInfo(defaultFile, 0, 0));
        }

        return match;
    }

    /**
     * Safely extract an integer value from a map.
     */
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse integer from string: {}", value);
            }
        }
        return null;
    }

    /**
     * Exception thrown when visitor execution fails.
     */
    public static class ExecutionException extends Exception {
        public ExecutionException(String message) {
            super(message);
        }

        public ExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
