package com.analyzer.migration.state;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.engine.ExecutionResult;
import com.analyzer.migration.engine.MigrationExecutionListener;
import com.analyzer.migration.engine.PhaseResult;
import com.analyzer.migration.engine.TaskResult;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;
import com.analyzer.migration.state.model.ExecutionStatus;
import com.analyzer.migration.state.model.MigrationExecutionState;
import com.analyzer.migration.state.model.PhaseExecutionRecord;
import com.analyzer.migration.state.model.TaskExecutionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Listener that updates migration state file during execution.
 * Captures execution events and persists them to the state file.
 */
public class StateFileListener implements MigrationExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(StateFileListener.class);

    private final MigrationStateManager stateManager;
    private final String planKey;
    private final boolean verboseOutput;
    private final Map<String, Long> phaseStartTimes;
    private final Map<String, Long> taskStartTimes;
    private final Map<String, PhaseExecutionRecord> currentPhaseRecords;
    private MigrationPlan currentPlan;

    /**
     * Create state file listener.
     *
     * @param projectRoot Project root directory
     * @param planKey     Unique key for the migration plan
     * @param verbose     Whether to capture detailed task output
     */
    public StateFileListener(Path projectRoot, String planKey, boolean verbose) {
        this.stateManager = new MigrationStateManager(projectRoot);
        this.planKey = planKey;
        this.verboseOutput = verbose;
        this.phaseStartTimes = new HashMap<>();
        this.taskStartTimes = new HashMap<>();
        this.currentPhaseRecords = new HashMap<>();
    }

    @Override
    public void onPlanStart(MigrationPlan plan, MigrationContext context) {
        this.currentPlan = plan; // Store plan for later reference
        try {
            // Initialize state file if needed
            stateManager.initializeStateFile(Paths.get(context.getProjectRoot().toString()));

            // Create or update migration state
            MigrationExecutionState migrationState = stateManager.getMigrationState(planKey);
            if (migrationState == null) {
                migrationState = new MigrationExecutionState(
                        plan.getName(),
                        plan.getVersion(),
                        planKey);
            }

            migrationState.setStatus(ExecutionStatus.IN_PROGRESS);
            migrationState.setStartedAt(Instant.now());
            migrationState.setLastExecuted(Instant.now());
            migrationState.setVariables(context.getAllVariables());

            stateManager.updateMigrationState(planKey, migrationState);
            logger.debug("Updated state for plan start: {}", planKey);
        } catch (IOException e) {
            logger.error("Failed to update state on plan start", e);
        }
    }

    @Override
    public void onPlanComplete(MigrationPlan plan, ExecutionResult result) {
        try {
            MigrationExecutionState migrationState = stateManager.getMigrationState(planKey);
            if (migrationState != null) {
                migrationState.setStatus(result.isSuccess() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
                migrationState.setLastExecuted(Instant.now());
                migrationState.setCurrentPhase(null);
                migrationState.setNextPhase(null);

                stateManager.updateMigrationState(planKey, migrationState);
                logger.info("Migration plan completed: {} - Status: {}", planKey, migrationState.getStatus());
            }
        } catch (IOException e) {
            logger.error("Failed to update state on plan complete", e);
        }
    }

    @Override
    public void onPhaseStart(Phase phase, MigrationContext context) {
        String phaseKey = phase.getName();
        phaseStartTimes.put(phaseKey, System.currentTimeMillis());

        // Create phase execution record
        PhaseExecutionRecord record = new PhaseExecutionRecord(phaseKey, phase.getName());
        record.setStatus(ExecutionStatus.IN_PROGRESS);
        currentPhaseRecords.put(phaseKey, record);

        try {
            MigrationExecutionState migrationState = stateManager.getMigrationState(planKey);
            if (migrationState != null) {
                migrationState.setCurrentPhase(phaseKey);
                migrationState.setLastExecuted(Instant.now());

                // Determine next phase
                if (currentPlan != null) {
                    List<Phase> phases = currentPlan.getPhases();
                    int currentIndex = phases.indexOf(phase);
                    if (currentIndex >= 0 && currentIndex < phases.size() - 1) {
                        migrationState.setNextPhase(phases.get(currentIndex + 1).getName());
                    } else {
                        migrationState.setNextPhase(null);
                    }
                }

                stateManager.updateMigrationState(planKey, migrationState);
            }
        } catch (IOException e) {
            logger.error("Failed to update state on phase start", e);
        }
    }

    @Override
    public boolean onPhaseComplete(Phase phase, PhaseResult result) {
        String phaseKey = phase.getName();
        Long startTime = phaseStartTimes.remove(phaseKey);
        PhaseExecutionRecord record = currentPhaseRecords.remove(phaseKey);

        if (record != null) {
            // Update phase record
            record.setStatus(result.isSuccess() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
            if (startTime != null) {
                record.setDurationMs(System.currentTimeMillis() - startTime);
            }

            // Calculate task statistics
            int completed = 0;
            int failed = 0;
            for (TaskExecutionDetail task : record.getTaskDetails()) {
                if (task.getStatus() == ExecutionStatus.SUCCESS) {
                    completed++;
                } else if (task.getStatus() == ExecutionStatus.FAILED) {
                    failed++;
                }
            }
            record.setTasksCompleted(completed);
            record.setTasksFailed(failed);
            record.setSummary(generatePhaseSummary(phase, result));

            try {
                // Add to execution history
                stateManager.addExecutionHistory(planKey, record);

                // Update migration state
                MigrationExecutionState migrationState = stateManager.getMigrationState(planKey);
                if (migrationState != null) {
                    if (result.isSuccess()) {
                        migrationState.addCompletedPhase(phaseKey);
                    } else {
                        migrationState.addFailedPhase(phaseKey);
                    }
                    migrationState.setLastExecuted(Instant.now());
                    stateManager.updateMigrationState(planKey, migrationState);
                }

                logger.debug("Phase complete: {} - Status: {}", phaseKey, record.getStatus());
            } catch (IOException e) {
                logger.error("Failed to update state on phase complete", e);
            }
        }

        return true; // Continue execution
    }

    @Override
    public void onTaskStart(Task task, MigrationContext context) {
        taskStartTimes.put(task.getId(), System.currentTimeMillis());

        // Get current phase record and add task detail
        String phaseId = getCurrentPhaseId(context);
        PhaseExecutionRecord phaseRecord = currentPhaseRecords.get(phaseId);
        if (phaseRecord != null) {
            TaskExecutionDetail taskDetail = new TaskExecutionDetail(task.getId(), task.getName());
            taskDetail.setStatus(ExecutionStatus.IN_PROGRESS);
            phaseRecord.addTaskDetail(taskDetail);
        }
    }

    @Override
    public boolean onTaskComplete(Task task, TaskResult result) {
        Long startTime = taskStartTimes.remove(task.getId());

        // Find and update task detail in current phase record
        String phaseId = getCurrentPhaseIdFromTask(task);
        PhaseExecutionRecord phaseRecord = currentPhaseRecords.get(phaseId);
        if (phaseRecord != null) {
            TaskExecutionDetail taskDetail = findTaskDetail(phaseRecord, task.getId());
            if (taskDetail != null) {
                taskDetail.setStatus(result.isSuccess() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
                if (startTime != null) {
                    taskDetail.setDurationMs(System.currentTimeMillis() - startTime);
                }
                taskDetail.setOutputSummary(generateTaskSummary(task, result));

                // Capture detailed output if verbose mode
                if (verboseOutput) {
                    String detailedOutput = collectTaskOutput(result);
                    if (detailedOutput != null && !detailedOutput.isEmpty()) {
                        taskDetail.setOutputDetail(detailedOutput);
                    }
                }

                if (!result.isSuccess() && result.getFailureReason() != null) {
                    taskDetail.setErrorMessage(result.getFailureReason());
                }
            }
        }

        return true; // Continue execution
    }

    /**
     * Generate summary for a phase.
     */
    private String generatePhaseSummary(Phase phase, PhaseResult result) {
        if (result.isSuccess()) {
            return String.format("Completed %s with %d tasks",
                    phase.getName(),
                    phase.getTasks().size());
        } else {
            return String.format("Failed %s: %s",
                    phase.getName(),
                    result.getFailureReason() != null ? result.getFailureReason() : "Unknown error");
        }
    }

    /**
     * Generate summary for a task.
     */
    private String generateTaskSummary(Task task, TaskResult result) {
        if (result.isSuccess()) {
            return String.format("Completed: %s", task.getName());
        } else {
            return String.format("Failed: %s",
                    result.getFailureReason() != null ? result.getFailureReason() : "Unknown error");
        }
    }

    /**
     * Collect detailed output from task execution blocks.
     */
    private String collectTaskOutput(TaskResult result) {
        if (result.getBlockResults() == null || result.getBlockResults().isEmpty()) {
            return null;
        }

        return result.getBlockResults().stream()
                .map(BlockResult::getMessage)
                .filter(msg -> msg != null && !msg.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Get current phase ID from context.
     */
    private String getCurrentPhaseId(MigrationContext context) {
        // This is a simplified approach - in production you'd track this more carefully
        // For now, return the first phase ID in our tracking map
        if (!currentPhaseRecords.isEmpty()) {
            return currentPhaseRecords.keySet().iterator().next();
        }
        return null;
    }

    /**
     * Get phase ID from task (simplified - assumes single phase execution).
     */
    private String getCurrentPhaseIdFromTask(Task task) {
        if (!currentPhaseRecords.isEmpty()) {
            return currentPhaseRecords.keySet().iterator().next();
        }
        return null;
    }

    /**
     * Find task detail in phase record.
     */
    private TaskExecutionDetail findTaskDetail(PhaseExecutionRecord phaseRecord, String taskId) {
        if (phaseRecord.getTaskDetails() != null) {
            for (TaskExecutionDetail detail : phaseRecord.getTaskDetails()) {
                if (detail.getTaskId().equals(taskId)) {
                    return detail;
                }
            }
        }
        return null;
    }

    /**
     * Get the state manager (for testing or external access).
     */
    public MigrationStateManager getStateManager() {
        return stateManager;
    }
}
