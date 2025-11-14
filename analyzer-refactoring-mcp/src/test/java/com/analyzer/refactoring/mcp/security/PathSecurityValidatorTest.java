package com.analyzer.refactoring.mcp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PathSecurityValidator.
 */
class PathSecurityValidatorTest {

    private PathSecurityValidator validator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        validator = new PathSecurityValidator();
        validator.initialize(tempDir);
    }

    @Test
    void testInitialization() {
        assertTrue(validator.isInitialized());
        assertEquals(tempDir.toAbsolutePath().normalize(), validator.getProjectRoot());
    }

    @Test
    void testValidatePathWithinProject() throws IOException {
        // Create a test file within project
        Path testFile = tempDir.resolve("src/main/java/Test.java");
        Files.createDirectories(testFile.getParent());
        Files.createFile(testFile);

        // Should not throw exception
        assertDoesNotThrow(() -> validator.validatePath(testFile));
    }

    @Test
    void testValidateRelativePathWithinProject() {
        // Relative path within project should be allowed
        assertDoesNotThrow(() -> validator.validatePath("src/main/java/Test.java"));
    }

    @Test
    void testValidatePathOutsideProject() {
        // Path outside project should throw SecurityException
        Path outsidePath = Path.of("/etc/passwd");
        assertThrows(SecurityException.class, () -> validator.validatePath(outsidePath));
    }

    @Test
    void testValidatePathWithTraversal() {
        // Directory traversal attempt should throw SecurityException
        String traversalPath = "src/../../etc/passwd";
        assertThrows(SecurityException.class, () -> validator.validatePath(traversalPath));
    }

    @Test
    void testValidateProjectPath() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("Test.java");
        Files.createFile(testFile);

        // Should not throw when paths match
        assertDoesNotThrow(() -> validator.validateProjectPath(tempDir.toString(), "Test.java"));
    }

    @Test
    void testValidateProjectPathMismatch() {
        // Should throw when provided project path doesn't match configured root
        Path differentPath = Path.of("/different/path");
        assertThrows(SecurityException.class,
                () -> validator.validateProjectPath(differentPath.toString(), "Test.java"));
    }

    @Test
    void testResolveAndValidate() {
        // Should resolve relative path and return absolute path
        Path resolved = validator.resolveAndValidate("src/main/java");
        assertNotNull(resolved);
        assertTrue(resolved.isAbsolute());
        assertTrue(resolved.startsWith(tempDir));
    }

    @Test
    void testResolveAndValidateWithTraversal() {
        // Should throw on traversal attempt
        assertThrows(SecurityException.class, () -> validator.resolveAndValidate("../../../etc/passwd"));
    }

    @Test
    void testUninitializedValidator() {
        PathSecurityValidator uninitValidator = new PathSecurityValidator();
        assertFalse(uninitValidator.isInitialized());
        assertThrows(IllegalStateException.class, () -> uninitValidator.validatePath("test.txt"));
    }

    @Test
    void testNullProjectRoot() {
        PathSecurityValidator nullValidator = new PathSecurityValidator();
        assertThrows(IllegalArgumentException.class, () -> nullValidator.initialize(null));
    }

    @Test
    void testSymbolicLinkResolution() throws IOException {
        // Create a file and a symbolic link to it
        Path realFile = tempDir.resolve("real.txt");
        Files.createFile(realFile);
        Path symlink = tempDir.resolve("link.txt");

        try {
            Files.createSymbolicLink(symlink, realFile);

            // Validation should follow the symlink and verify it's within project
            assertDoesNotThrow(() -> validator.validatePath(symlink));
        } catch (UnsupportedOperationException | IOException e) {
            // Skip test if symlinks not supported on this system
            System.out.println("Skipping symlink test - not supported on this system");
        }
    }

    @Test
    void testNonExistentFilePath() {
        // Validator should still validate non-existent paths
        // (files may be created after validation)
        assertDoesNotThrow(() -> validator.validatePath("future/file/that/doesnt/exist/yet.java"));
    }

    @Test
    void testAbsolutePathWithinProject() throws IOException {
        Path absolutePath = tempDir.resolve("src/Test.java");
        Files.createDirectories(absolutePath.getParent());

        assertDoesNotThrow(() -> validator.validatePath(absolutePath.toString()));
    }
}
