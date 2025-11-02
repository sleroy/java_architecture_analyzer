package com.analyzer.cli.examples;

import com.analyzer.cli.ApplyMigrationCommand;

import java.lang.reflect.Field;

/**
 * Example demonstrating partial execution modes:
 * - Task-specific execution (--task)
 * - Phase-specific execution (--phase)
 * - Resume from checkpoint (--resume)
 */
public class PartialExecutionExample {

    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("Partial Execution Modes Example");
        System.out.println("==============================================");
        System.out.println();

        // Example 1: Execute single task
        executeSingleTask();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 2: Execute single phase
        executeSinglePhase();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 3: Resume from checkpoint
        resumeFromCheckpoint();
    }

    private static void executeSingleTask() throws Exception {
        System.out.println("EXAMPLE 1: Execute Single Task");
        System.out.println("-".repeat(60));
        System.out.println();
        System.out.println("This example executes only TASK-000 from the migration plan.");
        System.out.println("Use case: Testing or debugging a specific task without");
        System.out.println("          running the entire plan.");
        System.out.println();

        ApplyMigrationCommand command = new ApplyMigrationCommand();

        // Set up command parameters
        setField(command, "projectPath", System.getProperty("user.dir"));
        setField(command, "planPath", "migration-plans/jboss-to-springboot-phase0-1.yaml");
        setField(command, "taskId", "TASK-000");
        setField(command, "dryRun", true);
        setField(command, "interactive", false);
        setField(command, "verbose", true);

        System.out.println("Command Configuration:");
        System.out.println("  Project:    " + System.getProperty("user.dir"));
        System.out.println("  Plan:       migration-plans/jboss-to-springboot-phase0-1.yaml");
        System.out.println("  Mode:       Single Task (TASK-000)");
        System.out.println("  Dry Run:    true");
        System.out.println();

        System.out.println("Executing...");
        System.out.println();

        Integer exitCode = command.call();

        System.out.println();
        System.out.println("Result: " + (exitCode == 0 ? "SUCCESS" : "FAILED"));
        System.out.println("Exit Code: " + exitCode);
    }

    private static void executeSinglePhase() throws Exception {
        System.out.println("EXAMPLE 2: Execute Single Phase");
        System.out.println("-".repeat(60));
        System.out.println();
        System.out.println("This example executes only Phase 0 from the migration plan.");
        System.out.println("Use case: Running preparation or setup phases independently");
        System.out.println("          before committing to the full migration.");
        System.out.println();

        ApplyMigrationCommand command = new ApplyMigrationCommand();

        // Set up command parameters
        setField(command, "projectPath", System.getProperty("user.dir"));
        setField(command, "planPath", "migration-plans/jboss-to-springboot-phase0-1.yaml");
        setField(command, "phaseId", "Phase 0: Migration Preparation");
        setField(command, "dryRun", true);
        setField(command, "interactive", false);
        setField(command, "verbose", true);

        System.out.println("Command Configuration:");
        System.out.println("  Project:    " + System.getProperty("user.dir"));
        System.out.println("  Plan:       migration-plans/jboss-to-springboot-phase0-1.yaml");
        System.out.println("  Mode:       Single Phase (Phase 0: Migration Preparation)");
        System.out.println("  Dry Run:    true");
        System.out.println();

        System.out.println("Executing...");
        System.out.println();

        Integer exitCode = command.call();

        System.out.println();
        System.out.println("Result: " + (exitCode == 0 ? "SUCCESS" : "FAILED"));
        System.out.println("Exit Code: " + exitCode);
    }

    private static void resumeFromCheckpoint() throws Exception {
        System.out.println("EXAMPLE 3: Resume from Checkpoint");
        System.out.println("-".repeat(60));
        System.out.println();
        System.out.println("This example resumes execution from the last checkpoint.");
        System.out.println("Use case: Recovering from failures or continuing interrupted");
        System.out.println("          migrations without redoing completed work.");
        System.out.println();

        ApplyMigrationCommand command = new ApplyMigrationCommand();

        // Set up command parameters
        setField(command, "projectPath", System.getProperty("user.dir"));
        setField(command, "planPath", "migration-plans/jboss-to-springboot-phase0-1.yaml");
        setField(command, "resume", true);
        setField(command, "dryRun", true);
        setField(command, "interactive", false);
        setField(command, "verbose", true);

        System.out.println("Command Configuration:");
        System.out.println("  Project:    " + System.getProperty("user.dir"));
        System.out.println("  Plan:       migration-plans/jboss-to-springboot-phase0-1.yaml");
        System.out.println("  Mode:       Resume from Checkpoint");
        System.out.println("  Dry Run:    true");
        System.out.println();

        System.out.println("Executing...");
        System.out.println();

        Integer exitCode = command.call();

        System.out.println();
        System.out.println("Result: " + (exitCode == 0 ? "SUCCESS" : "FAILED"));
        System.out.println("Exit Code: " + exitCode);
        System.out.println();
        System.out.println("Note: If no checkpoint exists, this will execute the full plan.");
    }

    // Helper method to set private fields using reflection
    private static void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
