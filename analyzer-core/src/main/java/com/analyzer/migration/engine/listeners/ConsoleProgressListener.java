package com.analyzer.migration.engine.listeners;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.engine.ExecutionResult;
import com.analyzer.migration.engine.MigrationExecutionListener;
import com.analyzer.migration.engine.PhaseResult;
import com.analyzer.migration.engine.TaskResult;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;

import java.time.Duration;

/**
 * Console-based progress listener that displays execution progress in a
 * user-friendly format.
 * 
 * <p>
 * Displays:
 * <ul>
 * <li>Plan start/completion with statistics</li>
 * <li>Phase progress with visual separators</li>
 * <li>Task completion status with icons</li>
 * <li>Block execution summary</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * MigrationEngine engine = new MigrationEngine("my-plan");
 * engine.addListener(new ConsoleProgressListener());
 * ExecutionResult result = engine.executePlan(plan, context);
 * </pre>
 */
public class ConsoleProgressListener implements MigrationExecutionListener {

    private static final String PLAN_SEPARATOR = "═══════════════════════════════════════════════════════════════════";
    private static final String PHASE_SEPARATOR = "───────────────────────────────────────────────────────────────────";
    private static final String SUCCESS_ICON = "✓";
    private static final String FAILURE_ICON = "✗";
    private static final String RUNNING_ICON = "▶";

    private int totalTasks = 0;
    private int completedTasks = 0;

    @Override
    public void onPlanStart(MigrationPlan plan, MigrationContext context) {
        System.out.println();
        System.out.println(PLAN_SEPARATOR);
        System.out.println("  MIGRATION PLAN: " + plan.getName());
        System.out.println("  " + plan.getDescription());
        System.out.println("  Phases: " + plan.getPhases().size());
        System.out.println(PLAN_SEPARATOR);
        System.out.println();

        // Calculate total tasks
        totalTasks = plan.getPhases().stream()
                .mapToInt(p -> p.getTasks().size())
                .sum();
        completedTasks = 0;
    }

    @Override
    public void onPlanComplete(MigrationPlan plan, ExecutionResult result) {
        System.out.println();
        System.out.println(PLAN_SEPARATOR);
        if (result.isSuccess()) {
            System.out.println("  " + SUCCESS_ICON + " MIGRATION COMPLETED SUCCESSFULLY");
        } else {
            System.out.println("  " + FAILURE_ICON + " MIGRATION FAILED");
            System.out.println("  Failed in phase: " + result.getFailurePhase());
            System.out.println("  Reason: " + result.getFailureReason());
        }
        System.out.println("  Duration: " + formatDuration(result.getDuration()));
        System.out.println("  Tasks: " + result.getSuccessfulTasks() + "/" + result.getTotalTasks() + " successful");
        System.out.println(PLAN_SEPARATOR);
        System.out.println();
    }

    @Override
    public void onPhaseStart(Phase phase, MigrationContext context) {
        System.out.println();
        System.out.println(PHASE_SEPARATOR);
        System.out.println("  " + RUNNING_ICON + " PHASE: " + phase.getName());
        System.out.println("  " + phase.getDescription());
        System.out.println("  Tasks: " + phase.getTasks().size());
        System.out.println(PHASE_SEPARATOR);
    }

    @Override
    public boolean onPhaseComplete(Phase phase, PhaseResult result) {
        if (result.isSuccess()) {
            System.out.println("  " + SUCCESS_ICON + " Phase '" + phase.getName() + "' completed successfully");
            System.out.println("    Duration: " + formatDuration(result.getDuration()));
            System.out.println("    Tasks: " + result.getSuccessfulTaskCount() + "/" + result.getTaskCount());
        } else {
            System.out.println("  " + FAILURE_ICON + " Phase '" + phase.getName() + "' failed");
            System.out.println("    Failed task: " + result.getFailureTask());
            System.out.println("    Reason: " + result.getFailureReason());
        }
        return true; // Continue execution
    }

    @Override
    public void onTaskStart(Task task, MigrationContext context) {
        System.out.println("    " + RUNNING_ICON + " " + task.getName());
    }

    @Override
    public boolean onTaskComplete(Task task, TaskResult result) {
        completedTasks++;
        double progress = (completedTasks * 100.0) / totalTasks;

        if (result.isSuccess()) {
            System.out.println("      " + SUCCESS_ICON + " " + task.getName() +
                    " [" + String.format("%.0f%%", progress) + "]" +
                    " (" + result.getDuration().toMillis() + "ms, " +
                    result.getBlockCount() + " blocks)");
        } else {
            System.out.println("      " + FAILURE_ICON + " " + task.getName() + " FAILED");
            System.out.println("        Reason: " + result.getFailureReason());
        }
        return true; // Continue execution
    }

    @Override
    public void onBlockComplete(MigrationBlock block, BlockResult result) {
        if (!result.isSuccess()) {
            System.out.println("        " + FAILURE_ICON + " Block failed: " + block.getName());
            System.out.println("          " + result.getMessage());
            // Display error details if available (e.g., command output)
            if (result.getErrorDetails() != null && !result.getErrorDetails().trim().isEmpty()) {
                System.out.println("          Details:");
                // Indent each line of error details
                for (String line : result.getErrorDetails().split("\n")) {
                    System.out.println("            " + line);
                }
            }
        } else {
            // Display success details for GraphQuery blocks to show result counts
            // prominently
            if (block.getType().toString().equals("GRAPH_QUERY")) {
                System.out.println("        " + SUCCESS_ICON + " " + result.getMessage());
            }
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
}
