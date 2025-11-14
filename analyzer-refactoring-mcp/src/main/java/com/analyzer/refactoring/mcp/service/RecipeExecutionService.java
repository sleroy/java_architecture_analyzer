package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteRecipeScript;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Result;
import org.openrewrite.java.JavaParser;
import org.openrewrite.SourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for executing OpenRewrite recipe scripts on Java source files.
 * Similar to OpenRewriteExecutionService but for transformations/recipes.
 * 
 * This service:
 * - Parses Java source files using OpenRewrite's JavaParser
 * - Executes compiled recipe scripts to transform code
 * - Generates diffs showing changes
 * - Returns transformation results with file paths
 */
@Service
public class RecipeExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeExecutionService.class);

    /**
     * Execute a recipe script on a Java project.
     * 
     * @param script      The compiled recipe script
     * @param projectPath Absolute path to the project root
     * @param filePaths   Optional list of relative file paths to process (null =
     *                    all files)
     * @return Execution result with transformed files and diffs
     * @throws ExecutionException if execution fails
     */
    public RecipeExecutionResult executeRecipeOnProject(
            OpenRewriteRecipeScript script,
            String projectPath,
            List<String> filePaths) throws ExecutionException {

        logger.info("Executing recipe on project: {}", projectPath);

        try {
            Path projectRoot = Paths.get(projectPath);

            // Determine which files to process
            List<Path> javaFiles = resolveJavaFiles(projectRoot, filePaths);

            if (javaFiles.isEmpty()) {
                logger.warn("No Java files found to process in: {}", projectPath);
                return new RecipeExecutionResult(new ArrayList<>(), false);
            }

            logger.info("Processing {} Java files", javaFiles.size());

            // Parse all Java files
            List<SourceFile> parsedFiles = parseJavaFiles(javaFiles);

            // Get recipe from compiled script
            Recipe recipe = getRecipeFromScript(script);

            // Execute recipe on files
            ExecutionContext ctx = new InMemoryExecutionContext(t -> {
                logger.warn("Execution error encountered", t);
            });

            // Apply recipe to each source file individually
            List<Result> results = new ArrayList<>();
            for (SourceFile sourceFile : parsedFiles) {
                SourceFile after = (SourceFile) recipe.getVisitor().visit(sourceFile, ctx);
                if (after != sourceFile) {
                    // File was changed
                    results.add(new Result(sourceFile, after));
                }
            }

            // Convert results to our format
            List<TransformedFile> transformedFiles = convertResults(results);

            boolean hasChanges = !transformedFiles.isEmpty();

            logger.info("Execution completed: {} files transformed", transformedFiles.size());
            return new RecipeExecutionResult(transformedFiles, hasChanges);

        } catch (IOException e) {
            throw new ExecutionException("Failed to read Java files: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ExecutionException("Failed to execute recipe: " + e.getMessage(), e);
        }
    }

    /**
     * Get Recipe instance from compiled Groovy script.
     */
    private Recipe getRecipeFromScript(OpenRewriteRecipeScript script) throws ExecutionException {
        try {
            CompiledScript compiledScript = script.getCompiledScript();
            ScriptEngine engine = compiledScript.getEngine();

            // Execute script - it should return a Recipe instance
            Object result = compiledScript.eval();

            if (!(result instanceof Recipe)) {
                throw new ExecutionException(
                        "Script did not return a Recipe instance, got: " +
                                (result != null ? result.getClass().getName() : "null"));
            }

            return (Recipe) result;

        } catch (ScriptException e) {
            logger.error("Script execution failed", e);
            throw new ExecutionException(
                    "Failed to create recipe from script: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Java files using OpenRewrite's JavaParser.
     */
    private List<SourceFile> parseJavaFiles(List<Path> javaFiles) throws IOException {
        logger.debug("Parsing {} Java files", javaFiles.size());

        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            logger.warn("Parse error encountered", t);
        });

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
     * Convert OpenRewrite Results to our TransformedFile format.
     */
    private List<TransformedFile> convertResults(List<Result> results) {
        List<TransformedFile> transformedFiles = new ArrayList<>();

        for (Result result : results) {
            if (result.getBefore() != null && result.getAfter() != null) {
                String filePath = result.getBefore().getSourcePath().toString();
                String diff = result.diff();

                TransformedFile transformedFile = new TransformedFile(
                        filePath,
                        result.getAfter().printAll(),
                        diff);

                transformedFiles.add(transformedFile);
            }
        }

        return transformedFiles;
    }

    /**
     * Exception thrown when recipe execution fails.
     */
    public static class ExecutionException extends Exception {
        public ExecutionException(String message) {
            super(message);
        }

        public ExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Result of recipe execution.
     */
    public static class RecipeExecutionResult {
        private final List<TransformedFile> transformedFiles;
        private final boolean hasChanges;

        public RecipeExecutionResult(List<TransformedFile> transformedFiles, boolean hasChanges) {
            this.transformedFiles = transformedFiles;
            this.hasChanges = hasChanges;
        }

        public List<TransformedFile> getTransformedFiles() {
            return transformedFiles;
        }

        public boolean hasChanges() {
            return hasChanges;
        }

        public int getFilesChanged() {
            return transformedFiles.size();
        }
    }

    /**
     * Represents a single transformed file.
     */
    public static class TransformedFile {
        private final String filePath;
        private final String newContent;
        private final String diff;

        public TransformedFile(String filePath, String newContent, String diff) {
            this.filePath = filePath;
            this.newContent = newContent;
            this.diff = diff;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getNewContent() {
            return newContent;
        }

        public String getDiff() {
            return diff;
        }
    }
}
