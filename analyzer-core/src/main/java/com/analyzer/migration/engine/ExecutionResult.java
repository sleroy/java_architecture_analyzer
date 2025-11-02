package com.analyzer.migration.engine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of executing a migration plan.
 * Contains overall execution status, timing information, and detailed results.
 */
public class ExecutionResult {
    private final boolean success;
    private final String planName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<PhaseResult> phaseResults;
    private final String failureReason;
    private final String failurePhase;

    private ExecutionResult(Builder builder) {
        this.success = builder.success;
        this.planName = builder.planName;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.phaseResults = Collections.unmodifiableList(new ArrayList<>(builder.phaseResults));
        this.failureReason = builder.failureReason;
        this.failurePhase = builder.failurePhase;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getPlanName() {
        return planName;
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

    public List<PhaseResult> getPhaseResults() {
        return phaseResults;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailurePhase() {
        return failurePhase;
    }

    /**
     * Get total number of tasks across all phases
     */
    public int getTotalTasks() {
        return phaseResults.stream()
                .mapToInt(PhaseResult::getTaskCount)
                .sum();
    }

    /**
     * Get number of successful tasks
     */
    public int getSuccessfulTasks() {
        return phaseResults.stream()
                .mapToInt(PhaseResult::getSuccessfulTaskCount)
                .sum();
    }

    /**
     * Get number of failed tasks
     */
    public int getFailedTasks() {
        return phaseResults.stream()
                .mapToInt(PhaseResult::getFailedTaskCount)
                .sum();
    }

    @Override
    public String toString() {
        return String.format(
                "ExecutionResult{success=%s, plan='%s', duration=%s, phases=%d, tasks=%d/%d successful}",
                success, planName, getDuration(), phaseResults.size(), getSuccessfulTasks(), getTotalTasks());
    }

    // Static factory methods

    public static ExecutionResult success(String planName, List<PhaseResult> phaseResults,
            LocalDateTime startTime, LocalDateTime endTime) {
        return new Builder()
                .success(true)
                .planName(planName)
                .startTime(startTime)
                .endTime(endTime)
                .phaseResults(phaseResults)
                .build();
    }

    public static ExecutionResult failed(String planName, String phaseName, String reason,
            List<PhaseResult> phaseResults,
            LocalDateTime startTime, LocalDateTime endTime) {
        return new Builder()
                .success(false)
                .planName(planName)
                .failurePhase(phaseName)
                .failureReason(reason)
                .startTime(startTime)
                .endTime(endTime)
                .phaseResults(phaseResults)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Builder

    public static class Builder {
        private boolean success;
        private String planName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<PhaseResult> phaseResults = new ArrayList<>();
        private String failureReason;
        private String failurePhase;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder planName(String planName) {
            this.planName = planName;
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

        public Builder phaseResults(List<PhaseResult> phaseResults) {
            this.phaseResults = phaseResults;
            return this;
        }

        public Builder addPhaseResult(PhaseResult phaseResult) {
            this.phaseResults.add(phaseResult);
            return this;
        }

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public Builder failurePhase(String failurePhase) {
            this.failurePhase = failurePhase;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }
}
