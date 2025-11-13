package com.analyzer.refactoring.mcp.service;

import com.analyzer.core.bedrock.BedrockApiClient;
import com.analyzer.core.bedrock.BedrockApiException;
import com.analyzer.core.bedrock.BedrockConfig;
import com.analyzer.core.bedrock.BedrockResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service for generating Groovy scripts using AWS Bedrock.
 * Uses the existing BedrockApiClient to generate OpenRewrite visitor scripts
 * based on pattern descriptions.
 */
@Service
public class GroovyScriptGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptGenerationService.class);

    private final BedrockApiClient bedrockClient;
    private final int maxRetries;
    private final ResourceLoader resourceLoader;
    private String skeletonExample;

    public GroovyScriptGenerationService(
            ResourceLoader resourceLoader,
            @Value("${groovy.bedrock.model}") String model,
            @Value("${groovy.bedrock.temperature:0.1}") double temperature,
            @Value("${groovy.bedrock.top-k:10}") int topK,
            @Value("${groovy.bedrock.max-tokens:4000}") int maxTokens,
            @Value("${groovy.bedrock.max-retries:3}") int maxRetries,
            @Value("${groovy.bedrock.timeout.seconds:60}") int timeoutSeconds,
            @Value("${groovy.bedrock.aws.region}") String awsRegion,
            @Value("${groovy.bedrock.aws.access-key}") String awsAccessKey,
            @Value("${groovy.bedrock.aws.secret-key}") String awsSecretKey) {

        this.resourceLoader = resourceLoader;
        this.maxRetries = maxRetries;

        // Configure Bedrock client for script generation using Properties
        BedrockConfig config = createBedrockConfig(
                model, temperature, topK, maxTokens, timeoutSeconds,
                awsRegion, awsAccessKey, awsSecretKey);

        this.bedrockClient = new BedrockApiClient(config);

        logger.info("Groovy script generation service initialized with model: {}, temperature: {}, maxRetries: {}",
                model, temperature, maxRetries);
    }

    /**
     * Load the Groovy visitor skeleton example on startup.
     */
    @PostConstruct
    public void loadSkeleton() {
        try {
            Resource resource = resourceLoader.getResource("classpath:groovy-visitor-skeleton.groovy");
            skeletonExample = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            logger.info("Loaded Groovy visitor skeleton example ({} chars)", skeletonExample.length());
        } catch (IOException e) {
            logger.error("Failed to load Groovy visitor skeleton", e);
            skeletonExample = null;
        }
    }

    /**
     * Create BedrockConfig from properties.
     */
    private BedrockConfig createBedrockConfig(
            String model,
            double temperature,
            int topK,
            int maxTokens,
            int timeoutSeconds,
            String awsRegion,
            String awsAccessKey,
            String awsSecretKey) {

        // Create properties for Bedrock configuration
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("bedrock.model.id", model);
        properties.setProperty("bedrock.temperature", String.valueOf(temperature));
        properties.setProperty("bedrock.top.p", "0.9");
        properties.setProperty("bedrock.max.tokens", String.valueOf(maxTokens));
        properties.setProperty("bedrock.timeout.seconds", String.valueOf(timeoutSeconds));
        properties.setProperty("bedrock.aws.region", awsRegion);
        properties.setProperty("bedrock.aws.access.key.id", awsAccessKey);
        properties.setProperty("bedrock.aws.secret.access.key", awsSecretKey);
        properties.setProperty("bedrock.rate.limit.rpm", "60");
        properties.setProperty("bedrock.retry.attempts", "1"); // We handle retries ourselves
        properties.setProperty("bedrock.log.requests", "false");
        properties.setProperty("bedrock.log.responses", "false");
        properties.setProperty("bedrock.enabled", "true");

        // Use reflection to create BedrockConfig with our properties
        try {
            java.lang.reflect.Constructor<BedrockConfig> constructor = BedrockConfig.class
                    .getDeclaredConstructor(java.util.Properties.class);
            constructor.setAccessible(true);
            return constructor.newInstance(properties);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create BedrockConfig", e);
        }
    }

    /**
     * Generate a Groovy visitor script based on pattern description.
     * Implements retry logic with error feedback.
     *
     * @param projectPath        the project path
     * @param patternDescription the pattern to search for
     * @param nodeType           the OpenRewrite node type
     * @param filePaths          optional list of file paths
     * @return the generated Groovy script source code
     * @throws ScriptGenerationException if generation fails after all retries
     */
    public GenerationResult generateVisitorScript(
            String projectPath,
            String patternDescription,
            String nodeType,
            List<String> filePaths) throws ScriptGenerationException {

        String previousScript = null;
        String previousError = null;
        int attempts = 0;

        while (attempts < maxRetries) {
            attempts++;
            logger.info("Generating visitor script (attempt {}/{}): pattern='{}', nodeType='{}'",
                    attempts, maxRetries, patternDescription, nodeType);

            try {
                String prompt = buildPrompt(
                        projectPath,
                        patternDescription,
                        nodeType,
                        filePaths,
                        previousScript,
                        previousError,
                        attempts);

                BedrockResponse response = bedrockClient.invokeModel(prompt);
                String generatedScript = extractScript(response.getText());

                logger.info("Script generated successfully on attempt {}", attempts);
                return new GenerationResult(generatedScript, attempts);

            } catch (BedrockApiException e) {
                logger.error("Bedrock API error on attempt {}: {}", attempts, e.getMessage());
                previousError = "Bedrock API error: " + e.getMessage();

                if (attempts >= maxRetries) {
                    throw new ScriptGenerationException(
                            "Failed to generate script after " + maxRetries + " attempts: " + e.getMessage(), e);
                }

                // Wait before retry
                waitBeforeRetry(attempts);
            }
        }

        throw new ScriptGenerationException("Failed to generate script after " + maxRetries + " attempts");
    }

    /**
     * Build the prompt for Bedrock to generate a Groovy visitor script.
     */
    private String buildPrompt(
            String projectPath,
            String patternDescription,
            String nodeType,
            List<String> filePaths,
            String previousScript,
            String previousError,
            int attemptNumber) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a Java code analysis expert. Generate a Groovy script that implements ");
        prompt.append("an OpenRewrite JavaIsoVisitor to find the following pattern in Java code.\n\n");

        prompt.append("## Pattern to Find\n");
        prompt.append(patternDescription).append("\n\n");

        prompt.append("## Target Node Type\n");
        prompt.append(nodeType).append("\n\n");

        prompt.append("## Context\n");
        prompt.append("Project Path: ").append(projectPath != null ? projectPath : "N/A").append("\n");
        if (filePaths != null && !filePaths.isEmpty()) {
            prompt.append("Target Files: ").append(String.join(", ", filePaths)).append("\n");
        } else {
            prompt.append("Scope: All Java files in project\n");
        }
        prompt.append("\n");

        prompt.append("## Requirements\n");
        prompt.append("1. Create a class that extends org.openrewrite.java.JavaIsoVisitor<ExecutionContext>\n");
        prompt.append("2. Override the appropriate visit method for the node type: visit").append(nodeType)
                .append("\n");
        prompt.append("3. Implement logic to match the pattern: ").append(patternDescription).append("\n");
        prompt.append("4. For each match found, collect it in a list\n");
        prompt.append("5. Store location information (file path, line number, column number)\n");
        prompt.append("6. Return ONLY the Groovy code, no explanations\n");
        prompt.append("7. Use proper OpenRewrite APIs and patterns\n\n");

        prompt.append("## WORKING EXAMPLE TO ADAPT\n");
        prompt.append("Here is a complete, tested Groovy visitor that demonstrates correct OpenRewrite API usage.\n");
        prompt.append("Adapt this pattern for your specific search requirements:\n\n");
        if (skeletonExample != null) {
            prompt.append("```groovy\n");
            prompt.append(skeletonExample);
            prompt.append("\n```\n\n");
        }

        prompt.append("## Expected Output Format\n");
        prompt.append("The script MUST return a List<Map<String, Object>> where each Map contains:\n");
        prompt.append("- nodeId: Unique identifier (use node.id.toString())\n");
        prompt.append("- nodeType: Type of the node (e.g., 'ClassDeclaration', 'MethodInvocation')\n");
        prompt.append("- className: Name of the class (if applicable)\n");
        prompt.append("- methodName: Name of the method (if applicable)\n");
        prompt.append("- fieldName: Name of the field (if applicable)\n");
        prompt.append("- location: Map with 'file', 'line', 'column' keys\n\n");

        prompt.append("Use OpenRewrite Cursor API to extract location:\n");
        prompt.append("- File path: getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString()\n");
        prompt.append("- Line number: node.getPrefix().getCoordinates().getLine()\n");
        prompt.append("- Column: node.getPrefix().getCoordinates().getColumn()\n\n");

        prompt.append("```groovy\n");
        prompt.append("import org.openrewrite.java.JavaIsoVisitor\n");
        prompt.append("import org.openrewrite.ExecutionContext\n");
        prompt.append("import org.openrewrite.java.tree.J\n");
        prompt.append("import org.openrewrite.SourceFile\n\n");
        prompt.append("class PatternVisitor extends JavaIsoVisitor<ExecutionContext> {\n");
        prompt.append("    List<Map<String, Object>> matches = []\n\n");
        prompt.append("    @Override\n");
        prompt.append("    J.").append(nodeType).append(" visit").append(nodeType)
                .append("(J.").append(nodeType).append(" node, ExecutionContext ctx) {\n");
        prompt.append("        // Example: Check if pattern matches\n");
        prompt.append("        if (/* your matching logic */) {\n");
        prompt.append("            def match = [\n");
        prompt.append("                nodeId: node.id.toString(),\n");
        prompt.append("                nodeType: '").append(nodeType).append("',\n");
        prompt.append("                className: extractClassName(node),  // implement as needed\n");
        prompt.append("                location: [\n");
        prompt.append(
                "                    file: getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),\n");
        prompt.append("                    line: node.getPrefix().getCoordinates().getLine(),\n");
        prompt.append("                    column: node.getPrefix().getCoordinates().getColumn()\n");
        prompt.append("                ]\n");
        prompt.append("            ]\n");
        prompt.append("            matches.add(match)\n");
        prompt.append("        }\n");
        prompt.append("        return super.visit").append(nodeType).append("(node, ctx)\n");
        prompt.append("    }\n\n");
        prompt.append("    // Helper method to extract class name from context\n");
        prompt.append("    private String extractClassName(J node) {\n");
        prompt.append("        def classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class)\n");
        prompt.append("        return classDecl?.simpleName ?: 'Unknown'\n");
        prompt.append("    }\n");
        prompt.append("}\n\n");
        prompt.append("// After creating the visitor, execute it and return matches\n");
        prompt.append("def visitor = new PatternVisitor()\n");
        prompt.append("visitor.visit(compilationUnit, new InMemoryExecutionContext())\n");
        prompt.append("return visitor.matches  // MUST return the matches list\n");
        prompt.append("```\n\n");

        // Add error feedback if this is a retry
        if (previousError != null && previousScript != null) {
            prompt.append("## Previous Attempt Failed\n");
            prompt.append("The previous script had this error:\n");
            prompt.append("```\n").append(previousError).append("\n```\n\n");
            prompt.append("Previous script that failed:\n");
            prompt.append("```groovy\n").append(previousScript).append("\n```\n\n");
            prompt.append("Please fix the error and generate a corrected script.\n\n");
        }

        prompt.append("Generate ONLY the Groovy code, enclosed in ```groovy and ``` markers. ");
        prompt.append("Do not include any explanations before or after the code block.");

        return prompt.toString();
    }

    /**
     * Extract the Groovy script from Bedrock response.
     * Looks for code blocks marked with ```groovy.
     */
    private String extractScript(String response) throws ScriptGenerationException {
        if (response == null || response.trim().isEmpty()) {
            throw new ScriptGenerationException("Empty response from Bedrock");
        }

        // Look for ```groovy code block
        String groovyMarker = "```groovy";
        String endMarker = "```";

        int startIdx = response.indexOf(groovyMarker);
        if (startIdx >= 0) {
            startIdx += groovyMarker.length();
            int endIdx = response.indexOf(endMarker, startIdx);
            if (endIdx > startIdx) {
                return response.substring(startIdx, endIdx).trim();
            }
        }

        // Fallback: look for any code block
        startIdx = response.indexOf("```");
        if (startIdx >= 0) {
            startIdx += 3;
            // Skip language identifier if present
            int newlineIdx = response.indexOf('\n', startIdx);
            if (newlineIdx > startIdx && newlineIdx - startIdx < 20) {
                startIdx = newlineIdx + 1;
            }
            int endIdx = response.indexOf("```", startIdx);
            if (endIdx > startIdx) {
                return response.substring(startIdx, endIdx).trim();
            }
        }

        // If no code blocks, return the whole response (assume it's the script)
        logger.warn("No code blocks found in response, using entire response as script");
        return response.trim();
    }

    /**
     * Wait before retrying.
     */
    private void waitBeforeRetry(int attemptNumber) {
        try {
            long waitMs = 1000L * attemptNumber; // Exponential backoff
            logger.debug("Waiting {}ms before retry", waitMs);
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Close the Bedrock client.
     */
    public void close() {
        if (bedrockClient != null) {
            bedrockClient.close();
        }
    }

    /**
     * Result of script generation.
     */
    public static class GenerationResult {
        private final String scriptSource;
        private final int attempts;

        public GenerationResult(String scriptSource, int attempts) {
            this.scriptSource = scriptSource;
            this.attempts = attempts;
        }

        public String getScriptSource() {
            return scriptSource;
        }

        public int getAttempts() {
            return attempts;
        }
    }

    /**
     * Exception thrown when script generation fails.
     */
    public static class ScriptGenerationException extends Exception {
        public ScriptGenerationException(String message) {
            super(message);
        }

        public ScriptGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
