package com.analyzer.core;

import com.analyzer.core.model.ProjectFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for metrics serialization and deserialization in BaseGraphNode.
 */
class MetricsSerializationTest {

    @Test
    void testMetricsJsonSerialization() throws Exception {
        // Create a ProjectFile with some metrics
        Path filePath = Paths.get("/test/path/TestClass.java");
        Path projectRoot = Paths.get("/test/path");
        ProjectFile projectFile = new ProjectFile(filePath, projectRoot);

        // Set some metrics
        projectFile.getMetrics().setMetric("complexity", 15);
        projectFile.getMetrics().setMetric("linesOfCode", 250);
        projectFile.getMetrics().setMetric("coverage", 0.85);

        // Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(projectFile);

        // Verify JSON contains metrics
        assertTrue(json.contains("\"metrics\""), "JSON should contain metrics field");
        assertTrue(json.contains("\"complexity\""), "JSON should contain complexity metric");
        assertTrue(json.contains("\"linesOfCode\""), "JSON should contain linesOfCode metric");
        assertTrue(json.contains("\"coverage\""), "JSON should contain coverage metric");

        System.out.println("Serialized JSON:\n" + json);

        // Deserialize back
        ProjectFile deserialized = mapper.readValue(json, ProjectFile.class);

        // Verify metrics are restored
        assertNotNull(deserialized.getMetrics(), "Metrics should not be null");
        
        Map<String, Double> metrics = deserialized.getMetrics().getAllMetrics();
        assertEquals(3, metrics.size(), "Should have 3 metrics");
        
        assertEquals(15.0, deserialized.getMetrics().getMetric("complexity").doubleValue(), 0.001);
        assertEquals(250.0, deserialized.getMetrics().getMetric("linesOfCode").doubleValue(), 0.001);
        assertEquals(0.85, deserialized.getMetrics().getMetric("coverage").doubleValue(), 0.001);
    }

    @Test
    void testEmptyMetricsSerialization() throws Exception {
        // Create a ProjectFile without metrics
        Path filePath = Paths.get("/test/path/EmptyClass.java");
        Path projectRoot = Paths.get("/test/path");
        ProjectFile projectFile = new ProjectFile(filePath, projectRoot);

        // Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(projectFile);

        // Deserialize back
        ProjectFile deserialized = mapper.readValue(json, ProjectFile.class);

        // Verify empty metrics
        assertNotNull(deserialized.getMetrics(), "Metrics should not be null");
        Map<String, Double> metrics = deserialized.getMetrics().getAllMetrics();
        assertEquals(0, metrics.size(), "Should have 0 metrics");
    }

    @Test
    void testMetricsUpdateAfterDeserialization() throws Exception {
        // Create and serialize
        Path filePath = Paths.get("/test/path/TestClass.java");
        Path projectRoot = Paths.get("/test/path");
        ProjectFile projectFile = new ProjectFile(filePath, projectRoot);
        projectFile.getMetrics().setMetric("initial", 100);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(projectFile);

        // Deserialize
        ProjectFile deserialized = mapper.readValue(json, ProjectFile.class);

        // Update metrics on deserialized object
        deserialized.getMetrics().setMetric("newMetric", 200);
        deserialized.getMetrics().setMetric("initial", 150);

        // Verify updates work
        assertEquals(150.0, deserialized.getMetrics().getMetric("initial").doubleValue(), 0.001);
        assertEquals(200.0, deserialized.getMetrics().getMetric("newMetric").doubleValue(), 0.001);
        assertEquals(2, deserialized.getMetrics().getAllMetrics().size());
    }

    @Test
    void testMetricsWithNullAndRemoval() throws Exception {
        Path filePath = Paths.get("/test/path/TestClass.java");
        Path projectRoot = Paths.get("/test/path");
        ProjectFile projectFile = new ProjectFile(filePath, projectRoot);

        // Set metrics
        projectFile.getMetrics().setMetric("metric1", 10);
        projectFile.getMetrics().setMetric("metric2", 20);
        projectFile.getMetrics().setMetric("metric3", 30);

        assertEquals(3, projectFile.getMetrics().getAllMetrics().size());

        // Remove one by setting to null
        projectFile.getMetrics().setMetric("metric2", null);

        assertEquals(2, projectFile.getMetrics().getAllMetrics().size());
        assertNull(projectFile.getMetrics().getMetric("metric2"));
        assertNotNull(projectFile.getMetrics().getMetric("metric1"));
        assertNotNull(projectFile.getMetrics().getMetric("metric3"));

        // Serialize and deserialize
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(projectFile);
        ProjectFile deserialized = mapper.readValue(json, ProjectFile.class);

        // Verify state is preserved
        assertEquals(2, deserialized.getMetrics().getAllMetrics().size());
        assertNull(deserialized.getMetrics().getMetric("metric2"));
    }
}
