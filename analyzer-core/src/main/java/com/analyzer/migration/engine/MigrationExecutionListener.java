package com.analyzer.migration.engine;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;

/**
 * Listener interface for migration execution lifecycle events.
 * Allows custom behavior and extensions without modifying core execution
 * engine.
 * 
 * <p>
 * Use cases:
 * <ul>
 * <li>Custom progress reporting (console, UI, web)</li>
 * <li>Metrics collection and monitoring</li>
 * <li>External system integration (JIRA, Slack, email)</li>
 * <li>Approval workflows and gates</li>
 * <li>Conditional execution logic</li>
 * <li>Audit logging</li>
 * </ul>
 * 
 * <p>
 * All methods have default implementations that do nothing, allowing
 * listeners to implement only the events they care about.
 */
public interface MigrationExecutionListener {

    /**
     * Called when migration plan execution starts.
     * 
     * @param plan    Migration plan being executed
     * @param context Execution context with variables
     */
    default void onPlanStart(MigrationPlan plan, MigrationContext context) {
    }

    /**
     * Called when migration plan execution completes (success or failure).
     * 
     * @param plan   Migration plan that was executed
     * @param result Final execution result
     */
    default void onPlanComplete(MigrationPlan plan, ExecutionResult result) {
    }

    /**
     * Called when a phase execution starts.
     * 
     * @param phase   Phase being executed
     * @param context Execution context
     */
    default void onPhaseStart(Phase phase, MigrationContext context) {
    }

    /**
     * Called when a phase execution completes.
     * 
     * <p>
     * Listeners can return false to stop execution of remaining phases.
     * This is useful for implementing approval gates or conditional execution.
     * 
     * @param phase  Phase that completed
     * @param result Phase execution result
     * @return true to continue with remaining phases, false to stop execution
     */
    default boolean onPhaseComplete(Phase phase, PhaseResult result) {
        return true; // Continue by default
    }

    /**
     * Called when a task execution starts.
     * 
     * @param task    Task being executed
     * @param context Execution context
     */
    default void onTaskStart(Task task, MigrationContext context) {
    }

    /**
     * Called when a task execution completes.
     * 
     * <p>
     * Listeners can return false to stop execution of remaining tasks in the phase.
     * 
     * @param task   Task that completed
     * @param result Task execution result
     * @return true to continue with remaining tasks, false to stop execution
     */
    default boolean onTaskComplete(Task task, TaskResult result) {
        return true; // Continue by default
    }

    /**
     * Called when a block execution starts.
     * 
     * @param block   Block being executed
     * @param context Execution context
     */
    default void onBlockStart(MigrationBlock block, MigrationContext context) {
    }

    /**
     * Called when a block execution completes.
     * 
     * @param block  Block that completed
     * @param result Block execution result
     */
    default void onBlockComplete(MigrationBlock block, BlockResult result) {
    }
}
