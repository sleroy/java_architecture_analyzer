package com.analyzer.api.analysis;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of an analysis execution.
 * Indicates success/failure and provides access to any data produced.
 */
public class AnalysisResult {

    private final boolean successful;
    private final String message;
    private final Map<String, Object> data;
    private final long executionTimeMs;
    private final Date timestamp;

    private AnalysisResult(boolean successful, String message, Map<String, Object> data, long executionTimeMs) {
        this.successful = successful;
        this.message = message;
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
        this.executionTimeMs = executionTimeMs;
        this.timestamp = new Date();
    }

    /**
     * Create a successful analysis result
     */
    public static AnalysisResult success() {
        return new AnalysisResult(true, "Analysis completed successfully", null, 0);
    }

    /**
     * Create a successful analysis result with a custom message
     */
    public static AnalysisResult success(String message) {
        return new AnalysisResult(true, message, null, 0);
    }

    /**
     * Create a successful analysis result with data
     */
    public static AnalysisResult success(String message, Map<String, Object> data) {
        return new AnalysisResult(true, message, data, 0);
    }

    /**
     * Create a successful analysis result with data and execution time
     */
    public static AnalysisResult success(String message, Map<String, Object> data, long executionTimeMs) {
        return new AnalysisResult(true, message, data, executionTimeMs);
    }

    /**
     * Create a failed analysis result
     */
    public static AnalysisResult failure(String message) {
        return new AnalysisResult(false, message, null, 0);
    }

    /**
     * Create a failed analysis result with execution time
     */
    public static AnalysisResult failure(String message, long executionTimeMs) {
        return new AnalysisResult(false, message, null, executionTimeMs);
    }

    /**
     * Create a failed analysis result from an exception
     */
    public static AnalysisResult failure(String message, Throwable cause) {
        String fullMessage = message + ": " + cause.getMessage();
        return new AnalysisResult(false, fullMessage, null, 0);
    }

    /**
     * Check if the analysis was successful
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * Check if the analysis failed
     */
    public boolean isFailed() {
        return !successful;
    }

    /**
     * Get the result message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get analysis data (read-only)
     */
    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    /**
     * Get a specific data value
     */
    public Object getData(String key) {
        return data.get(key);
    }

    /**
     * Get a specific data value with default
     */
    public Object getData(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }

    /**
     * Check if result contains specific data
     */
    public boolean hasData(String key) {
        return data.containsKey(key);
    }

    /**
     * Get execution time in milliseconds
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * Get timestamp when result was created
     */
    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }

    @Override
    public String toString() {
        return "AnalysisResult{" +
                "successful=" + successful +
                ", message='" + message + '\'' +
                ", dataKeys=" + data.keySet() +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}
