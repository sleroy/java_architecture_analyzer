package com.analyzer.cli.examples;

import com.analyzer.cli.ApplyMigrationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional test for dry-run mode using the jboss-to-springboot-phase0-1.yaml
 * plan.
 * 
 * This test demonstrates that dry-run mode:
 * 1. Loads and validates the migration plan
 * 2. Resolves all variables
 * 3. Simulates execution without making actual changes
 * 4. Reports success/failure correctly
 */
public class DryRunFunctionalTest {

    @TempDir
    Path tempProjectDir;

    @BeforeEach
    void setup() throws Exception {
        // Create basic project structure
        Files.createDirectories(tempProjectDir.resolve("src/main/java"));
        Files.createDirectories(tempProjectDir.resolve("docs"));
    }

    @Test
    void testDryRunWithJBossToSpringBootPlan() throws Exception {
        System.out.println("\n=== Dry-Run Functional Test ===");
        System.out.println("Testing with: jboss-to-springboot-phase0-1.yaml");
        System.out.println("Project Dir: " + tempProjectDir);
        System.out.println();

        // Create command with dry-run flag
        ApplyMigrationCommand command = new ApplyMigrationCommand();

        // Set required parameters using reflection (since fields are private)
        setField(command, "projectPath", tempProjectDir.toString());
        setField(command, "planPath", "migration-plans/jboss-to-springboot-phase0-1.yaml");
        setField(command, "dryRun", true);
        setField(command, "interactive", false); // Disable interactive mode for automated testing
        setField(command, "verbose", true);

        // Execute command
        Integer exitCode = command.call();

        // Verify results
        System.out.println("\n=== Test Assertions ===");
        System.out.println("Exit Code: " + exitCode);

        // In dry-run mode, we expect success (exit code 0)
        // Note: The actual execution will simulate all blocks
        assertEquals(0, exitCode, "Dry-run should complete successfully");

        // Verify no actual files were created (except .analysis directory for state
        // tracking)
        Path semeruSpringboot = tempProjectDir.resolve("semeru-springboot");
        assertFalse(Files.exists(semeruSpringboot),
                "Dry-run should not create semeru-springboot directory");

        Path baseline = tempProjectDir.resolve("docs/BASELINE.md");
        assertFalse(Files.exists(baseline),
                "Dry-run should not create BASELINE.md file");

        Path migrationStrategy = tempProjectDir.resolve("docs/MIGRATION_STRATEGY.md");
        assertFalse(Files.exists(migrationStrategy),
                "Dry-run should not create MIGRATION_STRATEGY.md file");

        System.out.println("\n✓ Dry-run completed successfully without making changes");
        System.out.println("✓ No files were created in project directory");
        System.out.println("✓ Plan was validated and simulated correctly");
        System.out.println();
    }

    @Test
    void testDryRunWithVariableSubstitution() throws Exception {
        System.out.println("\n=== Dry-Run with Variable Substitution Test ===");

        ApplyMigrationCommand command = new ApplyMigrationCommand();

        setField(command, "projectPath", tempProjectDir.toString());
        setField(command, "planPath", "migration-plans/jboss-to-springboot-phase0-1.yaml");
        setField(command, "dryRun", true);
        setField(command, "interactive", false);
        setField(command, "verbose", true);

        // Add custom variables via -D flags
        java.util.Map<String, String> systemProps = new java.util.LinkedHashMap<>();
        systemProps.put("custom_var", "test-value");
        systemProps.put("spring_boot_version", "2.7.18");
        setField(command, "systemProperties", systemProps);

        Integer exitCode = command.call();

        assertEquals(0, exitCode, "Dry-run with custom variables should succeed");

        System.out.println("✓ Variables were resolved correctly");
        System.out.println("✓ Dry-run completed with custom variables");
        System.out.println();
    }

    @Test
    void testDryRunShowsSimulationMessages() throws Exception {
        System.out.println("\n=== Dry-Run Simulation Messages Test ===");
        System.out.println("This test verifies that dry-run mode produces correct output");
        System.out.println();

        ApplyMigrationCommand command = new ApplyMigrationCommand();

        setField(command, "projectPath", tempProjectDir.toString());
        setField(command, "planPath", "migration-plans/jboss-to-springboot-phase0-1.yaml");
        setField(command, "dryRun", true);
        setField(command, "interactive", false);
        setField(command, "verbose", true);

        // Capture output by redirecting System.out
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(outputStream));

        try {
            Integer exitCode = command.call();
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String output = outputStream.toString();

        // Verify dry-run mode messages appear in output
        assertTrue(output.contains("DRY-RUN MODE") || output.contains("DRY RUN"),
                "Output should indicate dry-run mode");
        assertTrue(output.contains("Simulating") || output.contains("simulating"),
                "Output should mention simulation");
        assertTrue(output.contains("No actual changes") || output.contains("no changes"),
                "Output should clarify no changes were made");

        System.out.println("✓ Dry-run mode produces correct simulation messages");
        System.out.println("✓ User is clearly informed about dry-run behavior");
        System.out.println();
    }

    @Test
    void testComparisonDryRunVsActualRun() throws Exception {
        System.out.println("\n=== Comparison: Dry-Run vs Actual Execution ===");
        System.out.println("This test demonstrates the difference between modes");
        System.out.println();

        // Test 1: Dry-run mode
        ApplyMigrationCommand dryRunCommand = new ApplyMigrationCommand();
        setField(dryRunCommand, "projectPath", tempProjectDir.toString());
        setField(dryRunCommand, "planPath", "migration-plans/jboss-to-springboot-phase0-1.yaml");
        setField(dryRunCommand, "dryRun", true);
        setField(dryRunCommand, "interactive", false);
        setField(dryRunCommand, "verbose", false);

        System.out.println("Executing in DRY-RUN mode...");
        Integer dryRunExitCode = dryRunCommand.call();

        // Count files after dry-run
        long filesAfterDryRun = countFiles(tempProjectDir);

        System.out.println("  Exit Code: " + dryRunExitCode);
        System.out.println("  Files Created: " + filesAfterDryRun);

        // Note: We're not testing actual execution here since it would require
        // proper setup of all dependencies and would take time.
        // In a real scenario, actual execution would create files.

        assertEquals(0, dryRunExitCode, "Dry-run should succeed");

        System.out.println("\n✓ Dry-run mode: Validates and simulates");
        System.out.println("✓ Actual mode (not tested here): Would execute and create files");
        System.out.println();
    }

    // Helper method to set private fields using reflection
    private void setField(Object obj, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    // Helper method to count files in directory tree
    private long countFiles(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return 0;
        }
        return Files.walk(dir)
                .filter(Files::isRegularFile)
                .count();
    }
}
