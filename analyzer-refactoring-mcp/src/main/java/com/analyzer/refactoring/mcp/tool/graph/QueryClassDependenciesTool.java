package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP tool for querying class dependencies with full edge type information.
 * 
 * This tool returns all dependencies for a class, including:
 * - Outgoing dependencies (classes this class depends on)
 * - Incoming dependencies (classes that depend on this class)
 * - Edge types (DEPENDS_ON, IMPORTS, EXTENDS, IMPLEMENTS, CALLS, etc.)
 */
@Component
public class QueryClassDependenciesTool extends BaseRefactoringTool {

    private final GraphDatabaseService graphDatabaseService;

    public QueryClassDependenciesTool(GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService;
    }

    @Tool(description = "Query all dependencies and relationships for a Java class. " +
            "Returns outgoing dependencies (classes this class depends on) and incoming dependencies (classes that depend on this). "
            +
            "Each dependency includes the edge type (DEPENDS_ON, IMPORTS, EXTENDS, IMPLEMENTS, CALLS, USES, etc.) " +
            "and target/source class information. " +
            "Use this to understand class coupling, find impact of changes, or analyze dependency structure.")
    public String queryClassDependencies(
            @ToolParam(description = "Fully qualified name of the class to analyze") String fullyQualifiedName) {
        try {
            logger.info("Tool called: query_class_dependencies for {}", fullyQualifiedName);

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

            JavaClassNode classNode = classNodeOpt.get();
            String nodeId = classNode.getId();

            // Get all edges for this class
            List<GraphEdge> allEdges = graphDatabaseService.getClassRelationships(fullyQualifiedName);

            // Separate into outgoing and incoming
            List<Map<String, Object>> outgoingDeps = new ArrayList<>();
            List<Map<String, Object>> incomingDeps = new ArrayList<>();

            for (GraphEdge edge : allEdges) {
                boolean isOutgoing = edge.getSource().getId().equals(nodeId);

                Map<String, Object> dep = new HashMap<>();
                dep.put("edgeType", edge.getEdgeType());

                if (isOutgoing) {
                    // This class depends on target
                    dep.put("targetClass", getClassName(edge.getTarget()));
                    dep.put("targetId", edge.getTarget().getId());
                    dep.put("relationship", "depends on");
                    outgoingDeps.add(dep);
                } else {
                    // Target depends on this class
                    dep.put("sourceClass", getClassName(edge.getSource()));
                    dep.put("sourceId", edge.getSource().getId());
                    dep.put("relationship", "depended by");
                    incomingDeps.add(dep);
                }
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("className", fullyQualifiedName);
            response.put("outgoingDependencies", outgoingDeps);
            response.put("incomingDependencies", incomingDeps);
            response.put("totalOutgoing", outgoingDeps.size());
            response.put("totalIncoming", incomingDeps.size());

            // Group by edge type for summary
            Map<String, Long> outgoingByType = outgoingDeps.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            d -> (String) d.get("edgeType"),
                            java.util.stream.Collectors.counting()));

            Map<String, Long> incomingByType = incomingDeps.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            d -> (String) d.get("edgeType"),
                            java.util.stream.Collectors.counting()));

            response.put("outgoingByEdgeType", outgoingByType);
            response.put("incomingByEdgeType", incomingByType);

            return toJsonResponse(response);

        } catch (Exception e) {
            logger.error("Error in query_class_dependencies tool", e);
            return "{\"success\":false,\"error\":\"Error: " + e.getMessage() + "\"}";
        }
    }

    private String getClassName(com.analyzer.api.graph.GraphNode node) {
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
