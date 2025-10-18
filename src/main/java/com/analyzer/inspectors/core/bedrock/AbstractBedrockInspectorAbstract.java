package com.analyzer.inspectors.core.bedrock;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractTextFileInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for inspectors that use AWS Bedrock AI models for code
 * analysis.
 * Extends AbstractTextFileInspector to leverage source code content processing
 * capabilities.
 * <p>
 * Subclasses must implement:
 * - getName() from Inspector interface
 * - buildPrompt() to create model-specific prompts
 * - parseResponse() to process AI responses and set tags via ProjectFileDecorator
 */
public abstract class AbstractBedrockInspectorAbstract extends AbstractTextFileInspector {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractBedrockInspectorAbstract.class);

    private final BedrockConfig config;
    private BedrockApiClient apiClient;
    private boolean initializedSuccessfully = false;

    /**
     * Creates a AbstractBedrockInspectorAbstract with the specified ResourceResolver and default
     * configuration.
     *
     * @param resourceResolver the resolver for accessing source file resources
     */
    protected AbstractBedrockInspectorAbstract(ResourceResolver resourceResolver) {
        this(resourceResolver, BedrockConfig.load());
    }

    /**
     * Creates a AbstractBedrockInspectorAbstract with the specified ResourceResolver and
     * configuration.
     *
     * @param resourceResolver the resolver for accessing source file resources
     * @param config           the Bedrock configuration to use
     */
    protected AbstractBedrockInspectorAbstract(ResourceResolver resourceResolver, BedrockConfig config) {
        super(resourceResolver);
        this.config = config;

        try {
            // Validate configuration
            config.validate();

            // Initialize API client
            this.apiClient = new BedrockApiClient(config);

            logger.info("Initialized Bedrock inspector: {} with model: {}",
                    getName(), config.getModelId());
            this.initializedSuccessfully = true;

        } catch (BedrockConfigurationException e) {
            logger.warn("Invalid Bedrock configuration for inspector {}: {}. The inspector will be disabled.", getName(), e.getMessage());
            this.initializedSuccessfully = false;
        }
    }

    @Override
    protected final void processContent(String content, ProjectFile clazz, ProjectFileDecorator projectFileDecorator) {
        if (!initializedSuccessfully) {
            logger.debug("Bedrock inspector {} is not initialized due to configuration issues.", getName());
            projectFileDecorator.notApplicable();
            return;
        }
        // Check if Bedrock integration is enabled
        if (!config.isEnabled()) {
            logger.debug("Bedrock integration disabled for inspector: {}", getName());
            projectFileDecorator.notApplicable();
            return;
        }

        // Check if content is suitable for AI analysis
        if (content == null || content.trim().isEmpty()) {
            projectFileDecorator.error("No source content available for AI analysis");
            return;
        }

        if (content.length() < 10) {
            logger.debug("Content too short for meaningful AI analysis: {} characters", content.length());
            projectFileDecorator.notApplicable();
            return;
        }

        try {
            // Build prompt for AI model
            String prompt = buildPrompt(content, clazz);
            if (prompt == null || prompt.trim().isEmpty()) {
                projectFileDecorator.error("Failed to build prompt for AI analysis");
                return;
            }

            logger.debug("Invoking Bedrock model for class: {} with prompt length: {}",
                    clazz.getFullyQualifiedName(), prompt.length());

            // Call Bedrock API
            BedrockResponse response = apiClient.invokeModel(prompt);

            // Validate response
            if (!response.hasValidText()) {
                projectFileDecorator.error(
                        "Bedrock API returned empty or invalid response");
                return;
            }

            logger.debug("Received Bedrock response for class: {} with {} characters",
                    clazz.getFullyQualifiedName(), response.getText().length());

            // Parse and return result
            parseResponse(response.getText(), clazz, projectFileDecorator);

        } catch (BedrockApiException e) {
            logger.warn("Bedrock API call failed for class {}: {}",
                    clazz.getFullyQualifiedName(), e.getMessage());
            projectFileDecorator.error("Bedrock API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during Bedrock analysis for class {}: {}",
                    clazz.getFullyQualifiedName(), e.getMessage(), e);
            projectFileDecorator.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Build a prompt for the AI model based on the source code content and class
     * information.
     * This method should create a clear, specific prompt that will guide the AI to
     * produce
     * the desired analysis result.
     *
     * @param content the complete source code content
     * @param clazz   the class being analyzed (contains metadata like name,
     *                package, etc.)
     * @return the prompt string for the AI model
     */
    protected abstract String buildPrompt(String content, ProjectFile clazz);

    /**
     * Parse the response from the AI model and extract the analysis result.
     * This method should interpret the AI's response and set appropriate
     * tags on the ProjectFile via projectFile.setTag() calls.
     *
     * @param response        the text response from the AI model
     * @param clazz           the class being analyzed
     * @param projectFileDecorator decorator for handling errors and success states
     */
    protected abstract void parseResponse(String response, ProjectFile clazz, ProjectFileDecorator projectFileDecorator);


    /**
     * Get the Bedrock configuration used by this inspector.
     * Useful for debugging and testing.
     *
     * @return the current Bedrock configuration
     */
    protected BedrockConfig getConfig() {
        return config;
    }

    /**
     * Get the API client used by this inspector.
     * Useful for testing and advanced use cases.
     *
     * @return the current API client
     */
    protected BedrockApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Create a context-aware prompt that includes class metadata.
     * This is a utility method that subclasses can use to build comprehensive
     * prompts.
     *
     * @param basePrompt the base analysis prompt
     * @param content    the source code content
     * @param clazz      the class being analyzed
     * @return a context-enriched prompt
     */
    protected String buildContextualPrompt(String basePrompt, String content, ProjectFile clazz) {

        String promptBuilder = basePrompt + "\n\n" +

                // Add class context
                "Class Information:\n" +
                "- Class Name: " + clazz.getClassName() + "\n" +
                "- Package: " + clazz.getPackageName() + "\n" +
                "- Fully Qualified Name: " + clazz.getFullyQualifiedName() + "\n" +

                // Add source code
                "\nSource Code:\n" +
                "```java\n" +
                content +
                "\n```\n";

        return promptBuilder;
    }

    /**
     * Check if the configured model supports advanced prompting features.
     * This can be used by subclasses to optimize their prompts based on model
     * capabilities.
     *
     * @return true if the model supports Messages API (Claude 3+), false otherwise
     */
    protected boolean supportsAdvancedPrompting() {
        String modelId = config.getModelId();
        return modelId.contains("claude-3") ||
                modelId.contains("claude-sonnet-4") ||
                modelId.contains("us.anthropic.claude-3") ||
                modelId.contains("us.anthropic.claude-sonnet-4");
    }

    /**
     * Get the model family for optimization purposes.
     * This helps subclasses understand what type of model they're working with.
     *
     * @return the model family (CLAUDE, TITAN, or GENERIC)
     */
    protected ModelFamily getModelFamily() {
        String modelId = config.getModelId();
        if (modelId.contains("anthropic.claude") || modelId.contains("us.anthropic.claude")) {
            return ModelFamily.CLAUDE;
        } else if (modelId.contains("amazon.titan")) {
            return ModelFamily.TITAN;
        } else {
            return ModelFamily.GENERIC;
        }
    }

    /**
     * Enum representing different model families with their capabilities.
     */
    public enum ModelFamily {
        CLAUDE("Anthropic Claude models with strong reasoning and code analysis capabilities"),
        TITAN("Amazon Titan models optimized for general text generation"),
        GENERIC("Generic models with basic text generation capabilities");

        private final String description;

        ModelFamily(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Parse a numeric response from the AI model.
     * This is a utility method for inspectors that expect numeric results.
     *
     * @param response     the AI response text
     * @param defaultValue the default value if parsing fails
     * @return the parsed numeric value
     */
    protected double parseNumericResponse(String response, double defaultValue) {
        if (response == null || response.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            // Try to extract number from response
            String cleaned = response.trim().replaceAll("[^0-9.-]", "");
            if (!cleaned.isEmpty()) {
                return Double.parseDouble(cleaned);
            }
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse numeric response: {}", response);
        }

        return defaultValue;
    }

    /**
     * Parse a boolean response from the AI model.
     * This is a utility method for inspectors that expect yes/no results.
     *
     * @param response     the AI response text
     * @param defaultValue the default value if parsing fails
     * @return the parsed boolean value
     */
    protected boolean parseBooleanResponse(String response, boolean defaultValue) {
        if (response == null || response.trim().isEmpty()) {
            return defaultValue;
        }

        String lower = response.toLowerCase().trim();

        // Check for positive indicators
        if (lower.contains("yes") || lower.contains("true") ||
                lower.contains("positive") || lower.contains("detected")) {
            return true;
        }

        // Check for negative indicators
        if (lower.contains("no") || lower.contains("false") ||
                lower.contains("negative") || lower.contains("not detected")) {
            return false;
        }

        return defaultValue;
    }

    /**
     * Cleanup resources when the inspector is no longer needed.
     * Should be called when the application shuts down.
     */
    public void close() {
        if (apiClient != null) {
            apiClient.close();
        }
    }
}
