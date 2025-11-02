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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes Git commands with idempotent error handling.
 * Supports single command string or array of commands to execute sequentially.
 * 
 * <p>
 * When idempotent flag is enabled, certain Git errors are treated as success:
 * <ul>
 * <li>Branch already exists (exit 128)</li>
 * <li>Tag already exists (exit 128)</li>
 * <li>Nothing to commit (exit 1)</li>
 * <li>Already on branch (exit 0/1)</li>
 * </ul>
 */
public class GitCommandBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(GitCommandBlock.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    private final String name;
    private final List<String> args; // Git arguments (without 'git' prefix)
    private final String workingDirectory;
    private final long timeoutSeconds;
    private final boolean idempotent;
    private final boolean captureOutput;

    private GitCommandBlock(Builder builder) {
        this.name = builder.name;
        this.args = builder.args;
        this.workingDirectory = builder.workingDirectory;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.idempotent = builder.idempotent;
        this.captureOutput = builder.captureOutput;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();
        List<String> allOutput = new ArrayList<>();

        try {
            // Substitute variables in working directory
            String processedWorkDir = workingDirectory != null
                    ? context.substituteVariables(workingDirectory)
                    : context.getProjectRoot().toString();

            logger.info("Executing {} Git command(s) in directory: {}", args.size(), processedWorkDir);

            // Execute each Git command sequentially
            for (int i = 0; i < args.size(); i++) {
                String gitArgs = context.substituteVariables(args.get(i));
                String fullCommand = "git " + gitArgs;

                logger.info("Git command {}/{}: {}", i + 1, args.size(), fullCommand);

                BlockResult result = executeGitCommand(fullCommand, processedWorkDir);

                // Collect output
                if (result.getOutputVariables().containsKey("output")) {
                    allOutput.add((String) result.getOutputVariables().get("output"));
                }

                // Check if command failed
                if (!result.isSuccess()) {
                    // If idempotent, check if this is a recoverable error
                    if (idempotent && isIdempotentError(result)) {
                        logger.info("Git command returned expected error (idempotent mode), treating as success");
                        continue; // Treat as success, continue with next command
                    } else {
                        // Real failure
                        return result;
                    }
                }
            }

            // All commands succeeded
            long executionTime = System.currentTimeMillis() - startTime;

            return BlockResult.builder()
                    .success(true)
                    .message("Git command(s) executed successfully")
                    .executionTimeMs(executionTime)
                    .outputVariable("command_count", args.size())
                    .outputVariable("output", String.join("\n", allOutput))
                    .build();

        } catch (Exception e) {
            return BlockResult.failure(
                    "Unexpected error during Git command execution",
                    e.getMessage());
        }
    }

    /**
     * Execute a single Git command.
     */
    private BlockResult executeGitCommand(String fullCommand, String workingDir) {
        long startTime = System.currentTimeMillis();

        try {
            // Build process
            ProcessBuilder processBuilder = new ProcessBuilder();

            // Use shell to execute command
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                processBuilder.command("cmd.exe", "/c", fullCommand);
            } else {
                processBuilder.command("sh", "-c", fullCommand);
            }

            processBuilder.directory(new File(workingDir));
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
                        logger.info("  | {}", line);
                    }
                }
            }

            // Wait for completion with timeout
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return BlockResult.failure(
                        "Git command timeout after " + timeoutSeconds + " seconds",
                        "Command: " + fullCommand);
            }

            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;
            String output = String.join("\n", outputLines);

            // Build result
            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .executionTimeMs(executionTime)
                    .outputVariable("exit_code", exitCode)
                    .outputVariable("command", fullCommand)
                    .outputVariable("output", output);

            if (exitCode == 0) {
                return resultBuilder
                        .success(true)
                        .message("Git command executed successfully")
                        .build();
            } else {
                return resultBuilder
                        .success(false)
                        .message("Git command failed with exit code " + exitCode)
                        .errorDetails(output)
                        .build();
            }

        } catch (IOException e) {
            return BlockResult.failure(
                    "Failed to execute Git command",
                    "Error: " + e.getMessage() + "\nCommand: " + fullCommand);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BlockResult.failure(
                    "Git command execution interrupted",
                    "Command: " + fullCommand);
        }
    }

    /**
     * Check if the error is an idempotent error that should be treated as success.
     */
    private boolean isIdempotentError(BlockResult result) {
        if (result.isSuccess()) {
            return false; // Not an error
        }

        String output = result.getErrorDetails() != null ? result.getErrorDetails().toLowerCase() : "";
        Integer exitCode = (Integer) result.getOutputVariables().get("exit_code");

        // Branch already exists
        if (exitCode != null && exitCode == 128 && output.contains("already exists")) {
            return true;
        }

        // Nothing to commit
        if (exitCode != null && exitCode == 1 && output.contains("nothing to commit")) {
            return true;
        }

        // Already on branch
        if (output.contains("already on")) {
            return true;
        }

        // Branch is up to date
        if (output.contains("up to date") || output.contains("up-to-date")) {
            return true;
        }

        return false;
    }

    @Override
    public BlockType getType() {
        return BlockType.GIT;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (Git)\n");
        md.append("- Commands (").append(args.size()).append("):\n");
        for (String arg : args) {
            md.append("  - `git ").append(arg).append("`\n");
        }
        if (workingDirectory != null) {
            md.append("- Working Directory: `").append(workingDirectory).append("`\n");
        }
        md.append("- Idempotent: ").append(idempotent).append("\n");
        md.append("- Timeout: ").append(timeoutSeconds).append(" seconds\n");
        return md.toString();
    }

    @Override
    public boolean validate() {
        if (args == null || args.isEmpty()) {
            logger.error("Git args cannot be empty");
            return false;
        }
        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) {
                logger.error("Git arg cannot be empty");
                return false;
            }
        }
        if (timeoutSeconds <= 0) {
            logger.error("Timeout must be positive");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        // Would need proper template parsing for comprehensive extraction
        return new String[0];
    }

    public static class Builder {
        private String name;
        private List<String> args = new ArrayList<>();
        private String workingDirectory;
        private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private boolean idempotent = false;
        private boolean captureOutput = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set a single Git command argument.
         */
        public Builder args(String args) {
            this.args = new ArrayList<>(Arrays.asList(args));
            return this;
        }

        /**
         * Set multiple Git command arguments (executed sequentially).
         */
        public Builder args(List<String> args) {
            this.args = new ArrayList<>(args);
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

        public Builder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        public Builder captureOutput(boolean captureOutput) {
            this.captureOutput = captureOutput;
            return this;
        }

        public GitCommandBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (args == null || args.isEmpty()) {
                throw new IllegalStateException("Args is required");
            }
            return new GitCommandBlock(this);
        }
    }
}
