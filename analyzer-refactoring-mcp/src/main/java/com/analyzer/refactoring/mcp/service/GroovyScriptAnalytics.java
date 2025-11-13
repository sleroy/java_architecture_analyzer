package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.CallMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for tracking and analyzing Groovy script usage.
 * Records call metrics and generates usage reports.
 */
@Service
public class GroovyScriptAnalytics {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptAnalytics.class);
    private static final String CALLS_FILE_PREFIX = "calls-";
    private static final String CALLS_FILE_EXTENSION = ".jsonl";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final boolean enabled;
    private final Path analyticsPath;
    private final int retentionMonths;
    private final ObjectMapper objectMapper;
    private final Queue<CallMetrics> pendingWrites;

    public GroovyScriptAnalytics(
            @Value("${groovy.analytics.enabled:true}") boolean enabled,
            @Value("${groovy.analytics.storage.path:${groovy.script.storage.path:${user.home}/.java-refactoring-mcp/scripts}/analytics}") String analyticsPath,
            @Value("${groovy.analytics.retention-months:6}") int retentionMonths) {

        this.enabled = enabled;
        this.analyticsPath = Paths.get(analyticsPath);
        this.retentionMonths = retentionMonths;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.pendingWrites = new ConcurrentLinkedQueue<>();

        logger.info("Groovy script analytics initialized: enabled={}, path={}, retention={}mo",
                enabled, analyticsPath, retentionMonths);
    }

    /**
     * Initialize analytics directory on startup.
     */
    @PostConstruct
    public void initialize() {
        if (!enabled) {
            logger.info("Analytics tracking disabled");
            return;
        }

        try {
            Files.createDirectories(analyticsPath);
            logger.info("Analytics directory ready: {}", analyticsPath);

            // Cleanup old analytics files
            cleanupOldAnalytics();
        } catch (IOException e) {
            logger.error("Failed to create analytics directory: {}", analyticsPath, e);
        }
    }

    /**
     * Record a tool call with metrics.
     *
     * @param metrics the call metrics to record
     */
    public void recordCall(CallMetrics metrics) {
        if (!enabled) {
            return;
        }

        try {
            // Add to pending writes queue for batch processing
            pendingWrites.offer(metrics);

            logger.debug("Recorded call: pattern='{}', success={}, cacheHit={}",
                    metrics.getPatternDescription(), metrics.isSuccess(), metrics.isCacheHit());

        } catch (Exception e) {
            logger.error("Failed to record call metrics", e);
        }
    }

    /**
     * Flush pending metrics to disk every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000)
    public void flushPendingMetrics() {
        if (!enabled || pendingWrites.isEmpty()) {
            return;
        }

        try {
            Map<String, List<CallMetrics>> metricsByMonth = new HashMap<>();

            // Group metrics by month
            CallMetrics metrics;
            while ((metrics = pendingWrites.poll()) != null) {
                String month = getMonthKey(metrics.getTimestamp());
                metricsByMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(metrics);
            }

            // Write each month's metrics
            for (Map.Entry<String, List<CallMetrics>> entry : metricsByMonth.entrySet()) {
                writeMetricsToFile(entry.getKey(), entry.getValue());
            }

            logger.debug("Flushed {} call metrics to disk", metricsByMonth.values().stream()
                    .mapToInt(List::size).sum());

        } catch (Exception e) {
            logger.error("Failed to flush pending metrics", e);
        }
    }

    /**
     * Write metrics to the appropriate monthly file.
     */
    private void writeMetricsToFile(String month, List<CallMetrics> metrics) throws IOException {
        Path file = analyticsPath.resolve(CALLS_FILE_PREFIX + month + CALLS_FILE_EXTENSION);

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            for (CallMetrics metric : metrics) {
                String json = objectMapper.writeValueAsString(metric);
                writer.write(json);
                writer.newLine();
            }
        }
    }

    /**
     * Get analytics summary for a specific time period.
     *
     * @param startDate start of period
     * @param endDate   end of period
     * @return summary statistics
     */
    public AnalyticsSummary getSummary(Instant startDate, Instant endDate) {
        if (!enabled) {
            return new AnalyticsSummary();
        }

        try {
            // Flush any pending metrics first
            flushPendingMetrics();

            List<CallMetrics> allMetrics = loadMetricsForPeriod(startDate, endDate);
            return calculateSummary(allMetrics);

        } catch (Exception e) {
            logger.error("Failed to generate analytics summary", e);
            return new AnalyticsSummary();
        }
    }

    /**
     * Load all metrics for a time period.
     */
    private List<CallMetrics> loadMetricsForPeriod(Instant startDate, Instant endDate) throws IOException {
        List<CallMetrics> allMetrics = new ArrayList<>();

        try (Stream<Path> paths = Files.list(analyticsPath)) {
            List<Path> monthFiles = paths
                    .filter(p -> p.getFileName().toString().startsWith(CALLS_FILE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(CALLS_FILE_EXTENSION))
                    .collect(Collectors.toList());

            for (Path file : monthFiles) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    CallMetrics metrics = objectMapper.readValue(line, CallMetrics.class);
                    if (!metrics.getTimestamp().isBefore(startDate) &&
                            !metrics.getTimestamp().isAfter(endDate)) {
                        allMetrics.add(metrics);
                    }
                }
            }
        }

        return allMetrics;
    }

    /**
     * Calculate summary statistics from metrics.
     */
    private AnalyticsSummary calculateSummary(List<CallMetrics> metrics) {
        AnalyticsSummary summary = new AnalyticsSummary();

        if (metrics.isEmpty()) {
            return summary;
        }

        summary.totalCalls = metrics.size();
        summary.successfulCalls = (int) metrics.stream().filter(CallMetrics::isSuccess).count();
        summary.failedCalls = summary.totalCalls - summary.successfulCalls;
        summary.cacheHits = (int) metrics.stream().filter(CallMetrics::isCacheHit).count();
        summary.cacheMisses = summary.totalCalls - summary.cacheHits;
        summary.cacheHitRate = summary.totalCalls > 0 ? (double) summary.cacheHits / summary.totalCalls * 100.0 : 0.0;

        summary.scriptsGenerated = (int) metrics.stream().filter(CallMetrics::isScriptGenerated).count();
        summary.averageExecutionTimeMs = metrics.stream()
                .mapToLong(CallMetrics::getExecutionTimeMs)
                .average()
                .orElse(0.0);

        summary.totalMatches = metrics.stream().mapToInt(CallMetrics::getMatchesFound).sum();
        summary.totalTokensUsed = metrics.stream()
                .map(CallMetrics::getTokensUsed)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        summary.totalEstimatedCost = metrics.stream()
                .map(CallMetrics::getEstimatedCost)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        // Top patterns
        summary.topPatterns = metrics.stream()
                .collect(Collectors.groupingBy(CallMetrics::getPatternDescription, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));

        return summary;
    }

    /**
     * Generate and log daily summary report.
     */
    @Scheduled(cron = "0 0 0 * * *") // Run at midnight
    public void generateDailySummary() {
        if (!enabled) {
            return;
        }

        try {
            Instant endDate = Instant.now();
            Instant startDate = endDate.minus(1, ChronoUnit.DAYS);

            AnalyticsSummary summary = getSummary(startDate, endDate);

            logger.info("\n=== Groovy Script Analytics - Daily Summary ===\n" +
                    "Date: {}\n" +
                    "Total Calls: {}\n" +
                    "Successful: {} ({:.1f}%)\n" +
                    "Failed: {} ({:.1f}%)\n" +
                    "Cache Hit Rate: {:.1f}%\n" +
                    "Scripts Generated: {}\n" +
                    "Average Execution Time: {:.0f}ms\n" +
                    "Total Matches Found: {}\n" +
                    "Total Bedrock Tokens: {}\n" +
                    "Estimated Cost: ${:.4f}\n" +
                    "Top Patterns:\n{}",
                    startDate.atZone(ZoneId.systemDefault()).toLocalDate(),
                    summary.totalCalls,
                    summary.successfulCalls,
                    summary.totalCalls > 0 ? (double) summary.successfulCalls / summary.totalCalls * 100 : 0,
                    summary.failedCalls,
                    summary.totalCalls > 0 ? (double) summary.failedCalls / summary.totalCalls * 100 : 0,
                    summary.cacheHitRate,
                    summary.scriptsGenerated,
                    summary.averageExecutionTimeMs,
                    summary.totalMatches,
                    summary.totalTokensUsed,
                    summary.totalEstimatedCost,
                    formatTopPatterns(summary.topPatterns));

        } catch (Exception e) {
            logger.error("Failed to generate daily summary", e);
        }
    }

    /**
     * Format top patterns for logging.
     */
    private String formatTopPatterns(Map<String, Long> topPatterns) {
        if (topPatterns.isEmpty()) {
            return "  (none)";
        }

        StringBuilder sb = new StringBuilder();
        int rank = 1;
        for (Map.Entry<String, Long> entry : topPatterns.entrySet()) {
            sb.append(String.format("  %d. %s (%d calls)\n", rank++, entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }

    /**
     * Get month key from timestamp.
     */
    private String getMonthKey(Instant timestamp) {
        return timestamp.atZone(ZoneId.systemDefault()).format(MONTH_FORMATTER);
    }

    /**
     * Cleanup analytics files older than retention period.
     */
    public void cleanupOldAnalytics() {
        if (!enabled) {
            return;
        }

        try {
            YearMonth cutoffMonth = YearMonth.now().minusMonths(retentionMonths);

            try (Stream<Path> paths = Files.list(analyticsPath)) {
                List<Path> filesToDelete = paths
                        .filter(p -> p.getFileName().toString().startsWith(CALLS_FILE_PREFIX))
                        .filter(p -> {
                            String filename = p.getFileName().toString();
                            String monthStr = filename.substring(CALLS_FILE_PREFIX.length(),
                                    filename.length() - CALLS_FILE_EXTENSION.length());
                            try {
                                YearMonth fileMonth = YearMonth.parse(monthStr, MONTH_FORMATTER);
                                return fileMonth.isBefore(cutoffMonth);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .collect(Collectors.toList());

                for (Path file : filesToDelete) {
                    Files.deleteIfExists(file);
                }

                if (!filesToDelete.isEmpty()) {
                    logger.info("Cleaned up {} old analytics files (older than {} months)",
                            filesToDelete.size(), retentionMonths);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to cleanup old analytics", e);
        }
    }

    /**
     * Check if analytics is enabled.
     *
     * @return true if analytics is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Summary statistics for analytics.
     */
    public static class AnalyticsSummary {
        public int totalCalls;
        public int successfulCalls;
        public int failedCalls;
        public int cacheHits;
        public int cacheMisses;
        public double cacheHitRate;
        public int scriptsGenerated;
        public double averageExecutionTimeMs;
        public int totalMatches;
        public int totalTokensUsed;
        public double totalEstimatedCost;
        public Map<String, Long> topPatterns = new LinkedHashMap<>();
    }
}
