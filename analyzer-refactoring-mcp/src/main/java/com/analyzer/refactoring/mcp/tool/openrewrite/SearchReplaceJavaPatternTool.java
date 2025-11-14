package com.analyzer.refactoring.mcp.tool.openrewrite;

import com.analyzer.refactoring.mcp.model.CallMetrics;
import com.analyzer.refactoring.mcp.model.OpenRewriteRecipeScript;
import com.analyzer.refactoring.mcp.service.*;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.script.CompiledScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MCP tool for searching and replacing Java code patterns using AI-generated
 * OpenRewrite recipes.
 * 
 * This tool:
 * - Uses AWS Bedrock to dynamically generate OpenRewrite recipes
 * - Applies transformations to Java source code
 * - Returns diffs showing the changes made
 * - Caches recipes for performance (60-minute expiration)
 * - Includes retry logic with error feedback
 * 
 * This tool complements search_java_pattern by not just finding patterns,
 * but also transforming them according to specified rules.
 */
@Component
public class SearchReplaceJavaPatternTool extends BaseRefactoringTool {

    private final RecipeScriptCache scriptCache;
    private final RecipeGenerationService recipeGenerator;
    private final GroovyScriptExecutionService scriptExecutor;
    private final RecipeExecutionService recipeExecutor;
    private final GroovyScriptAnalytics analytics;

    private static final int MAX_EXECUTION_RETRIES = 3;

    @Autowired
    public SearchReplaceJavaPatternTool(
            final RecipeScriptCache scriptCache,
            final RecipeGenerationService recipeGenerator,
            final GroovyScriptExecutionService scriptExecutor,
            final RecipeExecutionService recipeExecutor,
            final GroovyScriptAnalytics analytics) {
        this.scriptCache = scriptCache;
        this.recipeGenerator = recipeGenerator;
        this.scriptExecutor = scriptExecutor;
        this.recipeExecutor = recipeExecutor;
        this.analytics = analytics;
    }

