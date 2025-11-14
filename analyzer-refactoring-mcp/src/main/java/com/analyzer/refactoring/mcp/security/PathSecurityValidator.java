package com.analyzer.refactoring.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Security validator for file path operations.
 * Ensures that all file operations are restricted to the configured project
 * root directory.
 * 
 * This prevents directory traversal attacks and unauthorized access to files
 * outside
 * the project scope. All paths are normalized and resolved to absolute paths
 * before
 * validation.
 */
@Component
public class PathSecurityValidator {

    private static final Logger logger = LoggerFactory.getLogger(PathSecurityValidator.class);

    private Path projectRoot;
    private boolean initialized = false;

    /**
     * Initialize the validator with the project root path.
     * Must be called before any validation operations.
     * 
     * @param projectRoot The absolute path to the project root directory
     * @throws IllegalArgumentException if projectRoot is null
     */
    public void initialize(Path projectRoot) {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root path cannot be null");
        }

        try {
            // Normalize and convert to absolute path
            this.projectRoot = projectRoot.toRealPath().normalize();
            this.initialized = true;
            logger.info("PathSecurityValidator initialized with project root: {}", this.projectRoot);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid project root path: " + projectRoot, e);
        }
    }

    /**
     * Validates that the given path is within the project root directory.
     * 
     * @param path The path to validate (can be relative or absolute)
     * @throws SecurityException     if the path is outside the project root
     * @throws IllegalStateException if the validator has not been initialized
     */
    public void validatePath(String path) throws SecurityException {
        validatePath(Paths.get(path));
    }

    /**
     * Validates that the given path is within the project root directory.
     * 
     * @param path The path to validate (can be relative or absolute)
     * @throws SecurityException     if the path is outside the project root
     * @throws IllegalStateException if the validator has not been initialized
     */
    public void validatePath(Path path) throws SecurityException {
        if (!initialized) {
            throw new IllegalStateException("PathSecurityValidator must be initialized before use");
        }

        try {
            // Resolve relative paths against project root, then normalize
            Path absolutePath;
            if (path.isAbsolute()) {
                absolutePath = path.normalize();
            } else {
                absolutePath = projectRoot.resolve(path).normalize();
            }

            // Convert to real path to resolve symbolic links
            // Note: This will fail if the file doesn't exist yet, so we handle that case
            Path realPath;
            try {
                realPath = absolutePath.toRealPath();
            } catch (IOException e) {
                // File doesn't exist yet - validate the normalized absolute path
                realPath = absolutePath;
            }

            // Check if the resolved path starts with project root
            if (!realPath.startsWith(projectRoot)) {
                String errorMsg = String.format(
                        "Access denied: Path '%s' is outside project root '%s'",
                        realPath, projectRoot);
                logger.error("Security violation: {}", errorMsg);
                throw new SecurityException(errorMsg);
            }

            logger.debug("Path validation successful: {} -> {}", path, realPath);

        } catch (SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format(
                    "Path validation failed for '%s': %s",
                    path, e.getMessage());
            logger.error("Security validation error: {}", errorMsg);
            throw new SecurityException(errorMsg, e);
        }
    }

    /**
     * Validates that a combined project path and relative file path is safe.
     * This is a convenience method for the common pattern used in tools.
     * 
     * @param projectPath  The project root path (should match configured root)
     * @param relativePath The relative path within the project
     * @throws SecurityException if the combined path is outside the project root
     */
    public void validateProjectPath(String projectPath, String relativePath) throws SecurityException {
        // First validate that projectPath matches our configured root
        Path providedRoot = Paths.get(projectPath).normalize();

        try {
            Path realProvidedRoot = providedRoot.toRealPath();
            if (!realProvidedRoot.equals(projectRoot)) {
                String errorMsg = String.format(
                        "Project path mismatch: provided '%s' does not match configured '%s'",
                        realProvidedRoot, projectRoot);
                logger.error("Security violation: {}", errorMsg);
                throw new SecurityException(errorMsg);
            }
        } catch (IOException e) {
            throw new SecurityException("Invalid project path: " + projectPath, e);
        }

        // Then validate the combined path
        Path combinedPath = Paths.get(projectPath, relativePath);
        validatePath(combinedPath);
    }

    /**
     * Returns the configured project root path.
     * 
     * @return The project root path, or null if not initialized
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * Checks if the validator has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Resolves a relative path against the project root and validates it.
     * 
     * @param relativePath The relative path to resolve
     * @return The absolute, validated path
     * @throws SecurityException if the path is outside the project root
     */
    public Path resolveAndValidate(String relativePath) throws SecurityException {
        Path resolved = projectRoot.resolve(relativePath).normalize();
        validatePath(resolved);
        return resolved;
    }
}
