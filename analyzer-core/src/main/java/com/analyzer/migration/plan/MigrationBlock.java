package com.analyzer.migration.plan;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.expression.ExpressionEvaluator;

/**
 * Base interface for all migration blocks.
 * Each block represents a single executable step in the migration process.
 */
public interface MigrationBlock {
    /**
     * Execute this block with the given migration context.
     *
     * @param context The migration context containing variables and project info
     * @return Result of the block execution
     */
    BlockResult execute(MigrationContext context);

    /**
     * Get the type of this block
     */
    BlockType getType();

    /**
     * Get a human-readable name for this block
     */
    String getName();

    /**
     * Get the conditional expression (enable_if) for this block.
     * If null or empty, the block is always enabled.
     *
     * @return The enable_if expression, or null if always enabled
     */
    default String getEnableIf() {
        return null;
    }

    /**
     * Check if this block is enabled based on its enable_if condition.
     * If no condition is specified (getEnableIf() returns null or empty),
     * the block is always enabled.
     *
     * @param context The migration context with variables for evaluation
     * @return true if block should execute, false if it should be skipped
     */
    default boolean isEnabled(MigrationContext context) {
        String condition = getEnableIf();
        if (condition == null || condition.trim().isEmpty()) {
            return true; // No condition = always enabled
        }

        try {
            return ExpressionEvaluator.evaluate(condition, context);
        } catch (ExpressionEvaluator.ExpressionEvaluationException e) {
            // On evaluation error, log and default to disabled for safety
            return false;
        }
    }

    /**
     * Generate Markdown documentation for this block.
     * Used to create documentation matching the migration plan format.
     *
     * @return Markdown description of this block
     */
    String toMarkdownDescription();

    /**
     * Validate this block's configuration before execution.
     * Checks for required parameters, valid paths, etc.
     *
     * @return true if block is valid and ready to execute
     */
    default boolean validate() {
        return true;
    }

    /**
     * Get any dependencies or prerequisites for this block.
     * Returns variable names that must be set in context before execution.
     *
     * @return Array of required variable names (empty if none)
     */
    default String[] getRequiredVariables() {
        return new String[0];
    }
}
