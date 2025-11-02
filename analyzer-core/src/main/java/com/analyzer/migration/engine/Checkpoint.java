package com.analyzer.migration.engine;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a checkpoint in the migration execution from which
 * the process can be resumed if interrupted.
 */
public class Checkpoint {
    private final String planName;
    private final String lastCompletedPhase;
    private final String lastCompletedTask;
    private final String currentPhase;
    private final String currentTask;
    private final Map<String, Object> contextVariables;
    private final LocalDateTime checkpointTime;
    private final boolean valid;

    private Checkpoint(Builder builder) {
        this.planName = builder.planName;
        this.lastCompletedPhase = builder.lastCompletedPhase;
        this.lastCompletedTask = builder.lastCompletedTask;
        this.currentPhase = builder.currentPhase;
        this.currentTask = builder.currentTask;
        this.contextVariables = new HashMap<>(builder.contextVariables);
        this.checkpointTime = builder.checkpointTime;
        this.valid = builder.valid;
    }

    public String getPlanName() {
        return planName;
    }

    public String getLastCompletedPhase() {
        return lastCompletedPhase;
    }

    public String getLastCompletedTask() {
        return lastCompletedTask;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public Map<String, Object> getContextVariables() {
        return new HashMap<>(contextVariables);
    }

    public LocalDateTime getCheckpointTime() {
        return checkpointTime;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Check if this checkpoint can be used to resume execution.
     * A checkpoint is resumable if it's valid and has phase/task information.
     */
    public boolean isResumable() {
        return valid && currentPhase != null;
    }

    @Override
    public String toString() {
        return String.format(
                "Checkpoint{plan='%s', phase='%s', task='%s', time=%s, valid=%s}",
                planName, currentPhase, currentTask, checkpointTime, valid);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String planName;
        private String lastCompletedPhase;
        private String lastCompletedTask;
        private String currentPhase;
        private String currentTask;
        private Map<String, Object> contextVariables = new HashMap<>();
        private LocalDateTime checkpointTime;
        private boolean valid = true;

        public Builder planName(String planName) {
            this.planName = planName;
            return this;
        }

        public Builder lastCompletedPhase(String lastCompletedPhase) {
            this.lastCompletedPhase = lastCompletedPhase;
            return this;
        }

        public Builder lastCompletedTask(String lastCompletedTask) {
            this.lastCompletedTask = lastCompletedTask;
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

        public Builder contextVariables(Map<String, Object> contextVariables) {
            this.contextVariables = contextVariables;
            return this;
        }

        public Builder addContextVariable(String key, Object value) {
            this.contextVariables.put(key, value);
            return this;
        }

        public Builder checkpointTime(LocalDateTime checkpointTime) {
            this.checkpointTime = checkpointTime;
            return this;
        }

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Checkpoint build() {
            if (checkpointTime == null) {
                checkpointTime = LocalDateTime.now();
            }
            return new Checkpoint(this);
        }
    }
}
