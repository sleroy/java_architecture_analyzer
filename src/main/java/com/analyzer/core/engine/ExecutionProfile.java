package com.analyzer.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks comprehensive execution metrics for inspector analysis including
 * timing,
 * phase distribution, and usage statistics.
 * 
 * Provides detailed reporting on:
 * - Which inspectors executed vs unused
 * - Average execution times and performance metrics
 * - Phase distribution (file detection vs analysis)
 * - Pass-by-pass convergence analysis
 */
public class ExecutionProfile {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionProfile.class);

    // Execution phases in the analysis workflow
    public enum ExecutionPhase {
        PHASE_1A_FILESYSTEM_SCAN("Phase 1a: Filesystem Scan"),
        PHASE_1C_EXTRACTED_CONTENT("Phase 1c: Extracted Content"),
        PHASE_2_ANALYSIS_PASS("Phase 2: Analysis Pass");

        private final String displayName;

        ExecutionPhase(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getShortName() {
            switch (this) {
                case PHASE_1A_FILESYSTEM_SCAN:
                    return "Phase 1a";
                case PHASE_1C_EXTRACTED_CONTENT:
                    return "Phase 1c";
                case PHASE_2_ANALYSIS_PASS:
                    return "Phase 2";
                default:
                    return this.toString();
            }
        }
    }

    // Individual execution record
    public static class InspectorExecution {
        private final String inspectorName;
        private final ExecutionPhase phase;
        private final Integer passNumber; // null for Phase 1
        private final long executionTimeMs;
        private final LocalDateTime timestamp;

        public InspectorExecution(String inspectorName, ExecutionPhase phase, Integer passNumber,
                long executionTimeMs) {
            this.inspectorName = inspectorName;
            this.phase = phase;
            this.passNumber = passNumber;
            this.executionTimeMs = executionTimeMs;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getInspectorName() {
            return inspectorName;
        }

        public ExecutionPhase getPhase() {
            return phase;
        }

        public Integer getPassNumber() {
            return passNumber;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    // Core tracking data
    private final Set<String> registeredInspectors;
    private final List<InspectorExecution> executions;
    private final LocalDateTime analysisStartTime;
    private LocalDateTime analysisEndTime;

    // Analysis metrics
    private int totalPasses = 0;
    private int totalFilesProcessed = 0;

    public ExecutionProfile(Collection<String> availableInspectors) {
        this.registeredInspectors = new HashSet<>(availableInspectors);
        this.executions = new ArrayList<>();
        this.analysisStartTime = LocalDateTime.now();

        logger.info("ExecutionProfile initialized with {} available inspectors", availableInspectors.size());
    }

    /**
     * Records an inspector execution in Phase 1 (file detection/extraction).
     * 
     * @param executionTimeNs execution time in nanoseconds (will be converted to
     *                        milliseconds for storage and display)
     */
    public void recordInspectorExecution(String inspectorName, ExecutionPhase phase, long executionTimeNs) {
        recordInspectorExecution(inspectorName, phase, null, executionTimeNs);
    }

    /**
     * Records an inspector execution in Phase 2 (analysis) with pass tracking.
     * 
     * @param executionTimeNs execution time in nanoseconds (will be converted to
     *                        milliseconds for storage and display)
     */
    public void recordInspectorExecution(String inspectorName, ExecutionPhase phase, int passNumber,
            long executionTimeNs) {
        recordInspectorExecution(inspectorName, phase, Integer.valueOf(passNumber), executionTimeNs);
    }

    /**
     * Records an inspector execution with full details.
     * 
     * @param executionTimeNs execution time in nanoseconds (will be converted to
     *                        milliseconds for storage and display)
     */
    private void recordInspectorExecution(String inspectorName, ExecutionPhase phase, Integer passNumber,
            long executionTimeNs) {
        // Convert nanoseconds to milliseconds for storage and display
        long executionTimeMs = executionTimeNs / 1_000_000;
        InspectorExecution execution = new InspectorExecution(inspectorName, phase, passNumber, executionTimeMs);
        executions.add(execution);

        logger.debug("Recorded execution: {} in {} ({}ms)", inspectorName,
                phase.getShortName() + (passNumber != null ? " Pass " + passNumber : ""), executionTimeMs);
    }

    /**
     * Sets analysis completion metrics.
     */
    public void setAnalysisMetrics(int totalPasses, int totalFiles) {
        this.totalPasses = totalPasses;
        this.totalFilesProcessed = totalFiles;
    }

    /**
     * Marks analysis as complete and finalizes timing.
     */
    public void markAnalysisComplete() {
        this.analysisEndTime = LocalDateTime.now();
        logger.info("ExecutionProfile analysis completed with {} executions recorded", executions.size());
    }

    /**
     * Generates comprehensive execution report.
     */
    public ExecutionReport generateReport() {
        return new ExecutionReport();
    }

    /**
     * Logs the comprehensive execution report.
     */
    public void logReport() {
        ExecutionReport report = generateReport();
        report.logReport();
    }

    /**
     * Comprehensive execution report with formatted output.
     */
    public class ExecutionReport {

        private final Map<String, List<InspectorExecution>> executionsByInspector;
        private final Set<String> executedInspectors;
        private final Set<String> unusedInspectors;
        private final Duration analysisDuration;

        public ExecutionReport() {
            // Group executions by inspector
            this.executionsByInspector = executions.stream()
                    .collect(Collectors.groupingBy(InspectorExecution::getInspectorName));

            this.executedInspectors = executionsByInspector.keySet();
            this.unusedInspectors = new HashSet<>(registeredInspectors);
            this.unusedInspectors.removeAll(executedInspectors);

            this.analysisDuration = analysisEndTime != null ? Duration.between(analysisStartTime, analysisEndTime)
                    : Duration.ZERO;
        }

        /**
         * Logs the complete execution report.
         */
        public void logReport() {
            logger.info("=== EXECUTION PROFILE REPORT ===");
            logAnalysisSummary();
            logInspectorExecutionSummary();
            logPerformanceAnalysis();
            logUtilizationAnalysis();
            logUnusedInspectors();
            logger.info("=== END EXECUTION PROFILE ===");
        }

        private void logAnalysisSummary() {
            String durationStr = formatDuration(analysisDuration);
            logger.info("Analysis Duration: {} ({} seconds)", durationStr, analysisDuration.getSeconds());
            logger.info("Total Passes: {}", totalPasses);
            logger.info("Total Files Processed: {}", String.format("%,d", totalFilesProcessed));
            logger.info("");
        }

        private void logInspectorExecutionSummary() {
            logger.info("Inspector Execution Summary:");

            // Calculate column widths
            int nameWidth = Math.max(35, registeredInspectors.stream().mapToInt(String::length).max().orElse(35));

            // Print header with simple ASCII characters
            String headerFormat = "+" + "-".repeat(nameWidth + 2)
                    + "+" + "-".repeat(10) + "+" + "-".repeat(13) + "+" + "-".repeat(15) + "+" + "-".repeat(13) + "+"
                    + "-".repeat(17) + "+";
            logger.info(headerFormat);

            // Print title row using String.format
            String titleFormat = "| %-" + nameWidth + "s | %-8s | %-11s | %-13s | %-11s | %-15s |";
            String titleRow = String.format(titleFormat, "Inspector Name", "Status", "Executions", "Avg Time (ms)",
                    "Total (ms)", "Phase(s)");
            logger.info(titleRow);

            String separatorFormat = "+" + "-".repeat(nameWidth + 2)
                    + "+" + "-".repeat(10) + "+" + "-".repeat(13) + "+" + "-".repeat(15) + "+" + "-".repeat(13) + "+"
                    + "-".repeat(17) + "+";
            logger.info(separatorFormat);

            // Sort inspectors: executed first (alphabetically), then unused
            // (alphabetically)
            List<String> sortedInspectors = new ArrayList<>();
            sortedInspectors.addAll(executedInspectors.stream().sorted().collect(Collectors.toList()));
            sortedInspectors.addAll(unusedInspectors.stream().sorted().collect(Collectors.toList()));

            // Print each inspector row using String.format
            String rowFormat = "| %-" + nameWidth + "s | %-8s | %-11s | %-13s | %-11s | %-15s |";

            for (String inspectorName : sortedInspectors) {
                if (executedInspectors.contains(inspectorName)) {
                    List<InspectorExecution> inspectorExecutions = executionsByInspector.get(inspectorName);
                    int executionCount = inspectorExecutions.size();
                    double avgTime = inspectorExecutions.stream().mapToLong(InspectorExecution::getExecutionTimeMs)
                            .average().orElse(0.0);
                    long totalTime = inspectorExecutions.stream().mapToLong(InspectorExecution::getExecutionTimeMs)
                            .sum();
                    String phases = formatInspectorPhases(inspectorExecutions);

                    String row = String.format(rowFormat,
                            truncate(inspectorName, nameWidth),
                            "EXECUTED",
                            String.format("%,d", executionCount),
                            String.format("%.1f", avgTime),
                            String.format("%,d", totalTime),
                            truncate(phases, 15));
                    logger.info(row);
                } else {
                    String row = String.format(rowFormat,
                            truncate(inspectorName, nameWidth),
                            "UNUSED",
                            "0",
                            "N/A",
                            "0",
                            "-");
                    logger.info(row);
                }
            }

            String footerFormat = "+" + "-".repeat(nameWidth + 2)
                    + "+" + "-".repeat(10) + "+" + "-".repeat(13) + "+" + "-".repeat(15) + "+" + "-".repeat(13) + "+"
                    + "-".repeat(17) + "+";
            logger.info(footerFormat);
            logger.info("");
        }

        private void logPerformanceAnalysis() {
            if (executions.isEmpty()) {
                logger.info("Performance Analysis: No executions recorded");
                logger.info("");
                return;
            }

            logger.info("Performance Analysis:");

            long totalExecutions = executions.size();
            double overallAvgTime = executions.stream().mapToLong(InspectorExecution::getExecutionTimeMs).average()
                    .orElse(0.0);

            // Find slowest and fastest inspectors
            Map<String, Double> avgTimesByInspector = executionsByInspector.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().stream().mapToLong(InspectorExecution::getExecutionTimeMs)
                                    .average().orElse(0.0)));

            Optional<Map.Entry<String, Double>> slowest = avgTimesByInspector.entrySet().stream()
                    .max(Map.Entry.comparingByValue());
            Optional<Map.Entry<String, Double>> fastest = avgTimesByInspector.entrySet().stream()
                    .min(Map.Entry.comparingByValue());

            logger.info("• Total Inspector Executions: {}", String.format("%,d", totalExecutions));
            logger.info("• Average Execution Time: {}", String.format("%.1fms", overallAvgTime));

            if (slowest.isPresent()) {
                logger.info("• Slowest Inspector: {} (avg: {})", slowest.get().getKey(),
                        String.format("%.1fms", slowest.get().getValue()));
            }
            if (fastest.isPresent()) {
                logger.info("• Fastest Inspector: {} (avg: {})", fastest.get().getKey(),
                        String.format("%.1fms", fastest.get().getValue()));
            }

            logger.info("");
        }

        private void logUtilizationAnalysis() {
            int totalInspectors = registeredInspectors.size();
            int executedCount = executedInspectors.size();
            int unusedCount = unusedInspectors.size();
            double utilizationRate = totalInspectors > 0 ? (executedCount * 100.0) / totalInspectors : 0.0;

            logger.info("Utilization Analysis:");
            logger.info("• Total Available Inspectors: {}", totalInspectors);
            logger.info("• Executed Inspectors: {} ({})", executedCount, String.format("%.1f%%", utilizationRate));
            logger.info("• Unused Inspectors: {} ({})", unusedCount, String.format("%.1f%%", 100.0 - utilizationRate));
            logger.info("");
        }

        private void logUnusedInspectors() {
            if (unusedInspectors.isEmpty()) {
                logger.info("Unused Inspectors: None - all inspectors were utilized!");
            } else {
                List<String> sortedUnused = unusedInspectors.stream().sorted().collect(Collectors.toList());
                logger.info("Unused Inspectors:");
                logger.info("[{}]", String.join(", ", sortedUnused));
            }
        }

        private String formatInspectorPhases(List<InspectorExecution> inspectorExecutions) {
            Set<ExecutionPhase> phases = inspectorExecutions.stream()
                    .map(InspectorExecution::getPhase)
                    .collect(Collectors.toSet());

            if (phases.isEmpty()) {
                return "-";
            }

            // Sort phases and format for display
            List<String> phaseNames = phases.stream()
                    .sorted()
                    .map(ExecutionPhase::getShortName)
                    .collect(Collectors.toList());

            return String.join(", ", phaseNames);
        }

        private String formatDuration(Duration duration) {
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();

            if (hours > 0) {
                return String.format("%02d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format("%02d:%02d", minutes, seconds);
            }
        }

        private String truncate(String str, int maxLength) {
            if (str.length() <= maxLength) {
                return str;
            }
            return str.substring(0, maxLength - 3) + "...";
        }

        // Getters for programmatic access
        public Set<String> getExecutedInspectors() {
            return new HashSet<>(executedInspectors);
        }

        public Set<String> getUnusedInspectors() {
            return new HashSet<>(unusedInspectors);
        }

        public Duration getAnalysisDuration() {
            return analysisDuration;
        }

        public int getTotalExecutions() {
            return executions.size();
        }
    }
}
