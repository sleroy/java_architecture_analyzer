package com.analyzer.migration.blocks.ai;

import com.analyzer.core.AnalysisConstants;
import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Executes Amazon Q CLI for AI-assisted tasks with automatic conversation
 * logging.
 * This block runs the 'q chat --no-interactive' command and captures the
 * response
 * for use in subsequent migration steps.
 */
public class AiAssistedBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(AiAssistedBlock.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final String CONVERSATIONS_DIR = "q";
    private static final String CONVERSATIONS_SUBDIR = "conversations";

    private final String name;
    private final String promptTemplate;
    private final String description;
    private final String outputVariable;
    private final int timeoutSeconds;

    private AiAssistedBlock(Builder builder) {
        this.name = builder.name;
        this.promptTemplate = builder.promptTemplate;
        this.description = builder.description;
        this.outputVariable = builder.outputVariable;
        this.timeoutSeconds = builder.timeoutSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Log available variables for debugging
            logger.debug("Processing AI assisted prompt '{}' with {} available variables", name,
                    context.getAllVariables().size());
            logger.debug("Available variables: {}", context.getAllVariables().keySet());

            // Process template with context variables
            String processedPrompt = context.substituteVariables(promptTemplate);

            // Log resolved prompt (truncated if too long)
            if (processedPrompt.length() <= 500) {
                logger.info("Resolved AI assisted prompt for '{}': {}", name, processedPrompt);
            } else {
                logger.info("Resolved AI assisted prompt for '{}': {} characters, starts with: {}",
                        name, processedPrompt.length(), processedPrompt.substring(0, 500) + "...");
            }

            // Execute Amazon Q CLI
            String response = executeAmazonQ(processedPrompt);

            // Save conversation to file
            Path conversationFile = saveConversation(context.getProjectRoot(), processedPrompt, response);

            long executionTime = System.currentTimeMillis() - startTime;

            logger.info("Amazon Q response received for '{}': {} characters, saved to: {}",
                    name, response.length(), conversationFile);

            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .success(true)
                    .message("Amazon Q response generated successfully")
                    .outputVariable("prompt", processedPrompt)
                    .outputVariable("conversation_file", conversationFile.toString())
                    .executionTimeMs(executionTime);

            // Set the output variable if specified
            if (outputVariable != null && !outputVariable.trim().isEmpty()) {
                resultBuilder.outputVariable(outputVariable, response);
            } else {
                // Default to "ai_response" if no output variable specified
                resultBuilder.outputVariable("ai_response", response);
            }

            return resultBuilder.build();

        } catch (Exception e) {
            logger.error("Failed to execute Amazon Q for '{}': {}", name, e.getMessage(), e);
            return BlockResult.failure(
                    "Failed to execute Amazon Q",
                    e.getMessage());
        }
    }

    /**
     * Execute Amazon Q CLI with the given prompt.
     */
    private String executeAmazonQ(String prompt) throws IOException, InterruptedException {
        logger.debug("Executing Amazon Q CLI for prompt: {} characters", prompt.length());

        // Build the command
        ProcessBuilder processBuilder = new ProcessBuilder("q", "chat", "--no-interactive", prompt);
        processBuilder.redirectErrorStream(true); // Merge stderr into stdout

        // Start the process
        Process process = processBuilder.start();

        // Read the output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Amazon Q CLI timed out after " + timeoutSeconds + " seconds");
        }

        // Check exit code
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorMessage = String.format("Amazon Q CLI failed with exit code %d. Output: %s",
                    exitCode, output.toString().trim());
            throw new IOException(errorMessage);
        }

        String response = output.toString().trim();
        logger.debug("Amazon Q CLI completed successfully, response length: {} characters", response.length());

        return response;
    }

    /**
     * Save the conversation to a file in the .analysis/q/conversations directory.
     */
    private Path saveConversation(Path projectRoot, String prompt, String response) throws IOException {
        // Create conversations directory using constants
        Path conversationDir = projectRoot
                .resolve(AnalysisConstants.ANALYSIS_DIR)
                .resolve(CONVERSATIONS_DIR)
                .resolve(CONVERSATIONS_SUBDIR);

        Files.createDirectories(conversationDir);

        // Generate filename with timestamp and block name
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sanitizedName = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = String.format("%s_%s.md", timestamp, sanitizedName);

        Path conversationFile = conversationDir.resolve(filename);

        // Create conversation content
        StringBuilder content = new StringBuilder();
        content.append("# Amazon Q Conversation: ").append(name).append("\n");
        content.append("**Timestamp:** ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        content.append("**Block:** ").append(name).append("\n");
        if (description != null && !description.trim().isEmpty()) {
            content.append("**Description:** ").append(description).append("\n");
        }
        content.append("\n");

        content.append("## Prompt\n");
        content.append("```\n");
        content.append(prompt);
        content.append("\n```\n\n");

        content.append("## Response\n");
        content.append(response);
        content.append("\n");

        // Write to file
        Files.write(conversationFile, content.toString().getBytes(StandardCharsets.UTF_8));

        logger.debug("Conversation saved to: {}", conversationFile);
        return conversationFile;
    }

    @Override
    public BlockType getType() {
        return BlockType.AI_ASSISTED;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (AI Assisted)\n");
        if (description != null && !description.isEmpty()) {
            md.append("- Description: ").append(description).append("\n");
        }
        if (promptTemplate.length() <= 200) {
            md.append("- Template: `").append(promptTemplate).append("`\n");
        } else {
            md.append("- Template: ").append(promptTemplate.length()).append(" characters\n");
        }
        md.append("- Uses Amazon Q CLI: `q chat --no-interactive`\n");
        if (outputVariable != null) {
            md.append("- Output Variable: `").append(outputVariable).append("`\n");
        }
        return md.toString();
    }

    @Override
    public boolean validate() {
        if (name == null || name.trim().isEmpty()) {
            logger.error("Name is required");
            return false;
        }
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            logger.error("Prompt template is required");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        // No specific variables required - template processing handles variable
        // dependencies
        return new String[0];
    }

    public static class Builder {
        private String name;
        private String promptTemplate;
        private String description;
        private String outputVariable;
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

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

        public Builder outputVariable(String outputVariable) {
            this.outputVariable = outputVariable;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public AiAssistedBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                throw new IllegalStateException("Prompt template is required");
            }
            return new AiAssistedBlock(this);
        }
    }
}
