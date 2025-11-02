package com.analyzer.migration.engine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of executing a migration phase.
 * Contains phase execution status, timing, and task results.
 */
public class PhaseResult {
    private final boolean success;
    private final String phaseName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<TaskResult> taskResults;
    private final String failureReason;
    private final String failureTask;

    private PhaseResult(Builder builder) {
        this.success = builder.success;
        this.phaseName = builder.phaseName;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.taskResults = Collections.unmodifiableList(new ArrayList<>(builder.taskResults));
        this.failureReason = builder.failureReason;
        this.failureTask = builder.failureTask;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    public List<TaskResult> getTaskResults() {
        return taskResults;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailureTask() {
        return failureTask;
    }

    /**
     * Get total number of tasks in this phase
     */
    public int getTaskCount() {
        return taskResults.size();
    }

    /**
     * Get number of successful tasks
     */
    public int getSuccessfulTaskCount() {
        return (int) taskResults.stream()
                .filter(TaskResult::isSuccess)
                .count();
    }

    /**
     * Get number of failed tasks
     */
    public int getFailedTaskCount() {
        return (int) taskResults.stream()
                .filter(result -> !result.isSuccess())
                .count();
    }

    @Override
    public String toString() {
        return String.format(
                "PhaseResult{success=%s, phase='%s', duration=%s, tasks=%d/%d successful}",
                success, phaseName, getDuration(), getSuccessfulTaskCount(), getTaskCount());
    }

    // Static factory methods

    public static PhaseResult success(String phaseName, List<TaskResult> taskResults,
            LocalDateTime startTime, LocalDateTime endTime) {
        return new Builder()
                .success(true)
                .phaseName(phaseName)
                .startTime(startTime)
                .endTime(endTime)
                .taskResults(taskResults)
                .build();
    }

    public static PhaseResult failed(String phaseName, String taskName, String reason,
            List<TaskResult> taskResults,
            LocalDateTime startTime, LocalDateTime endTime) {
        return new Builder()
                .success(false)
                .phaseName(phaseName)
                .failureTask(taskName)
                .failureReason(reason)
                .startTime(startTime)
                .endTime(endTime)
                .taskResults(taskResults)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Builder

    public static class Builder {
        private boolean success;
        private String phaseName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<TaskResult> taskResults = new ArrayList<>();
        private String failureReason;
        private String failureTask;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder phaseName(String phaseName) {
            this.phaseName = phaseName;
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder taskResults(List<TaskResult> taskResults) {
            this.taskResults = taskResults;
            return this;
        }

        public Builder addTaskResult(TaskResult taskResult) {
            this.taskResults.add(taskResult);
            return this;
        }

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public Builder failureTask(String failureTask) {
            this.failureTask = failureTask;
            return this;
        }

        public PhaseResult build() {
            return new PhaseResult(this);
        }
    }
}
