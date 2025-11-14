package com.analyzer.refactoring.mcp.tool.graph;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.metrics.Metrics;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.service.PatternMatcherAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueryMetricValuesTool.
 */
class QueryMetricValuesToolTest {

    @Mock
    private GraphDatabaseService graphDatabaseService;

    @Mock
    private PatternMatcherAgent patternMatcherAgent;

    @Mock
    private GraphRepository graphRepository;

    private QueryMetricValuesTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new QueryMetricValuesTool(graphDatabaseService, patternMatcherAgent);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testQueryMetricValuesWhenDatabaseNotInitialized() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(false);

        // When
        String result = tool.queryMetricValues("complexity metrics");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        assertFalse((Boolean) response.get("success"));
        assertEquals("Graph database not initialized", response.get("error"));
    }

    @Test
    void testQueryMetricValuesWithNoMetricsInDatabase() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(true);
        when(graphDatabaseService.getRepository()).thenReturn(Optional.of(graphRepository));
        when(graphRepository.getNodes()).thenReturn(Collections.emptyList());

        // When
        String result = tool.queryMetricValues("complexity metrics");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        assertTrue((Boolean) response.get("success"));
        assertEquals("complexity metrics", response.get("query"));
        assertTrue(((List<?>) response.get("values")).isEmpty());
        assertTrue(response.get("note").toString().contains("No metrics found"));
    }

    @Test
    void testQueryMetricValuesWithNoMatchingMetrics() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(true);

        // Create mock class with metrics
        JavaClassNode mockClass = createMockClassWithMetrics(
                "com.example.TestClass",
                Map.of("cyclomatic_complexity", 10.0));

        when(graphDatabaseService.getRepository()).thenReturn(Optional.of(graphRepository));
        when(graphRepository.getNodes()).thenReturn(List.of(mockClass));
        when(patternMatcherAgent.matchMetricsToQuery(any(), anySet()))
                .thenReturn(Collections.emptyList());

        // When
        String result = tool.queryMetricValues("unknown metric");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        assertTrue((Boolean) response.get("success"));
        assertEquals("unknown metric", response.get("query"));
        assertTrue(((List<?>) response.get("matchedMetrics")).isEmpty());
        assertTrue(((List<?>) response.get("values")).isEmpty());
        assertTrue(response.get("note").toString().contains("No metrics matched"));
    }

    @Test
    void testQueryMetricValuesSuccessful() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(true);

        // Create mock classes with metrics
        JavaClassNode class1 = createMockClassWithMetrics(
                "com.example.Class1",
                Map.of("cyclomatic_complexity", 10.0, "loc", 100.0));
        JavaClassNode class2 = createMockClassWithMetrics(
                "com.example.Class2",
                Map.of("cyclomatic_complexity", 20.0, "loc", 200.0));

        when(graphDatabaseService.getRepository()).thenReturn(Optional.of(graphRepository));
        when(graphRepository.getNodes()).thenReturn(List.of(class1, class2));
        when(patternMatcherAgent.matchMetricsToQuery(any(), anySet()))
                .thenReturn(List.of("cyclomatic_complexity"));

        // When
        String result = tool.queryMetricValues("complexity metrics");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        assertTrue((Boolean) response.get("success"));
        assertEquals("complexity metrics", response.get("query"));

        List<?> matchedMetrics = (List<?>) response.get("matchedMetrics");
        assertEquals(1, matchedMetrics.size());
        assertEquals("cyclomatic_complexity", matchedMetrics.get(0));

        List<?> values = (List<?>) response.get("values");
        assertEquals(2, values.size());

        Map<String, Object> statistics = (Map<String, Object>) response.get("statistics");
        assertNotNull(statistics);
        assertTrue(statistics.containsKey("cyclomatic_complexity"));

        Map<String, Object> complexityStats = (Map<String, Object>) statistics.get("cyclomatic_complexity");
        assertEquals(2, complexityStats.get("count"));
        assertEquals(10.0, complexityStats.get("min"));
        assertEquals(20.0, complexityStats.get("max"));
        assertEquals(15.0, complexityStats.get("avg"));
    }

    @Test
    void testQueryMetricValuesWithMultipleMetrics() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(true);

        JavaClassNode class1 = createMockClassWithMetrics(
                "com.example.Class1",
                Map.of(
                        "cyclomatic_complexity", 10.0,
                        "cognitive_complexity", 8.0,
                        "loc", 100.0));

        when(graphDatabaseService.getRepository()).thenReturn(Optional.of(graphRepository));
        when(graphRepository.getNodes()).thenReturn(List.of(class1));
        when(patternMatcherAgent.matchMetricsToQuery(any(), anySet()))
                .thenReturn(List.of("cyclomatic_complexity", "cognitive_complexity"));

        // When
        String result = tool.queryMetricValues("complexity metrics");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        assertTrue((Boolean) response.get("success"));

        List<?> matchedMetrics = (List<?>) response.get("matchedMetrics");
        assertEquals(2, matchedMetrics.size());

        List<?> values = (List<?>) response.get("values");
        assertEquals(2, values.size()); // 2 metrics for 1 class

        Map<String, Object> statistics = (Map<String, Object>) response.get("statistics");
        assertEquals(2, statistics.size());
        assertTrue(statistics.containsKey("cyclomatic_complexity"));
        assertTrue(statistics.containsKey("cognitive_complexity"));
    }

    @Test
    void testQueryMetricValuesWithClassesWithoutMetrics() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(true);

        JavaClassNode classWithMetrics = createMockClassWithMetrics(
                "com.example.Class1",
                Map.of("cyclomatic_complexity", 10.0));

        JavaClassNode classWithoutMetrics = mock(JavaClassNode.class);
        when(classWithoutMetrics.getMetrics()).thenReturn(null);

        when(graphDatabaseService.getRepository()).thenReturn(Optional.of(graphRepository));
        when(graphRepository.getNodes()).thenReturn(List.of(classWithMetrics, classWithoutMetrics));
        when(patternMatcherAgent.matchMetricsToQuery(any(), anySet()))
                .thenReturn(List.of("cyclomatic_complexity"));

        // When
        String result = tool.queryMetricValues("complexity");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        assertTrue((Boolean) response.get("success"));

        List<?> values = (List<?>) response.get("values");
        assertEquals(1, values.size()); // Only class with metrics
    }

    @Test
    void testStatisticsCalculation() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(true);

        // Create classes with known metric values to test statistics
        List<GraphNode> classes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            classes.add(createMockClassWithMetrics(
                    "com.example.Class" + i,
                    Map.of("loc", (double) (i * 100))));
        }

        when(graphDatabaseService.getRepository()).thenReturn(Optional.of(graphRepository));
        when(graphRepository.getNodes()).thenReturn(classes);
        when(patternMatcherAgent.matchMetricsToQuery(any(), anySet()))
                .thenReturn(List.of("loc"));

        // When
        String result = tool.queryMetricValues("lines of code");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        Map<String, Object> statistics = (Map<String, Object>) response.get("statistics");
        Map<String, Object> locStats = (Map<String, Object>) statistics.get("loc");

        assertEquals(5, locStats.get("count"));
        assertEquals(100.0, locStats.get("min"));
        assertEquals(500.0, locStats.get("max"));
        assertEquals(300.0, locStats.get("avg"));
        assertEquals(300.0, locStats.get("median")); // median of [100, 200, 300, 400, 500]
    }

    @Test
    void testErrorHandling() throws Exception {
        // Given
        when(graphDatabaseService.isInitialized()).thenReturn(true);
        when(graphDatabaseService.getRepository()).thenThrow(new RuntimeException("Database error"));

        // When
        String result = tool.queryMetricValues("complexity");

        // Then
        Map<String, Object> response = objectMapper.readValue(result, Map.class);
        assertFalse((Boolean) response.get("success"));
        assertTrue(response.get("error").toString().contains("Database error"));
    }

    // Helper method to create mock JavaClassNode with metrics
    private JavaClassNode createMockClassWithMetrics(String fqn, Map<String, Double> metricsMap) {
        JavaClassNode mockClass = mock(JavaClassNode.class);
        when(mockClass.getFullyQualifiedName()).thenReturn(fqn);

        Metrics mockMetrics = mock(Metrics.class);
        when(mockMetrics.getAllMetrics()).thenReturn(metricsMap);

        when(mockClass.getMetrics()).thenReturn(mockMetrics);

        return mockClass;
    }
}
