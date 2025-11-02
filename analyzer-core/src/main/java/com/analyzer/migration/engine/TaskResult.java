package com.analyzer.migration.engine;

import com.analyzer.migration.plan.BlockResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of executing a migration task.
 * Contains task execution status, timing, and block results.
 */
public class TaskResult {
    private final boolean success;
    private final String taskName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<BlockResult> blockResults;
    private final String failureReason;
    private final String failureBlock;

    private TaskResult(Builder builder) {
        this.success = builder.success;
        this.taskName = builder.taskName;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.blockResults = Collections.unmodifiableList(new ArrayList<>(builder.blockResults));
        this.failureReason = builder.failureReason;
        this.failureBlock = builder.failureBlock;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getTaskName() {
        return taskName;
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

    public List<BlockResult> getBlockResults() {
        return blockResults;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailureBlock() {
        return failureBlock;
    }

    /**
     * Get total number of blocks in this task
     */
    public int getBlockCount() {
        return blockResults.size();
    }

    /**
     * Get number of successful blocks
     */
    public int getSuccessfulBlockCount() {
        return (int) blockResults.stream()
                .filter(BlockResult::isSuccess)
                .count();
    }

    /**
     * Get number of failed blocks
     */
    public int getFailedBlockCount() {
        return (int) blockResults.stream()
                .filter(result -> !result.isSuccess())
                .count();
    }

    /**
     * Get total execution time of all blocks in milliseconds
     */
    public long getTotalExecutionTimeMs() {
        return blockResults.stream()
                .mapToLong(BlockResult::getExecutionTimeMs)
                .sum();
    }

    @Override
    public String toString() {
        return String.format(
                "TaskResult{success=%s, task='%s', duration=%s, blocks=%d/%d successful}",
                success, taskName, getDuration(), getSuccessfulBlockCount(), getBlockCount());
    }

    // Static factory methods

    public static TaskResult success(String taskName, List<BlockResult> blockResults,
            LocalDateTime startTime, LocalDateTime endTime) {
        return new Builder()
                .success(true)
                .taskName(taskName)
                .startTime(startTime)
                .endTime(endTime)
                .blockResults(blockResults)
                .build();
    }

    public static TaskResult failed(String taskName, String blockName, String reason,
            List<BlockResult> blockResults,
            LocalDateTime startTime, LocalDateTime endTime) {
        return new Builder()
                .success(false)
                .taskName(taskName)
                .failureBlock(blockName)
                .failureReason(reason)
                .startTime(startTime)
                .endTime(endTime)
                .blockResults(blockResults)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Builder

    public static class Builder {
        private boolean success;
        private String taskName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<BlockResult> blockResults = new ArrayList<>();
        private String failureReason;
        private String failureBlock;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder taskName(String taskName) {
            this.taskName = taskName;
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

        public Builder blockResults(List<BlockResult> blockResults) {
            this.blockResults = blockResults;
            return this;
        }

        public Builder addBlockResult(BlockResult blockResult) {
            this.blockResults.add(blockResult);
            return this;
        }

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public Builder failureBlock(String failureBlock) {
            this.failureBlock = failureBlock;
            return this;
        }

        public TaskResult build() {
            return new TaskResult(this);
        }
    }
}
