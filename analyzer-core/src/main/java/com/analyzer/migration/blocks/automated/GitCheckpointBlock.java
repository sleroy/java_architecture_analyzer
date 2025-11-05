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
 * Creates a git checkpoint by staging and committing all changes.
 */
public class GitCheckpointBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(GitCheckpointBlock.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final String name;
    private final String commitMessage;
    private final String workingDirectory;
    private final boolean includeUntracked;
    private final boolean forceCommit;
    private final long timeoutSeconds;

    private GitCheckpointBlock(Builder builder) {
        this.name = builder.name;
        this.commitMessage = builder.commitMessage;
        this.workingDirectory = builder.workingDirectory;
        this.includeUntracked = builder.includeUntracked;
        this.forceCommit = builder.forceCommit;
        this.timeoutSeconds = builder.timeoutSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String processedWorkDir = workingDirectory != null
                    ? context.substituteVariables(workingDirectory)
                    : context.getProjectRoot().toString();

            String processedCommitMessage = context.substituteVariables(commitMessage);

            logger.info("Creating git checkpoint in directory: {}", processedWorkDir);

            // Stage changes
            String addCommand = includeUntracked ? "git add -A" : "git add -u";
            BlockResult stageResult = executeGitCommand(addCommand, processedWorkDir);
            if (!stageResult.isSuccess()) {
                return stageResult;
            }

            // Check if there are changes
            BlockResult statusResult = executeGitCommand("git status --porcelain", processedWorkDir);
            boolean hasChanges = statusResult.getOutputVariables().get("output") != null &&
                    !statusResult.getOutputVariables().get("output").toString().trim().isEmpty();

            if (!hasChanges && !forceCommit) {
                logger.info("No changes to commit, skipping checkpoint");
                return BlockResult.builder()
                        .success(true)
                        .message("Git checkpoint skipped - no changes")
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .outputVariable("checkpoint_created", false)
                        .build();
            }

            // Commit
            String commitCommand = "git commit -m \"" + processedCommitMessage.replace("\"", "\\\"") + "\"";
            if (forceCommit) {
                commitCommand += " --allow-empty";
            }

            BlockResult commitResult = executeGitCommand(commitCommand, processedWorkDir);
            if (!commitResult.isSuccess()) {
                String errorDetails = commitResult.getErrorDetails();
                if (errorDetails != null && errorDetails.toLowerCase().contains("nothing to commit")) {
                    return BlockResult.builder()
                            .success(true)
                            .message("Git checkpoint - working directory already clean")
                            .executionTimeMs(System.currentTimeMillis() - startTime)
                            .outputVariable("checkpoint_created", false)
                            .build();
                }
                return commitResult;
            }

            return BlockResult.builder()
                    .success(true)
                    .message("Git checkpoint created successfully")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .outputVariable("checkpoint_created", true)
                    .outputVariable("commit_message", processedCommitMessage)
                    .build();

        } catch (Exception e) {
            return BlockResult.failure("Error creating git checkpoint", e.getMessage());
        }
    }

    private BlockResult executeGitCommand(String command, String workingDir) {
        try {
            ProcessBuilder pb;
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }

            pb.directory(new File(workingDir));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.info("  | {}", line);
                }
            }

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return BlockResult.failure("Timeout executing: " + command, "");
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString();

            if (exitCode == 0) {
                return BlockResult.builder()
                        .success(true)
                        .message("Command executed successfully")
                        .outputVariable("output", outputStr)
                        .build();
            } else {
                return BlockResult.builder()
                        .success(false)
                        .message("Command failed with exit code " + exitCode)
                        .errorDetails(outputStr)
                        .outputVariable("output", outputStr)
                        .build();
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return BlockResult.failure("Failed to execute: " + command, e.getMessage());
        }
    }

    @Override
    public BlockType getType() {
        return BlockType.CHECKPOINT;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        return "**" + name + "** (Git Checkpoint)\n" +
                "- Commit Message: " + commitMessage + "\n" +
                "- Include Untracked: " + includeUntracked + "\n" +
                "- Force Commit: " + forceCommit;
    }

    @Override
    public boolean validate() {
        if (name == null || name.trim().isEmpty()) {
            logger.error("Name cannot be empty");
            return false;
        }
        if (commitMessage == null || commitMessage.trim().isEmpty()) {
            logger.error("Commit message cannot be empty");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        return new String[0];
    }

    public static class Builder {
        private String name;
        private String commitMessage;
        private String workingDirectory;
        private boolean includeUntracked = true;
        private boolean forceCommit = false;
        private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder commitMessage(String commitMessage) {
            this.commitMessage = commitMessage;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder includeUntracked(boolean includeUntracked) {
            this.includeUntracked = includeUntracked;
            return this;
        }

        public Builder forceCommit(boolean forceCommit) {
            this.forceCommit = forceCommit;
            return this;
        }

        public Builder timeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public GitCheckpointBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (commitMessage == null || commitMessage.isEmpty()) {
                throw new IllegalStateException("Commit message is required");
            }
            return new GitCheckpointBlock(this);
        }
    }
}
