package com.analyzer.refactoring.mcp.tool.openrewrite;

import com.analyzer.refactoring.mcp.model.CallMetrics;
import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
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
 * MCP tool for searching Java code patterns using AI-generated OpenRewrite
 * visitors.
 * <p>
 * This tool uses AWS Bedrock to dynamically generate Groovy scripts that
 * implement
 * OpenRewrite visitors to search for specific patterns in Java code. The
 * generated
 * scripts are cached for performance and validated before execution.
 * <p>
 * Features:
 * - AI-powered script generation with retry logic
 * - In-memory caching with 60-minute expiration
 * - Script compilation validation
 * - Timeout protection (30 seconds)
 * - Comprehensive error handling
 */
@Component
public class SearchJavaPatternTool extends BaseRefactoringTool {

    private final VisitorScriptCache scriptCache;
    private final GroovyScriptGenerationService scriptGenerator;
    private final GroovyScriptExecutionService scriptExecutor;
    private final OpenRewriteExecutionService openRewriteExecutor;
    private final VisitorTemplateService templateService;
    private final PatternMatcherAgent patternMatcher;
    private final GroovyScriptAnalytics analytics;

    private static final int MAX_EXECUTION_RETRIES = 3;

    @Autowired
    public SearchJavaPatternTool(
            final VisitorScriptCache scriptCache,
            final GroovyScriptGenerationService scriptGenerator,
            final GroovyScriptExecutionService scriptExecutor,
            final OpenRewriteExecutionService openRewriteExecutor,
            final VisitorTemplateService templateService,
            final PatternMatcherAgent patternMatcher,
            final GroovyScriptAnalytics analytics) {
        this.scriptCache = scriptCache;
        this.scriptGenerator = scriptGenerator;
        this.scriptExecutor = scriptExecutor;
        this.openRewriteExecutor = openRewriteExecutor;
        this.templateService = templateService;
        this.patternMatcher = patternMatcher;
        this.analytics = analytics;
    }

