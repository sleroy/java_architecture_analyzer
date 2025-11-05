package com.analyzer.migration.blocks.automated;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitCheckpointBlockTest {

    @TempDir
    Path tempDir;

    private MigrationContext context;

    @BeforeEach
    void setUp() throws IOException {
        // Initialize git repository
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempDir.toFile());
        pb.start();

        // Configure git user
        pb = new ProcessBuilder("git", "config", "user.name", "Test User");
        pb.directory(tempDir.toFile());
        pb.start();

        pb = new ProcessBuilder("git", "config", "user.email", "test@example.com");
        pb.directory(tempDir.toFile());
        pb.start();

        // Wait a bit for git init to complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create context
        context = new MigrationContext(tempDir);
    }

    @Test
    void testCheckpointWithChanges() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        // Create checkpoint
        GitCheckpointBlock block = GitCheckpointBlock.builder()
                .name("test-checkpoint")
                .commitMessage("Test checkpoint commit")
                .includeUntracked(true)
                .build();

        // Execute
        BlockResult result = block.execute(context);

        // Verify
        assertTrue(result.isSuccess(), "Checkpoint should succeed");
        assertEquals(true, result.getOutputVariables().get("checkpoint_created"));
    }

    @Test
    void testCheckpointWithNoChanges() {
        // Create checkpoint with no changes
        GitCheckpointBlock block = GitCheckpointBlock.builder()
                .name("test-checkpoint-no-changes")
                .commitMessage("Test checkpoint with no changes")
                .includeUntracked(false)
                .build();

        // Execute
        BlockResult result = block.execute(context);

        // Verify - should skip checkpoint
        assertTrue(result.isSuccess(), "Checkpoint should succeed even with no changes");
        assertEquals(false, result.getOutputVariables().get("checkpoint_created"));
    }

    @Test
    void testCheckpointWithForceCommit() {
        // Create checkpoint with force commit
        GitCheckpointBlock block = GitCheckpointBlock.builder()
                .name("test-checkpoint-force")
                .commitMessage("Forced empty commit")
                .forceCommit(true)
                .build();

        // Execute
        BlockResult result = block.execute(context);

        // Verify - should create empty commit
        assertTrue(result.isSuccess(), "Force commit should succeed");
        assertEquals(true, result.getOutputVariables().get("checkpoint_created"));
    }

    @Test
    void testCheckpointInNonGitDirectory(@TempDir Path nonGitDir) {
        MigrationContext nonGitContext = new MigrationContext(nonGitDir);

        GitCheckpointBlock block = GitCheckpointBlock.builder()
                .name("test-checkpoint-no-git")
                .commitMessage("Test in non-git directory")
                .build();

        // Execute
        BlockResult result = block.execute(nonGitContext);

        // Verify - should fail
        assertFalse(result.isSuccess(), "Should fail in non-git directory");
        assertTrue(result.getMessage().contains("Not a git repository"));
    }

    @Test
    void testVariableSubstitution() throws IOException {
        // Add variable to context
        context.setVariable("test_message", "Checkpoint from variable");

        // Create a test file
        Path testFile = tempDir.resolve("test2.txt");
        Files.writeString(testFile, "test content 2");

        // Create checkpoint with variable
        GitCheckpointBlock block = GitCheckpointBlock.builder()
                .name("test-checkpoint-vars")
                .commitMessage("${test_message}")
                .includeUntracked(true)
                .build();

        // Execute
        BlockResult result = block.execute(context);

        // Verify
        assertTrue(result.isSuccess(), "Checkpoint should succeed");
        assertEquals("Checkpoint from variable", result.getOutputVariables().get("commit_message"));
    }

    @Test
    void testValidation() {
        // Test missing name
        assertThrows(IllegalStateException.class, () -> {
            GitCheckpointBlock.builder()
                    .commitMessage("Test message")
                    .build();
        });

        // Test missing commit message
        assertThrows(IllegalStateException.class, () -> {
            GitCheckpointBlock.builder()
                    .name("test")
                    .build();
        });

        // Test valid configuration
        GitCheckpointBlock block = GitCheckpointBlock.builder()
                .name("test")
                .commitMessage("Test message")
                .build();

        assertTrue(block.validate(), "Valid block should pass validation");
    }
}
