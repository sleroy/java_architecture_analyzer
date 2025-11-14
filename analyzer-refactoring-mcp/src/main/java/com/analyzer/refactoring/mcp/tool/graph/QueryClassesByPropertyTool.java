package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP tool for querying classes by property values.
 * 
 * Properties are custom key-value pairs stored on graph nodes during analysis.
 * Examples: packageName, visibility, isAbstract, lineCount, etc.
 */
@Component
public class QueryClassesByPropertyTool extends BaseRefactoringTool {

    private final GraphDatabaseService graphDatabaseService;

    public QueryClassesByPropertyTool(GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService;
    }

    @Tool(description = "Query classes by property name and optional value. " +
            "Properties are key-value pairs stored during analysis (e.g., packageName, visibility, isAbstract). " +
            "If only propertyName is provided, returns all classes that have that property. " +
            "If propertyValue is also provided, returns only classes where property equals that value. " +
            "Use this to find classes by specific characteristics stored as properties.")
    public String queryClassesByProperty(
            @ToolParam(description = "Property name to search for (e.g., 'packageName', 'visibility')") String propertyName,

            @ToolParam(description = "Optional property value to match (e.g., 'public', 'abstract'). If not provided, returns all classes with this property") String propertyValue) {
        try {
            logger.info("Tool called: query_classes_by_property for property: {}, value: {}",
                    propertyName, propertyValue);

            if (!graphDatabaseService.isInitialized()) {
                return databaseNotAvailableError();
            }

            Optional<com.analyzer.api.graph.GraphRepository> repoOpt = graphDatabaseService.getRepository();
            if (repoOpt.isEmpty()) {
                return databaseNotAvailableError();
            }

            com.analyzer.api.graph.GraphRepository repository = repoOpt.get();

            // Query classes
            List<Map<String, Object>> matchedClasses = new ArrayList<>();

            for (GraphNode node : repository.getNodes()) {
                if (node instanceof JavaClassNode) {
                    JavaClassNode classNode = (JavaClassNode) node;

                    // Check if property exists
                    Object propValue = classNode.getProperty(propertyName);
                    if (propValue == null) {
                        continue;
                    }

                    // If value filter specified, check it matches
                    if (propertyValue != null && !propertyValue.isEmpty()) {
                        if (!propValue.toString().equals(propertyValue)) {
                            continue;
                        }
                    }

                    // Match found
                    Map<String, Object> classInfo = new HashMap<>();
                    classInfo.put("fullyQualifiedName", classNode.getFullyQualifiedName());
                    classInfo.put("propertyValue", propValue);
                    classInfo.put("methodCount", classNode.getMethodCount());
                    classInfo.put("fieldCount", classNode.getFieldCount());
                    classInfo.put("allProperties", classNode.getProperties());

                    matchedClasses.add(classInfo);
                }
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("propertyName", propertyName);
            response.put("propertyValue", propertyValue);
            response.put("matchedClasses", matchedClasses);
            response.put("totalMatches", matchedClasses.size());

            return toJsonResponse(response);

        } catch (Exception e) {
            logger.error("Error in query_classes_by_property tool", e);
            return "{\"success\":false,\"error\":\"Error: " + e.getMessage() + "\"}";
        }
    }

    private String databaseNotAvailableError() {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", "Graph database not initialized");
        error.put("message", "Run the analyzer application first to generate the database");
        return toJsonResponse(error);
    }
}
