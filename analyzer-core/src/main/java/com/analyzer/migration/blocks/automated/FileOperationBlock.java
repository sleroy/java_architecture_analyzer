package com.analyzer.migration.blocks.automated;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Performs file system operations with template content support.
 * Operations: CREATE, COPY, MOVE, DELETE for files and directories.
 */
public class FileOperationBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(FileOperationBlock.class);

    private final String name;
    private final FileOperation operation;
    private final String sourcePath;
    private final String targetPath;
    private final String content;
    private final boolean createDirectories;
    private final String filesVariable; // For CREATE_MULTIPLE - variable name containing files
    private final String basePath; // For CREATE_MULTIPLE - base directory path

    private FileOperationBlock(Builder builder) {
        this.name = builder.name;
        this.operation = builder.operation;
        this.sourcePath = builder.sourcePath;
        this.targetPath = builder.targetPath;
        this.content = builder.content;
        this.createDirectories = builder.createDirectories;
        this.filesVariable = builder.filesVariable;
        this.basePath = builder.basePath;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            switch (operation) {
                case CREATE:
                    return executeCreate(context, startTime);
                case CREATE_MULTIPLE:
                    return executeCreateMultiple(context, startTime);
                case APPEND:
                    return executeAppend(context, startTime);
                case COPY:
                    return executeCopy(context, startTime);
                case MOVE:
                    return executeMove(context, startTime);
                case DELETE:
                    return executeDelete(context, startTime);
                case REPLACE:
                    return executeReplace(context, startTime);
                default:
                    return BlockResult.failure("Unknown operation", "Operation: " + operation);
            }
        } catch (Exception e) {
            return BlockResult.failure(
                    "File operation failed: " + operation,
                    e.getMessage());
        }
    }

    private BlockResult executeCreate(MigrationContext context, long startTime) throws IOException {
        String processedPath = context.substituteVariables(targetPath);
        Path path = Paths.get(processedPath);

        // Create parent directories if needed
        if (createDirectories && path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        // Process content template if provided
        String processedContent = "";
        if (content != null) {
            processedContent = context.substituteVariables(content);
        }

        // Write file
        Files.writeString(path, processedContent, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Created file: {}", path);

        return BlockResult.builder()
                .success(true)
                .message("File created successfully")
                .outputVariable("created_path", path.toString())
                .outputVariable("size", Files.size(path))
                .executionTimeMs(executionTime)
                .build();
    }

    private BlockResult executeCreateMultiple(MigrationContext context, long startTime) {
        try {
            // Get the files variable from context
            Object filesObj = context.getVariable(filesVariable);
            if (filesObj == null) {
                return BlockResult.failure("Files variable not found",
                        "Variable '" + filesVariable + "' is not defined in context");
            }

            // Parse files structure - expect Map<String, String> (filename -> content)
            java.util.Map<String, String> files;
            if (filesObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> filesMap = (java.util.Map<String, String>) filesObj;
                files = filesMap;
            } else {
                return BlockResult.failure("Invalid files variable type",
                        "Expected Map<String, String>, got: " + filesObj.getClass().getName());
            }

            // Process base path
            String processedBasePath = context.substituteVariables(basePath);

            // Create each file using internal CREATE operations
            java.util.List<String> createdFiles = new java.util.ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            StringBuilder errors = new StringBuilder();

            for (java.util.Map.Entry<String, String> entry : files.entrySet()) {
                String fileName = entry.getKey();
                String fileContent = entry.getValue();

                try {
                    // Construct full file path
                    String filePath = processedBasePath + "/" + fileName;

                    // Reuse CREATE logic via internal block
                    FileOperationBlock createBlock = FileOperationBlock.builder()
                            .name(name + "-" + fileName)
                            .operation(FileOperation.CREATE)
                            .targetPath(filePath)
                            .content(fileContent)
                            .createDirectories(createDirectories)
                            .build();

                    BlockResult result = createBlock.execute(context);

                    if (result.isSuccess()) {
                        createdFiles.add(filePath);
                        successCount++;
                        logger.info("Created file {}/{}: {}", successCount, files.size(), filePath);
                    } else {
                        failureCount++;
                        errors.append(String.format("Failed to create %s: %s\n",
                                fileName, result.getMessage()));
                    }
                } catch (Exception e) {
                    failureCount++;
                    errors.append(String.format("Error creating %s: %s\n",
                            fileName, e.getMessage()));
                    logger.error("Error creating file: {}", fileName, e);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // Build result
            boolean overallSuccess = failureCount == 0;
            String message = String.format("Created %d files (%d successful, %d failed)",
                    files.size(), successCount, failureCount);

            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .success(overallSuccess)
                    .message(message)
                    .outputVariable("created_files", createdFiles)
                    .outputVariable("files_created_count", successCount)
                    .outputVariable("files_failed_count", failureCount)
                    .executionTimeMs(executionTime);

            if (failureCount > 0) {
                resultBuilder.warning("Some files failed to create:\n" + errors.toString());
            }

            return resultBuilder.build();

        } catch (Exception e) {
            return BlockResult.failure(
                    "CREATE_MULTIPLE operation failed",
                    e.getMessage());
        }
    }

    private BlockResult executeAppend(MigrationContext context, long startTime) throws IOException {
        String processedPath = context.substituteVariables(targetPath);
        Path path = Paths.get(processedPath);

        // Create parent directories if needed
        if (createDirectories && path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        // Process content template if provided
        String processedContent = "";
        if (content != null) {
            processedContent = context.substituteVariables(content);
        }

        // Append to file (create if doesn't exist)
        Files.writeString(path, processedContent, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Appended to file: {}", path);

        return BlockResult.builder()
                .success(true)
                .message("Content appended successfully")
                .outputVariable("target_path", path.toString())
                .outputVariable("size", Files.size(path))
                .executionTimeMs(executionTime)
                .build();
    }

    private BlockResult executeCopy(MigrationContext context, long startTime) throws IOException {
        String processedSource = context.substituteVariables(sourcePath);
        String processedTarget = context.substituteVariables(targetPath);

        Path source = Paths.get(processedSource);
        Path target = Paths.get(processedTarget);

        if (!Files.exists(source)) {
            return BlockResult.failure("Source does not exist", "Path: " + source);
        }

        // Create parent directories if needed
        if (createDirectories && target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        // Copy file or directory
        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Copied: {} -> {}", source, target);

        return BlockResult.builder()
                .success(true)
                .message("Copy completed successfully")
                .outputVariable("source_path", source.toString())
                .outputVariable("target_path", target.toString())
                .executionTimeMs(executionTime)
                .build();
    }

    private BlockResult executeMove(MigrationContext context, long startTime) throws IOException {
        String processedSource = context.substituteVariables(sourcePath);
        String processedTarget = context.substituteVariables(targetPath);

        Path source = Paths.get(processedSource);
        Path target = Paths.get(processedTarget);

        if (!Files.exists(source)) {
            return BlockResult.failure("Source does not exist", "Path: " + source);
        }

        // Create parent directories if needed
        if (createDirectories && target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        // Move file or directory
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Moved: {} -> {}", source, target);

        return BlockResult.builder()
                .success(true)
                .message("Move completed successfully")
                .outputVariable("source_path", source.toString())
                .outputVariable("target_path", target.toString())
                .executionTimeMs(executionTime)
                .build();
    }

    private BlockResult executeDelete(MigrationContext context, long startTime) throws IOException {
        String processedPath = context.substituteVariables(sourcePath);
        Path path = Paths.get(processedPath);

        if (!Files.exists(path)) {
            return BlockResult.builder()
                    .success(true)
                    .message("Path does not exist (already deleted)")
                    .warning("Path did not exist: " + path)
                    .outputVariable("deleted_path", path.toString())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // Delete file or directory recursively
        if (Files.isDirectory(path)) {
            deleteDirectory(path);
        } else {
            Files.delete(path);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Deleted: {}", path);

        return BlockResult.builder()
                .success(true)
                .message("Delete completed successfully")
                .outputVariable("deleted_path", path.toString())
                .executionTimeMs(executionTime)
                .build();
    }

    private BlockResult executeReplace(MigrationContext context, long startTime) throws IOException {
        String processedPath = context.substituteVariables(targetPath);
        Path path = Paths.get(processedPath);

        if (!Files.exists(path)) {
            return BlockResult.failure(
                    "Cannot replace content - file does not exist",
                    "Path: " + path);
        }

        if (Files.isDirectory(path)) {
            return BlockResult.failure(
                    "Cannot replace content - path is a directory",
                    "Path: " + path);
        }

        // Process content template if provided
        String processedContent = "";
        if (content != null) {
            processedContent = context.substituteVariables(content);
        }

        // Replace file content
        Files.writeString(path, processedContent, StandardOpenOption.TRUNCATE_EXISTING);

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Replaced content in file: {}", path);

        return BlockResult.builder()
                .success(true)
                .message("File content replaced successfully")
                .outputVariable("replaced_path", path.toString())
                .outputVariable("size", Files.size(path))
                .executionTimeMs(executionTime)
                .build();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public BlockType getType() {
        return BlockType.FILE_OPERATION;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (File Operation)\n");
        md.append("- Operation: ").append(operation).append("\n");

        switch (operation) {
            case CREATE:
            case APPEND:
                md.append("- Target: `").append(targetPath).append("`\n");
                if (content != null && content.length() <= 100) {
                    md.append("- Content: `").append(content).append("`\n");
                } else if (content != null) {
                    md.append("- Content: ").append(content.length()).append(" characters\n");
                }
                break;
            case COPY:
            case MOVE:
                md.append("- Source: `").append(sourcePath).append("`\n");
                md.append("- Target: `").append(targetPath).append("`\n");
                break;
            case DELETE:
                md.append("- Path: `").append(sourcePath).append("`\n");
                break;
            case REPLACE:
                md.append("- Target: `").append(targetPath).append("`\n");
                if (content != null && content.length() <= 100) {
                    md.append("- Content: `").append(content).append("`\n");
                } else if (content != null) {
                    md.append("- Content: ").append(content.length()).append(" characters\n");
                }
                break;
        }

        return md.toString();
    }

    @Override
    public boolean validate() {
        if (operation == null) {
            logger.error("Operation is required");
            return false;
        }

        switch (operation) {
            case CREATE:
            case APPEND:
                if (targetPath == null || targetPath.isEmpty()) {
                    logger.error("Target path is required for {} operation", operation);
                    return false;
                }
                break;
            case CREATE_MULTIPLE:
                if (filesVariable == null || filesVariable.isEmpty()) {
                    logger.error("Files variable is required for CREATE_MULTIPLE operation");
                    return false;
                }
                if (basePath == null || basePath.isEmpty()) {
                    logger.error("Base path is required for CREATE_MULTIPLE operation");
                    return false;
                }
                break;
            case COPY:
            case MOVE:
                if (sourcePath == null || sourcePath.isEmpty()) {
                    logger.error("Source path is required for {} operation", operation);
                    return false;
                }
                if (targetPath == null || targetPath.isEmpty()) {
                    logger.error("Target path is required for {} operation", operation);
                    return false;
                }
                break;
            case DELETE:
                if (sourcePath == null || sourcePath.isEmpty()) {
                    logger.error("Source path is required for DELETE operation");
                    return false;
                }
                break;
            case REPLACE:
                if (targetPath == null || targetPath.isEmpty()) {
                    logger.error("Target path is required for REPLACE operation");
                    return false;
                }
                break;
        }

        return true;
    }

    public enum FileOperation {
        CREATE,
        CREATE_MULTIPLE,
        APPEND,
        COPY,
        MOVE,
        DELETE,
        REPLACE
    }

    public static class Builder {
        private String name;
        private FileOperation operation;
        private String sourcePath;
        private String targetPath;
        private String content;
        private boolean createDirectories = true;
        private String filesVariable; // For CREATE_MULTIPLE
        private String basePath; // For CREATE_MULTIPLE

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder operation(FileOperation operation) {
            this.operation = operation;
            return this;
        }

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder targetPath(String targetPath) {
            this.targetPath = targetPath;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder createDirectories(boolean createDirectories) {
            this.createDirectories = createDirectories;
            return this;
        }

        public Builder filesVariable(String filesVariable) {
            this.filesVariable = filesVariable;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public FileOperationBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (operation == null) {
                throw new IllegalStateException("Operation is required");
            }
            return new FileOperationBlock(this);
        }
    }
}