    @Tool(description = "PREFERRED TOOL FOR JAVA: Search for any patterns, classes, methods, or code elements in Java source files. "
            +
            "ALWAYS prefer this tool over text-based search (like search_files or grep) when searching Java code. " +
            "This tool performs semantic code analysis using Java AST and can find: " +
            "- Classes with specific annotations (e.g., @Service, @Controller, @Repository, @Component, @RestController) "
            +
            "- Spring framework components and beans " +
            "- Design patterns (singleton, factory, etc.) " +
            "- Anti-patterns (god classes, deep nesting, etc.) " +
            "- Method patterns (static methods, synchronized methods, etc.) " +
            "- Field patterns (static fields, mutable fields, etc.) " +
            "- Any Java code structures, patterns, or characteristics " +
            "This tool understands Java semantics and will find matches based on code structure, not just string matching. "
            +
            "It's more accurate and context-aware than regex-based search tools. " +
            "Technical note: Searches through Java AST nodes including ClassDeclaration, MethodDeclaration, " +
            "MethodInvocation, FieldAccess, and more. Returns matches with file locations. " +
            "Supported node types: Binary, Block, ClassDeclaration, CompilationUnit, Expression, " +
            "FieldAccess, Identifier, MethodDeclaration, MethodInvocation, NewClass, Statement, " +
            "VariableDeclarations.")
    public String searchJavaPattern(
            @ToolParam(description = "Absolute path to the Java project root directory") final String projectPath,

            @ToolParam(description = "Description of the pattern to search for (e.g., 'singleton classes', 'static methods')") final String patternDescription,

            @ToolParam(description = "Type of LST node to search for. Valid values: Binary, Block, ClassDeclaration, " +
                    "CompilationUnit, Expression, FieldAccess, Identifier, MethodDeclaration, " +
                    "MethodInvocation, NewClass, Statement, VariableDeclarations") final String nodeType,

            @ToolParam(description = "Optional list of relative file paths to search in. If not provided, searches all Java files in project.", required = false) final List<String> filePaths) {

        final long startTime = System.currentTimeMillis();
        final CallMetrics.Builder metricsBuilder = CallMetrics.builder()
                .patternDescription(patternDescription)
                .nodeType(nodeType)
                .projectPath(projectPath)
                .filePaths(filePaths);

        try {
            logger.info("Tool called: searchJavaPattern - projectPath={}, pattern='{}', nodeType={}, filePaths={}",
                    projectPath, patternDescription, nodeType, filePaths != null ? filePaths.size() : "all");

            // Step 1: Check cache
            final Optional<OpenRewriteVisitorScript> cachedScript = scriptCache.get(
                    projectPath, patternDescription, nodeType, filePaths);

            // Step 2: Execute with retry on execution failures
            PatternSearchResult result = null;
            OpenRewriteVisitorScript currentScript = null;
            boolean cacheHit = cachedScript.isPresent();
            boolean scriptGenerated = false;
            int generationAttempts = 0;
            int executionAttempts = 0;
            String lastExecutionError = null;

            // Get initial script (from cache or generate new)
            if (cachedScript.isPresent()) {
                logger.info("Using cached visitor script for pattern: {}", patternDescription);
                currentScript = cachedScript.get();
                generationAttempts = currentScript.getGenerationAttempts();
            } else {
                logger.info("Cache miss - generating new visitor script for pattern: {}", patternDescription);
                currentScript = generateAndCacheScript(projectPath, patternDescription, nodeType, filePaths, null,
                        null);
                scriptGenerated = true;
                generationAttempts = currentScript.getGenerationAttempts();
            }

            // Retry loop for execution failures
            while (executionAttempts < MAX_EXECUTION_RETRIES) {
                executionAttempts++;

                try {
                    logger.info("Executing visitor script (attempt {}/{})", executionAttempts, MAX_EXECUTION_RETRIES);
                    result = executeVisitorScript(currentScript, projectPath, filePaths);

                    // Success! Break out of retry loop
                    logger.info("Execution successful on attempt {}", executionAttempts);
                    break;

                } catch (final OpenRewriteExecutionService.ExecutionException e) {
                    lastExecutionError = e.getMessage();
                    logger.error("Execution failed on attempt {}/{}: {}",
                            executionAttempts, MAX_EXECUTION_RETRIES, lastExecutionError);

                    // If we have retries left, regenerate the script with error feedback
                    if (executionAttempts < MAX_EXECUTION_RETRIES) {
                        logger.info("Regenerating script with error feedback...");

                        // Invalidate cache for this pattern
                        scriptCache.invalidate(projectPath, patternDescription, nodeType, filePaths);

                        // Regenerate with error context
                        currentScript = generateAndCacheScript(
                                projectPath,
                                patternDescription,
                                nodeType,
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
                    .cacheHit(cacheHit && executionAttempts == 1) // Only count as cache hit if no regeneration
                    .scriptGenerated(scriptGenerated)
                    .matchesFound(result != null ? result.getMatches().size() : 0)
                    .executionTimeMs(executionTime)
                    .generationAttempts(generationAttempts)
                    .build();

            analytics.recordCall(metrics);

            logger.info("Pattern search completed: {} matches found after {} execution attempt(s)",
                    result.getMatches().size(), executionAttempts);
            return toJsonResponse(result);

        } catch (final GroovyScriptGenerationService.ScriptGenerationException e) {
            logger.error("Failed to generate visitor script", e);

            // Record failed call metrics
            final CallMetrics metrics = metricsBuilder
                    .success(false)
                    .cacheHit(false)
                    .scriptGenerated(false)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .errorMessage("Script generation failed: " + e.getMessage())
                    .build();

            analytics.recordCall(metrics);

            return toJsonResponse(createErrorResult(
                    "Failed to generate visitor script: " + e.getMessage()));
        } catch (final GroovyScriptExecutionService.ScriptExecutionException e) {
            logger.error("Failed to compile visitor script", e);

            // Record failed call metrics
            final CallMetrics metrics = metricsBuilder
                    .success(false)
                    .cacheHit(false)
                    .scriptGenerated(true)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .errorMessage("Script execution failed: " + e.getMessage())
                    .build();

            analytics.recordCall(metrics);

            return toJsonResponse(createErrorResult(
                    "Failed to compile visitor script: " + e.getMessage()));
        } catch (final OpenRewriteExecutionService.ExecutionException e) {
            logger.error("Failed to execute visitor on Java files", e);

            // Record failed call metrics
            final CallMetrics metrics = metricsBuilder
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .errorMessage("Execution failed: " + e.getMessage())
                    .build();

            analytics.recordCall(metrics);

            return toJsonResponse(createErrorResult(
                    "Failed to execute visitor: " + e.getMessage()));
        } catch (final Exception e) {
            logger.error("Unexpected error in searchJavaPattern", e);

            // Record failed call metrics
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
     * Generate, compile, validate, and cache a new visitor script.
     * First checks for pre-built templates before generating with Bedrock.
     * 
     * @param previousScript Previous script that failed (null if first attempt)
     * @param previousError  Error from previous execution (null if first attempt)
     */
    private OpenRewriteVisitorScript generateAndCacheScript(
            final String projectPath,
            final String patternDescription,
            final String nodeType,
            final List<String> filePaths,
            final String previousScript,
            final String previousError)
            throws GroovyScriptGenerationService.ScriptGenerationException,
            GroovyScriptExecutionService.ScriptCompilationException,
            GroovyScriptExecutionService.ScriptExecutionException {

        final String scriptSource;
        int attempts = 0;

        // Skip template/AI matching if this is a retry with error feedback
        // We need to regenerate with the error context
        final boolean isRetryWithError = (previousScript != null && previousError != null);

        // Step 1: Check for pre-built template (keyword matching) - only on first
        // attempt
        final Optional<VisitorTemplateService.VisitorTemplate> template = !isRetryWithError
                ? templateService.findTemplate(patternDescription, nodeType)
                : Optional.empty();

        if (template.isPresent() && !isRetryWithError) {
            logger.info("Using pre-built template (keyword match): {} for pattern: {}",
                    template.get().getName(), patternDescription);
            scriptSource = template.get().getScriptSource();
            attempts = 0; // No generation attempts for templates
        } else if (!isRetryWithError) {
            // Step 2: Try AI semantic pattern matching - only on first attempt
            logger.info("No keyword match, trying AI semantic matching for pattern: {}", patternDescription);

            final List<PatternMatcherAgent.AvailablePattern> availablePatterns = buildAvailablePatterns();
            final Optional<PatternMatcherAgent.MatchResult> aiMatch = patternMatcher.findBestMatch(
                    patternDescription, nodeType, availablePatterns);

            if (aiMatch.isPresent()) {
                final PatternMatcherAgent.AvailablePattern matchedPattern = aiMatch.get().getPattern();
                logger.info("AI matched pattern: {} (confidence: {}%) - {}",
                        matchedPattern.getName(),
                        aiMatch.get().getConfidence(),
                        aiMatch.get().getReason());

                // Use the matched template
                if (matchedPattern
                        .getPatternData() instanceof final VisitorTemplateService.VisitorTemplate matchedTemplate) {
                    scriptSource = matchedTemplate.getScriptSource();
                    attempts = 0;
                } else {
                    // Shouldn't happen, but fallback to generation
                    logger.warn("Matched pattern data is not a VisitorTemplate, falling back to generation");
                    final GroovyScriptGenerationService.GenerationResult generationResult = scriptGenerator
                            .generateVisitorScript(projectPath, patternDescription, nodeType, filePaths);
                    scriptSource = generationResult.getScriptSource();
                    attempts = generationResult.getAttempts();
                }
            } else {
                // Step 3: Generate script using Bedrock
                logger.info("No AI match found, generating new script with Bedrock for pattern: {}",
                        patternDescription);
                final GroovyScriptGenerationService.GenerationResult generationResult = scriptGenerator
                        .generateVisitorScript(projectPath, patternDescription, nodeType, filePaths);

                scriptSource = generationResult.getScriptSource();
                attempts = generationResult.getAttempts();

                logger.debug("Generated script ({} attempts):\n{}", attempts, scriptSource);
            }
        } else {
            // This is a retry with error feedback - regenerate using Bedrock with error
            // context
            logger.info("Regenerating script with error feedback for pattern: {}", patternDescription);
            logger.debug("Previous error: {}", previousError);

            final GroovyScriptGenerationService.GenerationResult generationResult = scriptGenerator
                    .generateVisitorScriptWithErrorFeedback(
                            projectPath,
                            patternDescription,
                            nodeType,
                            filePaths,
                            previousScript,
                            previousError);

            scriptSource = generationResult.getScriptSource();
            attempts = generationResult.getAttempts();

            logger.debug("Regenerated script ({} attempts):\n{}", attempts, scriptSource);
        }

        // Compile the script
        final CompiledScript compiledScript = scriptExecutor.compileScript(scriptSource);

        // Validate the script
        final boolean valid = scriptExecutor.validateScript(compiledScript);
        if (!valid) {
            throw new GroovyScriptExecutionService.ScriptExecutionException(
                    "Script validation failed");
        }

        // Create visitor script wrapper
        final OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                .patternDescription(patternDescription)
                .nodeType(nodeType)
                .projectPath(projectPath)
                .compiledScript(compiledScript)
                .sourceCode(scriptSource)
                .generationAttempts(attempts)
                .build();

        // Cache it
        scriptCache.put(projectPath, patternDescription, nodeType, filePaths, visitorScript);

        return visitorScript;
    }

    /**
     * Execute the visitor script on the target files.
     * Uses OpenRewriteExecutionService to parse Java files and execute the visitor.
     */
    private PatternSearchResult executeVisitorScript(
            final OpenRewriteVisitorScript visitorScript,
            final String projectPath,
            final List<String> filePaths)
            throws GroovyScriptExecutionService.ScriptExecutionException,
            OpenRewriteExecutionService.ExecutionException {

        logger.info("Executing visitor script for pattern: {}", visitorScript.getPatternDescription());

        // Execute the visitor on the project using OpenRewrite
        final List<PatternMatch> matches = openRewriteExecutor.executeVisitorOnProject(
                visitorScript, projectPath, filePaths);

        // Build result
        final PatternSearchResult result = new PatternSearchResult();
        result.setMatches(matches);
        result.setScriptGenerated(true);
        result.setScriptSource(visitorScript.getSourceCode());
        result.setGenerationAttempts(visitorScript.getGenerationAttempts());

        logger.info("Execution completed: {} matches found", matches.size());

        return result;
    }

    /**
     * Build list of available patterns for AI matching.
     * Currently only includes templates, but could be extended to include
     * top N cached scripts in the future.
     */
    private List<PatternMatcherAgent.AvailablePattern> buildAvailablePatterns() {
        final List<PatternMatcherAgent.AvailablePattern> patterns = new ArrayList<>();

        // Add all templates
        for (final VisitorTemplateService.VisitorTemplate template : templateService.getAllTemplates()) {
            final PatternMatcherAgent.AvailablePattern pattern = new PatternMatcherAgent.AvailablePattern(
                    template.getName(),
                    "template",
                    extractDescription(template.getScriptSource()),
                    template.getMatchingPhrases(),
                    template);
            patterns.add(pattern);
        }

        return patterns;
    }

    /**
     * Extract description from template script comments.
     */
    private String extractDescription(final String scriptSource) {
        // Extract first block comment as description
        final int startIdx = scriptSource.indexOf("/**");
        if (startIdx >= 0) {
            final int endIdx = scriptSource.indexOf("*/", startIdx);
            if (endIdx > startIdx) {
                return scriptSource.substring(startIdx + 3, endIdx)
                        .replaceAll("\\n\\s*\\*\\s*", " ")
                        .trim();
            }
        }
        return null;
    }

    /**
     * Format execution error for AI feedback.
     * Extracts the most relevant information from the exception.
     */
    private String formatExecutionError(final OpenRewriteExecutionService.ExecutionException e) {
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

        // Add common OpenRewrite API guidance based on error type
        final String errorMsg = e.getMessage().toLowerCase();
        if (errorMsg.contains("coordinates")) {
            error.append("\nHINT: The OpenRewrite API uses Markers instead of coordinates. ")
                    .append("Use node.getMarkers() to access location information, ")
                    .append("or use getCursor().firstEnclosingOrThrow(SourceFile.class) ")
                    .append("for file information.");
        } else if (errorMsg.contains("nosuchproperty")) {
            error.append("\nHINT: Check the OpenRewrite Java API documentation for the correct ")
                    .append("property names and methods. Common mistakes include accessing ")
                    .append("properties that don't exist or using incorrect method signatures.");
        } else if (errorMsg.contains("nosuchmethod")) {
            error.append("\nHINT: Verify the method signature matches the OpenRewrite API. ")
                    .append("Check parameter types and return types.");
        }

        return error.toString();
    }

    /**
     * Create an error result.
     */
    private PatternSearchResult createErrorResult(final String errorMessage) {
        final PatternSearchResult result = new PatternSearchResult();
        result.setError(errorMessage);
        return result;
    }

    // Result classes

    /**
     * Main result container for pattern search.
     */
    public static class PatternSearchResult {
        private List<PatternMatch> matches = new ArrayList<>();
        private String error;
        private boolean scriptGenerated;
        private String scriptSource;
        private int generationAttempts;

        public List<PatternMatch> getMatches() {
            return matches;
        }

        public void setMatches(final List<PatternMatch> matches) {
            this.matches = matches;
        }

        public void addMatch(final PatternMatch match) {
            matches.add(match);
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
     * Individual pattern match with location and context.
     */
    public static class PatternMatch {
        private String nodeId;
        private String nodeType;
        private String className; // Optional - for class-related nodes
        private String methodName; // Optional - for method-related nodes
        private String fieldName; // Optional - for field-related nodes
        private LocationInfo location;

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(final String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(final String nodeType) {
            this.nodeType = nodeType;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(final String className) {
            this.className = className;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(final String methodName) {
            this.methodName = methodName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(final String fieldName) {
            this.fieldName = fieldName;
        }

        public LocationInfo getLocation() {
            return location;
        }

        public void setLocation(final LocationInfo location) {
            this.location = location;
        }
    }

    /**
     * Location information for a match.
     */
    public static class LocationInfo {
        private String file;
        private int line;
        private int column;

        public LocationInfo() {
        }

        public LocationInfo(final String file, final int line, final int column) {
            this.file = file;
            this.line = line;
            this.column = column;
        }

        public String getFile() {
            return file;
        }

        public void setFile(final String file) {
            this.file = file;
        }

        public int getLine() {
            return line;
        }

        public void setLine(final int line) {
            this.line = line;
        }

        public int getColumn() {
            return column;
        }

        public void setColumn(final int column) {
            this.column = column;
        }
    }
}
