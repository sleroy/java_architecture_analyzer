package com.analyzer.refactoring.mcp.config;

import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.service.PatternMatcherAgent;
import com.analyzer.refactoring.mcp.tool.graph.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for graph database query tools.
 * These tools provide read-only access to the H2 graph database for querying
 * class metadata, dependencies, metrics, and relationships.
 */
@Configuration
public class GraphToolConfiguration {

    @Bean
    public QueryGraphStatisticsTool queryGraphStatisticsTool(
            GraphDatabaseService graphDatabaseService) {
        return new QueryGraphStatisticsTool(graphDatabaseService);
    }

    @Bean
    public QueryClassDependenciesTool queryClassDependenciesTool(
            GraphDatabaseService graphDatabaseService) {
        return new QueryClassDependenciesTool(graphDatabaseService);
    }

    @Bean
    public QueryClassMetricsTool queryClassMetricsTool(
            GraphDatabaseService graphDatabaseService) {
        return new QueryClassMetricsTool(graphDatabaseService);
    }

    @Bean
    public QueryClassesByTagTool queryClassesByTagTool(
            GraphDatabaseService graphDatabaseService,
            PatternMatcherAgent patternMatcherAgent) {
        return new QueryClassesByTagTool(graphDatabaseService, patternMatcherAgent);
    }

    @Bean
    public QueryClassesByPropertyTool queryClassesByPropertyTool(
            GraphDatabaseService graphDatabaseService) {
        return new QueryClassesByPropertyTool(graphDatabaseService);
    }

    @Bean
    public QueryClassRelationshipsTool queryClassRelationshipsTool(
            GraphDatabaseService graphDatabaseService) {
        return new QueryClassRelationshipsTool(graphDatabaseService);
    }

    @Bean
    public QueryMetricValuesTool queryMetricValuesTool(
            GraphDatabaseService graphDatabaseService,
            PatternMatcherAgent patternMatcherAgent) {
        return new QueryMetricValuesTool(graphDatabaseService, patternMatcherAgent);
    }
}
