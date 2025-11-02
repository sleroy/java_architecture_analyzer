package com.analyzer.migration.engine;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and persists migration execution progress.
 * Enables resuming from checkpoints if execution is interrupted.
 */
public class ProgressTracker {
    private static final Logger logger = LoggerFactory.getLogger(ProgressTracker.class);

    private final String executionId;
    private final String planName;
    private final Map<String, PhaseProgress> phaseProgress;
    private final Map<String, TaskProgress> taskProgress;
    private LocalDateTime planStartTime;
    private LocalDateTime lastUpdateTime;
    private String currentPhase;
    private String currentTask;

    public ProgressTracker(String planName) {
        this.executionId = UUID.randomUUID().toString();
        this.planName = planName;
        this.phaseProgress = new ConcurrentHashMap<>();
        this.taskProgress = new ConcurrentHashMap<>();
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * Record the start of plan execution
     */
    public void recordPlanStart(String planName) {
        this.planStartTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
        logger.info("Started migration plan: {} (execution ID: {})", planName, executionId);
    }

    /**
     * Record the completion of plan execution
     */
    public void recordPlanComplete(String planName, boolean success) {
        this.lastUpdateTime = LocalDateTime.now();
        logger.info("Completed migration plan: {} - success: {}", planName, success);
    }

    /**
     * Record the start of a phase
     */
    public void recordPhaseStart(String phaseName) {
        this.currentPhase = phaseName;
        PhaseProgress progress = new PhaseProgress(phaseName, LocalDateTime.now());
        phaseProgress.put(phaseName, progress);
        this.lastUpdateTime = LocalDateTime.now();
        logger.info("Started phase: {}", phaseName);
    }

    /**
     * Record the completion of a phase
     */
    public void recordPhaseComplete(String phaseName, boolean success) {
        PhaseProgress progress = phaseProgress.get(phaseName);
        if (progress != null) {
            progress.endTime = LocalDateTime.now();
            progress.success = success;
        }
        this.lastUpdateTime = LocalDateTime.now();
        logger.info("Completed phase: {} - success: {}", phaseName, success);
    }

    /**
     * Record the start of a task
     */
    public void recordTaskStart(String taskName) {
        this.currentTask = taskName;
        TaskProgress progress = new TaskProgress(taskName, currentPhase, LocalDateTime.now());
        taskProgress.put(taskName, progress);
        this.lastUpdateTime = LocalDateTime.now();
        logger.info("Started task: {}", taskName);
    }

    /**
     * Record the completion of a task
     */
    public void recordTaskComplete(String taskName, boolean success) {
        TaskProgress progress = taskProgress.get(taskName);
        if (progress != null) {
            progress.endTime = LocalDateTime.now();
            progress.success = success;
        }
        this.lastUpdateTime = LocalDateTime.now();
        logger.info("Completed task: {} - success: {}", taskName, success);
    }

    /**
     * Record the execution of a block
     */
    public void recordBlockExecution(String blockName, BlockResult result) {
        TaskProgress progress = taskProgress.get(currentTask);
        if (progress != null) {
            progress.blockResults.add(result);
        }
        this.lastUpdateTime = LocalDateTime.now();
        logger.debug("Recorded block execution: {} - success: {}", blockName, result.isSuccess());
    }

    /**
     * Get current progress information
     */
    public ProgressInfo getProgress() {
        int totalPhases = phaseProgress.size();
        int completedPhases = (int) phaseProgress.values().stream()
                .filter(p -> p.endTime != null)
                .count();

        int totalTasks = taskProgress.size();
        int completedTasks = (int) taskProgress.values().stream()
                .filter(t -> t.endTime != null && t.success)
                .count();
        int failedTasks = (int) taskProgress.values().stream()
                .filter(t -> t.endTime != null && !t.success)
                .count();

        String status = determineStatus();

        return ProgressInfo.builder()
                .planName(planName)
                .currentPhase(currentPhase)
                .currentTask(currentTask)
                .totalPhases(totalPhases)
                .completedPhases(completedPhases)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .failedTasks(failedTasks)
                .startTime(planStartTime)
                .lastUpdateTime(lastUpdateTime)
                .status(status)
                .build();
    }

    /**
     * Check if execution can be resumed from a checkpoint
     */
    public boolean canResume() {
        // Check if there's valid progress that can be resumed
        return !taskProgress.isEmpty() && currentPhase != null;
    }

    /**
     * Get the last checkpoint for resuming execution
     */
    public Checkpoint getLastCheckpoint(MigrationContext context) {
        if (!canResume()) {
            return Checkpoint.builder()
                    .planName(planName)
                    .valid(false)
                    .build();
        }

        // Find last completed task
        String lastCompletedPhase = null;
        String lastCompletedTask = null;

        for (TaskProgress tp : taskProgress.values()) {
            if (tp.endTime != null && tp.success) {
                lastCompletedPhase = tp.phaseName;
                lastCompletedTask = tp.taskName;
            }
        }

        return Checkpoint.builder()
                .planName(planName)
                .lastCompletedPhase(lastCompletedPhase)
                .lastCompletedTask(lastCompletedTask)
                .currentPhase(currentPhase)
                .currentTask(currentTask)
                .contextVariables(context.getAllVariables())
                .checkpointTime(lastUpdateTime)
                .valid(true)
                .build();
    }

    /**
     * Get execution ID
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Get completed task names
     */
    public Set<String> getCompletedTaskNames() {
        Set<String> completed = new HashSet<>();
        for (Map.Entry<String, TaskProgress> entry : taskProgress.entrySet()) {
            if (entry.getValue().endTime != null && entry.getValue().success) {
                completed.add(entry.getKey());
            }
        }
        return completed;
    }

    private String determineStatus() {
        long failedTasks = taskProgress.values().stream()
                .filter(t -> t.endTime != null && !t.success)
                .count();

        if (failedTasks > 0) {
            return "FAILED";
        }

        if (currentTask != null) {
            return "IN_PROGRESS";
        }

        long completedTasks = taskProgress.values().stream()
                .filter(t -> t.endTime != null && t.success)
                .count();

        if (completedTasks > 0 && completedTasks == taskProgress.size()) {
            return "COMPLETED";
        }

        return "PENDING";
    }

    // Internal progress tracking classes

    private static class PhaseProgress {
        final String phaseName;
        final LocalDateTime startTime;
        LocalDateTime endTime;
        boolean success;

        PhaseProgress(String phaseName, LocalDateTime startTime) {
            this.phaseName = phaseName;
            this.startTime = startTime;
        }
    }

    private static class TaskProgress {
        final String taskName;
        final String phaseName;
        final LocalDateTime startTime;
        final List<BlockResult> blockResults;
        LocalDateTime endTime;
        boolean success;

        TaskProgress(String taskName, String phaseName, LocalDateTime startTime) {
            this.taskName = taskName;
            this.phaseName = phaseName;
            this.startTime = startTime;
            this.blockResults = new ArrayList<>();
        }
    }
}
