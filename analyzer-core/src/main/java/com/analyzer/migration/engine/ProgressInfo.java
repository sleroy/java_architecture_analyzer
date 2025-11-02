package com.analyzer.migration.engine;

import java.time.LocalDateTime;

/**
 * Provides progress information about a migration plan execution.
 * Used for reporting and monitoring purposes.
 */
public class ProgressInfo {
    private final String planName;
    private final String currentPhase;
    private final String currentTask;
    private final int totalPhases;
    private final int completedPhases;
    private final int totalTasks;
    private final int completedTasks;
    private final int failedTasks;
    private final double completionPercentage;
    private final LocalDateTime startTime;
    private final LocalDateTime lastUpdateTime;
    private final String status;

    private ProgressInfo(Builder builder) {
        this.planName = builder.planName;
        this.currentPhase = builder.currentPhase;
        this.currentTask = builder.currentTask;
        this.totalPhases = builder.totalPhases;
        this.completedPhases = builder.completedPhases;
        this.totalTasks = builder.totalTasks;
        this.completedTasks = builder.completedTasks;
        this.failedTasks = builder.failedTasks;
        this.completionPercentage = calculatePercentage(completedTasks, totalTasks);
        this.startTime = builder.startTime;
        this.lastUpdateTime = builder.lastUpdateTime;
        this.status = builder.status;
    }

    private double calculatePercentage(int completed, int total) {
        if (total == 0) {
            return 0.0;
        }
        return (completed * 100.0) / total;
    }

    public String getPlanName() {
        return planName;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public int getTotalPhases() {
        return totalPhases;
    }

    public int getCompletedPhases() {
        return completedPhases;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public int getFailedTasks() {
        return failedTasks;
    }

    public double getCompletionPercentage() {
        return completionPercentage;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    public String getStatus() {
        return status;
    }

    public int getRemainingTasks() {
        return totalTasks - completedTasks;
    }

    @Override
    public String toString() {
        return String.format(
                "ProgressInfo{plan='%s', phase='%s', task='%s', progress=%.1f%% (%d/%d tasks), status=%s}",
                planName, currentPhase, currentTask, completionPercentage, completedTasks, totalTasks, status);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String planName;
        private String currentPhase;
        private String currentTask;
        private int totalPhases;
        private int completedPhases;
        private int totalTasks;
        private int completedTasks;
        private int failedTasks;
        private LocalDateTime startTime;
        private LocalDateTime lastUpdateTime;
        private String status;

        public Builder planName(String planName) {
            this.planName = planName;
            return this;
        }

        public Builder currentPhase(String currentPhase) {
            this.currentPhase = currentPhase;
            return this;
        }

        public Builder currentTask(String currentTask) {
            this.currentTask = currentTask;
            return this;
        }

        public Builder totalPhases(int totalPhases) {
            this.totalPhases = totalPhases;
            return this;
        }

        public Builder completedPhases(int completedPhases) {
            this.completedPhases = completedPhases;
            return this;
        }

        public Builder totalTasks(int totalTasks) {
            this.totalTasks = totalTasks;
            return this;
        }

        public Builder completedTasks(int completedTasks) {
            this.completedTasks = completedTasks;
            return this;
        }

        public Builder failedTasks(int failedTasks) {
            this.failedTasks = failedTasks;
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder lastUpdateTime(LocalDateTime lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public ProgressInfo build() {
            return new ProgressInfo(this);
        }
    }
}
