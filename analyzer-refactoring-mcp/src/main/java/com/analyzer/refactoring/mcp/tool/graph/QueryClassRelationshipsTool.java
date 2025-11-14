package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP tool for querying raw graph relationships (edges) for a class.
 * 
 * This tool returns the raw graph edges showing all relationships,
 * useful for understanding the complete graph structure around a class.
 */
@Component
public class QueryClassRelationshipsTool extends BaseRefactoringTool {

    private final GraphDatabaseService graphDatabaseService;

    public QueryClassRelationshipsTool(GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService;
    }

    @Tool(description = "Query all graph relationships (edges) for a Java class. " +
            "Returns raw graph edges showing source, target, and edge type for all relationships. " +
            "This provides the complete graph structure around a class including all edge types: " +
            "DEPENDS_ON, IMPORTS, EXTENDS, IMPLEMENTS, CALLS, USES, CONTAINS, etc. " +
            "Use this for detailed graph analysis or to understand the complete relationship network.")
    public String queryClassRelationships(
            @ToolParam(description = "Fully qualified name of the class to analyze") String fullyQualifiedName) {
        try {
            logger.info("Tool called: query_class_relationships for {}", fullyQualifiedName);

            if (!graphDatabaseService.isInitialized()) {
                return databaseNotAvailableError();
            }

            // Find the class node
            Optional<JavaClassNode> classNodeOpt = graphDatabaseService.findClassNode(fullyQualifiedName);
            if (classNodeOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Class not found in graph database");
                error.put("className", fullyQualifiedName);
                return toJsonResponse(error);
            }

            // Get all edges
            List<GraphEdge> edges = graphDatabaseService.getClassRelationships(fullyQualifiedName);

            // Convert to detailed format
            List<Map<String, Object>> relationships = new ArrayList<>();
            Map<String, Integer> edgeTypeCounts = new HashMap<>();

            String nodeId = classNodeOpt.get().getId();

            for (GraphEdge edge : edges) {
                Map<String, Object> rel = new HashMap<>();

                boolean isOutgoing = edge.getSource().getId().equals(nodeId);
                String edgeType = edge.getEdgeType();

                rel.put("edgeType", edgeType);
                rel.put("direction", isOutgoing ? "outgoing" : "incoming");
                rel.put("sourceId", edge.getSource().getId());
                rel.put("targetId", edge.getTarget().getId());
                rel.put("sourceClass", getClassName(edge.getSource()));
                rel.put("targetClass", getClassName(edge.getTarget()));

                relationships.add(rel);

                // Count by type
                edgeTypeCounts.merge(edgeType, 1, Integer::sum);
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("className", fullyQualifiedName);
            response.put("relationships", relationships);
            response.put("totalRelationships", relationships.size());
            response.put("edgeTypeCounts", edgeTypeCounts);
            response.put("uniqueEdgeTypes", edgeTypeCounts.keySet());

            return toJsonResponse(response);

        } catch (Exception e) {
            logger.error("Error in query_class_relationships tool", e);
            return "{\"success\":false,\"error\":\"Error: " + e.getMessage() + "\"}";
        }
    }

    private String getClassName(GraphNode node) {
        if (node instanceof JavaClassNode) {
            return ((JavaClassNode) node).getFullyQualifiedName();
        }
        return node.getId();
    }

    private String databaseNotAvailableError() {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "Graph database not initialized");
        error.put("message", "Run the analyzer application first to generate the database");
        return toJsonResponse(error);
    }
}
