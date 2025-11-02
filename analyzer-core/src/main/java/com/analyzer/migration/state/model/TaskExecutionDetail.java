package com.analyzer.migration.state.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Detailed execution information for a single task.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskExecutionDetail {
    private String taskId;
    private String taskName;
    private ExecutionStatus status;
    private Long durationMs;
    private String outputSummary;
    private String outputDetail; // Only populated with --verbose
    private String errorMessage;

    public TaskExecutionDetail() {
    }

    public TaskExecutionDetail(String taskId, String taskName) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.status = ExecutionStatus.PENDING;
    }

    // Getters and setters

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public String getOutputDetail() {
        return outputDetail;
    }

    public void setOutputDetail(String outputDetail) {
        this.outputDetail = outputDetail;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
