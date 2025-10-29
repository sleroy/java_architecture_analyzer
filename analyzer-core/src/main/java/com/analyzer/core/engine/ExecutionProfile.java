package com.analyzer.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tracks comprehensive execution metrics for inspector analysis including
 * timing,
 * phase distribution, and usage statistics.
 */
public class ExecutionProfile {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionProfile.class);

    // Execution phases in the analysis workflow
    public enum ExecutionPhase {
        PHASE_1A_FILESYSTEM_SCAN("Phase 1a: Filesystem Scan"),
        PHASE_1C_EXTRACTED_CONTENT("Phase 1c: Extracted Content"),
        PHASE_2_CLASSNODE_COLLECTION("Phase 2: ClassNode Collection"),
        PHASE_3_PROJECTFILE_ANALYSIS("Phase 3: ProjectFile Analysis"),
        PHASE_4_CLASSNODE_ANALYSIS("Phase 4: ClassNode Analysis");

        private final String displayName;

        ExecutionPhase(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getShortName() {
            return displayName.substring(0, displayName.indexOf(':'));
        }
    }

    // Individual execution record (converted to record)
    public record InspectorExecution(
            String inspectorName,
            ExecutionPhase phase,
            Integer passNumber,
            long executionTimeMs,
            LocalDateTime timestamp) {

        public InspectorExecution(String inspectorName, ExecutionPhase phase, Integer passNumber,
                long executionTimeMs) {
            this(inspectorName, phase, passNumber, executionTimeMs, LocalDateTime.now());
        }
    }

    // Core tracking data
    private final Set<String> registeredInspectors;
    private final List<InspectorExecution> executions;
    private final LocalDateTime analysisStartTime;
    private LocalDateTime analysisEndTime;
    private int totalPasses = 0;
    private int totalFilesProcessed = 0;

    public ExecutionProfile(Collection<String> availableInspectors) {
        this.registeredInspectors = new HashSet<>(availableInspectors);
        this.executions = new ArrayList<>();
        this.analysisStartTime = LocalDateTime.now();
        logger.info("ExecutionProfile initialized with {} available inspectors", availableInspectors.size());
    }

    public void recordInspectorExecution(String inspectorName, ExecutionPhase phase, long executionTimeNs) {
        recordInspectorExecution(inspectorName, phase, null, executionTimeNs);
    }

    public void recordInspectorExecution(String inspectorName, ExecutionPhase phase, int passNumber,
            long executionTimeNs) {
        recordInspectorExecution(inspectorName, phase, Integer.valueOf(passNumber), executionTimeNs);
    }

    private void recordInspectorExecution(String inspectorName, ExecutionPhase phase, Integer passNumber,
            long executionTimeNs) {
        long executionTimeMs = executionTimeNs / 1_000_000;
        executions.add(new InspectorExecution(inspectorName, phase, passNumber, executionTimeMs));
        logger.debug("Recorded execution: {} in {} ({}ms)", inspectorName,
                phase.getShortName() + (passNumber != null ? " Pass " + passNumber : ""), executionTimeMs);
    }

    public void setAnalysisMetrics(int totalPasses, int totalFiles) {
        this.totalPasses = totalPasses;
        this.totalFilesProcessed = totalFiles;
    }

    public void markAnalysisComplete() {
        this.analysisEndTime = LocalDateTime.now();
        logger.info("ExecutionProfile analysis completed with {} executions recorded", executions.size());
    }

    public ExecutionReport generateReport() {
        return new ExecutionReport();
    }

    public void logReport() {
        generateReport().logReport();
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
            this.executionsByInspector = executions.stream()
                    .collect(Collectors.groupingBy(InspectorExecution::inspectorName));
            this.executedInspectors = executionsByInspector.keySet();
            this.unusedInspectors = new HashSet<>(registeredInspectors);
            this.unusedInspectors.removeAll(executedInspectors);
            this.analysisDuration = analysisEndTime != null
                    ? Duration.between(analysisStartTime, analysisEndTime)
                    : Duration.ZERO;
        }

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
            logger.info("Analysis Duration: {} ({} seconds)", formatDuration(analysisDuration),
                    analysisDuration.getSeconds());
            logger.info("Total Passes: {}", totalPasses);
            logger.info("Total Files Processed: {}", String.format("%,d", totalFilesProcessed));
            logger.info("");
        }

        private void logInspectorExecutionSummary() {
            logger.info("Inspector Execution Summary:");
            int nameWidth = Math.max(35, registeredInspectors.stream().mapToInt(String::length).max().orElse(35));

            // Table structure
            int[] columnWidths = { nameWidth + 2, 10, 13, 15, 13, 17 };
            String border = buildTableBorder(columnWidths);
            String titleFormat = "| %-" + nameWidth + "s | %-8s | %-11s | %-13s | %-11s | %-18s |";

            logger.info(border);
            logger.info(String.format(titleFormat, "Inspector Name", "Status", "Executions",
                    "Avg Time (ms)", "Total (ms)", "Phase(s)"));
            logger.info(border);

            // Sort and display inspectors
            List<String> sortedInspectors = Stream.concat(
                    executedInspectors.stream().sorted(),
                    unusedInspectors.stream().sorted()).toList();

            for (String inspectorName : sortedInspectors) {
                if (executedInspectors.contains(inspectorName)) {
                    logExecutedInspector(inspectorName, nameWidth, titleFormat);
                } else {
                    logger.info(String.format(titleFormat, truncate(inspectorName, nameWidth),
                            "UNUSED", "0", "N/A", "0", "-"));
                }
            }

            logger.info(border);
            logger.info("");
        }

        private void logExecutedInspector(String inspectorName, int nameWidth, String format) {
            List<InspectorExecution> inspectorExecutions = executionsByInspector.get(inspectorName);
            double avgTime = inspectorExecutions.stream()
                    .mapToLong(InspectorExecution::executionTimeMs).average().orElse(0.0);
            long totalTime = inspectorExecutions.stream()
                    .mapToLong(InspectorExecution::executionTimeMs).sum();

            logger.info(String.format(format,
                    truncate(inspectorName, nameWidth),
                    "EXECUTED",
                    String.format("%,d", inspectorExecutions.size()),
                    String.format("%.1f", avgTime),
                    String.format("%,d", totalTime),
                    truncate(formatInspectorPhases(inspectorExecutions), 18)));
        }

        private void logPerformanceAnalysis() {
            if (executions.isEmpty()) {
                logger.info("Performance Analysis: No executions recorded\n");
                return;
            }

            logger.info("Performance Analysis:");
            double overallAvgTime = executions.stream()
                    .mapToLong(InspectorExecution::executionTimeMs).average().orElse(0.0);

            Map<String, Double> avgTimesByInspector = executionsByInspector.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue().stream().mapToLong(InspectorExecution::executionTimeMs)
                                    .average().orElse(0.0)));

            logger.info("• Total Inspector Executions: {}", String.format("%,d", executions.size()));
            logger.info("• Average Execution Time: {}", String.format("%.1fms", overallAvgTime));

            avgTimesByInspector.entrySet().stream().max(Map.Entry.comparingByValue())
                    .ifPresent(e -> logger.info("• Slowest Inspector: {} (avg: {})",
                            e.getKey(), String.format("%.1fms", e.getValue())));
            avgTimesByInspector.entrySet().stream().min(Map.Entry.comparingByValue())
                    .ifPresent(e -> logger.info("• Fastest Inspector: {} (avg: {})",
                            e.getKey(), String.format("%.1fms", e.getValue())));
            logger.info("");
        }

        private void logUtilizationAnalysis() {
            int totalInspectors = registeredInspectors.size();
            int executedCount = executedInspectors.size();
            double utilizationRate = totalInspectors > 0 ? (executedCount * 100.0) / totalInspectors : 0.0;

            logger.info("Utilization Analysis:");
            logger.info("• Total Available Inspectors: {}", totalInspectors);
            logger.info("• Executed Inspectors: {} ({})", executedCount, String.format("%.1f%%", utilizationRate));
            logger.info("• Unused Inspectors: {} ({})", unusedInspectors.size(),
                    String.format("%.1f%%", 100.0 - utilizationRate));
            logger.info("");
        }

        private void logUnusedInspectors() {
            if (unusedInspectors.isEmpty()) {
                logger.info("Unused Inspectors: None - all inspectors were utilized!");
            } else {
                logger.info("Unused Inspectors:");
                logger.info("[{}]", String.join(", ", unusedInspectors.stream().sorted().toList()));
            }
        }

        private String formatInspectorPhases(List<InspectorExecution> inspectorExecutions) {
            if (inspectorExecutions.isEmpty())
                return "-";

            Map<ExecutionPhase, Set<Integer>> phasePassMap = new LinkedHashMap<>();
            for (InspectorExecution execution : inspectorExecutions) {
                phasePassMap.computeIfAbsent(execution.phase(), k -> new TreeSet<>());
                if (execution.passNumber() != null) {
                    phasePassMap.get(execution.phase()).add(execution.passNumber());
                }
            }

            return phasePassMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> {
                        String phaseName = entry.getKey().getShortName();
                        Set<Integer> passes = entry.getValue();
                        if (passes.isEmpty())
                            return phaseName;
                        return phaseName + " " + passes.stream()
                                .map(p -> "Pass " + p).collect(Collectors.joining(", "));
                    })
                    .collect(Collectors.joining("; "));
        }

        private String formatDuration(Duration duration) {
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    : String.format("%02d:%02d", minutes, seconds);
        }

        private String buildTableBorder(int[] columnWidths) {
            return Arrays.stream(columnWidths)
                    .mapToObj(w -> "+" + "-".repeat(w))
                    .collect(Collectors.joining()) + "+";
        }

        private String truncate(String str, int maxLength) {
            return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
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
