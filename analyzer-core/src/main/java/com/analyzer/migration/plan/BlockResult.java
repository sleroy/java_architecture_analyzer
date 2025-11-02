package com.analyzer.migration.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of executing a migration block.
 * Contains success/failure status, output data, and any error information.
 */
public class BlockResult {
    private final boolean success;
    private final String message;
    private final Map<String, Object> outputVariables;
    private final List<String> warnings;
    private final String errorDetails;
    private final long executionTimeMs;
    private final boolean skipped;

    private BlockResult(Builder builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.outputVariables = new HashMap<>(builder.outputVariables);
        this.warnings = new ArrayList<>(builder.warnings);
        this.errorDetails = builder.errorDetails;
        this.executionTimeMs = builder.executionTimeMs;
        this.skipped = builder.skipped;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BlockResult success(String message) {
        return new Builder().success(true).message(message).build();
    }

    public static BlockResult failure(String message, String errorDetails) {
        return new Builder().success(false).message(message).errorDetails(errorDetails).build();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getOutputVariables() {
        return new HashMap<>(outputVariables);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public static class Builder {
        private boolean success = true;
        private String message = "";
        private Map<String, Object> outputVariables = new HashMap<>();
        private List<String> warnings = new ArrayList<>();
        private String errorDetails;
        private long executionTimeMs;
        private boolean skipped = false;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder outputVariable(String key, Object value) {
            this.outputVariables.put(key, value);
            return this;
        }

        public Builder outputVariables(Map<String, Object> variables) {
            this.outputVariables.putAll(variables);
            return this;
        }

        public Builder warning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings.addAll(warnings);
            return this;
        }

        public Builder errorDetails(String errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder skipped(boolean skipped) {
            this.skipped = skipped;
            return this;
        }

        public BlockResult build() {
            return new BlockResult(this);
        }
    }
}
