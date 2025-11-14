package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.service.PatternMatcherAgent;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP tool for querying classes by tags using natural language.
 * 
 * This tool uses AI (Bedrock) to match natural language queries to actual tag
 * names,
 * making it easy to find classes without knowing exact tag names.
 * 
 * Examples:
 * - "stateless EJBs" → matches tag "ejb.type=stateless"
 * - "high complexity" → matches tag "complexity.level=high"
 * - "services" → matches tag "layer=service"
 */
@Component
public class QueryClassesByTagTool extends BaseRefactoringTool {

    private final GraphDatabaseService graphDatabaseService;
    private final PatternMatcherAgent patternMatcherAgent;

    public QueryClassesByTagTool(
            GraphDatabaseService graphDatabaseService,
            PatternMatcherAgent patternMatcherAgent) {
        this.graphDatabaseService = graphDatabaseService;
        this.patternMatcherAgent = patternMatcherAgent;
    }

    @Tool(description = "Query classes using natural language tag description (AI-enhanced). " +
            "Describe what you're looking for (e.g., 'stateless EJBs', 'high complexity classes', 'service layer') " +
            "and the tool will use AI to match your description to actual tags in the database, then return matching classes. "
            +
            "Returns list of classes with their tags, metrics, and basic information. " +
            "Use this when you want to find classes by characteristics without knowing exact tag names.")
    public String queryClassesByTag(
            @ToolParam(description = "Natural language description of what you're looking for (e.g., 'stateless EJBs', 'complex classes')") String naturalLanguageQuery) {
        try {
            logger.info("Tool called: query_classes_by_tag with query: {}", naturalLanguageQuery);

            if (!graphDatabaseService.isInitialized()) {
                return databaseNotAvailableError();
            }

            // Get all available tags from database
            Set<String> availableTags = graphDatabaseService.getAllTagNames();

            if (availableTags.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("query", naturalLanguageQuery);
                response.put("matchedClasses", Collections.emptyList());
                response.put("note", "No tags found in database - classes may not be tagged yet");
                return toJsonResponse(response);
            }

            logger.info("Found {} unique tags in database", availableTags.size());

            // Use AI to match natural language to actual tags
            List<String> matchedTags = patternMatcherAgent.matchTagsToQuery(
                    naturalLanguageQuery,
                    availableTags);

            logger.info("AI matched query to {} tags: {}", matchedTags.size(), matchedTags);

            // Query classes with matched tags
            List<Map<String, Object>> matchedClasses = new ArrayList<>();
            Set<String> processedClasses = new HashSet<>();

            for (String tag : matchedTags) {
                List<JavaClassNode> classes = graphDatabaseService.findClassesByTag(tag);

                for (JavaClassNode classNode : classes) {
                    String fqn = classNode.getFullyQualifiedName();

                    // Avoid duplicates
                    if (processedClasses.contains(fqn)) {
                        continue;
                    }
                    processedClasses.add(fqn);

                    Map<String, Object> classInfo = new HashMap<>();
                    classInfo.put("fullyQualifiedName", fqn);
                    classInfo.put("matchedTag", tag);
                    classInfo.put("allTags", classNode.getTags());
                    classInfo.put("methodCount", classNode.getMethodCount());
                    classInfo.put("fieldCount", classNode.getFieldCount());
                    classInfo.put("sourceType", classNode.getSourceType());

                    matchedClasses.add(classInfo);
                }
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", naturalLanguageQuery);
            response.put("matchedTags", matchedTags);
            response.put("availableTagsCount", availableTags.size());
            response.put("matchedClasses", matchedClasses);
            response.put("totalMatches", matchedClasses.size());

            return toJsonResponse(response);

        } catch (Exception e) {
            logger.error("Error in query_classes_by_tag tool", e);
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
