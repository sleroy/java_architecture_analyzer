package com.analyzer.cli;

import com.analyzer.migration.loader.MigrationPlanConverter;
import com.analyzer.migration.loader.YamlMigrationPlanLoader;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;
import com.analyzer.migration.state.MigrationStateManager;
import com.analyzer.migration.state.model.ExecutionStatus;
import com.analyzer.migration.state.model.MigrationExecutionState;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * CLI command to display migration plan structure and status.
 * Shows phases, tasks, and execution status if project path is provided.
 */
@Command(name = "plan-info", description = "Display migration plan structure and status", mixinStandardHelpOptions = true)
public class PlanInfoCommand implements Callable<Integer> {

    @Option(names = { "--plan" }, description = "Path to migration plan YAML file", required = true)
    private String planPath;

    @Option(names = { "--project" }, description = "Project root path (to show execution status)")
    private String projectPath;

    @Option(names = { "--phase" }, description = "Show details for a specific phase")
    private String phaseFilter;

    @Option(names = { "--task" }, description = "Show details for a specific task")
    private String taskFilter;

    @Override
    public Integer call() {
        try {
            // Load the migration plan
            // Note: We pass null for repository since plan-info doesn't execute queries
            MigrationPlanConverter converter = new MigrationPlanConverter(null);
            YamlMigrationPlanLoader loader = new YamlMigrationPlanLoader(converter);
            MigrationPlan plan = loader.loadFromPath(Paths.get(planPath));

            // Get execution state if project path provided
            MigrationExecutionState executionState = null;
            if (projectPath != null) {
                MigrationStateManager stateManager = new MigrationStateManager(Paths.get(projectPath));
                if (stateManager.exists()) {
                    String planKey = Paths.get(planPath).getFileName().toString();
                    executionState = stateManager.getMigrationState(planKey);
                }
            }

            // Display plan information
            displayPlanHeader(plan, executionState);

            if (phaseFilter != null) {
                // Display specific phase
                displayPhaseDetails(plan, phaseFilter, executionState);
            } else if (taskFilter != null) {
                // Display specific task
                displayTaskDetails(plan, taskFilter, executionState);
            } else {
                // Display all phases
                displayAllPhases(plan, executionState);
            }

            return 0;
        } catch (IOException e) {
            System.err.println("Error loading migration plan: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private void displayPlanHeader(MigrationPlan plan, MigrationExecutionState state) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Migration Plan: " + plan.getName());
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Version:     " + plan.getVersion());
        System.out.println("Description: " + (plan.getDescription() != null ? plan.getDescription() : "N/A"));
        System.out.println("Phases:      " + plan.getPhases().size());

        if (state != null) {
            System.out.println();
            System.out.println("─────────────────────────────────────────────────────────────");
            System.out.println("Execution Status");
            System.out.println("─────────────────────────────────────────────────────────────");
            System.out.println("Status:         " + state.getStatus());
            if (state.getStartedAt() != null) {
                System.out.println("Started:        " + state.getStartedAt());
            }
            if (state.getLastExecuted() != null) {
                System.out.println("Last Executed:  " + state.getLastExecuted());
            }
            if (state.getCurrentPhase() != null) {
                System.out.println("Current Phase:  " + state.getCurrentPhase());
            }
            if (state.getNextPhase() != null) {
                System.out.println("Next Phase:     " + state.getNextPhase());
            }
            System.out.println("Completed:      " + state.getCompletedPhases().size() + " phases");
            if (!state.getFailedPhases().isEmpty()) {
                System.out.println("Failed:         " + state.getFailedPhases().size() + " phases");
            }
        }
        System.out.println();
    }

    private void displayAllPhases(MigrationPlan plan, MigrationExecutionState state) {
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("Phases");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();

        for (Phase phase : plan.getPhases()) {
            String statusIcon = getPhaseStatusIcon(phase, state);
            System.out.printf("%s Phase: %s%n", statusIcon, phase.getName());
            System.out.printf("   Order:       %d%n", phase.getOrder());
            System.out.printf("   Description: %s%n", phase.getDescription() != null ? phase.getDescription() : "N/A");
            System.out.printf("   Tasks:       %d%n", phase.getTasks().size());

            if (state != null) {
                ExecutionStatus phaseStatus = getPhaseExecutionStatus(phase, state);
                if (phaseStatus != null && phaseStatus != ExecutionStatus.PENDING) {
                    System.out.printf("   Status:      %s%n", phaseStatus);
                }
            }
            System.out.println();
        }
    }

    private void displayPhaseDetails(MigrationPlan plan, String phaseName, MigrationExecutionState state) {
        Phase phase = findPhase(plan, phaseName);
        if (phase == null) {
            System.err.println("Phase not found: " + phaseName);
            return;
        }

        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("Phase Details: " + phase.getName());
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("Order:       " + phase.getOrder());
        System.out.println("Description: " + (phase.getDescription() != null ? phase.getDescription() : "N/A"));

        if (state != null) {
            ExecutionStatus phaseStatus = getPhaseExecutionStatus(phase, state);
            if (phaseStatus != null) {
                System.out.println("Status:      " + phaseStatus);
            }
        }

        System.out.println();
        System.out.println("Tasks:");
        System.out.println();

        for (Task task : phase.getTasks()) {
            String statusIcon = "  ";
            System.out.printf("%s Task: %s (ID: %s)%n", statusIcon, task.getName(), task.getId());
            System.out.printf("     Description: %s%n", task.getDescription() != null ? task.getDescription() : "N/A");
            System.out.printf("     Blocks:      %d%n", task.getBlocks().size());

            if (task.isManualReviewRequired()) {
                System.out.println("     Manual Review: Required");
            }
            System.out.println();
        }
    }

    private void displayTaskDetails(MigrationPlan plan, String taskId, MigrationExecutionState state) {
        Task task = findTask(plan, taskId);
        if (task == null) {
            System.err.println("Task not found: " + taskId);
            return;
        }

        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("Task Details: " + task.getName());
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("ID:              " + task.getId());
        System.out.println("Description:     " + (task.getDescription() != null ? task.getDescription() : "N/A"));
        System.out.println("Blocks:          " + task.getBlocks().size());


        if (task.isManualReviewRequired()) {
            System.out.println("Manual Review:   Required");
        }

        if (task.getSuccessCriteria() != null) {
            System.out.println("Success Criteria: " + task.getSuccessCriteria());
        }

        System.out.println();
        System.out.println("Blocks:");
        task.getBlocks().forEach(block -> {
            System.out
                    .println("  - " + block.getType() + (block.getName() != null ? " (" + block.getName() + ")" : ""));
        });
        System.out.println();
    }

    private Phase findPhase(MigrationPlan plan, String phaseName) {
        for (Phase phase : plan.getPhases()) {
            if (phase.getName().equals(phaseName)) {
                return phase;
            }
        }
        return null;
    }

    private Task findTask(MigrationPlan plan, String taskId) {
        for (Phase phase : plan.getPhases()) {
            for (Task task : phase.getTasks()) {
                if (task.getId().equals(taskId)) {
                    return task;
                }
            }
        }
        return null;
    }

    private String getPhaseStatusIcon(Phase phase, MigrationExecutionState state) {
        if (state == null) {
            return "○";
        }

        ExecutionStatus status = getPhaseExecutionStatus(phase, state);
        if (status == null) {
            return "○";
        }

        switch (status) {
            case SUCCESS:
                return "✓";
            case FAILED:
                return "✗";
            case IN_PROGRESS:
                return "◐";
            default:
                return "○";
        }
    }

    private ExecutionStatus getPhaseExecutionStatus(Phase phase, MigrationExecutionState state) {
        if (state == null) {
            return null;
        }

        String phaseName = phase.getName();
        if (state.getCompletedPhases().contains(phaseName)) {
            return ExecutionStatus.SUCCESS;
        }
        if (state.getFailedPhases().contains(phaseName)) {
            return ExecutionStatus.FAILED;
        }
        if (phaseName.equals(state.getCurrentPhase())) {
            return ExecutionStatus.IN_PROGRESS;
        }

        return ExecutionStatus.PENDING;
    }
}
