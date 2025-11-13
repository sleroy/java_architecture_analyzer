package com.analyzer.refactoring.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Metrics for a single searchJavaPattern tool call.
 * Tracks request parameters, execution results, and impact metrics.
 */
public class CallMetrics {

    private final String callId;
    private final Instant timestamp;
    private final String patternDescription;
    private final String nodeType;
    private final String projectPath;
    private final List<String> filePaths;
    private final boolean success;
    private final boolean scriptGenerated;
    private final boolean cacheHit;
    private final int matchesFound;
    private final long executionTimeMs;
    private final int generationAttempts;
    private final String errorMessage;
    private final int filesAnalyzed;
    private final long linesScanned;
    private final Integer tokensUsed;
    private final Double estimatedCost;

    @JsonCreator
    public CallMetrics(
            @JsonProperty("callId") String callId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("patternDescription") String patternDescription,
            @JsonProperty("nodeType") String nodeType,
            @JsonProperty("projectPath") String projectPath,
            @JsonProperty("filePaths") List<String> filePaths,
            @JsonProperty("success") boolean success,
            @JsonProperty("scriptGenerated") boolean scriptGenerated,
            @JsonProperty("cacheHit") boolean cacheHit,
            @JsonProperty("matchesFound") int matchesFound,
            @JsonProperty("executionTimeMs") long executionTimeMs,
            @JsonProperty("generationAttempts") int generationAttempts,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("filesAnalyzed") int filesAnalyzed,
            @JsonProperty("linesScanned") long linesScanned,
            @JsonProperty("tokensUsed") Integer tokensUsed,
            @JsonProperty("estimatedCost") Double estimatedCost) {

        this.callId = callId;
        this.timestamp = timestamp;
        this.patternDescription = patternDescription;
        this.nodeType = nodeType;
        this.projectPath = projectPath;
        this.filePaths = filePaths;
        this.success = success;
        this.scriptGenerated = scriptGenerated;
        this.cacheHit = cacheHit;
        this.matchesFound = matchesFound;
        this.executionTimeMs = executionTimeMs;
        this.generationAttempts = generationAttempts;
        this.errorMessage = errorMessage;
        this.filesAnalyzed = filesAnalyzed;
        this.linesScanned = linesScanned;
        this.tokensUsed = tokensUsed;
        this.estimatedCost = estimatedCost;
    }

    /**
     * Builder for CallMetrics.
     */
    public static class Builder {
        private String callId = UUID.randomUUID().toString();
        private Instant timestamp = Instant.now();
        private String patternDescription;
        private String nodeType;
        private String projectPath;
        private List<String> filePaths;
        private boolean success;
        private boolean scriptGenerated;
        private boolean cacheHit;
        private int matchesFound;
        private long executionTimeMs;
        private int generationAttempts;
        private String errorMessage;
        private int filesAnalyzed;
        private long linesScanned;
        private Integer tokensUsed;
        private Double estimatedCost;

        public Builder patternDescription(String patternDescription) {
            this.patternDescription = patternDescription;
            return this;
        }

        public Builder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder projectPath(String projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder filePaths(List<String> filePaths) {
            this.filePaths = filePaths;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder scriptGenerated(boolean scriptGenerated) {
            this.scriptGenerated = scriptGenerated;
            return this;
        }

        public Builder cacheHit(boolean cacheHit) {
            this.cacheHit = cacheHit;
            return this;
        }

        public Builder matchesFound(int matchesFound) {
            this.matchesFound = matchesFound;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder generationAttempts(int generationAttempts) {
            this.generationAttempts = generationAttempts;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder filesAnalyzed(int filesAnalyzed) {
            this.filesAnalyzed = filesAnalyzed;
            return this;
        }

        public Builder linesScanned(long linesScanned) {
            this.linesScanned = linesScanned;
            return this;
        }

        public Builder tokensUsed(Integer tokensUsed) {
            this.tokensUsed = tokensUsed;
            return this;
        }

        public Builder estimatedCost(Double estimatedCost) {
            this.estimatedCost = estimatedCost;
            return this;
        }

        public CallMetrics build() {
            return new CallMetrics(
                    callId,
                    timestamp,
                    patternDescription,
                    nodeType,
                    projectPath,
                    filePaths,
                    success,
                    scriptGenerated,
                    cacheHit,
                    matchesFound,
                    executionTimeMs,
                    generationAttempts,
                    errorMessage,
                    filesAnalyzed,
                    linesScanned,
                    tokensUsed,
                    estimatedCost);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getCallId() {
        return callId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getPatternDescription() {
        return patternDescription;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public List<String> getFilePaths() {
        return filePaths;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isScriptGenerated() {
        return scriptGenerated;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public int getMatchesFound() {
        return matchesFound;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public int getGenerationAttempts() {
        return generationAttempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getFilesAnalyzed() {
        return filesAnalyzed;
    }

    public long getLinesScanned() {
        return linesScanned;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public Double getEstimatedCost() {
        return estimatedCost;
    }
}
