package com.analyzer.refactoring.mcp.service;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.serialization.JsonSerializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GraphDatabaseService.
 * 
 * Note: These tests verify the service behavior without requiring an actual
 * database.
 * They test initialization, error handling, and service state management.
 */
class GraphDatabaseServiceTest {

    private GraphDatabaseService service;
    private JsonSerializationService jsonSerializer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new GraphDatabaseService();
        jsonSerializer = new JsonSerializationService();
    }

    @Test
    void testInitializationWithoutDatabase() {
        // Initialize with a directory that doesn't have a database
        boolean initialized = service.initialize(tempDir, jsonSerializer);

        // Should return false but not throw exception
        assertFalse(initialized);
        assertFalse(service.isInitialized());
        assertEquals(tempDir, service.getProjectRoot());
    }

    @Test
    void testUninitializedServiceState() {
        // Service should start uninitialized
        assertFalse(service.isInitialized());
        assertNull(service.getProjectRoot());
    }

    @Test
    void testFindClassNodeWhenNotInitialized() {
        // Should return empty Optional when not initialized
        Optional<JavaClassNode> result = service.findClassNode("com.example.MyClass");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindProjectFileWhenNotInitialized() {
        // Should return empty Optional when not initialized
        Optional<ProjectFile> result = service.findProjectFile("src/main/java/MyClass.java");
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetRepositoryWhenNotInitialized() {
        // Should return empty Optional when not initialized
        Optional<GraphRepository> result = service.getRepository();
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetStatisticsWhenNotInitialized() {
        // Should return zero statistics when not initialized
        GraphDatabaseService.GraphStatistics stats = service.getStatistics();
        assertNotNull(stats);
        assertEquals(0, stats.getTotalNodes());
        assertEquals(0, stats.getTotalEdges());
        assertEquals(0, stats.getClassNodes());
        assertEquals(0, stats.getFileNodes());
    }

    @Test
    void testStatisticsToString() {
        GraphDatabaseService.GraphStatistics stats = new GraphDatabaseService.GraphStatistics(100, 200, 30, 70);

        String str = stats.toString();
        assertTrue(str.contains("totalNodes=100"));
        assertTrue(str.contains("totalEdges=200"));
        assertTrue(str.contains("classNodes=30"));
        assertTrue(str.contains("fileNodes=70"));
    }

    @Test
    void testStatisticsGetters() {
        GraphDatabaseService.GraphStatistics stats = new GraphDatabaseService.GraphStatistics(100, 200, 30, 70);

        assertEquals(100, stats.getTotalNodes());
        assertEquals(200, stats.getTotalEdges());
        assertEquals(30, stats.getClassNodes());
        assertEquals(70, stats.getFileNodes());
    }

    @Test
    void testInitializeWithNonExistentAnalyzerDirectory() throws Exception {
        // Create temp directory without .analyzer subdirectory
        Path projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot);

        boolean initialized = service.initialize(projectRoot, jsonSerializer);

        assertFalse(initialized);
        assertFalse(service.isInitialized());
        assertEquals(projectRoot, service.getProjectRoot());
    }

    @Test
    void testInitializeWithAnalyzerDirButNoDatabase() throws Exception {
        // Create .analyzer directory but no database file
        Path projectRoot = tempDir.resolve("test-project");
        Path analyzerDir = projectRoot.resolve(".analyzer");
        Files.createDirectories(analyzerDir);

        boolean initialized = service.initialize(projectRoot, jsonSerializer);

        assertFalse(initialized);
        assertFalse(service.isInitialized());
    }

    @Test
    void testProjectRootAfterInitialization() {
        service.initialize(tempDir, jsonSerializer);
        assertEquals(tempDir, service.getProjectRoot());
    }

    @Test
    void testMultipleInitializationAttempts() {
        // First initialization
        boolean first = service.initialize(tempDir, jsonSerializer);
        assertFalse(first);

        // Second initialization with same path
        boolean second = service.initialize(tempDir, jsonSerializer);
        assertFalse(second);

        // Service state should reflect last initialization attempt
        assertEquals(tempDir, service.getProjectRoot());
    }

    @Test
    void testFindClassNodeWithNullFQN() {
        service.initialize(tempDir, jsonSerializer);

        // Should handle null gracefully by returning empty Optional
        Optional<JavaClassNode> result = service.findClassNode(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindProjectFileWithNullPath() {
        service.initialize(tempDir, jsonSerializer);

        // Should handle null gracefully by returning empty Optional
        Optional<ProjectFile> result = service.findProjectFile(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindClassNodeWithEmptyFQN() {
        service.initialize(tempDir, jsonSerializer);

        // Should return empty Optional for empty string
        Optional<JavaClassNode> result = service.findClassNode("");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindProjectFileWithEmptyPath() {
        service.initialize(tempDir, jsonSerializer);

        // Should return empty Optional for empty string
        Optional<ProjectFile> result = service.findProjectFile("");
        assertTrue(result.isEmpty());
    }
}
