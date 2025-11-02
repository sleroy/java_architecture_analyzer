package com.analyzer.migration.blocks.ai;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.bedrock.BedrockConfig;
import com.analyzer.core.bedrock.BedrockApiClient;
import com.analyzer.core.bedrock.BedrockResponse;
import com.analyzer.core.bedrock.BedrockApiException;
import com.analyzer.core.bedrock.BedrockConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Generates a single AI prompt using AWS Bedrock models.
 * Supports template processing, response caching, and multiple response
 * formats.
 */
public class AiPromptBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(AiPromptBlock.class);

    private final String name;
    private final String promptTemplate;
    private final String description;
    private final boolean displayFormatted;
    private final ResponseFormat responseFormat;
    private final boolean enableBedrock;
    private final String outputVariable;
    private final Double temperature;
    private final Integer maxTokens;
    private final LocalCache cache;
    private final ObjectMapper objectMapper;

    // Bedrock components (initialized lazily)
    private BedrockConfig bedrockConfig;
    private BedrockApiClient bedrockClient;
    private boolean bedrockInitialized = false;

    private AiPromptBlock(Builder builder) {
        this.name = builder.name;
        this.promptTemplate = builder.promptTemplate;
        this.description = builder.description;
        this.displayFormatted = builder.displayFormatted;
        this.responseFormat = builder.responseFormat;
        this.enableBedrock = builder.enableBedrock;
        this.outputVariable = builder.outputVariable;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.cache = new LocalCache(true); // Enable caching for responses
        this.objectMapper = new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Log available variables for debugging
            logger.debug("Processing AI prompt '{}' with {} available variables", name,
                    context.getAllVariables().size());
            logger.debug("Available variables: {}", context.getAllVariables().keySet());

            // Log original template (truncated if too long)
            if (promptTemplate.length() <= 500) {
                logger.debug("Original prompt template: {}", promptTemplate);
            } else {
                logger.debug("Original prompt template: {} characters, starts with: {}",
                        promptTemplate.length(), promptTemplate.substring(0, 200) + "...");
            }

            // Process template with context variables
            String processedPrompt = context.substituteVariables(promptTemplate);

            // Log resolved prompt (truncated if too long)
            if (processedPrompt.length() <= 500) {
                logger.info("Resolved AI prompt for '{}': {}", name, processedPrompt);
            } else {
                logger.info("Resolved AI prompt for '{}': {} characters, starts with: {}",
                        name, processedPrompt.length(), processedPrompt.substring(0, 500) + "...");
            }

            // Format output for display
            String formattedOutput = formatPromptForDisplay(processedPrompt);

            // Initialize Bedrock if enabled and not already initialized
            if (enableBedrock && !bedrockInitialized) {
                initializeBedrock();
            }

            String response = null;
            String parsedResponse = null;

            // If Bedrock is enabled and initialized, make API call
            if (enableBedrock && bedrockInitialized && bedrockConfig.isEnabled()) {
                try {
                    response = invokeBedrockWithCaching(processedPrompt);
                    parsedResponse = parseResponse(response, responseFormat);
                    logger.info("Bedrock API response received for '{}': {} characters", name, response.length());
                } catch (BedrockApiException e) {
                    logger.warn("Bedrock API call failed for '{}', continuing with prompt-only output: {}", name,
                            e.getMessage());
                    // Continue execution with prompt-only output
                }
            }

            // Log the prompt
            logger.info("AI Prompt generated: {}", name);
            if (displayFormatted) {
                System.out.println("\n" + formattedOutput);
                if (response != null) {
                    System.out.println("\nAI RESPONSE:");
                    System.out.println("=".repeat(80));
                    System.out.println(parsedResponse != null ? parsedResponse : response);
                    System.out.println("=".repeat(80));
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;

            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .success(true)
                    .message("AI prompt generated successfully")
                    .outputVariable("prompt", processedPrompt)
                    .outputVariable("formatted_prompt", formattedOutput)
                    .executionTimeMs(executionTime);

            // Add response variables if available
            if (response != null) {
                // Use custom output variable name if specified, otherwise default to
                // "ai_response"
                String responseVarName = outputVariable != null ? outputVariable : "ai_response";
                resultBuilder.outputVariable(responseVarName, response);

                // Always keep the standard variable names for backward compatibility
                if (outputVariable != null) {
                    resultBuilder.outputVariable("ai_response", response);
                    resultBuilder.outputVariable(outputVariable, response);
                }

                if (parsedResponse != null) {
                    resultBuilder.outputVariable(responseVarName + "_parsed", parsedResponse);
                    if (outputVariable != null) {
                        resultBuilder.outputVariable("ai_response_parsed", parsedResponse);
                        resultBuilder.outputVariable(outputVariable, parsedResponse);
                    }
                }
            }

            return resultBuilder.build();

        } catch (Exception e) {
            logger.error("Failed to generate AI prompt '{}': {}", name, e.getMessage(), e);
            return BlockResult.failure(
                    "Failed to generate AI prompt",
                    e.getMessage());
        }
    }

    /**
     * Initialize Bedrock configuration and API client.
     */
    private void initializeBedrock() {
        try {
            // Load Bedrock configuration
            this.bedrockConfig = BedrockConfig.load();
            this.bedrockConfig.validate();

            // Initialize API client
            this.bedrockClient = new BedrockApiClient(bedrockConfig);
            this.bedrockInitialized = true;

            logger.info("Bedrock initialized for AI prompt '{}' with model: {}", name, bedrockConfig.getModelId());
        } catch (BedrockConfigurationException e) {
            logger.warn("Failed to initialize Bedrock for '{}': {}. Will continue with prompt-only output.",
                    name, e.getMessage());
            this.bedrockInitialized = false;
        } catch (Exception e) {
            logger.warn("Unexpected error initializing Bedrock for '{}': {}. Will continue with prompt-only output.",
                    name, e.getMessage());
            this.bedrockInitialized = false;
        }
    }

    /**
     * Invoke Bedrock API with caching support.
     */
    private String invokeBedrockWithCaching(String prompt) throws BedrockApiException {
        // Create cache key based on prompt and configuration
        String cacheKey = createCacheKey(prompt);

        // Try to get cached response first
        if (bedrockConfig.isCacheResults()) {
            String cachedResponse = cache.getOrCompute(cacheKey, () -> {
                try {
                    BedrockResponse response = bedrockClient.invokeModel(prompt);
                    return response.getText();
                } catch (BedrockApiException e) {
                    logger.debug("Cache miss, API call failed: {}", e.getMessage());
                    return null;
                }
            });

            if (cachedResponse != null) {
                logger.debug("Cache hit for prompt '{}'", name);
                return cachedResponse;
            }
        }

        // Make API call if not cached or caching disabled
        BedrockResponse response = bedrockClient.invokeModel(prompt);
        return response.getText();
    }

    /**
     * Create a cache key based on prompt and model configuration.
     */
    private String createCacheKey(String prompt) {
        try {
            String keyData = prompt + "|" + bedrockConfig.getModelId() + "|" +
                    bedrockConfig.getTemperature() + "|" + bedrockConfig.getTopP();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keyData.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "bedrock_" + hexString.toString().substring(0, 16);
        } catch (Exception e) {
            // Fallback to simple hash if SHA-256 fails
            return "bedrock_" + Math.abs((prompt + bedrockConfig.getModelId()).hashCode());
        }
    }

    /**
     * Parse the AI response based on the specified response format.
     */
    private String parseResponse(String response, ResponseFormat format) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        switch (format) {
            case TEXT:
                return response.trim();

            case JSON:
                try {
                    // Validate and pretty-print JSON
                    Object jsonObject = objectMapper.readValue(response, Object.class);
                    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
                } catch (Exception e) {
                    logger.warn("Failed to parse response as JSON for '{}': {}. Returning raw response.",
                            name, e.getMessage());
                    return response;
                }

            case STRUCTURED:
                // For structured parsing, extract key information
                try {
                    // Try to parse as JSON first
                    Object jsonObject = objectMapper.readValue(response, Object.class);
                    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
                } catch (Exception e) {
                    // If not JSON, apply basic text structure cleanup
                    return response.trim()
                            .replaceAll("\\n\\s*\\n", "\n\n") // Clean up multiple newlines
                            .replaceAll("^\\s+", "") // Remove leading whitespace
                            .replaceAll("\\s+$", ""); // Remove trailing whitespace
                }

            default:
                return response.trim();
        }
    }

    /**
     * Formats the prompt for user-friendly display.
     */
    private String formatPromptForDisplay(String prompt) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("=".repeat(80)).append("\n");
        formatted.append("AI PROMPT: ").append(name).append("\n");
        if (description != null && !description.isEmpty()) {
            formatted.append("Description: ").append(description).append("\n");
        }
        formatted.append("=".repeat(80)).append("\n");
        formatted.append(prompt).append("\n");
        formatted.append("=".repeat(80)).append("\n");
        return formatted.toString();
    }

    @Override
    public BlockType getType() {
        return BlockType.AI_PROMPT;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (AI Prompt)\n");
        if (description != null && !description.isEmpty()) {
            md.append("- Description: ").append(description).append("\n");
        }
        if (promptTemplate.length() <= 200) {
            md.append("- Template: `").append(promptTemplate).append("`\n");
        } else {
            md.append("- Template: ").append(promptTemplate.length()).append(" characters\n");
        }
        return md.toString();
    }

    @Override
    public boolean validate() {
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            logger.error("Prompt template is required");
            return false;
        }
        return true;
    }

    public static class Builder {
        private String name;
        private String promptTemplate;
        private String description;
        private boolean displayFormatted = true;
        private ResponseFormat responseFormat = ResponseFormat.TEXT;
        private boolean enableBedrock = true;
        private String outputVariable;
        private Double temperature;
        private Integer maxTokens;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder promptTemplate(String promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder displayFormatted(boolean displayFormatted) {
            this.displayFormatted = displayFormatted;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder enableBedrock(boolean enableBedrock) {
            this.enableBedrock = enableBedrock;
            return this;
        }

        public Builder outputVariable(String outputVariable) {
            this.outputVariable = outputVariable;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public AiPromptBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                throw new IllegalStateException("Prompt template is required");
            }
            return new AiPromptBlock(this);
        }
    }
}
