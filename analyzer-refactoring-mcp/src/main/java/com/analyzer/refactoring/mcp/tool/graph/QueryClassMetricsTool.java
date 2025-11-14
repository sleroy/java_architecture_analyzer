package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.metrics.Metrics;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool for querying class complexity metrics from the graph database.
 * <p>
 * This tool returns metrics like:
 * - Complexity measures (cyclomatic, cognitive)
 * - Size metrics (LOC, method count, field count)
 * - Coupling metrics (afferent, efferent coupling)
 * - Other quality metrics stored in the database
 */
@Component
public class QueryClassMetricsTool extends BaseRefactoringTool {

    private final GraphDatabaseService graphDatabaseService;

    public QueryClassMetricsTool(final GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService;
    }

    @Tool(description = "Query complexity and quality metrics for a Java class from the graph database. " +
            "Returns metrics including complexity (cyclomatic, cognitive), size (LOC, method count), " +
            "coupling (afferent, efferent), and other quality indicators. " +
            "Use this to assess code quality, identify complex classes, or prioritize refactoring efforts.")
    public String queryClassMetrics(
            @ToolParam(description = "Fully qualified name of the class to analyze") final String fullyQualifiedName) {
        try {
            logger.info("Tool called: query_class_metrics for {}", fullyQualifiedName);

            if (!graphDatabaseService.isInitialized()) {
                return databaseNotAvailableError();
            }

            // Find the class node
            final Optional<JavaClassNode> classNodeOpt = graphDatabaseService.findClassNode(fullyQualifiedName);
            if (classNodeOpt.isEmpty()) {
                final Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Class not found in graph database");
                error.put("className", fullyQualifiedName);
                return toJsonResponse(error);
            }

            final JavaClassNode classNode = classNodeOpt.get();

            // Build response
            final Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("className", fullyQualifiedName);

            // Basic counts
            response.put("methodCount", classNode.getMethodCount());
            response.put("fieldCount", classNode.getFieldCount());
            response.put("sourceType", classNode.getSourceType());

            // Get metrics if available
            final Metrics metrics = classNode.getMetrics();
            if (metrics != null) {
                // Extract all metrics using the Metrics API
                final Map<String, Double> allMetrics = metrics.getAllMetrics();
                response.put("metrics", allMetrics);
                response.put("metricsAvailable", true);
                response.put("metricsCount", allMetrics.size());
            } else {
                response.put("metrics", Collections.emptyMap());
                response.put("metricsAvailable", false);
                response.put("metricsCount", 0);
                response.put("note", "No metrics found - run full analysis to generate metrics");
            }

            // Include tags for context
            response.put("tags", classNode.getTags());

            return toJsonResponse(response);

        } catch (final Exception e) {
            logger.error("Error in query_class_metrics tool", e);
            return "{\"success\":false,\"error\":\"Error: " + e.getMessage() + "\"}";
        }
    }

    private String databaseNotAvailableError() {
        final Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "Graph database not initialized");
        error.put("message", "Run the analyzer application first to generate the database");
        return toJsonResponse(error);
    }
}
