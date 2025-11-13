package com.analyzer.refactoring.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Metadata for a persisted Groovy visitor script.
 * Tracks generation details, usage statistics, and validation status.
 */
public class ScriptMetadata {

    private final String scriptHash;
    private final String patternDescription;
    private final String nodeType;
    private final String projectPath;
    private final List<String> filePaths;
    private final Instant generatedAt;
    private final int generationAttempts;
    private final boolean validated;
    private int usageCount;
    private Instant lastUsed;
    private int successCount;
    private int failureCount;

    @JsonCreator
    public ScriptMetadata(
            @JsonProperty("scriptHash") String scriptHash,
            @JsonProperty("patternDescription") String patternDescription,
            @JsonProperty("nodeType") String nodeType,
            @JsonProperty("projectPath") String projectPath,
            @JsonProperty("filePaths") List<String> filePaths,
            @JsonProperty("generatedAt") Instant generatedAt,
            @JsonProperty("generationAttempts") int generationAttempts,
            @JsonProperty("validated") boolean validated,
            @JsonProperty("usageCount") int usageCount,
            @JsonProperty("lastUsed") Instant lastUsed,
            @JsonProperty("successCount") int successCount,
            @JsonProperty("failureCount") int failureCount) {

        this.scriptHash = scriptHash;
        this.patternDescription = patternDescription;
        this.nodeType = nodeType;
        this.projectPath = projectPath;
        this.filePaths = filePaths;
        this.generatedAt = generatedAt;
        this.generationAttempts = generationAttempts;
        this.validated = validated;
        this.usageCount = usageCount;
        this.lastUsed = lastUsed;
        this.successCount = successCount;
        this.failureCount = failureCount;
    }

    /**
     * Create new metadata for a freshly generated script.
     */
    public static ScriptMetadata create(
            String scriptHash,
            String patternDescription,
            String nodeType,
            String projectPath,
            List<String> filePaths,
            int generationAttempts,
            boolean validated) {

        return new ScriptMetadata(
                scriptHash,
                patternDescription,
                nodeType,
                projectPath,
                filePaths,
                Instant.now(),
                generationAttempts,
                validated,
                0, // initial usage count
                null, // not used yet
                0, // initial success count
                0 // initial failure count
        );
    }

    /**
     * Record a successful execution of this script.
     */
    public void recordSuccess() {
        this.usageCount++;
        this.successCount++;
        this.lastUsed = Instant.now();
    }

    /**
     * Record a failed execution of this script.
     */
    public void recordFailure() {
        this.usageCount++;
        this.failureCount++;
        this.lastUsed = Instant.now();
    }

    // Getters

    public String getScriptHash() {
        return scriptHash;
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

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public int getGenerationAttempts() {
        return generationAttempts;
    }

    public boolean isValidated() {
        return validated;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    /**
     * Calculate success rate percentage.
     */
    public double getSuccessRate() {
        if (usageCount == 0) {
            return 0.0;
        }
        return (double) successCount / usageCount * 100.0;
    }
}
