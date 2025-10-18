package com.analyzer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated component for tracking inspector progress and execution order.
 * 
 * Features:
 * - Tracks first-time inspector triggers with processing order
 * - Monitors progress across all files and inspectors
 * - Thread-safe for concurrent inspector execution
 * - Provides comprehensive reporting and statistics
 */
public class InspectorProgressTracker {
    private static final Logger logger = LoggerFactory.getLogger(InspectorProgressTracker.class);

    // First-time trigger tracking
    private final Set<String> triggeredInspectors = ConcurrentHashMap.newKeySet();
    private final Map<String, InspectorTriggerInfo> firstTriggers = new ConcurrentHashMap<>();
    private final AtomicInteger processingOrderCounter = new AtomicInteger(0);

    // Progress tracking
    private final Map<String, AtomicInteger> filesProcessedPerInspector = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> filesProcessedByInspector = new ConcurrentHashMap<>();
    private final AtomicInteger totalInspectorExecutions = new AtomicInteger(0);

    // Analysis metrics
    private final LocalDateTime trackingStartTime = LocalDateTime.now();
    private volatile LocalDateTime trackingCompletedTime;

    /**
     * Information about when an inspector was first triggered.
     */
    public static class InspectorTriggerInfo {
        private final String inspectorName;
        private final LocalDateTime firstTriggerTime;
        private final int processingOrder;
        private final String firstProjectFilePath;

        public InspectorTriggerInfo(String inspectorName, LocalDateTime firstTriggerTime,
                int processingOrder, String firstProjectFilePath) {
            this.inspectorName = inspectorName;
            this.firstTriggerTime = firstTriggerTime;
            this.processingOrder = processingOrder;
            this.firstProjectFilePath = firstProjectFilePath;
        }

        public String getInspectorName() {
            return inspectorName;
        }

        public LocalDateTime getFirstTriggerTime() {
            return firstTriggerTime;
        }

        public int getProcessingOrder() {
            return processingOrder;
        }

        public String getFirstProjectFilePath() {
            return firstProjectFilePath;
        }

        @Override
        public String toString() {
            return String.format("InspectorTriggerInfo{inspector='%s', order=%d, time=%s, firstFile='%s'}",
                    inspectorName, processingOrder, firstTriggerTime, firstProjectFilePath);
        }
    }

    /**
     * Record that an inspector has been triggered on a specific file.
     * If this is the first time the inspector is triggered, log it with processing
     * order.
     *
     * @param inspectorName the name of the inspector
     * @param projectFile   the file being processed
     */
    public void recordInspectorTrigger(String inspectorName, ProjectFile projectFile) {
        recordInspectorTrigger(inspectorName, projectFile.getRelativePath());
    }

    /**
     * Record that an inspector has been triggered on a specific file.
     * If this is the first time the inspector is triggered, log it with processing
     * order.
     *
     * @param inspectorName the name of the inspector
     * @param filePath      the path of the file being processed
     */
    public void recordInspectorTrigger(String inspectorName, String filePath) {
        // Check if this is the first time this inspector is triggered
        boolean isFirstTime = triggeredInspectors.add(inspectorName);

        if (isFirstTime) {
            // This is the first time this inspector is triggered
            int order = processingOrderCounter.incrementAndGet();
            LocalDateTime triggerTime = LocalDateTime.now();

            InspectorTriggerInfo triggerInfo = new InspectorTriggerInfo(
                    inspectorName, triggerTime, order, filePath);

            firstTriggers.put(inspectorName, triggerInfo);

            logger.debug("FIRST-TIME TRIGGER: Inspector '{}' triggered for the first time (order: {}, file: '{}')",
                    inspectorName, order, filePath);
        }

        // Update progress tracking
        filesProcessedPerInspector.computeIfAbsent(inspectorName, k -> new AtomicInteger(0)).incrementAndGet();
        filesProcessedByInspector.computeIfAbsent(inspectorName, k -> ConcurrentHashMap.newKeySet()).add(filePath);
        totalInspectorExecutions.incrementAndGet();

        logger.debug("Inspector '{}' executed on file: '{}'", inspectorName, filePath);
    }

    /**
     * Check if an inspector has been triggered at least once.
     *
     * @param inspectorName the name of the inspector
     * @return true if the inspector has been triggered
     */
    public boolean hasBeenTriggered(String inspectorName) {
        return triggeredInspectors.contains(inspectorName);
    }

    /**
     * Get information about when an inspector was first triggered.
     *
     * @param inspectorName the name of the inspector
     * @return trigger information, or empty if never triggered
     */
    public Optional<InspectorTriggerInfo> getFirstTriggerInfo(String inspectorName) {
        return Optional.ofNullable(firstTriggers.get(inspectorName));
    }

    /**
     * Get the processing order for an inspector (when it was first triggered).
     *
     * @param inspectorName the name of the inspector
     * @return processing order, or -1 if never triggered
     */
    public int getProcessingOrder(String inspectorName) {
        return getFirstTriggerInfo(inspectorName)
                .map(InspectorTriggerInfo::getProcessingOrder)
                .orElse(-1);
    }

