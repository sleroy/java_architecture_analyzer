package com.analyzer.migration.blocks.automated;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes shell commands with output capture and exit code handling.
 * Supports variable substitution in commands and working directory
 * specification.
 */
public class CommandBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(CommandBlock.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    private final String name;
    private final String command;
    private final String workingDirectory;
    private final long timeoutSeconds;
    private final boolean captureOutput;
    private final String enableIf;
    private final String outputVariableName;

    private CommandBlock(Builder builder) {
        this.name = builder.name;
        this.command = builder.command;
        this.workingDirectory = builder.workingDirectory;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.captureOutput = builder.captureOutput;
        this.enableIf = builder.enableIf;
        this.outputVariableName = builder.outputVariableName;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Substitute variables in command and working directory
            String processedCommand = context.substituteVariables(command);
            String processedWorkDir = workingDirectory != null
                    ? context.substituteVariables(workingDirectory)
                    : context.getProjectRoot().toString();

            logger.info("Executing command: {} in directory: {}", processedCommand, processedWorkDir);

            // Build process
            ProcessBuilder processBuilder = new ProcessBuilder();

            // Use shell to execute command (supports piping, etc.)
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                processBuilder.command("cmd.exe", "/c", processedCommand);
            } else {
                processBuilder.command("sh", "-c", processedCommand);
            }

            processBuilder.directory(new File(processedWorkDir));
            processBuilder.redirectErrorStream(true); // Merge stderr with stdout

            // Start process
            Process process = processBuilder.start();

            // Capture output
            List<String> outputLines = new ArrayList<>();
            if (captureOutput) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputLines.add(line);
                        // Log at INFO level so users can see command output in real-time
                        logger.info("  | {}", line);
                    }
                }
            }

            // Wait for completion with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return BlockResult.failure(
                        "Command timeout after " + timeoutSeconds + " seconds",
                        "Command: " + processedCommand);
            }

            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;

            // Build result
            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .executionTimeMs(executionTime)
                    .outputVariable("exit_code", exitCode)
                    .outputVariable("command", processedCommand);

            if (captureOutput && !outputLines.isEmpty()) {
                String joinedOutput = String.join("\n", outputLines);
                // Use custom output variable name if specified, otherwise default to "output"
                String varName = (outputVariableName != null && !outputVariableName.trim().isEmpty())
                        ? outputVariableName
                        : "output";
                resultBuilder.outputVariable(varName, joinedOutput);
                resultBuilder.outputVariable("output_lines", outputLines);
            }

            if (exitCode == 0) {
                return resultBuilder
                        .success(true)
                        .message("Command executed successfully")
                        .build();
            } else {
                return resultBuilder
                        .success(false)
                        .message("Command failed with exit code " + exitCode)
                        .errorDetails(captureOutput ? String.join("\n", outputLines) : "Output not captured")
                        .build();
            }

        } catch (IOException e) {
            return BlockResult.failure(
                    "Failed to execute command",
                    "Error: " + e.getMessage() + "\nCommand: " + command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BlockResult.failure(
                    "Command execution interrupted",
                    "Command: " + command);
        } catch (Exception e) {
            return BlockResult.failure(
                    "Unexpected error during command execution",
                    e.getMessage());
        }
    }

    @Override
    public BlockType getType() {
        return BlockType.COMMAND;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getEnableIf() {
        return enableIf;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (Command)\n");
        md.append("- Command: `").append(command).append("`\n");
        if (workingDirectory != null) {
            md.append("- Working Directory: `").append(workingDirectory).append("`\n");
        }
        md.append("- Timeout: ").append(timeoutSeconds).append(" seconds\n");
        return md.toString();
    }

    @Override
    public boolean validate() {
        if (command == null || command.trim().isEmpty()) {
            logger.error("Command cannot be empty");
            return false;
        }
        if (timeoutSeconds <= 0) {
            logger.error("Timeout must be positive");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        // Extract variables from command string (simple implementation)
        List<String> vars = new ArrayList<>();
        // This would need proper template parsing for comprehensive extraction
        // For now, return empty - validation will happen during execution
        return vars.toArray(new String[0]);
    }

    public static class Builder {
        private String name;
        private String command;
        private String workingDirectory;
        private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private boolean captureOutput = true;
        private String enableIf;
        private String outputVariableName;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder timeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder captureOutput(boolean captureOutput) {
            this.captureOutput = captureOutput;
            return this;
        }

        public Builder enableIf(String enableIf) {
            this.enableIf = enableIf;
            return this;
        }

        public Builder outputVariableName(String outputVariableName) {
            this.outputVariableName = outputVariableName;
            return this;
        }

        public CommandBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (command == null || command.isEmpty()) {
                throw new IllegalStateException("Command is required");
            }
            return new CommandBlock(this);
        }
    }
}
