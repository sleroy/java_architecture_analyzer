package com.analyzer.migration.blocks.ai;

import com.analyzer.core.AnalysisConstants;
import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.migration.template.TemplateAwareBlock;
import com.analyzer.migration.template.TemplateProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Executes Amazon Q CLI for AI-assisted tasks with automatic conversation
 * logging.
 * This block runs the 'q chat' command with piped input and captures the
 * response
 * for use in subsequent migration steps.
 */
public class AiAssistedBlock implements TemplateAwareBlock {
    private static final Logger logger = LoggerFactory.getLogger(AiAssistedBlock.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final String CONVERSATIONS_DIR = "q";
    private static final String CONVERSATIONS_SUBDIR = "conversations";

    private final String name;
    private final String promptTemplate;
    private final String description;
    private final String outputVariable;
    private final int timeoutSeconds;
    private final TemplateProperty<Path> workingDirectoryTemplate;

    private AiAssistedBlock(Builder builder) {
        this.name = builder.name;
        this.promptTemplate = builder.promptTemplate;
        this.description = builder.description;
        this.outputVariable = builder.outputVariable;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.workingDirectoryTemplate = builder.workingDirectoryTemplate;
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
            if (processedPrompt.length() <= 2048) {
                logger.info("Resolved AI assisted prompt for '{}': {}", name, processedPrompt);
            } else {
                logger.info("Resolved AI assisted prompt for '{}': {} characters, starts with: {}",
                        name, processedPrompt.length(), processedPrompt.substring(0, 2048) + "...");
            }

            // Execute Amazon Q CLI
            String response = executeAmazonQ(processedPrompt, context);

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
     * Execute Amazon Q CLI with the given prompt using piped input.
     */
    private String executeAmazonQ(String prompt, MigrationContext context) throws IOException, InterruptedException {
        Path workingDir = workingDirectoryTemplate.resolve(context);
        logger.debug("Executing Amazon Q CLI with piped input for prompt: {} characters, working directory: {}",
                prompt.length(), workingDir);

        // Build the command for non-interactive chat mode
        ProcessBuilder processBuilder = new ProcessBuilder("q", "chat", "--no-interactive", "--trust-all-tools");
        // Don't merge stderr into stdout - capture them separately
        processBuilder.redirectErrorStream(false);

        // Set working directory for the process
        processBuilder.directory(workingDir.toFile());

        // Set environment to ensure proper execution
        processBuilder.environment().put("CI", "true");

        // Start the process
        Process process = processBuilder.start();

        // Capture stdout and stderr separately
        StringBuilder stdoutOutput = new StringBuilder();
        StringBuilder stderrOutput = new StringBuilder();

        // Read stdout in a separate thread
        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutOutput.append(line).append("\n");
                    // Use info level for better visibility
                    logger.info("Amazon Q stdout: {}", line);
                }
            } catch (IOException e) {
                logger.debug("Stdout reading interrupted: {}", e.getMessage());
            }
        });

        // Read stderr in a separate thread
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrOutput.append(line).append("\n");
                    logger.warn("Amazon Q stderr: {}", line);
                }
            } catch (IOException e) {
                logger.debug("Stderr reading interrupted: {}", e.getMessage());
            }
        });

        // Write prompt to stdin in a separate thread
        Thread stdinWriter = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

                logger.info("Writing prompt to Amazon Q CLI stdin");
                writer.write(prompt);
                writer.newLine();
                writer.flush();

                // Close stdin to signal end of input
                writer.close();
                logger.info("Prompt sent and stdin closed");

            } catch (IOException e) {
                logger.warn("Stdin writing failed: {}", e.getMessage());
            }
        });

        stdoutReader.start();
        stderrReader.start();
        stdinWriter.start();

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            logger.warn("Amazon Q CLI timed out after {} seconds, forcibly terminating", timeoutSeconds);
            process.destroyForcibly();
            stdoutReader.interrupt();
            stderrReader.interrupt();
            stdinWriter.interrupt();

            // Give the forcible termination a moment to complete
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            throw new IOException("Amazon Q CLI timed out after " + timeoutSeconds + " seconds");
        }

        // Wait for all threads to complete
        try {
            stdoutReader.join(5000); // Wait max 5 seconds for stdout reading to complete
            stderrReader.join(5000); // Wait max 5 seconds for stderr reading to complete
            stdinWriter.join(1000); // Wait max 1 second for stdin writing to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check exit code
        int exitCode = process.exitValue();
        String stdoutContent = stdoutOutput.toString().trim();
        String stderrContent = stderrOutput.toString().trim();

        if (exitCode != 0) {
            // Log stderr content at error level if process failed
            if (!stderrContent.isEmpty()) {
                logger.error("Amazon Q CLI stderr output: {}", stderrContent);
            }

            String errorMessage = String.format("Amazon Q CLI failed with exit code %d. Stdout: %s, Stderr: %s",
                    exitCode, stdoutContent, stderrContent);
            throw new IOException(errorMessage);
        }

        // Log stderr content at warn level even if process succeeded (might contain
        // warnings)
        if (!stderrContent.isEmpty()) {
            logger.warn("Amazon Q CLI completed with stderr output: {}", stderrContent);
        }

        logger.info("Amazon Q CLI completed successfully, stdout length: {} characters, stderr length: {} characters",
                stdoutContent.length(), stderrContent.length());

        // Use stdout as the main response, but validate we got some content
        if (stdoutContent.isEmpty()) {
            if (!stderrContent.isEmpty()) {
                throw new IOException("Amazon Q CLI completed but returned no stdout output. Stderr: " + stderrContent);
            } else {
                throw new IOException("Amazon Q CLI completed but returned no output");
            }
        }

        return stdoutContent;
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
        md.append("- Uses Amazon Q CLI: `q chat` (with piped input)\n");
        if (outputVariable != null) {
            md.append("- Output Variable: `").append(outputVariable).append("`\n");
        }
        return md.toString();
    }

    @Override
    public boolean validate() {
        // Use validateTemplates() for template-aware validation
        return validateTemplates();
    }

    @Override
    public String[] getRequiredVariables() {
        // No specific variables required - template processing handles variable
        // dependencies
        return new String[0];
    }

    @Override
    public void resolveTemplates(MigrationContext context) {
        // Templates are resolved on-demand during execution
        // No pre-resolution needed for this block
    }

    @Override
    public boolean validateTemplates() {
        if (name == null || name.trim().isEmpty()) {
            logger.error("Name is required");
            return false;
        }
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            logger.error("Prompt template is required");
            return false;
        }
        if (workingDirectoryTemplate == null) {
            logger.error("Working directory template is required");
            return false;
        }
        // Template validation - check syntax without resolution
        return workingDirectoryTemplate.hasVariables() ||
                !workingDirectoryTemplate.getTemplate().trim().isEmpty();
    }

    @Override
    public boolean hasUnresolvedTemplates() {
        return workingDirectoryTemplate != null && !workingDirectoryTemplate.isResolved();
    }

    public static class Builder {
        private String name;
        private String promptTemplate;
        private String description;
        private String outputVariable;
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private TemplateProperty<Path> workingDirectoryTemplate;

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

        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectoryTemplate = new TemplateProperty<>(
                    workingDirectory.toString(),
                    Paths::get);
            return this;
        }

        public Builder workingDirectoryTemplate(String template) {
            this.workingDirectoryTemplate = new TemplateProperty<>(template, Paths::get);
            return this;
        }

        public AiAssistedBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                throw new IllegalStateException("Prompt template is required for the task " + name);
            }
            if (workingDirectoryTemplate == null) {
                throw new IllegalStateException("Working directory template is required for the task " + name);
            }
            return new AiAssistedBlock(this);
        }
    }
}
