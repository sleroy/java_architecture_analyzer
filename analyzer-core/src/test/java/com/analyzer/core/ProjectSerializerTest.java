package com.analyzer.core;

import com.analyzer.core.export.ProjectSerializer;
import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSerializerTest {

    @TempDir
    Path tempDir;

    private ProjectSerializer serializer;
    private ObjectMapper mapper;
    private GraphRepository graphRepository;

    @BeforeEach
    void setUp() {
        graphRepository = mock(GraphRepository.class);
        when(graphRepository.getAllEdges()).thenReturn(Collections.emptyList());
        serializer = new ProjectSerializer(tempDir.toFile(), graphRepository);
        mapper = new ObjectMapper();
    }

    @Test
    void testSerializeProject() throws Exception {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test/root"));

        ProjectFile javaFile = createMockProjectFile("Test.java", "com.test.Test");
        Map<String, ProjectFile> files = new HashMap<>();
        files.put("test", javaFile);
        when(project.getProjectFiles()).thenReturn(files);

        serializer.serialize(project);

        File projectJson = tempDir.resolve("project.json").toFile();
        assertTrue(projectJson.exists());

        JsonNode projectData = mapper.readTree(projectJson);
        assertEquals("TestProject", projectData.get("name").asText());
        assertEquals(1, projectData.get("fileCount").asInt());
    }

    @Test
    void testSerializeNodes() throws Exception {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test"));
        when(project.getProjectFiles()).thenReturn(new HashMap<>());

        // Create mock node
        GraphNode mockNode = mock(GraphNode.class);
        when(mockNode.getId()).thenReturn("test-node-id");
        when(mockNode.getNodeType()).thenReturn("project_file");
        when(mockNode.getDisplayLabel()).thenReturn("Test.java");
        when(mockNode.getTags()).thenReturn(Set.of("java"));
        when(mockNode.getNodeProperties()).thenReturn(Map.of("fileName", "Test.java"));

        when(graphRepository.getNodes()).thenReturn(List.of(mockNode));

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/project_file").toFile();
        assertTrue(nodesDir.exists());

        File[] nodeFiles = nodesDir.listFiles((dir, name) -> name.startsWith("node-") && name.endsWith(".json"));
        assertEquals(1, nodeFiles.length);

        JsonNode nodeData = mapper.readTree(nodeFiles[0]);
        assertEquals("test-node-id", nodeData.get("id").asText());
        assertEquals("project_file", nodeData.get("nodeType").asText());
        assertEquals("Test.java", nodeData.get("displayLabel").asText());
    }

    private ProjectFile createMockProjectFile(String fileName, String fqn) {
        ProjectFile file = mock(ProjectFile.class);
        when(file.getFileName()).thenReturn(fileName);
        when(file.getProperty("fullyQualifiedName")).thenReturn(fqn);
        when(file.getRelativePath()).thenReturn("src/main/java/" + fileName);
        when(file.getFilePath()).thenReturn(Paths.get("/test/" + fileName));
        when(file.getTags()).thenReturn(new HashSet<>());
        when(file.getNodeProperties()).thenReturn(new HashMap<>());
        return file;
    }

    @Test
    void testSerializeNodeWithJsonStringProperty() throws Exception {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test"));
        when(project.getProjectFiles()).thenReturn(new HashMap<>());

        // Create mock node with JSON property
        Map<String, Object> properties = new HashMap<>();
        String jsonString = "{\"interfaceName\":\"HelloWorldHome\",\"homeType\":\"REMOTE\"}";
        properties.put("ejb.home", jsonString);

        GraphNode mockNode = mock(GraphNode.class);
        when(mockNode.getId()).thenReturn("test-node");
        when(mockNode.getNodeType()).thenReturn("class_node");
        when(mockNode.getDisplayLabel()).thenReturn("Test");
        when(mockNode.getTags()).thenReturn(Set.of());
        when(mockNode.getNodeProperties()).thenReturn(properties);

        when(graphRepository.getNodes()).thenReturn(List.of(mockNode));

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/class_node").toFile();
        File[] nodeFiles = nodesDir.listFiles((dir, name) -> name.startsWith("node-") && name.endsWith(".json"));
        assertEquals(1, nodeFiles.length);

        JsonNode nodeData = mapper.readTree(nodeFiles[0]);
        JsonNode propertiesNode = nodeData.get("properties");
        assertNotNull(propertiesNode);

        JsonNode ejbNode = propertiesNode.get("ejb");
        assertNotNull(ejbNode);

        JsonNode homeNode = ejbNode.get("home");
        assertTrue(homeNode.isObject());
        assertEquals("HelloWorldHome", homeNode.get("interfaceName").asText());
        assertEquals("REMOTE", homeNode.get("homeType").asText());
    }

    @Test
    void testClearOutputDirectory() throws Exception {
        // Arrange
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test"));
        when(project.getProjectFiles()).thenReturn(new HashMap<>());

        // Create a dummy file in the output directory
        File dummyFile = tempDir.resolve("dummy.txt").toFile();
        assertTrue(dummyFile.createNewFile());
        assertTrue(dummyFile.exists());

        // Act
        serializer.serialize(project);

        // Assert
        assertFalse(dummyFile.exists(), "Dummy file should have been deleted");
        assertTrue(tempDir.resolve("project.json").toFile().exists(), "New project file should be created");
    }

    @Test
    void testTagAndPropertySerialization() throws Exception {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test"));
        when(project.getProjectFiles()).thenReturn(new HashMap<>());

        // Create mock node with tags and properties
        Map<String, Object> properties = new HashMap<>();
        properties.put("alpha", "value");
        properties.put("gamma", 123);

        GraphNode mockNode = mock(GraphNode.class);
        when(mockNode.getId()).thenReturn("test-node");
        when(mockNode.getNodeType()).thenReturn("project_file");
        when(mockNode.getDisplayLabel()).thenReturn("Test.java");
        when(mockNode.getTags()).thenReturn(Set.of("zeta"));
        when(mockNode.getNodeProperties()).thenReturn(properties);

        when(graphRepository.getNodes()).thenReturn(List.of(mockNode));

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/project_file").toFile();
        File[] nodeFiles = nodesDir.listFiles((dir, name) -> name.startsWith("node-") && name.endsWith(".json"));
        assertEquals(1, nodeFiles.length);

        JsonNode nodeData = mapper.readTree(nodeFiles[0]);

        // Verify tags are a simple array
        JsonNode tagsNode = nodeData.get("tags");
        assertNotNull(tagsNode);
        assertTrue(tagsNode.isArray());
        assertEquals(1, tagsNode.size());
        assertEquals("zeta", tagsNode.get(0).asText());

        // Verify properties are nested correctly
        JsonNode propsNode = nodeData.get("properties");
        assertNotNull(propsNode);
        assertTrue(propsNode.isObject());
        assertEquals("value", propsNode.get("alpha").asText());
        assertEquals(123, propsNode.get("gamma").asInt());
    }

    @Test
    void testMetricPrefixing() throws Exception {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test"));
        when(project.getProjectFiles()).thenReturn(new HashMap<>());

        // Create mock node with nested metrics
        Map<String, Object> properties = new HashMap<>();
        properties.put("metrics.cloc", 25);
        properties.put("metrics.cyclomatic-complexity", 1);

        GraphNode mockNode = mock(GraphNode.class);
        when(mockNode.getId()).thenReturn("test-node");
        when(mockNode.getNodeType()).thenReturn("class_node");
        when(mockNode.getDisplayLabel()).thenReturn("Test");
        when(mockNode.getTags()).thenReturn(Set.of());
        when(mockNode.getNodeProperties()).thenReturn(properties);

        when(graphRepository.getNodes()).thenReturn(List.of(mockNode));

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/class_node").toFile();
        File[] nodeFiles = nodesDir.listFiles((dir, name) -> name.startsWith("node-") && name.endsWith(".json"));
        assertEquals(1, nodeFiles.length);

        JsonNode nodeData = mapper.readTree(nodeFiles[0]);
        JsonNode propertiesNode = nodeData.get("properties");
        assertNotNull(propertiesNode);

        JsonNode metricsNode = propertiesNode.get("metrics");
        assertNotNull(metricsNode);

        assertEquals(25, metricsNode.get("cloc").asInt());
        assertEquals(1, metricsNode.get("cyclomatic-complexity").asInt());
    }

    @Test
    void testSerializeEdges() throws Exception {
        // Arrange
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test"));
        when(project.getProjectFiles()).thenReturn(new HashMap<>());

        // Create mock nodes
        GraphNode sourceNode = mock(GraphNode.class);
        when(sourceNode.getId()).thenReturn("/test/Source.java");

        GraphNode targetNode = mock(GraphNode.class);
        when(targetNode.getId()).thenReturn("/test/Target.java");

        // Create mock edge
        GraphEdge edge = mock(GraphEdge.class);
        when(edge.getId()).thenReturn("edge_123");
        when(edge.getEdgeType()).thenReturn("depends_on");
        when(edge.getSource()).thenReturn(sourceNode);
        when(edge.getTarget()).thenReturn(targetNode);

        Map<String, Object> edgeProperties = new HashMap<>();
        edgeProperties.put("strength", "strong");
        when(edge.getProperties()).thenReturn(edgeProperties);

        // Configure repository to return the edge
        when(graphRepository.getAllEdges()).thenReturn(List.of(edge));

        // Act
        serializer.serialize(project);

        // Assert
        File edgesDir = tempDir.resolve("edges/depends_on").toFile();
        assertTrue(edgesDir.exists(), "Edge type directory should be created");

        File[] edgeFiles = edgesDir.listFiles((dir, name) -> name.startsWith("edge-") && name.endsWith(".json"));
        assertNotNull(edgeFiles);
        assertEquals(1, edgeFiles.length, "Should have one edge file");

        JsonNode edgeData = mapper.readTree(edgeFiles[0]);
        assertEquals("edge_123", edgeData.get("id").asText());
        assertEquals("depends_on", edgeData.get("edgeType").asText());
        assertEquals("/test/Source.java", edgeData.get("sourceId").asText());
        assertEquals("/test/Target.java", edgeData.get("targetId").asText());

        JsonNode properties = edgeData.get("properties");
        assertNotNull(properties);
        assertEquals("strong", properties.get("strength").asText());
    }

    @Test
    void testSerializeMultipleEdgeTypes() throws Exception {
        // Arrange
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("TestProject");
        when(project.getProjectPath()).thenReturn(Paths.get("/test"));
        when(project.getProjectFiles()).thenReturn(new HashMap<>());

        GraphNode node1 = mock(GraphNode.class);
        when(node1.getId()).thenReturn("/test/Node1.java");

        GraphNode node2 = mock(GraphNode.class);
        when(node2.getId()).thenReturn("/test/Node2.java");

        // Create edges of different types
        GraphEdge dependsEdge = mock(GraphEdge.class);
        when(dependsEdge.getId()).thenReturn("edge_dep_1");
        when(dependsEdge.getEdgeType()).thenReturn("depends_on");
        when(dependsEdge.getSource()).thenReturn(node1);
        when(dependsEdge.getTarget()).thenReturn(node2);
        when(dependsEdge.getProperties()).thenReturn(new HashMap<>());

        GraphEdge implementsEdge = mock(GraphEdge.class);
        when(implementsEdge.getId()).thenReturn("edge_impl_1");
        when(implementsEdge.getEdgeType()).thenReturn("implements");
        when(implementsEdge.getSource()).thenReturn(node2);
        when(implementsEdge.getTarget()).thenReturn(node1);
        when(implementsEdge.getProperties()).thenReturn(new HashMap<>());

        when(graphRepository.getAllEdges()).thenReturn(List.of(dependsEdge, implementsEdge));

        // Act
        serializer.serialize(project);

        // Assert - verify both edge type directories exist
        File dependsDir = tempDir.resolve("edges/depends_on").toFile();
        assertTrue(dependsDir.exists(), "depends_on directory should exist");

        File implementsDir = tempDir.resolve("edges/implements").toFile();
        assertTrue(implementsDir.exists(), "implements directory should exist");

        // Verify each has one edge file
        File[] dependsFiles = dependsDir.listFiles((dir, name) -> name.startsWith("edge-") && name.endsWith(".json"));
        assertNotNull(dependsFiles);
        assertEquals(1, dependsFiles.length);

        File[] implementsFiles = implementsDir
                .listFiles((dir, name) -> name.startsWith("edge-") && name.endsWith(".json"));
        assertNotNull(implementsFiles);
        assertEquals(1, implementsFiles.length);
    }
}