    /**
     * Get the number of files processed by a specific inspector.
     *
     * @param inspectorName the name of the inspector
     * @return number of files processed, or 0 if never triggered
     */
    public int getFilesProcessedCount(String inspectorName) {
        AtomicInteger counter = filesProcessedPerInspector.get(inspectorName);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get the set of files processed by a specific inspector.
     *
     * @param inspectorName the name of the inspector
     * @return unmodifiable set of file paths processed
     */
    public Set<String> getFilesProcessed(String inspectorName) {
        Set<String> files = filesProcessedByInspector.get(inspectorName);
        return files != null ? Collections.unmodifiableSet(files) : Collections.emptySet();
    }

    /**
     * Get all inspectors that have been triggered, in processing order.
     *
     * @return list of inspector names ordered by first trigger time
     */
    public List<String> getTriggeredInspectorsInOrder() {
        return firstTriggers.values().stream()
                .sorted(Comparator.comparingInt(InspectorTriggerInfo::getProcessingOrder))
                .map(InspectorTriggerInfo::getInspectorName)
                .toList();
    }

    /**
     * Get all first-time trigger information, ordered by processing order.
     *
     * @return list of trigger information ordered by trigger order
     */
    public List<InspectorTriggerInfo> getAllFirstTriggers() {
        return firstTriggers.values().stream()
                .sorted(Comparator.comparingInt(InspectorTriggerInfo::getProcessingOrder))
                .toList();
    }

    /**
     * Get comprehensive progress statistics.
     *
     * @return progress summary
     */
    public ProgressSummary getProgressSummary() {
        return new ProgressSummary(
                triggeredInspectors.size(),
                totalInspectorExecutions.get(),
                filesProcessedPerInspector.size(),
                trackingStartTime,
                trackingCompletedTime);
    }

    /**
     * Mark tracking as completed (for timing statistics).
     */
    public void markTrackingCompleted() {
        this.trackingCompletedTime = LocalDateTime.now();
        logger.info("Inspector progress tracking completed. {} inspectors triggered, {} total executions",
                triggeredInspectors.size(), totalInspectorExecutions.get());
    }

    /**
     * Log a comprehensive report of inspector progress and processing order.
     */
    public void logProgressReport() {
        logger.info("=== Inspector Progress Report ===");

        ProgressSummary summary = getProgressSummary();
        logger.info("Summary: {} inspectors triggered, {} total executions, tracking duration: {}",
                summary.getTriggeredInspectorsCount(), summary.getTotalExecutions(), summary.getTrackingDuration());

        if (!firstTriggers.isEmpty()) {
            logger.info("=== Inspector Processing Order (First-Time Triggers) ===");
            getAllFirstTriggers().forEach(trigger -> logger.info("#{}: {} (first triggered on '{}')",
                    trigger.getProcessingOrder(),
                    trigger.getInspectorName(),
                    trigger.getFirstProjectFilePath()));
        }

        if (!filesProcessedPerInspector.isEmpty()) {
            logger.info("=== Files Processed Per Inspector (Top 10) ===");
            filesProcessedPerInspector.entrySet().stream()
                    .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                            (a, b) -> Integer.compare(b.get(), a.get())))
                    .limit(10)
                    .forEach(entry -> logger.info("{}: {} files", entry.getKey(), entry.getValue().get()));
        }

        logger.info("=== End Inspector Progress Report ===");
    }

    /**
     * Reset all tracking data. Useful for testing or restarting analysis.
     */
    public void reset() {
        triggeredInspectors.clear();
        firstTriggers.clear();
        processingOrderCounter.set(0);
        filesProcessedPerInspector.clear();
        filesProcessedByInspector.clear();
        totalInspectorExecutions.set(0);
        trackingCompletedTime = null;
        logger.debug("Inspector progress tracker reset");
    }

    /**
     * Summary of progress tracking statistics.
     */
    public static class ProgressSummary {
        private final int triggeredInspectorsCount;
        private final int totalExecutions;
        private final int inspectorsWithFiles;
        private final LocalDateTime trackingStartTime;
        private final LocalDateTime trackingCompletedTime;

        public ProgressSummary(int triggeredInspectorsCount, int totalExecutions, int inspectorsWithFiles,
                LocalDateTime trackingStartTime, LocalDateTime trackingCompletedTime) {
            this.triggeredInspectorsCount = triggeredInspectorsCount;
            this.totalExecutions = totalExecutions;
            this.inspectorsWithFiles = inspectorsWithFiles;
            this.trackingStartTime = trackingStartTime;
            this.trackingCompletedTime = trackingCompletedTime;
        }

        public int getTriggeredInspectorsCount() {
            return triggeredInspectorsCount;
        }

        public int getTotalExecutions() {
            return totalExecutions;
        }

        public int getInspectorsWithFiles() {
            return inspectorsWithFiles;
        }

        public LocalDateTime getTrackingStartTime() {
            return trackingStartTime;
        }

        public LocalDateTime getTrackingCompletedTime() {
            return trackingCompletedTime;
        }

        public String getTrackingDuration() {
            if (trackingCompletedTime == null) {
                return "ongoing";
            }
            long seconds = java.time.Duration.between(trackingStartTime, trackingCompletedTime).getSeconds();
            return seconds + "s";
        }

        @Override
        public String toString() {
            return String.format("ProgressSummary{triggered=%d, executions=%d, duration=%s}",
                    triggeredInspectorsCount, totalExecutions, getTrackingDuration());
        }
    }
}
