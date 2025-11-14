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
 * Service for generating OpenRewrite Recipe scripts using AWS Bedrock.
 * Similar to GroovyScriptGenerationService but generates recipes for
 * transformations
 * instead of visitors for searching.
 */
@Service
public class RecipeGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeGenerationService.class);

    private final BedrockApiClient bedrockClient;
    private final int maxRetries;
    private final ResourceLoader resourceLoader;
    private String recipeSkeleton;

    public RecipeGenerationService(
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

        // Configure Bedrock client
        BedrockConfig config = createBedrockConfig(
                model, temperature, topK, maxTokens, timeoutSeconds,
                awsRegion, awsAccessKey, awsSecretKey);

        this.bedrockClient = new BedrockApiClient(config);

        logger.info("Recipe generation service initialized with model: {}, temperature: {}, maxRetries: {}",
                model, temperature, maxRetries);
    }

    /**
     * Load the recipe skeleton example on startup.
     */
    @PostConstruct
    public void loadSkeleton() {
        try {
            Resource resource = resourceLoader.getResource("classpath:recipe-skeleton.groovy");
            recipeSkeleton = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            logger.info("Loaded recipe skeleton example ({} chars)", recipeSkeleton.length());
        } catch (IOException e) {
            logger.error("Failed to load recipe skeleton", e);
            recipeSkeleton = null;
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
        properties.setProperty("bedrock.retry.attempts", "1");
        properties.setProperty("bedrock.log.requests", "false");
        properties.setProperty("bedrock.log.responses", "false");
        properties.setProperty("bedrock.enabled", "true");

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
     * Generate a recipe script based on pattern and transformation description.
     */
    public GenerationResult generateRecipeScript(
            String projectPath,
            String patternDescription,
            String nodeType,
            String transformation,
            List<String> filePaths) throws ScriptGenerationException {

        return generateRecipeScriptWithErrorFeedback(
                projectPath, patternDescription, nodeType, transformation, filePaths, null, null);
    }

    /**
     * Generate a recipe script with error feedback from a previous failed attempt.
     */
    public GenerationResult generateRecipeScriptWithErrorFeedback(
            String projectPath,
            String patternDescription,
            String nodeType,
            String transformation,
            List<String> filePaths,
            String previousScript,
            String previousError) throws ScriptGenerationException {

        int attempts = 0;

        while (attempts < maxRetries) {
            attempts++;
            logger.info("Generating recipe script (attempt {}/{}): pattern='{}', transformation='{}'",
                    attempts, maxRetries, patternDescription, transformation);

            try {
                String prompt = buildPrompt(
                        projectPath,
                        patternDescription,
                        nodeType,
                        transformation,
                        filePaths,
                        previousScript,
                        previousError);

                BedrockResponse response = bedrockClient.invokeModel(prompt);
                String generatedScript = extractScript(response.getText());

                logger.info("Recipe script generated successfully on attempt {}", attempts);
                return new GenerationResult(generatedScript, attempts);

            } catch (BedrockApiException e) {
                logger.error("Bedrock API error on attempt {}: {}", attempts, e.getMessage());
                previousError = "Bedrock API error: " + e.getMessage();

                if (attempts >= maxRetries) {
                    throw new ScriptGenerationException(
                            "Failed to generate recipe after " + maxRetries + " attempts: " + e.getMessage(), e);
                }

                waitBeforeRetry(attempts);
            }
        }

        throw new ScriptGenerationException("Failed to generate recipe after " + maxRetries + " attempts");
    }

    /**
     * Build the prompt for Bedrock to generate a recipe script.
     */
    private String buildPrompt(
            String projectPath,
            String patternDescription,
            String nodeType,
            String transformation,
            List<String> filePaths,
            String previousScript,
            String previousError) {

        String contextSection = buildContextSection(projectPath, filePaths);
        String skeletonSection = recipeSkeleton != null
                ? "\n```groovy\n" + recipeSkeleton + "\n```\n"
                : "";
        String exampleCode = buildExampleCode(nodeType, transformation);
        String errorFeedback = buildErrorFeedbackSection(previousScript, previousError);

        return """
                You are a Java code refactoring expert. Generate a Groovy script that implements \
                an OpenRewrite Recipe to transform Java code matching a specific pattern.

                ## Pattern to Find and Transform
                %s

                ## Transformation to Apply
                %s

                ## Target Node Type
                %s

                %s

                ## Requirements
                1. Create a class that extends org.openrewrite.Recipe
                2. Implement getDisplayName() and getDescription()
                3. Implement getVisitor() returning a JavaIsoVisitor<ExecutionContext>
                4. In the visitor, override the appropriate visit method for node type: visit%s
                5. Implement transformation logic using withXXX() methods for immutable updates
                6. Return ONLY the Groovy code, no explanations
                7. Use @CompileStatic annotation for type safety
                8. Use proper OpenRewrite Recipe patterns

                ## WORKING EXAMPLE TO ADAPT
                Here is a complete, tested Recipe that demonstrates correct OpenRewrite Recipe usage.
                Adapt this pattern for your specific transformation:
                %s

                ## Important Notes on Transformations
                - OpenRewrite uses immutable AST nodes
                - Never modify nodes directly - use withXXX() methods
                - Common transformation methods:
                  * withSimpleName(newName) - rename methods/fields
                  * withModifiers(newModifiers) - change modifiers
                  * withBody(newBody) - replace method body
                  * withAnnotations(newAnnotations) - add/remove annotations
                - Always call super.visitXXX() to continue traversal
                - Return the modified node from visit methods

                %s
                %s
                Generate ONLY the Groovy code, enclosed in ```groovy and ``` markers. \
                Do not include any explanations before or after the code block.
                """
                .formatted(
                        patternDescription,
                        transformation,
                        nodeType,
                        contextSection,
                        nodeType,
                        skeletonSection,
                        exampleCode,
                        errorFeedback);
    }

    /**
     * Build context section of the prompt.
     */
    private String buildContextSection(String projectPath, List<String> filePaths) {
        String filesInfo = (filePaths != null && !filePaths.isEmpty())
                ? "Target Files: " + String.join(", ", filePaths)
                : "Scope: All Java files in project";

        return """
                ## Context
                Project Path: %s
                %s
                """.formatted(
                projectPath != null ? projectPath : "N/A",
                filesInfo);
    }

    /**
     * Build example code section for recipe generation.
     */
    private String buildExampleCode(String nodeType, String transformation) {
        return """
                ```groovy
                import groovy.transform.CompileStatic
                import org.openrewrite.ExecutionContext
                import org.openrewrite.Recipe
                import org.openrewrite.TreeVisitor
                import org.openrewrite.java.JavaIsoVisitor
                import org.openrewrite.java.tree.J

                @CompileStatic
                class TransformationRecipe extends Recipe {

                    @Override
                    String getDisplayName() {
                        return "Apply transformation: %s"
                    }

                    @Override
                    String getDescription() {
                        return "Transforms code matching the pattern"
                    }

                    @Override
                    TreeVisitor<?, ExecutionContext> getVisitor() {
                        return new JavaIsoVisitor<ExecutionContext>() {

                            @Override
                            J.%s visit%s(J.%s node, ExecutionContext ctx) {
                                // Check if pattern matches
                                if (/* your matching logic */) {
                                    // Apply transformation using withXXX() methods
                                    return node.withSimpleName("newName")  // example transformation
                                }

                                return super.visit%s(node, ctx)
                            }
                        }
                    }
                }

                // Return the recipe instance
                return new TransformationRecipe()
                ```
                """.formatted(transformation, nodeType, nodeType, nodeType, nodeType);
    }

    /**
     * Build error feedback section if there was a previous attempt.
     */
    private String buildErrorFeedbackSection(String previousScript, String previousError) {
        if (previousError == null || previousScript == null) {
            return "";
        }

        String staticCompilationSuggestion = "";
        if (isStaticCompilationError(previousError)) {
            staticCompilationSuggestion = """

                    SUGGESTION: The error appears to be related to @CompileStatic constraints. \
                    Check type declarations and ensure all types are properly declared.
                    """;
        }

        return """

                ## Previous Attempt Failed
                The previous recipe had this error:
                ```
                %s
                ```
                %s
                Previous recipe that failed:
                ```groovy
                %s
                ```

                Please fix the error and generate a corrected recipe.
                """.formatted(previousError, staticCompilationSuggestion, previousScript);
    }

    /**
     * Extract the recipe script from Bedrock response.
     */
    private String extractScript(String response) throws ScriptGenerationException {
        if (response == null || response.trim().isEmpty()) {
            throw new ScriptGenerationException("Empty response from Bedrock");
        }

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

        startIdx = response.indexOf("```");
        if (startIdx >= 0) {
            startIdx += 3;
            int newlineIdx = response.indexOf('\n', startIdx);
            if (newlineIdx > startIdx && newlineIdx - startIdx < 20) {
                startIdx = newlineIdx + 1;
            }
            int endIdx = response.indexOf("```", startIdx);
            if (endIdx > startIdx) {
                return response.substring(startIdx, endIdx).trim();
            }
        }

        logger.warn("No code blocks found in response, using entire response as script");
        return response.trim();
    }

    /**
     * Check if error is related to static compilation.
     */
    private boolean isStaticCompilationError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String lowerError = errorMessage.toLowerCase();
        return lowerError.contains("static type checking") ||
                lowerError.contains("cannot find matching method") ||
                lowerError.contains("cannot assign value of type") ||
                lowerError.contains("incompatible types") ||
                lowerError.contains("cannot convert from");
    }

    /**
     * Wait before retrying.
     */
    private void waitBeforeRetry(int attemptNumber) {
        try {
            long waitMs = 1000L * attemptNumber;
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
     * Result of recipe generation.
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
     * Exception thrown when recipe generation fails.
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
