package com.analyzer.migration.engine;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.migration.plan.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Scanner;

/**
 * Executes migration tasks with dependency resolution.
 * Handles task ordering using topological sort and validates blocks before
 * execution.
 */
public class TaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);

    private List<MigrationExecutionListener> listeners = new ArrayList<>();

    /**
     * Set listeners for this executor (called by MigrationEngine).
     *
     * @param listeners List of listeners
     */
    void setListeners(List<MigrationExecutionListener> listeners) {
        this.listeners = listeners != null ? listeners : new ArrayList<>();
    }

    /**
     * Return tasks in the order they are defined.
     * Tasks will execute sequentially in the order specified in the migration plan.
     *
     * @param tasks List of tasks to execute
     * @return Tasks in the order they were provided
     */
    public List<Task> resolveDependencies(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        logger.debug("Tasks will execute in definition order: {} tasks", tasks.size());
        return new ArrayList<>(tasks);
    }

    /**
     * Execute a single task by running all its blocks in sequence.
     *
     * @param task    Task to execute
     * @param context Migration context with variables
     * @return TaskResult with execution details
     */
    public TaskResult executeTask(Task task, MigrationContext context) {
        logger.info("Executing task: {}", task.getName());
        LocalDateTime startTime = LocalDateTime.now();
        List<BlockResult> blockResults = new ArrayList<>();

        fireTaskStartEvent(task, context);

        try {
            TaskResult result = executeAllBlocks(task, context, blockResults, startTime);
            if (result != null) {
                return result; // Early termination due to failure
            }

            return handleTaskCompletion(task, blockResults, startTime);

        } catch (Exception e) {
            return handleTaskException(task, e, blockResults, startTime);
        }
    }

    /**
     * Execute all blocks in a task sequentially.
     *
     * @param task         Task containing blocks to execute
     * @param context      Migration context
     * @param blockResults List to collect block results
     * @param startTime    Task start time for failure reporting
     * @return TaskResult if early termination is needed, null if all blocks
     *         completed
     */
    private TaskResult executeAllBlocks(Task task, MigrationContext context, List<BlockResult> blockResults,
            LocalDateTime startTime) {
        List<MigrationBlock> blocks = task.getBlocks();

        for (int i = 0; i < blocks.size(); i++) {
            MigrationBlock block = blocks.get(i);
            logBlockExecution(i + 1, blocks.size(), block);

            fireBlockStartEvent(block, context);

            // Handle conditional block execution
            if (!block.isEnabled(context)) {
                handleSkippedBlock(block, blockResults);
                continue;
            }

            // Handle step-by-step mode
            promptUserInStepByStepMode(context, block);

            // Validate block before execution
            TaskResult validationResult = validateBlockExecution(task, block, blockResults, startTime);
            if (validationResult != null) {
                return validationResult; // Validation failed
            }

            // Execute the block
            BlockResult result = executeBlock(block, context);
            blockResults.add(result);

            fireBlockCompleteEvent(block, result);
            updateContextWithOutputVariables(context, result);

            // Handle block execution failure
            TaskResult failureResult = handleBlockFailure(task, block, result, blockResults, startTime);
            if (failureResult != null) {
                return failureResult; // Block failure with stop-on-failure
            }

            logBlockCompletion(block, result);
        }

        return null; // All blocks completed successfully
    }

    /**
     * Handle task completion and listener validation.
     */
    private TaskResult handleTaskCompletion(Task task, List<BlockResult> blockResults, LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        logger.info("Task completed successfully: {} ({} blocks)", task.getName(), blockResults.size());

        TaskResult taskResult = TaskResult.success(task.getName(), blockResults, startTime, endTime);

        if (!fireTaskCompleteEventAndCheckContinuation(task, taskResult)) {
            logger.warn("Task execution stopped by listener");
            return createFailedTaskResult(task.getName(), "listener-stop", "Stopped by listener",
                    blockResults, startTime, endTime);
        }

        return taskResult;
    }

    /**
     * Handle unexpected exceptions during task execution.
     */
    private TaskResult handleTaskException(Task task, Exception e, List<BlockResult> blockResults,
            LocalDateTime startTime) {
        logger.error("Unexpected error executing task: {}", task.getName(), e);
        LocalDateTime endTime = LocalDateTime.now();

        TaskResult taskResult = createFailedTaskResult(
                task.getName(),
                "unknown",
                "Unexpected error: " + e.getMessage(),
                blockResults,
                startTime,
                endTime);

        fireTaskCompleteEvent(task, taskResult);
        return taskResult;
    }

    /**
     * Log block execution progress.
     */
    private void logBlockExecution(int blockIndex, int totalBlocks, MigrationBlock block) {
        logger.debug("Executing block {}/{}: {} ({})",
                blockIndex, totalBlocks, block.getName(), block.getType());
    }

    /**
     * Handle a skipped block due to enable_if condition.
     */
    private void handleSkippedBlock(MigrationBlock block, List<BlockResult> blockResults) {
        String enableIfCondition = block.getEnableIf();
        logger.info("Skipping block (condition not met): {} - enable_if: {}",
                block.getName(), enableIfCondition);

        BlockResult skippedResult = createSkippedBlockResult(enableIfCondition);
        blockResults.add(skippedResult);
        fireBlockCompleteEvent(block, skippedResult);
    }

    /**
     * Prompt user to continue in step-by-step mode.
     */
    private void promptUserInStepByStepMode(MigrationContext context, MigrationBlock block) {
        if (context.isStepByStepMode() && !context.isDryRun()) {
            System.out.println("\n[STEP-BY-STEP] Press Enter to execute next block: " +
                    block.getName() + " (" + block.getType() + ")");
            try {
                new Scanner(System.in).nextLine();
            } catch (Exception e) {
                logger.warn("Failed to read user input, continuing execution");
            }
        }
    }

    /**
     * Validate block before execution.
     *
     * @return TaskResult if validation failed and task should terminate, null if
     *         validation passed
     */
    private TaskResult validateBlockExecution(Task task, MigrationBlock block, List<BlockResult> blockResults,
            LocalDateTime startTime) {
        if (!block.validate()) {
            String errorMsg = String.format("Block validation failed: %s", block.getName());
            logger.error(errorMsg);

            BlockResult failureResult = createFailedBlockResult(errorMsg);
            blockResults.add(failureResult);

            LocalDateTime endTime = LocalDateTime.now();
            return createFailedTaskResult(task.getName(), block.getName(), "Validation failed",
                    blockResults, startTime, endTime);
        }
        return null;
    }

    /**
     * Execute a single block (dry-run or normal execution).
     */
    private BlockResult executeBlock(MigrationBlock block, MigrationContext context) {
        if (context.isDryRun()) {
            logger.info("[DRY-RUN] Simulating block: {} ({})", block.getName(), block.getType());
            return simulateBlockExecution(block);
        } else {
            return block.execute(context);
        }
    }

    /**
     * Update migration context with output variables from block result.
     */
    private void updateContextWithOutputVariables(MigrationContext context, BlockResult result) {
        if (result.getOutputVariables() != null && !result.getOutputVariables().isEmpty()) {
            context.setVariables(result.getOutputVariables());
            logger.debug("Updated context with {} output variables", result.getOutputVariables().size());
        }
    }

    /**
     * Handle block execution failure.
     *
     * @return TaskResult if task should terminate due to failure, null if task
     *         should continue
     */
    private TaskResult handleBlockFailure(Task task, MigrationBlock block, BlockResult result,
            List<BlockResult> blockResults, LocalDateTime startTime) {
        if (!result.isSuccess()) {
            logger.error("Block execution failed: {} - {}", block.getName(), result.getMessage());

            if (shouldStopOnBlockFailure(task)) {
                LocalDateTime endTime = LocalDateTime.now();
                return createFailedTaskResult(task.getName(), block.getName(), result.getMessage(),
                        blockResults, startTime, endTime);
            } else {
                logger.warn("Continuing task execution despite block failure (stopOnBlockFailure=false)");
            }
        }
        return null;
    }

    /**
     * Log block completion.
     */
    private void logBlockCompletion(MigrationBlock block, BlockResult result) {
        logger.debug("Block completed: {} ({}ms)", block.getName(), result.getExecutionTimeMs());
    }

    // ========== Listener Event Methods ==========

    /**
     * Fire task start event to all listeners.
     */
    private void fireTaskStartEvent(Task task, MigrationContext context) {
        for (MigrationExecutionListener listener : listeners) {
            listener.onTaskStart(task, context);
        }
    }

    /**
     * Fire block start event to all listeners.
     */
    private void fireBlockStartEvent(MigrationBlock block, MigrationContext context) {
        for (MigrationExecutionListener listener : listeners) {
            listener.onBlockStart(block, context);
        }
    }

    /**
     * Fire block complete event to all listeners.
     */
    private void fireBlockCompleteEvent(MigrationBlock block, BlockResult result) {
        for (MigrationExecutionListener listener : listeners) {
            listener.onBlockComplete(block, result);
        }
    }

    /**
     * Fire task complete event to all listeners.
     */
    private void fireTaskCompleteEvent(Task task, TaskResult result) {
        for (MigrationExecutionListener listener : listeners) {
            listener.onTaskComplete(task, result);
        }
    }

    /**
     * Fire task complete event and check if execution should continue.
     *
     * @return true if task execution should continue, false if stopped by listener
     */
    private boolean fireTaskCompleteEventAndCheckContinuation(Task task, TaskResult result) {
        for (MigrationExecutionListener listener : listeners) {
            if (!listener.onTaskComplete(task, result)) {
                return false;
            }
        }
        return true;
    }

    // ========== Result Creation Methods ==========

    /**
     * Create a skipped block result.
     */
    private BlockResult createSkippedBlockResult(String enableIfCondition) {
        return BlockResult.builder()
                .success(true)
                .message("Skipped - condition not met: " + enableIfCondition)
                .skipped(true)
                .executionTimeMs(0)
                .build();
    }

    /**
     * Create a failed block result.
     */
    private BlockResult createFailedBlockResult(String errorMessage) {
        return BlockResult.builder()
                .success(false)
                .message(errorMessage)
                .executionTimeMs(0)
                .build();
    }

    /**
     * Create a failed task result.
     */
    private TaskResult createFailedTaskResult(String taskName, String failedBlockName, String message,
            List<BlockResult> blockResults, LocalDateTime startTime, LocalDateTime endTime) {
        return TaskResult.failed(taskName, failedBlockName, message, blockResults, startTime, endTime);
    }

    /**
     * Determine if task should stop on block failure.
     * Can be extended to read from task properties.
     */
    private boolean shouldStopOnBlockFailure(Task task) {
        // Default behavior: stop on first block failure
        // This could be made configurable per task
        return true;
    }

    /**
     * Validate all blocks in a task before execution.
     *
     * @param task Task to validate
     * @return true if all blocks are valid
     */
    public boolean validateTask(Task task) {
        if (task.getBlocks().isEmpty()) {
            logger.warn("Task {} has no blocks", task.getName());
            return false;
        }

        for (MigrationBlock block : task.getBlocks()) {
            if (!block.validate()) {
                logger.error("Block validation failed in task {}: {}",
                        task.getName(), block.getName());
                return false;
            }
        }

        return true;
    }

    /**
     * Simulate block execution for dry-run mode.
     * Returns a successful BlockResult without actually executing the block.
     *
     * @param block Block to simulate
     * @return Simulated BlockResult
     */
    private BlockResult simulateBlockExecution(MigrationBlock block) {
        return BlockResult.builder()
                .success(true)
                .message(String.format("[DRY-RUN] Would execute %s block: %s",
                        block.getType(), block.getName()))
                .executionTimeMs(0)
                .build();
    }
}
