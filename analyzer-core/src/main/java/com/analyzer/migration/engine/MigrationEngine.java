package com.analyzer.migration.engine;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Core execution engine for migration plans.
 * Orchestrates phase, task, and block execution with progress tracking and
 * error handling.
 */
public class MigrationEngine {
    private static final Logger logger = LoggerFactory.getLogger(MigrationEngine.class);

    private final TaskExecutor taskExecutor;
    private final ProgressTracker progressTracker;
    private final List<MigrationExecutionListener> listeners;
    private volatile boolean pauseRequested = false;
    private volatile boolean cancelRequested = false;

    public MigrationEngine(String planName) {
        this.taskExecutor = new TaskExecutor();
        this.progressTracker = new ProgressTracker(planName);
        this.listeners = new ArrayList<>();
    }

    /**
     * Add a listener to receive execution events.
     *
     * @param listener Listener to add
     */
    public void addListener(MigrationExecutionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a listener.
     *
     * @param listener Listener to remove
     */
    public void removeListener(MigrationExecutionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Remove all listeners.
     */
    public void clearListeners() {
        listeners.clear();
    }

    /**
     * Execute a complete migration plan.
     *
     * @param plan    Migration plan to execute
     * @param context Migration context with variables
     * @return ExecutionResult with detailed outcomes
     */
    public ExecutionResult executePlan(MigrationPlan plan, MigrationContext context) {
        logger.info("========================================");
        logger.info("Starting migration plan: {}", plan.getName());
        logger.info("Description: {}", plan.getDescription());
        logger.info("Phases: {}", plan.getPhases().size());
        logger.info("========================================");

        LocalDateTime startTime = LocalDateTime.now();
        List<PhaseResult> phaseResults = new ArrayList<>();

        try {
            progressTracker.recordPlanStart(plan.getName());

            // Fire plan start event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanStart(plan, context);
            }

            // Execute each phase in sequence
            for (int i = 0; i < plan.getPhases().size(); i++) {
                Phase phase = plan.getPhases().get(i);

                // Check for cancellation
                if (cancelRequested) {
                    logger.warn("Migration cancelled by user");
                    LocalDateTime endTime = LocalDateTime.now();
                    return ExecutionResult.failed(
                            plan.getName(),
                            phase.getName(),
                            "Cancelled by user",
                            phaseResults,
                            startTime,
                            endTime);
                }

                // Check for pause
                while (pauseRequested && !cancelRequested) {
                    logger.info("Migration paused - waiting for resume...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted during pause");
                    }
                }

                logger.info("Executing phase {}/{}: {}", i + 1, plan.getPhases().size(), phase.getName());

                // Fire phase start event
                for (MigrationExecutionListener listener : listeners) {
                    listener.onPhaseStart(phase, context);
                }

                PhaseResult phaseResult = executePhase(phase, context);
                phaseResults.add(phaseResult);

                // Fire phase complete event - check if should continue
                boolean shouldContinue = true;
                for (MigrationExecutionListener listener : listeners) {
                    if (!listener.onPhaseComplete(phase, phaseResult)) {
                        shouldContinue = false;
                        logger.warn("Execution stopped by listener after phase: {}", phase.getName());
                        break;
                    }
                }

                if (!shouldContinue) {
                    progressTracker.recordPlanComplete(plan.getName(), false);
                    LocalDateTime endTime = LocalDateTime.now();
                    return ExecutionResult.failed(
                            plan.getName(),
                            phase.getName(),
                            "Stopped by listener",
                            phaseResults,
                            startTime,
                            endTime);
                }

                if (!phaseResult.isSuccess()) {
                    logger.error("Phase failed: {} - {}", phase.getName(), phaseResult.getFailureReason());

                    // Stop execution if phase failed
                    progressTracker.recordPlanComplete(plan.getName(), false);
                    LocalDateTime endTime = LocalDateTime.now();
                    return ExecutionResult.failed(
                            plan.getName(),
                            phase.getName(),
                            phaseResult.getFailureReason(),
                            phaseResults,
                            startTime,
                            endTime);
                }

                logger.info("Phase completed successfully: {}", phase.getName());
            }

            // All phases completed successfully
            progressTracker.recordPlanComplete(plan.getName(), true);
            LocalDateTime endTime = LocalDateTime.now();

            logger.info("========================================");
            logger.info("Migration plan completed successfully: {}", plan.getName());
            logger.info("Total phases: {}", phaseResults.size());
            logger.info("Total tasks: {}", phaseResults.stream()
                    .mapToInt(PhaseResult::getTaskCount).sum());
            logger.info("Duration: {}", java.time.Duration.between(startTime, endTime));
            logger.info("========================================");

            ExecutionResult result = ExecutionResult.success(plan.getName(), phaseResults, startTime, endTime);

            // Fire plan complete event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanComplete(plan, result);
            }

            return result;

        } catch (Exception e) {
            logger.error("Unexpected error executing migration plan: {}", plan.getName(), e);
            progressTracker.recordPlanComplete(plan.getName(), false);
            LocalDateTime endTime = LocalDateTime.now();
            return ExecutionResult.failed(
                    plan.getName(),
                    "unknown",
                    "Unexpected error: " + e.getMessage(),
                    phaseResults,
                    startTime,
                    endTime);
        }
    }

    /**
     * Execute a single phase by ID from the plan.
     *
     * @param plan    Migration plan containing the phase
     * @param phaseId ID of the phase to execute (searches by ID first, then by
     *                name)
     * @param context Migration context
     * @return ExecutionResult with the single phase execution
     */
    public ExecutionResult executePhaseById(MigrationPlan plan, String phaseId, MigrationContext context) {
        logger.info("========================================");
        logger.info("Executing single phase: {}", phaseId);
        logger.info("From plan: {}", plan.getName());
        logger.info("========================================");

        LocalDateTime startTime = LocalDateTime.now();

        // Find the phase by ID first, then by name as fallback
        Phase targetPhase = null;
        for (Phase phase : plan.getPhases()) {
            if ((phase.getId() != null && phase.getId().equalsIgnoreCase(phaseId)) ||
                    phase.getName().equalsIgnoreCase(phaseId)) {
                targetPhase = phase;
                break;
            }
        }

        if (targetPhase == null) {
            logger.error("Phase not found: {}", phaseId);
            LocalDateTime endTime = LocalDateTime.now();
            return ExecutionResult.failed(
                    plan.getName(),
                    phaseId,
                    "Phase not found: " + phaseId,
                    new ArrayList<>(),
                    startTime,
                    endTime);
        }

        try {
            progressTracker.recordPlanStart(plan.getName());

            // Fire plan start event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanStart(plan, context);
            }

            // Fire phase start event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPhaseStart(targetPhase, context);
            }

            // Execute the phase
            PhaseResult phaseResult = executePhase(targetPhase, context);
            List<PhaseResult> phaseResults = new ArrayList<>();
            phaseResults.add(phaseResult);

            // Fire phase complete event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPhaseComplete(targetPhase, phaseResult);
            }

            progressTracker.recordPlanComplete(plan.getName(), phaseResult.isSuccess());
            LocalDateTime endTime = LocalDateTime.now();

            ExecutionResult result;
            if (phaseResult.isSuccess()) {
                logger.info("Phase completed successfully: {}", phaseId);
                result = ExecutionResult.success(plan.getName(), phaseResults, startTime, endTime);
            } else {
                logger.error("Phase failed: {}", phaseId);
                result = ExecutionResult.failed(
                        plan.getName(),
                        phaseId,
                        phaseResult.getFailureReason(),
                        phaseResults,
                        startTime,
                        endTime);
            }

            // Fire plan complete event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanComplete(plan, result);
            }

            return result;

        } catch (Exception e) {
            logger.error("Unexpected error executing phase: {}", phaseId, e);
            progressTracker.recordPlanComplete(plan.getName(), false);
            LocalDateTime endTime = LocalDateTime.now();
            return ExecutionResult.failed(
                    plan.getName(),
                    phaseId,
                    "Unexpected error: " + e.getMessage(),
                    new ArrayList<>(),
                    startTime,
                    endTime);
        }
    }

    /**
     * Execute a single task by ID from the plan.
     *
     * @param plan    Migration plan containing the task
     * @param taskId  ID of the task to execute
     * @param context Migration context
     * @return ExecutionResult with the single task execution
     */
    public ExecutionResult executeTaskById(MigrationPlan plan, String taskId, MigrationContext context) {
        logger.info("========================================");
        logger.info("Executing single task: {}", taskId);
        logger.info("From plan: {}", plan.getName());
        logger.info("========================================");

        LocalDateTime startTime = LocalDateTime.now();

        // Find the task across all phases
        Task targetTask = null;
        Phase parentPhase = null;
        for (Phase phase : plan.getPhases()) {
            for (Task task : phase.getTasks()) {
                if (task.getId().equals(taskId)) {
                    targetTask = task;
                    parentPhase = phase;
                    break;
                }
            }
            if (targetTask != null) {
                break;
            }
        }

        if (targetTask == null) {
            logger.error("Task not found: {}", taskId);
            LocalDateTime endTime = LocalDateTime.now();
            return ExecutionResult.failed(
                    plan.getName(),
                    taskId,
                    "Task not found: " + taskId,
                    new ArrayList<>(),
                    startTime,
                    endTime);
        }

        try {
            progressTracker.recordPlanStart(plan.getName());

            // Fire plan start event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanStart(plan, context);
            }

            // Fire phase start event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPhaseStart(parentPhase, context);
            }

            logger.info("Executing task: {} from phase: {}", targetTask.getName(), parentPhase.getName());

            // Pass listeners to task executor
            taskExecutor.setListeners(listeners);

            // Execute the task
            progressTracker.recordTaskStart(targetTask.getName());
            TaskResult taskResult = taskExecutor.executeTask(targetTask, context);
            progressTracker.recordTaskComplete(targetTask.getName(), taskResult.isSuccess());

            // Record block executions
            taskResult.getBlockResults().forEach(
                    blockResult -> progressTracker.recordBlockExecution(blockResult.getMessage(), blockResult));

            // Create phase result with single task
            List<TaskResult> taskResults = new ArrayList<>();
            taskResults.add(taskResult);
            LocalDateTime phaseEndTime = LocalDateTime.now();

            PhaseResult phaseResult;
            if (taskResult.isSuccess()) {
                phaseResult = PhaseResult.success(parentPhase.getName(), taskResults, startTime, phaseEndTime);
            } else {
                phaseResult = PhaseResult.failed(
                        parentPhase.getName(),
                        targetTask.getName(),
                        taskResult.getFailureReason(),
                        taskResults,
                        startTime,
                        phaseEndTime);
            }

            // Fire phase complete event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPhaseComplete(parentPhase, phaseResult);
            }

            // Create execution result
            List<PhaseResult> phaseResults = new ArrayList<>();
            phaseResults.add(phaseResult);

            progressTracker.recordPlanComplete(plan.getName(), taskResult.isSuccess());
            LocalDateTime endTime = LocalDateTime.now();

            ExecutionResult result;
            if (taskResult.isSuccess()) {
                logger.info("Task completed successfully: {}", taskId);
                result = ExecutionResult.success(plan.getName(), phaseResults, startTime, endTime);
            } else {
                logger.error("Task failed: {}", taskId);
                result = ExecutionResult.failed(
                        plan.getName(),
                        taskId,
                        taskResult.getFailureReason(),
                        phaseResults,
                        startTime,
                        endTime);
            }

            // Fire plan complete event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanComplete(plan, result);
            }

            return result;

        } catch (Exception e) {
            logger.error("Unexpected error executing task: {}", taskId, e);
            progressTracker.recordPlanComplete(plan.getName(), false);
            LocalDateTime endTime = LocalDateTime.now();
            return ExecutionResult.failed(
                    plan.getName(),
                    taskId,
                    "Unexpected error: " + e.getMessage(),
                    new ArrayList<>(),
                    startTime,
                    endTime);
        }
    }

    /**
     * Resume execution from the last checkpoint.
     *
     * @param plan    Migration plan to resume
     * @param context Migration context
     * @return ExecutionResult with execution details
     */
    public ExecutionResult resumeFromCheckpoint(MigrationPlan plan, MigrationContext context) {
        logger.info("========================================");
        logger.info("Resuming migration plan from checkpoint: {}", plan.getName());
        logger.info("========================================");

        if (!canResume()) {
            logger.warn("No checkpoint found, executing full plan");
            return executePlan(plan, context);
        }

        Checkpoint checkpoint = getLastCheckpoint(context);
        logger.info("Found checkpoint from: {}", checkpoint.getCheckpointTime());
        logger.info("Last completed task: {}", checkpoint.getLastCompletedTask());

        LocalDateTime startTime = LocalDateTime.now();
        List<PhaseResult> phaseResults = new ArrayList<>();

        try {
            progressTracker.recordPlanStart(plan.getName());

            // Fire plan start event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanStart(plan, context);
            }

            // Get completed tasks from progress tracker
            Set<String> completedTasks = progressTracker.getCompletedTaskNames();
            logger.info("Skipping {} already completed tasks", completedTasks.size());

            // Execute each phase, skipping completed tasks
            for (int i = 0; i < plan.getPhases().size(); i++) {
                Phase phase = plan.getPhases().get(i);

                // Check if all tasks in this phase are completed
                boolean allTasksComplete = phase.getTasks().stream()
                        .allMatch(task -> completedTasks.contains(task.getId()));

                if (allTasksComplete) {
                    logger.info("Skipping completed phase: {}", phase.getName());
                    continue;
                }

                // Check for cancellation
                if (cancelRequested) {
                    logger.warn("Migration cancelled by user");
                    LocalDateTime endTime = LocalDateTime.now();
                    return ExecutionResult.failed(
                            plan.getName(),
                            phase.getName(),
                            "Cancelled by user",
                            phaseResults,
                            startTime,
                            endTime);
                }

                logger.info("Resuming phase {}/{}: {}", i + 1, plan.getPhases().size(), phase.getName());

                // Fire phase start event
                for (MigrationExecutionListener listener : listeners) {
                    listener.onPhaseStart(phase, context);
                }

                // Execute phase, skipping completed tasks
                PhaseResult phaseResult = executePhaseWithSkip(phase, context, completedTasks);
                phaseResults.add(phaseResult);

                // Fire phase complete event
                boolean shouldContinue = true;
                for (MigrationExecutionListener listener : listeners) {
                    if (!listener.onPhaseComplete(phase, phaseResult)) {
                        shouldContinue = false;
                        logger.warn("Execution stopped by listener after phase: {}", phase.getName());
                        break;
                    }
                }

                if (!shouldContinue) {
                    progressTracker.recordPlanComplete(plan.getName(), false);
                    LocalDateTime endTime = LocalDateTime.now();
                    return ExecutionResult.failed(
                            plan.getName(),
                            phase.getName(),
                            "Stopped by listener",
                            phaseResults,
                            startTime,
                            endTime);
                }

                if (!phaseResult.isSuccess()) {
                    logger.error("Phase failed: {} - {}", phase.getName(), phaseResult.getFailureReason());
                    progressTracker.recordPlanComplete(plan.getName(), false);
                    LocalDateTime endTime = LocalDateTime.now();
                    return ExecutionResult.failed(
                            plan.getName(),
                            phase.getName(),
                            phaseResult.getFailureReason(),
                            phaseResults,
                            startTime,
                            endTime);
                }

                logger.info("Phase completed successfully: {}", phase.getName());
            }

            // All phases completed successfully
            progressTracker.recordPlanComplete(plan.getName(), true);
            LocalDateTime endTime = LocalDateTime.now();

            logger.info("========================================");
            logger.info("Migration plan resumed and completed successfully: {}", plan.getName());
            logger.info("========================================");

            ExecutionResult result = ExecutionResult.success(plan.getName(), phaseResults, startTime, endTime);

            // Fire plan complete event
            for (MigrationExecutionListener listener : listeners) {
                listener.onPlanComplete(plan, result);
            }

            return result;

        } catch (Exception e) {
            logger.error("Unexpected error resuming migration plan: {}", plan.getName(), e);
            progressTracker.recordPlanComplete(plan.getName(), false);
            LocalDateTime endTime = LocalDateTime.now();
            return ExecutionResult.failed(
                    plan.getName(),
                    "unknown",
                    "Unexpected error: " + e.getMessage(),
                    phaseResults,
                    startTime,
                    endTime);
        }
    }

    /**
     * Execute a phase while skipping already completed tasks.
     *
     * @param phase          Phase to execute
     * @param context        Migration context
     * @param completedTasks Set of already completed task IDs
     * @return PhaseResult with execution details
     */
    private PhaseResult executePhaseWithSkip(Phase phase, MigrationContext context, Set<String> completedTasks) {
        logger.info("Executing phase (with skip): {}", phase.getName());

        LocalDateTime startTime = LocalDateTime.now();
        List<TaskResult> taskResults = new ArrayList<>();

        try {
            progressTracker.recordPhaseStart(phase.getName());

            // Pass listeners to task executor
            taskExecutor.setListeners(listeners);

            // Resolve task dependencies
            List<Task> orderedTasks = taskExecutor.resolveDependencies(phase.getTasks());

            // Execute each task
            for (int i = 0; i < orderedTasks.size(); i++) {
                Task task = orderedTasks.get(i);

                // Skip if already completed
                if (completedTasks.contains(task.getId())) {
                    logger.info("Skipping completed task {}/{}: {}", i + 1, orderedTasks.size(), task.getName());
                    continue;
                }

                // Check for cancellation
                if (cancelRequested) {
                    logger.warn("Phase cancelled by user");
                    LocalDateTime endTime = LocalDateTime.now();
                    return PhaseResult.failed(
                            phase.getName(),
                            task.getName(),
                            "Cancelled by user",
                            taskResults,
                            startTime,
                            endTime);
                }

                logger.info("Executing task {}/{}: {} ({})",
                        i + 1, orderedTasks.size(), task.getName(), task.getId());

                progressTracker.recordTaskStart(task.getName());
                TaskResult taskResult = taskExecutor.executeTask(task, context);
                taskResults.add(taskResult);
                progressTracker.recordTaskComplete(task.getName(), taskResult.isSuccess());

                // Record block executions
                taskResult.getBlockResults().forEach(
                        blockResult -> progressTracker.recordBlockExecution(blockResult.getMessage(), blockResult));

                if (taskResult.isSuccess()) {
                    completedTasks.add(task.getId());
                    logger.info("Task completed successfully: {}", task.getName());
                } else {
                    logger.error("Task failed: {} - {}", task.getName(), taskResult.getFailureReason());
                    progressTracker.recordPhaseComplete(phase.getName(), false);
                    LocalDateTime endTime = LocalDateTime.now();
                    return PhaseResult.failed(
                            phase.getName(),
                            task.getName(),
                            taskResult.getFailureReason(),
                            taskResults,
                            startTime,
                            endTime);
                }
            }

            // All tasks completed successfully
            progressTracker.recordPhaseComplete(phase.getName(), true);
            LocalDateTime endTime = LocalDateTime.now();
            logger.info("Phase completed successfully: {} ({} tasks)", phase.getName(), taskResults.size());

            return PhaseResult.success(phase.getName(), taskResults, startTime, endTime);

        } catch (Exception e) {
            logger.error("Unexpected error executing phase: {}", phase.getName(), e);
            progressTracker.recordPhaseComplete(phase.getName(), false);
            LocalDateTime endTime = LocalDateTime.now();
            return PhaseResult.failed(
                    phase.getName(),
                    "unknown",
                    "Unexpected error: " + e.getMessage(),
                    taskResults,
                    startTime,
                    endTime);
        }
    }

    /**
     * Execute a single phase.
     *
     * @param phase   Phase to execute
     * @param context Migration context
     * @return PhaseResult with execution details
     */
    private PhaseResult executePhase(Phase phase, MigrationContext context) {
        logger.info("----------------------------------------");
        logger.info("Phase: {}", phase.getName());
        logger.info("Description: {}", phase.getDescription());
        logger.info("Tasks: {}", phase.getTasks().size());
        logger.info("----------------------------------------");

        LocalDateTime startTime = LocalDateTime.now();
        List<TaskResult> taskResults = new ArrayList<>();

        try {
            progressTracker.recordPhaseStart(phase.getName());

            // Pass listeners to task executor
            taskExecutor.setListeners(listeners);

            // Execute tasks in the order they are defined
            List<Task> tasks = phase.getTasks();
            logger.info("Executing {} tasks in definition order", tasks.size());

            // Execute each task
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);

                // Check for cancellation
                if (cancelRequested) {
                    logger.warn("Phase cancelled by user");
                    LocalDateTime endTime = LocalDateTime.now();
                    return PhaseResult.failed(
                            phase.getName(),
                            task.getName(),
                            "Cancelled by user",
                            taskResults,
                            startTime,
                            endTime);
                }

                logger.info("Executing task {}/{}: {} ({})",
                        i + 1, tasks.size(), task.getName(), task.getId());

                progressTracker.recordTaskStart(task.getName());
                TaskResult taskResult = taskExecutor.executeTask(task, context);
                taskResults.add(taskResult);
                progressTracker.recordTaskComplete(task.getName(), taskResult.isSuccess());

                // Record block executions
                taskResult.getBlockResults().forEach(
                        blockResult -> progressTracker.recordBlockExecution(blockResult.getMessage(), blockResult));

                if (taskResult.isSuccess()) {
                    logger.info("Task completed successfully: {}", task.getName());
                } else {
                    logger.error("Task failed: {} - {}", task.getName(), taskResult.getFailureReason());

                    // Stop phase execution on task failure
                    progressTracker.recordPhaseComplete(phase.getName(), false);
                    LocalDateTime endTime = LocalDateTime.now();
                    return PhaseResult.failed(
                            phase.getName(),
                            task.getName(),
                            taskResult.getFailureReason(),
                            taskResults,
                            startTime,
                            endTime);
                }
            }

            // All tasks completed successfully
            progressTracker.recordPhaseComplete(phase.getName(), true);
            LocalDateTime endTime = LocalDateTime.now();
            logger.info("Phase completed successfully: {} ({} tasks)", phase.getName(), taskResults.size());

            return PhaseResult.success(phase.getName(), taskResults, startTime, endTime);

        } catch (Exception e) {
            logger.error("Unexpected error executing phase: {}", phase.getName(), e);
            progressTracker.recordPhaseComplete(phase.getName(), false);
            LocalDateTime endTime = LocalDateTime.now();
            return PhaseResult.failed(
                    phase.getName(),
                    "unknown",
                    "Unexpected error: " + e.getMessage(),
                    taskResults,
                    startTime,
                    endTime);
        }
    }

    /**
     * Pause the current execution.
     * Execution will pause at the next phase/task boundary.
     */
    public void pauseExecution() {
        logger.info("Pause requested");
        this.pauseRequested = true;
    }

    /**
     * Resume execution from pause.
     */
    public void resumeExecution() {
        logger.info("Resuming execution");
        this.pauseRequested = false;
    }

    /**
     * Cancel the current execution.
     * Execution will stop at the next phase/task boundary.
     */
    public void cancelExecution() {
        logger.warn("Cancellation requested");
        this.cancelRequested = true;
        this.pauseRequested = false; // Ensure we're not paused
    }

    /**
     * Get current progress information.
     */
    public ProgressInfo getProgress() {
        return progressTracker.getProgress();
    }

    /**
     * Check if execution can be resumed from a checkpoint.
     */
    public boolean canResume() {
        return progressTracker.canResume();
    }

    /**
     * Get last checkpoint for resuming execution.
     */
    public Checkpoint getLastCheckpoint(MigrationContext context) {
        return progressTracker.getLastCheckpoint(context);
    }

    /**
     * Get the progress tracker instance.
     */
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * Reset pause/cancel flags (useful for testing or manual reset).
     */
    public void resetFlags() {
        this.pauseRequested = false;
        this.cancelRequested = false;
    }
}
