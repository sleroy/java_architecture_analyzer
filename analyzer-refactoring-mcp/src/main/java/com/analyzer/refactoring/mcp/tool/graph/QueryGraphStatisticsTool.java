package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP tool for querying overall graph database statistics.
 * 
 * This tool provides a high-level overview of the project structure
 * including total nodes, edges, and breakdown by node types.
 */
@Component
public class QueryGraphStatisticsTool extends BaseRefactoringTool {

    private final GraphDatabaseService graphDatabaseService;

    public QueryGraphStatisticsTool(GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService;
    }

    @Tool(description = "Get overall statistics about the project's graph database. " +
            "Returns total node count, edge count, class count, and file count. " +
            "Use this to understand project size and structure at a glance. " +
            "Requires graph database to be loaded (returns error if not available).")
    public String queryGraphStatistics() {
        try {
            logger.info("Tool called: query_graph_statistics");

            if (!graphDatabaseService.isInitialized()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Graph database not initialized");
                error.put("message", "Run the analyzer application first to generate the database");
                return toJsonResponse(error);
            }

            GraphDatabaseService.GraphStatistics stats = graphDatabaseService.getStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalNodes", stats.getTotalNodes());
            response.put("totalEdges", stats.getTotalEdges());
            response.put("classNodes", stats.getClassNodes());
            response.put("fileNodes", stats.getFileNodes());
            response.put("projectRoot", graphDatabaseService.getProjectRoot().toString());

            return toJsonResponse(response);

        } catch (Exception e) {
            logger.error("Error in query_graph_statistics tool", e);
            return "{\"success\":false,\"error\":\"Error: " + e.getMessage() + "\"}";
        }
    }
}
