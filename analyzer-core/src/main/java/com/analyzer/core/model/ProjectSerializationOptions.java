package com.analyzer.core.model;

/**
 * Configuration options for project serialization and deserialization.
 * Provides settings for compression, streaming, and performance optimization.
 */
public class ProjectSerializationOptions {

    private boolean compressionEnabled = false;
    private boolean streamingMode = false;
    private int bufferSize = 8192; // 8KB default buffer
    private boolean prettyPrint = true;
    private boolean includeTimestamps = true;
    private boolean includeGraphData = true;
    private boolean validateOnLoad = true;

    // Factory methods for common configurations

    /**
     * Default configuration - pretty printed JSON with timestamps and graph data.
     */
    public static ProjectSerializationOptions defaults() {
        return new ProjectSerializationOptions();
    }

    /**
     * Compressed configuration - enables gzip compression for large projects.
     */
    public static ProjectSerializationOptions compressed() {
        ProjectSerializationOptions options = new ProjectSerializationOptions();
        options.compressionEnabled = true;
        options.prettyPrint = false; // Save space
        return options;
    }

    /**
     * Streaming configuration - optimized for large datasets with minimal memory
     * usage.
     */
    public static ProjectSerializationOptions streaming() {
        ProjectSerializationOptions options = new ProjectSerializationOptions();
        options.streamingMode = true;
        options.bufferSize = 16384; // Larger buffer for streaming
        options.prettyPrint = false; // Save memory
        return options;
    }

    /**
     * Minimal configuration - excludes optional data for smaller file size.
     */
    public static ProjectSerializationOptions minimal() {
        ProjectSerializationOptions options = new ProjectSerializationOptions();
        options.compressionEnabled = true;
        options.prettyPrint = false;
        options.includeTimestamps = false;
        options.includeGraphData = false;
        return options;
    }

    // Getters and setters

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public ProjectSerializationOptions setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
        return this;
    }

    public boolean isStreamingMode() {
        return streamingMode;
    }

    public ProjectSerializationOptions setStreamingMode(boolean streamingMode) {
        this.streamingMode = streamingMode;
        return this;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public ProjectSerializationOptions setBufferSize(int bufferSize) {
        this.bufferSize = Math.max(1024, bufferSize); // Minimum 1KB
        return this;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public ProjectSerializationOptions setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }

    public boolean isIncludeTimestamps() {
        return includeTimestamps;
    }

    public ProjectSerializationOptions setIncludeTimestamps(boolean includeTimestamps) {
        this.includeTimestamps = includeTimestamps;
        return this;
    }

    public boolean isIncludeGraphData() {
        return includeGraphData;
    }

    public ProjectSerializationOptions setIncludeGraphData(boolean includeGraphData) {
        this.includeGraphData = includeGraphData;
        return this;
    }

    public boolean isValidateOnLoad() {
        return validateOnLoad;
    }

    public ProjectSerializationOptions setValidateOnLoad(boolean validateOnLoad) {
        this.validateOnLoad = validateOnLoad;
        return this;
    }

    /**
     * Get the recommended file extension based on configuration.
     */
    public String getRecommendedFileExtension() {
        if (compressionEnabled) {
            return ".json.gz";
        } else {
            return ".json";
        }
    }

    /**
     * Check if configuration is optimized for large datasets.
     */
    public boolean isOptimizedForLargeDatasets() {
        return streamingMode || compressionEnabled || !prettyPrint;
    }

    @Override
    public String toString() {
        return String.format(
                "ProjectSerializationOptions{compression=%s, streaming=%s, bufferSize=%d, prettyPrint=%s, timestamps=%s, graph=%s}",
                compressionEnabled, streamingMode, bufferSize, prettyPrint, includeTimestamps, includeGraphData);
    }
}
