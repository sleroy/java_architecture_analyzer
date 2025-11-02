package com.analyzer.migration.state.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Record of a phase execution with task details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhaseExecutionRecord {
    private Instant timestamp;
    private String phaseId;
    private String phaseName;
    private ExecutionStatus status;
    private Long durationMs;
    private Integer tasksCompleted;
    private Integer tasksFailed;
    private String summary;
    private List<TaskExecutionDetail> taskDetails;

    public PhaseExecutionRecord() {
        this.taskDetails = new ArrayList<>();
    }

    public PhaseExecutionRecord(String phaseId, String phaseName) {
        this.phaseId = phaseId;
        this.phaseName = phaseName;
        this.timestamp = Instant.now();
        this.status = ExecutionStatus.PENDING;
        this.taskDetails = new ArrayList<>();
        this.tasksCompleted = 0;
        this.tasksFailed = 0;
    }

    // Getters and setters

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public void setPhaseId(String phaseId) {
        this.phaseId = phaseId;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public void setPhaseName(String phaseName) {
        this.phaseName = phaseName;
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

    public Integer getTasksCompleted() {
        return tasksCompleted;
    }

    public void setTasksCompleted(Integer tasksCompleted) {
        this.tasksCompleted = tasksCompleted;
    }

    public Integer getTasksFailed() {
        return tasksFailed;
    }

    public void setTasksFailed(Integer tasksFailed) {
        this.tasksFailed = tasksFailed;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<TaskExecutionDetail> getTaskDetails() {
        return taskDetails;
    }

    public void setTaskDetails(List<TaskExecutionDetail> taskDetails) {
        this.taskDetails = taskDetails;
    }

    public void addTaskDetail(TaskExecutionDetail taskDetail) {
        if (this.taskDetails == null) {
            this.taskDetails = new ArrayList<>();
        }
        this.taskDetails.add(taskDetail);
    }
}
