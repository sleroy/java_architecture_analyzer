package com.analyzer.migration.blocks.automated;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies OpenRewrite recipes to filtered code files in batch operations.
 * Supports recipe configuration and integration with existing OpenRewrite
 * setup.
 */
public class OpenRewriteBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(OpenRewriteBlock.class);

    private final String name;
    private final String recipeName;
    private final List<String> filePaths;
    private final String filePattern;
    private final String baseDirectory;

    private OpenRewriteBlock(Builder builder) {
        this.name = builder.name;
        this.recipeName = builder.recipeName;
        this.filePaths = builder.filePaths != null ? new ArrayList<>(builder.filePaths) : new ArrayList<>();
        this.filePattern = builder.filePattern;
        this.baseDirectory = builder.baseDirectory;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Substitute variables in recipe name and paths
            String processedRecipe = context.substituteVariables(recipeName);
            String processedBaseDir = baseDirectory != null
                    ? context.substituteVariables(baseDirectory)
                    : context.getProjectRoot().toString();

            // Process file paths
            List<String> processedFiles = new ArrayList<>();
            if (!filePaths.isEmpty()) {
                processedFiles = filePaths.stream()
                        .map(context::substituteVariables)
                        .collect(Collectors.toList());
            } else if (filePattern != null) {
                // Find files matching pattern in base directory
                String processedPattern = context.substituteVariables(filePattern);
                processedFiles = findFilesByPattern(processedBaseDir, processedPattern);
            } else {
                return BlockResult.failure(
                        "No files specified",
                        "Either filePaths or filePattern must be provided");
            }

            if (processedFiles.isEmpty()) {
                return BlockResult.builder()
                        .success(true)
                        .message("No files matched the criteria")
                        .warning("No files found to process")
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            logger.info("Applying OpenRewrite recipe '{}' to {} files", processedRecipe, processedFiles.size());

            // Execute OpenRewrite recipe
            // NOTE: This is a placeholder for actual OpenRewrite integration
            // The actual implementation would use OpenRewrite's API to:
            // 1. Load the recipe by name
            // 2. Parse the source files
            // 3. Apply the recipe transformations
            // 4. Write the results back
            int filesProcessed = applyRecipe(processedRecipe, processedFiles, processedBaseDir);

            long executionTime = System.currentTimeMillis() - startTime;

            return BlockResult.builder()
                    .success(true)
                    .message("OpenRewrite recipe applied successfully")
                    .outputVariable("recipe_name", processedRecipe)
                    .outputVariable("files_processed", filesProcessed)
                    .outputVariable("file_list", processedFiles)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            return BlockResult.failure(
                    "OpenRewrite recipe execution failed",
                    e.getMessage());
        }
    }

    /**
     * Applies the OpenRewrite recipe to the specified files.
     * This is a placeholder implementation that would need to be integrated
     * with the actual OpenRewrite execution engine.
     */
    private int applyRecipe(String recipeName, List<String> files, String baseDir) {
        // TODO: Integrate with actual OpenRewrite execution
        // This would:
        // 1. Create ExecutionContext
        // 2. Load recipe from classpath or configuration
        // 3. Parse source files
        // 4. Run recipe
        // 5. Write results

        logger.warn("OpenRewrite integration not yet implemented - this is a placeholder");
        logger.info("Would apply recipe '{}' to {} files in {}", recipeName, files.size(), baseDir);

        // For now, just log what would happen
        for (String file : files) {
            logger.debug("Would process file: {}", file);
        }

        return files.size();
    }

    /**
     * Finds files matching the given pattern in the base directory.
     */
    private List<String> findFilesByPattern(String baseDir, String pattern) throws Exception {
        List<String> matchedFiles = new ArrayList<>();
        Path basePath = Paths.get(baseDir);

        if (!Files.exists(basePath)) {
            logger.warn("Base directory does not exist: {}", baseDir);
            return matchedFiles;
        }

        // Simple pattern matching - could be enhanced with glob patterns
        Files.walk(basePath)
                .filter(Files::isRegularFile)
                .filter(path -> matchesPattern(path, pattern))
                .forEach(path -> matchedFiles.add(path.toString()));

        return matchedFiles;
    }

    /**
     * Simple pattern matching - supports * wildcard and file extensions.
     */
    private boolean matchesPattern(Path path, String pattern) {
        String fileName = path.getFileName().toString();

        // Support patterns like "*.java", "Test*.java", etc.
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return fileName.matches(regex);
        }

        // Direct match
        return fileName.equals(pattern);
    }

    @Override
    public BlockType getType() {
        return BlockType.OPENREWRITE;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (OpenRewrite)\n");
        md.append("- Recipe: `").append(recipeName).append("`\n");

        if (!filePaths.isEmpty()) {
            md.append("- Files: ").append(filePaths.size()).append(" specified\n");
        }
        if (filePattern != null) {
            md.append("- Pattern: `").append(filePattern).append("`\n");
        }
        if (baseDirectory != null) {
            md.append("- Base Directory: `").append(baseDirectory).append("`\n");
        }

        return md.toString();
    }

    @Override
    public boolean validate() {
        if (recipeName == null || recipeName.trim().isEmpty()) {
            logger.error("Recipe name is required");
            return false;
        }

        if (filePaths.isEmpty() && filePattern == null) {
            logger.error("Either filePaths or filePattern must be provided");
            return false;
        }

        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        // Could extract variables from recipe name, but for now return empty
        return new String[0];
    }

    public static class Builder {
        private String name;
        private String recipeName;
        private List<String> filePaths;
        private String filePattern;
        private String baseDirectory;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder recipeName(String recipeName) {
            this.recipeName = recipeName;
            return this;
        }

        public Builder filePaths(List<String> filePaths) {
            this.filePaths = filePaths;
            return this;
        }

        public Builder addFilePath(String filePath) {
            if (this.filePaths == null) {
                this.filePaths = new ArrayList<>();
            }
            this.filePaths.add(filePath);
            return this;
        }

        public Builder filePattern(String filePattern) {
            this.filePattern = filePattern;
            return this;
        }

        public Builder baseDirectory(String baseDirectory) {
            this.baseDirectory = baseDirectory;
            return this;
        }

        public OpenRewriteBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (recipeName == null || recipeName.isEmpty()) {
                throw new IllegalStateException("Recipe name is required");
            }
            return new OpenRewriteBlock(this);
        }
    }
}
