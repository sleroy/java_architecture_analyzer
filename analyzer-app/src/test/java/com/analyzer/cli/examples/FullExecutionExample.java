package com.analyzer.cli.examples;

import com.analyzer.cli.AnalyzerCLI;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example demonstrating how to execute a full migration plan with step-by-step
 * mode.
 * <p>
 * This will execute the migration tasks with manual confirmation before each
 * block.
 * Step-by-step mode allows you to review and approve each operation before it
 * executes.
 * <p>
 * NOTE: Make sure you have a test EJB project ready before running this.
 */
public class FullExecutionExample {

    public static void main(final String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("Full Migration Execution Example (with Step-by-Step Mode)");
        System.out.println("=".repeat(80));
        System.out.println();

        // Create a test project directory if it doesn't exist
        final Path testProjectPath = Paths.get("demo-ejb2-project");
        if (!Files.exists(testProjectPath)) {
            System.out.println("Creating test project directory: " + testProjectPath);
            Files.createDirectories(testProjectPath);
            System.out.println(
                    "NOTE: This is an empty test directory. Replace with actual EJB project for real migration.");
            System.out.println();
        }


        // STEP 1: Execute inventory command first to analyze the project
        System.out.println("=".repeat(80));
        System.out.println("STEP 1: Running project inventory analysis");
        System.out.println("=".repeat(80));
        System.out.println();

        final String[] inventoryArgs = {
                "inventory",
                "--project", testProjectPath.toString(),
                "--max-passes", "5"
                , "--java_version", "17", "--packages", "br"
        };

        System.out.println("Executing inventory command with:");
        for (final String arg : inventoryArgs) {
            if (arg.startsWith("--")) {
                System.out.println("  " + arg);
            }
        }
        System.out.println();

        final int inventoryExitCode = 0; //new CommandLine(new AnalyzerCLI()).execute(inventoryArgs);

        System.out.println();
        System.out.println("Inventory analysis completed with exit code: " + inventoryExitCode);
        System.out.println();

        if (0 != inventoryExitCode) {
            System.err.println("Error: Inventory analysis failed. Aborting migration.");
            System.exit(inventoryExitCode);
        }

        // STEP 2: Execute migration with the plan
        System.out.println("=".repeat(80));
        System.out.println("STEP 2: Applying migration plan");
        System.out.println("=".repeat(80));
        System.out.println();

        // Build command arguments - using new modular migration plan structure
        // The plan file should be loaded from the file system to support includes
        final String planPath = "migrations/ejb2spring/jboss-to-springboot.yaml";
        final String[] migrationArgs = {
                "apply",
                "--project", testProjectPath.toString(),
                "--plan", planPath,
                // "--step-by-step", // Enable step-by-step mode for manual control
                // "--dry-run", // Use dry-run to safely demonstrate step-by-step mode
                // "--resume", // Resume from last checkpoint (if migration was interrupted)
                "--verbose",
                // Variables are defined in migrations/ejb2spring/common/variables.yaml
                // Override them here if needed for your specific project
                "-Dspring_boot_version=3.5.7",
                // Note: java_version is auto-detected from system, don't override unless needed
                // "-Djava_version=21", // Uncomment to override detected version
                "-Dbase_package=com.example.app",
                "-Dtarget_database=h2"
        };

        System.out.println("Executing migration with the following configuration:");
        for (final String arg : migrationArgs) {
            if (arg.startsWith("-D") || arg.startsWith("--")) {
                System.out.println("  " + arg);
            }
        }
        System.out.println();
        System.out.println("Migration Plan: " + planPath);
        System.out.println("  - Modular plan structure with includes");
        System.out.println("  - Common variables in migrations/ejb2spring/common/variables.yaml");
        System.out.println("  - Phases included from migrations/ejb2spring/phases/*.yaml");
        System.out.println();
        System.out.println("Step-by-Step Mode: ENABLED");
        System.out.println("  - You will be prompted before each block execution");
        System.out.println("  - Press Enter to proceed, or Ctrl+C to abort");
        System.out.println();
        System.out.println("Dry-Run Mode: ENABLED");
        System.out.println("  - Simulating execution without making actual changes");
        System.out.println("  - Step-by-step prompts will be skipped in dry-run mode");
        System.out.println();
        System.out.println("Note: Remove --dry-run flag to execute for real with step-by-step prompts");
        System.out.println();

        // Execute the migration command
        final int migrationExitCode = new CommandLine(new AnalyzerCLI()).execute(migrationArgs);

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("Migration completed with exit code: " + migrationExitCode);
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("To run this example:");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"com.analyzer.cli.examples.FullExecutionExample\"");
        System.out.println();
        System.out.println("Execution Summary:");
        System.out.println("  Step 1 - Inventory analysis exit code: " + inventoryExitCode);
        System.out.println("  Step 2 - Migration execution exit code: " + migrationExitCode);
        System.out.println();
        System.out.println("Migration Plan Used:");
        System.out.println("  - Main: migrations/ejb2spring/jboss-to-springboot.yaml");
        System.out.println("  - Phases: migrations/ejb2spring/phases/*.yaml (included)");
        System.out.println("  - Common: migrations/ejb2spring/common/*.yaml (included)");
        System.out.println();
        System.out.println("Step-by-Step Mode Features:");
        System.out.println("  - Manual confirmation before each block");
        System.out.println("  - Review operations before execution");
        System.out.println("  - Abort at any time with Ctrl+C");
        System.out.println("  - Disabled in dry-run mode (use without --dry-run for interactive execution)");
        System.out.println();
        System.out.println("To use with real execution (no dry-run):");
        System.out.println("  Step 1: analyzer inventory --project /path/to/project");
        System.out.println("  Step 2: analyzer apply --project /path/to/project \\");
        System.out.println("                         --plan migrations/ejb2spring/jboss-to-springboot.yaml \\");
        System.out.println("                         --step-by-step");
        System.out.println();
        System.out.println("To resume after crash or interruption:");
        System.out.println("  analyzer apply --project /path/to/project \\");
        System.out.println("                 --plan migrations/ejb2spring/jboss-to-springboot.yaml \\");
        System.out.println("                 --resume");
        System.out.println();
        System.out.println("Resume Features:");
        System.out.println("  - Skips completed phases/tasks automatically");
        System.out.println("  - Restores ALL variables from saved state");
        System.out.println("  - Includes runtime-generated variables (e.g., detected_java_version)");
        System.out.println("  - CLI -D flags can still override saved variables if needed");
        System.out.println("  - State saved to: <project>/.analysis/migration-state.json");
        System.out.println("=".repeat(80));
    }
}
