package com.analyzer.migration.blocks.validation;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Pauses execution for human review and validation.
 * Supports various validation types including file existence checks,
 * git status verification, and manual confirmation.
 */
public class InteractiveValidationBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(InteractiveValidationBlock.class);

    private final String name;
    private final ValidationType validationType;
    private final String message;
    private final List<String> validationParams;
    private final boolean required;

    private InteractiveValidationBlock(Builder builder) {
        this.name = builder.name;
        this.validationType = builder.validationType;
        this.message = builder.message;
        this.validationParams = builder.validationParams;
        this.required = builder.required;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Display validation message
            displayValidationPrompt(context);

            // Perform automatic checks based on validation type
            boolean autoCheckPassed = performAutomaticValidation(context);

            // Request manual confirmation
            boolean userConfirmed = requestUserConfirmation();

            long executionTime = System.currentTimeMillis() - startTime;

            if (!autoCheckPassed || !userConfirmed) {
                if (required) {
                    return BlockResult.builder()
                            .success(false)
                            .message("Validation failed or not confirmed")
                            .outputVariable("validation_type", validationType.toString())
                            .outputVariable("auto_check_passed", autoCheckPassed)
                            .outputVariable("user_confirmed", userConfirmed)
                            .errorDetails("Validation is required to continue")
                            .executionTimeMs(executionTime)
                            .build();
                } else {
                    return BlockResult.builder()
                            .success(true)
                            .message("Validation skipped (not required)")
                            .warning("Validation was not confirmed but continuing")
                            .outputVariable("validation_type", validationType.toString())
                            .outputVariable("auto_check_passed", autoCheckPassed)
                            .outputVariable("user_confirmed", userConfirmed)
                            .executionTimeMs(executionTime)
                            .build();
                }
            }

            return BlockResult.builder()
                    .success(true)
                    .message("Validation passed")
                    .outputVariable("validation_type", validationType.toString())
                    .outputVariable("auto_check_passed", autoCheckPassed)
                    .outputVariable("user_confirmed", userConfirmed)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            return BlockResult.failure(
                    "Validation failed with error",
                    e.getMessage());
        }
    }

    private void displayValidationPrompt(MigrationContext context) {
        String processedMessage = context.substituteVariables(message);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("VALIDATION CHECKPOINT: " + name);
        System.out.println("Type: " + validationType);
        System.out.println("=".repeat(80));
        System.out.println(processedMessage);
        System.out.println("=".repeat(80));
    }

    private boolean performAutomaticValidation(MigrationContext context) {
        switch (validationType) {
            case FILE_EXISTS:
                return validateFileExists(context);
            case GIT_BRANCH_CLEAN:
                return validateGitBranchClean(context);
            case MANUAL_CONFIRM:
                return true; // No automatic check, rely on manual confirmation
            default:
                logger.warn("Unknown validation type: {}", validationType);
                return true;
        }
    }

    private boolean validateFileExists(MigrationContext context) {
        if (validationParams == null || validationParams.isEmpty()) {
            logger.warn("No file paths specified for FILE_EXISTS validation");
            return true;
        }

        boolean allExist = true;
        for (String param : validationParams) {
            String processedPath = context.substituteVariables(param);
            boolean exists = Files.exists(Paths.get(processedPath));
            System.out.println("  - " + processedPath + ": " + (exists ? "EXISTS" : "NOT FOUND"));
            if (!exists) {
                allExist = false;
            }
        }

        return allExist;
    }

    private boolean validateGitBranchClean(MigrationContext context) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "status", "--porcelain");
            pb.directory(context.getProjectRoot().toFile());
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();

            boolean isClean = (line == null || line.trim().isEmpty());
            System.out.println("  Git status: " + (isClean ? "CLEAN" : "HAS UNCOMMITTED CHANGES"));

            return isClean;

        } catch (Exception e) {
            logger.error("Failed to check git status", e);
            System.out.println("  Git status check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean requestUserConfirmation() {
        System.out.println("\nConfirm to continue (y/n): ");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String response = reader.readLine();
            return response != null && response.trim().equalsIgnoreCase("y");
        } catch (Exception e) {
            logger.error("Failed to read user input", e);
            return false;
        }
    }

    @Override
    public BlockType getType() {
        return BlockType.INTERACTIVE_VALIDATION;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (Validation Checkpoint)\n");
        md.append("- Type: ").append(validationType).append("\n");
        md.append("- Required: ").append(required ? "Yes" : "No").append("\n");
        if (message != null && !message.isEmpty()) {
            if (message.length() <= 100) {
                md.append("- Message: ").append(message).append("\n");
            } else {
                md.append("- Message: ").append(message.length()).append(" characters\n");
            }
        }
        if (validationParams != null && !validationParams.isEmpty()) {
            md.append("- Parameters: ").append(String.join(", ", validationParams)).append("\n");
        }
        return md.toString();
    }

    @Override
    public boolean validate() {
        if (validationType == null) {
            logger.error("Validation type is required");
            return false;
        }

        if (validationType == ValidationType.FILE_EXISTS) {
            if (validationParams == null || validationParams.isEmpty()) {
                logger.error("File paths are required for FILE_EXISTS validation");
                return false;
            }
        }

        return true;
    }

    public enum ValidationType {
        /**
         * Check if specified files exist
         */
        FILE_EXISTS,

        /**
         * Check if git branch has no uncommitted changes
         */
        GIT_BRANCH_CLEAN,

        /**
         * Manual confirmation only, no automatic checks
         */
        MANUAL_CONFIRM
    }

    public static class Builder {
        private String name;
        private ValidationType validationType;
        private String message;
        private List<String> validationParams;
        private boolean required = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder validationType(ValidationType validationType) {
            this.validationType = validationType;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder validationParams(List<String> validationParams) {
            this.validationParams = validationParams;
            return this;
        }

        public Builder validationParams(String... params) {
            this.validationParams = Arrays.asList(params);
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public InteractiveValidationBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (validationType == null) {
                throw new IllegalStateException("Validation type is required");
            }
            return new InteractiveValidationBlock(this);
        }
    }
}
