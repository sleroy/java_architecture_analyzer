package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.metrics.Metrics;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.service.PatternMatcherAgent;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP tool for querying metric values across all classes using natural
 * language.
 * 
 * This tool uses AI (Bedrock) to match natural language metric queries to
 * actual
 * metric names in the database, then retrieves all values for those metrics
 * across
 * all classes.
 * 
 * Examples:
 * - "complexity metrics" → cyclomatic_complexity, cognitive_complexity
 * - "lines of code" → loc, sloc
 * - "coupling" → afferent_coupling, efferent_coupling
 * 
 * Returns all classes with their values for the matched metrics, plus
 * statistical
 * analysis (min, max, average, median).
 */
@Component
public class QueryMetricValuesTool extends BaseRefactoringTool {

    private final GraphDatabaseService graphDatabaseService;
    private final PatternMatcherAgent patternMatcherAgent;

    public QueryMetricValuesTool(
            GraphDatabaseService graphDatabaseService,
            PatternMatcherAgent patternMatcherAgent) {
        this.graphDatabaseService = graphDatabaseService;
        this.patternMatcherAgent = patternMatcherAgent;
    }

    @Tool(description = "Query metric values across all classes using natural language description (AI-enhanced). " +
            "Describe the metrics you want (e.g., 'complexity metrics', 'lines of code', 'coupling metrics') " +
            "and the tool will use AI to match your description to actual metric names, then return all classes " +
            "with their values for those metrics. Includes statistical analysis (min, max, avg, median). " +
            "Use this to understand metric distributions, identify outliers, or find classes with specific metric values.")
    public String queryMetricValues(
            @ToolParam(description = "Natural language description of metrics to query (e.g., 'complexity metrics', 'lines of code')") String naturalLanguageQuery) {
        try {
            logger.info("Tool called: query_metric_values with query: {}", naturalLanguageQuery);

            if (!graphDatabaseService.isInitialized()) {
                return databaseNotAvailableError();
            }

            // Get all available metrics from all classes in the database
            Set<String> availableMetrics = collectAllMetricNames();

            if (availableMetrics.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("query", naturalLanguageQuery);
                response.put("values", Collections.emptyList());
                response.put("note", "No metrics found in database - run full analysis to generate metrics");
                return toJsonResponse(response);
            }

            logger.info("Found {} unique metrics in database", availableMetrics.size());

            // Use AI to match natural language to actual metric names
            List<String> matchedMetrics = patternMatcherAgent.matchMetricsToQuery(
                    naturalLanguageQuery,
                    availableMetrics);

            if (matchedMetrics.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("query", naturalLanguageQuery);
                response.put("availableMetrics", new ArrayList<>(availableMetrics));
                response.put("matchedMetrics", Collections.emptyList());
                response.put("values", Collections.emptyList());
                response.put("note", "No metrics matched your query. See availableMetrics for all metric names.");
                return toJsonResponse(response);
            }

            logger.info("AI matched query to {} metrics: {}", matchedMetrics.size(), matchedMetrics);

            // Collect values for matched metrics
            List<Map<String, Object>> metricValues = new ArrayList<>();
            Map<String, MetricStatistics> statistics = new HashMap<>();

            // Initialize statistics collectors for each matched metric
            for (String metricName : matchedMetrics) {
                statistics.put(metricName, new MetricStatistics());
            }

            // Query all classes and extract metric values
            Optional<com.analyzer.api.graph.GraphRepository> repoOpt = graphDatabaseService.getRepository();
            if (repoOpt.isEmpty()) {
                return databaseNotAvailableError();
            }

            com.analyzer.api.graph.GraphRepository repository = repoOpt.get();

            for (GraphNode node : repository.getNodes()) {
                if (!(node instanceof JavaClassNode)) {
                    continue;
                }

                JavaClassNode classNode = (JavaClassNode) node;
                Metrics metrics = classNode.getMetrics();

                if (metrics == null) {
                    continue;
                }

                Map<String, Double> allMetrics = metrics.getAllMetrics();

                for (String metricName : matchedMetrics) {
                    if (allMetrics.containsKey(metricName)) {
                        Double value = allMetrics.get(metricName);

                        if (value != null) {
                            Map<String, Object> metricValue = new HashMap<>();
                            metricValue.put("className", classNode.getFullyQualifiedName());
                            metricValue.put("metricName", metricName);
                            metricValue.put("metricValue", value);

                            metricValues.add(metricValue);

                            // Update statistics
                            statistics.get(metricName).addValue(value);
                        }
                    }
                }
            }

            // Build response with statistics
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", naturalLanguageQuery);
            response.put("matchedMetrics", matchedMetrics);
            response.put("totalValues", metricValues.size());
            response.put("values", metricValues);

            // Add statistics for each metric
            Map<String, Map<String, Object>> statsMap = new HashMap<>();
            for (Map.Entry<String, MetricStatistics> entry : statistics.entrySet()) {
                String metricName = entry.getKey();
                MetricStatistics stats = entry.getValue();

                if (stats.getCount() > 0) {
                    Map<String, Object> statMap = new HashMap<>();
                    statMap.put("count", stats.getCount());
                    statMap.put("min", stats.getMin());
                    statMap.put("max", stats.getMax());
                    statMap.put("avg", stats.getAverage());
                    statMap.put("median", stats.getMedian());
                    statMap.put("stdDev", stats.getStandardDeviation());

                    statsMap.put(metricName, statMap);
                }
            }
            response.put("statistics", statsMap);

            return toJsonResponse(response);

        } catch (Exception e) {
            logger.error("Error in query_metric_values tool", e);
            return "{\"success\":false,\"error\":\"Error: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Collect all unique metric names from all classes in the database.
     */
    private Set<String> collectAllMetricNames() {
        Set<String> metricNames = new HashSet<>();

        Optional<com.analyzer.api.graph.GraphRepository> repoOpt = graphDatabaseService.getRepository();
        if (repoOpt.isEmpty()) {
            return metricNames;
        }

        com.analyzer.api.graph.GraphRepository repository = repoOpt.get();

        for (GraphNode node : repository.getNodes()) {
            if (node instanceof JavaClassNode) {
                JavaClassNode classNode = (JavaClassNode) node;
                Metrics metrics = classNode.getMetrics();

                if (metrics != null) {
                    Map<String, Double> allMetrics = metrics.getAllMetrics();
                    metricNames.addAll(allMetrics.keySet());
                }
            }
        }

        return metricNames;
    }

    private String databaseNotAvailableError() {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "Graph database not initialized");
        error.put("message", "Run the analyzer application first to generate the database");
        return toJsonResponse(error);
    }

    /**
     * Helper class for calculating statistics on metric values.
     */
    private static class MetricStatistics {
        private final List<Double> values = new ArrayList<>();

        public void addValue(double value) {
            values.add(value);
        }

        public int getCount() {
            return values.size();
        }

        public double getMin() {
            return values.stream().min(Double::compareTo).orElse(0.0);
        }

        public double getMax() {
            return values.stream().max(Double::compareTo).orElse(0.0);
        }

        public double getAverage() {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getMedian() {
            if (values.isEmpty()) {
                return 0.0;
            }

            List<Double> sorted = new ArrayList<>(values);
            Collections.sort(sorted);

            int size = sorted.size();
            if (size % 2 == 0) {
                return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
            } else {
                return sorted.get(size / 2);
            }
        }

        public double getStandardDeviation() {
            if (values.size() < 2) {
                return 0.0;
            }

            double avg = getAverage();
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - avg, 2))
                    .average()
                    .orElse(0.0);

            return Math.sqrt(variance);
        }
    }
}
