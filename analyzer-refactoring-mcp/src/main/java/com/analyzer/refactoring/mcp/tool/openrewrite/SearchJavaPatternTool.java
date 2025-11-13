package com.analyzer.refactoring.mcp.tool.openrewrite;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import com.analyzer.refactoring.mcp.service.GroovyScriptExecutionService;
import com.analyzer.refactoring.mcp.service.GroovyScriptGenerationService;
import com.analyzer.refactoring.mcp.service.OpenRewriteExecutionService;
import com.analyzer.refactoring.mcp.service.PatternMatcherAgent;
import com.analyzer.refactoring.mcp.service.VisitorScriptCache;
import com.analyzer.refactoring.mcp.service.VisitorTemplateService;
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
 * 
 * This tool uses AWS Bedrock to dynamically generate Groovy scripts that
 * implement
 * OpenRewrite visitors to search for specific patterns in Java code. The
 * generated
 * scripts are cached for performance and validated before execution.
 * 
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

    @Autowired
    public SearchJavaPatternTool(
            VisitorScriptCache scriptCache,
            GroovyScriptGenerationService scriptGenerator,
            GroovyScriptExecutionService scriptExecutor,
            OpenRewriteExecutionService openRewriteExecutor,
            VisitorTemplateService templateService,
            PatternMatcherAgent patternMatcher) {
        this.scriptCache = scriptCache;
        this.scriptGenerator = scriptGenerator;
        this.scriptExecutor = scriptExecutor;
        this.openRewriteExecutor = openRewriteExecutor;
        this.templateService = templateService;
        this.patternMatcher = patternMatcher;
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
            @ToolParam(description = "Absolute path to the Java project root directory") String projectPath,

            @ToolParam(description = "Description of the pattern to search for (e.g., 'singleton classes', 'static methods')") String patternDescription,

            @ToolParam(description = "Type of LST node to search for. Valid values: Binary, Block, ClassDeclaration, " +
                    "CompilationUnit, Expression, FieldAccess, Identifier, MethodDeclaration, " +
                    "MethodInvocation, NewClass, Statement, VariableDeclarations") String nodeType,

            @ToolParam(description = "Optional list of relative file paths to search in. If not provided, searches all Java files in project.", required = false) List<String> filePaths) {
        try {
            logger.info("Tool called: searchJavaPattern - projectPath={}, pattern='{}', nodeType={}, filePaths={}",
                    projectPath, patternDescription, nodeType, filePaths != null ? filePaths.size() : "all");

            // Step 1: Check cache
            Optional<OpenRewriteVisitorScript> cachedScript = scriptCache.get(
                    projectPath, patternDescription, nodeType, filePaths);

            OpenRewriteVisitorScript visitorScript;

            if (cachedScript.isPresent()) {
                logger.info("Using cached visitor script for pattern: {}", patternDescription);
                visitorScript = cachedScript.get();
            } else {
                logger.info("Cache miss - generating new visitor script for pattern: {}", patternDescription);
                visitorScript = generateAndCacheScript(projectPath, patternDescription, nodeType, filePaths);
            }

            // Step 2: Execute the visitor script
            PatternSearchResult result = executeVisitorScript(visitorScript, projectPath, filePaths);

            logger.info("Pattern search completed: {} matches found", result.getMatches().size());
            return toJsonResponse(result);

        } catch (GroovyScriptGenerationService.ScriptGenerationException e) {
            logger.error("Failed to generate visitor script", e);
            return toJsonResponse(createErrorResult(
                    "Failed to generate visitor script: " + e.getMessage()));
        } catch (GroovyScriptExecutionService.ScriptExecutionException e) {
            logger.error("Failed to compile visitor script", e);
            return toJsonResponse(createErrorResult(
                    "Failed to compile visitor script: " + e.getMessage()));
        } catch (OpenRewriteExecutionService.ExecutionException e) {
            logger.error("Failed to execute visitor on Java files", e);
            return toJsonResponse(createErrorResult(
                    "Failed to execute visitor: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in searchJavaPattern", e);
            return toJsonResponse(createErrorResult(
                    "Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Generate, compile, validate, and cache a new visitor script.
     * First checks for pre-built templates before generating with Bedrock.
     */
    private OpenRewriteVisitorScript generateAndCacheScript(
            String projectPath,
            String patternDescription,
            String nodeType,
            List<String> filePaths)
            throws GroovyScriptGenerationService.ScriptGenerationException,
            GroovyScriptExecutionService.ScriptCompilationException,
            GroovyScriptExecutionService.ScriptExecutionException {

        String scriptSource;
        int attempts = 0;

        // Step 1: Check for pre-built template (keyword matching)
        Optional<VisitorTemplateService.VisitorTemplate> template = templateService.findTemplate(patternDescription,
                nodeType);

        if (template.isPresent()) {
            logger.info("Using pre-built template (keyword match): {} for pattern: {}",
                    template.get().getName(), patternDescription);
            scriptSource = template.get().getScriptSource();
            attempts = 0; // No generation attempts for templates
        } else {
            // Step 2: Try AI semantic pattern matching
            logger.info("No keyword match, trying AI semantic matching for pattern: {}", patternDescription);

            List<PatternMatcherAgent.AvailablePattern> availablePatterns = buildAvailablePatterns();
            Optional<PatternMatcherAgent.MatchResult> aiMatch = patternMatcher.findBestMatch(
                    patternDescription, nodeType, availablePatterns);

            if (aiMatch.isPresent()) {
                PatternMatcherAgent.AvailablePattern matchedPattern = aiMatch.get().getPattern();
                logger.info("AI matched pattern: {} (confidence: {}%) - {}",
                        matchedPattern.getName(),
                        aiMatch.get().getConfidence(),
                        aiMatch.get().getReason());

                // Use the matched template
                if (matchedPattern.getPatternData() instanceof VisitorTemplateService.VisitorTemplate) {
                    VisitorTemplateService.VisitorTemplate matchedTemplate = (VisitorTemplateService.VisitorTemplate) matchedPattern
                            .getPatternData();
                    scriptSource = matchedTemplate.getScriptSource();
                    attempts = 0;
                } else {
                    // Shouldn't happen, but fallback to generation
                    logger.warn("Matched pattern data is not a VisitorTemplate, falling back to generation");
                    GroovyScriptGenerationService.GenerationResult generationResult = scriptGenerator
                            .generateVisitorScript(projectPath, patternDescription, nodeType, filePaths);
                    scriptSource = generationResult.getScriptSource();
                    attempts = generationResult.getAttempts();
                }
            } else {
                // Step 3: Generate script using Bedrock
                logger.info("No AI match found, generating new script with Bedrock for pattern: {}",
                        patternDescription);
                GroovyScriptGenerationService.GenerationResult generationResult = scriptGenerator
                        .generateVisitorScript(projectPath, patternDescription, nodeType, filePaths);

                scriptSource = generationResult.getScriptSource();
                attempts = generationResult.getAttempts();

                logger.debug("Generated script ({} attempts):\n{}", attempts, scriptSource);
            }
        }

        // Compile the script
        CompiledScript compiledScript = scriptExecutor.compileScript(scriptSource);

        // Validate the script
        boolean valid = scriptExecutor.validateScript(compiledScript);
        if (!valid) {
            throw new GroovyScriptExecutionService.ScriptExecutionException(
                    "Script validation failed");
        }

        // Create visitor script wrapper
        OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
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
            OpenRewriteVisitorScript visitorScript,
            String projectPath,
            List<String> filePaths)
            throws GroovyScriptExecutionService.ScriptExecutionException,
            OpenRewriteExecutionService.ExecutionException {

        logger.info("Executing visitor script for pattern: {}", visitorScript.getPatternDescription());

        // Execute the visitor on the project using OpenRewrite
        List<PatternMatch> matches = openRewriteExecutor.executeVisitorOnProject(
                visitorScript, projectPath, filePaths);

        // Build result
        PatternSearchResult result = new PatternSearchResult();
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
        List<PatternMatcherAgent.AvailablePattern> patterns = new ArrayList<>();

        // Add all templates
        for (VisitorTemplateService.VisitorTemplate template : templateService.getAllTemplates()) {
            PatternMatcherAgent.AvailablePattern pattern = new PatternMatcherAgent.AvailablePattern(
                    template.getName(),
                    "template",
                    extractDescription(template.getScriptSource()),
                    template.getKeywords(),
                    template);
            patterns.add(pattern);
        }

        return patterns;
    }

    /**
     * Extract description from template script comments.
     */
    private String extractDescription(String scriptSource) {
        // Extract first block comment as description
        int startIdx = scriptSource.indexOf("/**");
        if (startIdx >= 0) {
            int endIdx = scriptSource.indexOf("*/", startIdx);
            if (endIdx > startIdx) {
                return scriptSource.substring(startIdx + 3, endIdx)
                        .replaceAll("\\n\\s*\\*\\s*", " ")
                        .trim();
            }
        }
        return null;
    }

    /**
     * Create an error result.
     */
    private PatternSearchResult createErrorResult(String errorMessage) {
        PatternSearchResult result = new PatternSearchResult();
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

        public void setMatches(List<PatternMatch> matches) {
            this.matches = matches;
        }

        public void addMatch(PatternMatch match) {
            this.matches.add(match);
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public boolean isScriptGenerated() {
            return scriptGenerated;
        }

        public void setScriptGenerated(boolean scriptGenerated) {
            this.scriptGenerated = scriptGenerated;
        }

        public String getScriptSource() {
            return scriptSource;
        }

        public void setScriptSource(String scriptSource) {
            this.scriptSource = scriptSource;
        }

        public int getGenerationAttempts() {
            return generationAttempts;
        }

        public void setGenerationAttempts(int generationAttempts) {
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

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public LocationInfo getLocation() {
            return location;
        }

        public void setLocation(LocationInfo location) {
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

        public LocationInfo(String file, int line, int column) {
            this.file = file;
            this.line = line;
            this.column = column;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public int getColumn() {
            return column;
        }

        public void setColumn(int column) {
            this.column = column;
        }
    }
}