    @Tool(description = "Search for Java patterns and apply transformations using OpenRewrite recipes. " +
            "This tool finds code matching a pattern and transforms it according to specified rules. " +
            "Use this when you need to refactor code, rename elements, change patterns, or apply " +
            "systematic transformations across Java files. " +
            "The tool generates an OpenRewrite recipe using AI, applies it to your code, and returns " +
            "a diff showing the changes. " +
            "Transformations are applied automatically - use with caution on production code. " +
            "Supported transformations include: renaming methods/classes/fields, changing annotations, " +
            "refactoring patterns, updating method signatures, and more. " +
            "Technical note: Uses OpenRewrite's recipe system to apply transformations safely to the AST.")
    public String searchReplaceJavaPattern(
            @ToolParam(description = "Absolute path to the Java project root directory") final String projectPath,

            @ToolParam(description = "Description of the pattern to find (e.g., 'methods starting with old')") final String patternDescription,

            @ToolParam(description = "Transformation to apply (e.g., 'rename to start with new instead')") final String transformation,

            @ToolParam(description = "Type of LST node to search for. Valid values: Binary, Block, ClassDeclaration, " +
                    "CompilationUnit, Expression, FieldAccess, Identifier, MethodDeclaration, " +
                    "MethodInvocation, NewClass, Statement, VariableDeclarations") final String nodeType,

            @ToolParam(description = "Optional list of relative file paths to transform. If not provided, transforms all Java files in project.", required = false) final List<String> filePaths) {

        final long startTime = System.currentTimeMillis();
        final CallMetrics.Builder metricsBuilder = CallMetrics.builder()
                .patternDescription(patternDescription)
                .nodeType(nodeType)
                .projectPath(projectPath)
                .filePaths(filePaths);

        try {
            logger.info(
                    "Tool called: searchReplaceJavaPattern - projectPath={}, pattern='{}', transformation='{}', nodeType={}, filePaths={}",
                    projectPath, patternDescription, transformation, nodeType,
                    filePaths != null ? filePaths.size() : "all");

            // Step 1: Check cache
            final Optional<OpenRewriteRecipeScript> cachedScript = scriptCache.get(
                    projectPath, patternDescription, nodeType, transformation, filePaths);

            // Step 2: Execute with retry on execution failures
            RecipeTransformationResult result = null;
            OpenRewriteRecipeScript currentScript = null;
            boolean cacheHit = cachedScript.isPresent();
            boolean scriptGenerated = false;
            int generationAttempts = 0;
            int executionAttempts = 0;
            String lastExecutionError = null;

            // Get initial script (from cache or generate new)
            if (cachedScript.isPresent()) {
                logger.info("Using cached recipe script for pattern: {}", patternDescription);
                currentScript = cachedScript.get();
                generationAttempts = currentScript.getGenerationAttempts();
            } else {
                logger.info("Cache miss - generating new recipe script for pattern: {}", patternDescription);
                currentScript = generateAndCacheRecipe(projectPath, patternDescription, nodeType, transformation,
                        filePaths, null, null);
                scriptGenerated = true;
                generationAttempts = currentScript.getGenerationAttempts();
            }

            // Retry loop for execution failures
            while (executionAttempts < MAX_EXECUTION_RETRIES) {
                executionAttempts++;

                try {
                    logger.info("Executing recipe script (attempt {}/{})", executionAttempts, MAX_EXECUTION_RETRIES);
                    result = executeRecipeScript(currentScript, projectPath, filePaths);

                    // Success! Break out of retry loop
                    logger.info("Execution successful on attempt {}", executionAttempts);
                    break;

                } catch (final RecipeExecutionService.ExecutionException e) {
                    lastExecutionError = e.getMessage();
                    logger.error("Execution failed on attempt {}/{}: {}",
                            executionAttempts, MAX_EXECUTION_RETRIES, lastExecutionError);

                    // If we have retries left, regenerate the script with error feedback
                    if (executionAttempts < MAX_EXECUTION_RETRIES) {
                        logger.info("Regenerating recipe with error feedback...");

                        // Invalidate cache for this pattern
                        scriptCache.invalidate(projectPath, patternDescription, nodeType, transformation, filePaths);

                        // Regenerate with error context
                        currentScript = generateAndCacheRecipe(
                                projectPath,
                                patternDescription,
                                nodeType,
                                transformation,
                                filePaths,
                                currentScript.getSourceCode(),
                                formatExecutionError(e));

                        scriptGenerated = true;
                        generationAttempts += currentScript.getGenerationAttempts();

                        // Wait a bit before retry
                        try {
                            Thread.sleep(1000 * executionAttempts);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        // Out of retries, throw the exception
                        throw e;
                    }
                }
            }

            final long executionTime = System.currentTimeMillis() - startTime;

            // Record successful call metrics
            final CallMetrics metrics = metricsBuilder
                    .success(true)
                    .cacheHit(cacheHit && executionAttempts == 1)
                    .scriptGenerated(scriptGenerated)
                    .matchesFound(result != null ? result.getFilesChanged() : 0)
                    .executionTimeMs(executionTime)
                    .generationAttempts(generationAttempts)
                    .build();

            analytics.recordCall(metrics);

            logger.info("Recipe transformation completed: {} files changed after {} execution attempt(s)",
                    result.getFilesChanged(), executionAttempts);
            return toJsonResponse(result);

        } catch (final RecipeGenerationService.ScriptGenerationException e) {
            logger.error("Failed to generate recipe script", e);

            final CallMetrics metrics = metricsBuilder
                    .success(false)
                    .cacheHit(false)
                    .scriptGenerated(false)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .errorMessage("Recipe generation failed: " + e.getMessage())
                    .build();

            analytics.recordCall(metrics);

            return toJsonResponse(createErrorResult(
                    "Failed to generate recipe script: " + e.getMessage()));

        } catch (final GroovyScriptExecutionService.ScriptExecutionException e) {
            logger.error("Failed to compile recipe script", e);

            final CallMetrics metrics = metricsBuilder
                    .success(false)
                    .cacheHit(false)
                    .scriptGenerated(true)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .errorMessage("Script compilation failed: " + e.getMessage())
                    .build();

            analytics.recordCall(metrics);

            return toJsonResponse(createErrorResult(
                    "Failed to compile recipe script: " + e.getMessage()));

        } catch (final RecipeExecutionService.ExecutionException e) {
            logger.error("Failed to execute recipe on Java files", e);

            final CallMetrics metrics = metricsBuilder
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .errorMessage("Execution failed: " + e.getMessage())
                    .build();

            analytics.recordCall(metrics);

            return toJsonResponse(createErrorResult(
                    "Failed to execute recipe: " + e.getMessage()));

        } catch (final Exception e) {
            logger.error("Unexpected error in searchReplaceJavaPattern", e);

            final CallMetrics metrics = metricsBuilder
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .errorMessage("Unexpected error: " + e.getMessage())
                    .build();

            analytics.recordCall(metrics);

            return toJsonResponse(createErrorResult(
                    "Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Generate, compile, validate, and cache a new recipe script.
     * 
     * @param previousScript Previous script that failed (null if first attempt)
     * @param previousError  Error from previous execution (null if first attempt)
     */
    private OpenRewriteRecipeScript generateAndCacheRecipe(
            final String projectPath,
            final String patternDescription,
            final String nodeType,
            final String transformation,
            final List<String> filePaths,
            final String previousScript,
            final String previousError)
            throws RecipeGenerationService.ScriptGenerationException,
            GroovyScriptExecutionService.ScriptCompilationException,
            GroovyScriptExecutionService.ScriptExecutionException {

        // Generate recipe script using Bedrock
        final RecipeGenerationService.GenerationResult generationResult = recipeGenerator
                .generateRecipeScriptWithErrorFeedback(
                        projectPath,
                        patternDescription,
                        nodeType,
                        transformation,
                        filePaths,
                        previousScript,
                        previousError);

        final String scriptSource = generationResult.getScriptSource();
        final int attempts = generationResult.getAttempts();

        logger.debug("Generated recipe script ({} attempts):\n{}", attempts, scriptSource);

        // Compile the script
        final CompiledScript compiledScript = scriptExecutor.compileScript(scriptSource);

        // Validate the script
        final boolean valid = scriptExecutor.validateScript(compiledScript);
        if (!valid) {
            throw new GroovyScriptExecutionService.ScriptExecutionException(
                    "Recipe script validation failed");
        }

        // Create recipe script wrapper
        final OpenRewriteRecipeScript recipeScript = OpenRewriteRecipeScript.builder()
                .patternDescription(patternDescription)
                .transformation(transformation)
                .nodeType(nodeType)
                .projectPath(projectPath)
                .compiledScript(compiledScript)
                .sourceCode(scriptSource)
                .generationAttempts(attempts)
                .build();

        // Cache it
        scriptCache.put(projectPath, patternDescription, nodeType, transformation, filePaths, recipeScript);

        return recipeScript;
    }

    /**
     * Execute the recipe script on the target files.
     */
    private RecipeTransformationResult executeRecipeScript(
            final OpenRewriteRecipeScript recipeScript,
            final String projectPath,
            final List<String> filePaths)
            throws GroovyScriptExecutionService.ScriptExecutionException,
            RecipeExecutionService.ExecutionException {

        logger.info("Executing recipe script for transformation: {}", recipeScript.getTransformation());

        // Execute the recipe on the project using OpenRewrite
        final RecipeExecutionService.RecipeExecutionResult executionResult = recipeExecutor
                .executeRecipeOnProject(
                        recipeScript, projectPath, filePaths);

        // Build result
        final RecipeTransformationResult result = new RecipeTransformationResult();
        result.setFilesChanged(executionResult.getFilesChanged());
        result.setHasChanges(executionResult.hasChanges());
        result.setScriptGenerated(true);
        result.setScriptSource(recipeScript.getSourceCode());
        result.setGenerationAttempts(recipeScript.getGenerationAttempts());

        // Add transformed files with diffs
        for (RecipeExecutionService.TransformedFile file : executionResult.getTransformedFiles()) {
            result.addTransformedFile(new TransformedFileInfo(
                    file.getFilePath(),
                    file.getDiff(),
                    file.getNewContent()));
        }

        logger.info("Execution completed: {} files transformed", result.getFilesChanged());

        return result;
    }

    /**
     * Format execution error for AI feedback.
     */
    private String formatExecutionError(final RecipeExecutionService.ExecutionException e) {
        final StringBuilder error = new StringBuilder();

        error.append("EXECUTION ERROR: ").append(e.getMessage()).append("\n\n");

        // Extract the root cause
        Throwable cause = e.getCause();
        while (cause != null) {
            error.append("Caused by: ").append(cause.getClass().getName())
                    .append(": ").append(cause.getMessage()).append("\n");

            // Add stack trace snippet (first 5 lines)
            final StackTraceElement[] stackTrace = cause.getStackTrace();
            for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
                error.append("  at ").append(stackTrace[i]).append("\n");
            }

            cause = cause.getCause();
            if (cause != null) {
                error.append("\n");
            }
        }

        return error.toString();
    }

    /**
     * Create an error result.
     */
    private RecipeTransformationResult createErrorResult(final String errorMessage) {
        final RecipeTransformationResult result = new RecipeTransformationResult();
        result.setError(errorMessage);
        return result;
    }

    // Result classes

    /**
     * Main result container for recipe transformation.
     */
    public static class RecipeTransformationResult {
        private List<TransformedFileInfo> transformedFiles = new ArrayList<>();
        private int filesChanged;
        private boolean hasChanges;
        private String error;
        private boolean scriptGenerated;
        private String scriptSource;
        private int generationAttempts;

        public List<TransformedFileInfo> getTransformedFiles() {
            return transformedFiles;
        }

        public void setTransformedFiles(final List<TransformedFileInfo> transformedFiles) {
            this.transformedFiles = transformedFiles;
        }

        public void addTransformedFile(final TransformedFileInfo file) {
            transformedFiles.add(file);
        }

        public int getFilesChanged() {
            return filesChanged;
        }

        public void setFilesChanged(final int filesChanged) {
            this.filesChanged = filesChanged;
        }

        public boolean isHasChanges() {
            return hasChanges;
        }

        public void setHasChanges(final boolean hasChanges) {
            this.hasChanges = hasChanges;
        }

        public String getError() {
            return error;
        }

        public void setError(final String error) {
            this.error = error;
        }

        public boolean isScriptGenerated() {
            return scriptGenerated;
        }

        public void setScriptGenerated(final boolean scriptGenerated) {
            this.scriptGenerated = scriptGenerated;
        }

        public String getScriptSource() {
            return scriptSource;
        }

        public void setScriptSource(final String scriptSource) {
            this.scriptSource = scriptSource;
        }

        public int getGenerationAttempts() {
            return generationAttempts;
        }

        public void setGenerationAttempts(final int generationAttempts) {
            this.generationAttempts = generationAttempts;
        }
    }

    /**
     * Information about a transformed file.
     */
    public static class TransformedFileInfo {
        private final String filePath;
        private final String diff;
        private final String newContent;

        public TransformedFileInfo(String filePath, String diff, String newContent) {
            this.filePath = filePath;
            this.diff = diff;
            this.newContent = newContent;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getDiff() {
            return diff;
        }

        public String getNewContent() {
            return newContent;
        }
    }
}
