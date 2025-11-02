package com.analyzer.migration.state.model;

/**
 * Status of a migration execution (plan, phase, or task).
 */
public enum ExecutionStatus {
    /**
     * Not yet started.
     */
    PENDING,

    /**
     * Currently executing.
     */
    IN_PROGRESS,

    /**
     * Completed successfully.
     */
    SUCCESS,

    /**
     * Failed with error.
     */
    FAILED,

    /**
     * Interrupted before completion.
     */
    INTERRUPTED
}
