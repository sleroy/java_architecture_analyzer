package com.analyzer.core;

import com.analyzer.core.export.ProjectSerializer;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSerializerTest {

    @TempDir
    Path tempDir;

    private ProjectSerializer serializer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        serializer = new ProjectSerializer(tempDir.toFile());
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

        ProjectFile javaFile = createMockProjectFile("Test.java", "com.test.Test");
        Map<String, ProjectFile> files = new HashMap<>();
        files.put("test", javaFile);
        when(project.getProjectFiles()).thenReturn(files);

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/java").toFile();
        assertTrue(nodesDir.exists());
        
        File[] nodeFiles = nodesDir.listFiles((dir, name) -> name.startsWith("node-") && name.endsWith(".json"));
        assertEquals(1, nodeFiles.length);

        JsonNode nodeData = mapper.readTree(nodeFiles[0]);
        assertEquals("java", nodeData.get("type").asText());
        assertEquals("Test.java", nodeData.get("fileName").asText());
        assertEquals("com.test.Test", nodeData.get("fullyQualifiedName").asText());
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

        ProjectFile javaFile = createMockProjectFile("Test.java", "com.test.Test");
        
        Map<String, Object> properties = new HashMap<>();
        String jsonString = "{\"interfaceName\":\"HelloWorldHome\",\"homeType\":\"REMOTE\"}";
        properties.put("ejb.home", jsonString);
        when(javaFile.getNodeProperties()).thenReturn(properties);

        Map<String, ProjectFile> files = new HashMap<>();
        files.put("test", javaFile);
        when(project.getProjectFiles()).thenReturn(files);

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/java").toFile();
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

        ProjectFile javaFile = createMockProjectFile("Test.java", "com.test.Test");

        Map<String, Object> properties = new HashMap<>();
        properties.put("alpha", "value");
        properties.put("gamma", 123);
        when(javaFile.getNodeProperties()).thenReturn(properties);
        when(javaFile.getTags()).thenReturn(Set.of("zeta"));

        Map<String, ProjectFile> files = new HashMap<>();
        files.put("test", javaFile);
        when(project.getProjectFiles()).thenReturn(files);

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/java").toFile();
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

        ProjectFile javaFile = createMockProjectFile("Test.java", "com.test.Test");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("metrics.cloc", 25);
        properties.put("metrics.cyclomatic-complexity", 1);
        when(javaFile.getNodeProperties()).thenReturn(properties);

        Map<String, ProjectFile> files = new HashMap<>();
        files.put("test", javaFile);
        when(project.getProjectFiles()).thenReturn(files);

        serializer.serialize(project);

        File nodesDir = tempDir.resolve("nodes/java").toFile();
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
}
