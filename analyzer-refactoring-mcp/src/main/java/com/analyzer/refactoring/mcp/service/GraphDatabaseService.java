package com.analyzer.refactoring.mcp.service;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.AnalysisConstants;
import com.analyzer.core.db.H2GraphDatabase;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.serialization.JsonSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Service for managing H2 graph database operations in the MCP server.
 * 
 * This service provides read-only access to the project's analysis database,
 * enabling tools to query graph metadata about classes, files, and
 * dependencies.
 * 
 * The database is loaded once at startup and cached in memory for fast access.
 * All operations are read-only to maintain database integrity.
 */
@Service
public class GraphDatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(GraphDatabaseService.class);

    private H2GraphDatabase database;
    private GraphRepository repository;
    private Path projectRoot;
    private boolean initialized = false;

    /**
     * Initialize the database service with the project root path.
     * Attempts to load the H2 database if it exists.
     * 
     * @param projectRoot    The absolute path to the project root directory
     * @param jsonSerializer The JSON serialization service for deserializing nodes
     * @return true if database was successfully loaded, false if database doesn't
     *         exist
     */
    public boolean initialize(Path projectRoot, JsonSerializationService jsonSerializer) {
        this.projectRoot = projectRoot;

        try {
            Path databasePath = projectRoot.resolve(AnalysisConstants.ANALYSIS_DIR)
                    .resolve(AnalysisConstants.GRAPH_DB_NAME);
            Path dbFile = Paths.get(databasePath.toString() + ".mv.db");

            if (!Files.exists(dbFile)) {
                logger.info("No graph database found at: {}", dbFile);
                logger.info("Graph metadata features will not be available.");
                logger.info("Run the analyzer application first to generate the database.");
                initialized = false;
                return false;
            }

            logger.info("Loading graph database from: {}", databasePath);

            // Create load options
            LoadOptions options = LoadOptions.builder()
                    .withProjectRoot(projectRoot)
                    .withDatabasePath(databasePath)
                    .loadAllNodes()
                    .loadAllEdges()
                    .build();

            // Initialize database
            database = new H2GraphDatabase(options, jsonSerializer);
            database.load();

            // Load into memory repository
            repository = database.snapshot();

            int nodeCount = repository.getNodes().size();
            int edgeCount = repository.getAllEdges().size();

            logger.info("Graph database loaded successfully: {} nodes, {} edges", nodeCount, edgeCount);
            initialized = true;
            return true;

        } catch (Exception e) {
            logger.error("Failed to initialize graph database", e);
            initialized = false;
            return false;
        }
    }

    /**
     * Checks if the database service is initialized and ready for queries.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the project root path.
     * 
     * @return The project root path, or null if not initialized
     */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * Find a JavaClassNode by its fully qualified name.
     * 
     * @param fullyQualifiedName The fully qualified class name (e.g.,
     *                           "com.example.MyClass")
     * @return Optional containing the JavaClassNode if found
     */
    public Optional<JavaClassNode> findClassNode(String fullyQualifiedName) {
        if (!initialized) {
            logger.debug("Database not initialized, cannot query for class: {}", fullyQualifiedName);
            return Optional.empty();
        }

        try {
            // Search for the class node by fully qualified name
            for (GraphNode node : repository.getNodes()) {
                if (node instanceof JavaClassNode) {
                    JavaClassNode classNode = (JavaClassNode) node;
                    if (fullyQualifiedName.equals(classNode.getFullyQualifiedName())) {
                        return Optional.of(classNode);
                    }
                }
            }

            logger.debug("JavaClassNode not found: {}", fullyQualifiedName);
            return Optional.empty();

        } catch (Exception e) {
            logger.error("Error finding JavaClassNode: {}", fullyQualifiedName, e);
            return Optional.empty();
        }
    }

    /**
     * Find a ProjectFile by its relative file path.
     * 
     * @param relativePath The relative path from project root (e.g.,
     *                     "src/main/java/MyClass.java")
     * @return Optional containing the ProjectFile if found
     */
    public Optional<ProjectFile> findProjectFile(String relativePath) {
        if (!initialized) {
            logger.debug("Database not initialized, cannot query for file: {}", relativePath);
            return Optional.empty();
        }

        try {
            // Normalize the path for comparison
            Path normalizedPath = Paths.get(relativePath).normalize();

            // Search for the file node
            for (GraphNode node : repository.getNodes()) {
                if (node instanceof ProjectFile) {
                    ProjectFile fileNode = (ProjectFile) node;
                    Path filePath = fileNode.getFilePath();

                    // Compare normalized paths
                    if (filePath != null && filePath.normalize().equals(normalizedPath)) {
                        return Optional.of(fileNode);
                    }
                }
            }

            logger.debug("ProjectFile not found: {}", relativePath);
            return Optional.empty();

        } catch (Exception e) {
            logger.error("Error finding ProjectFile: {}", relativePath, e);
            return Optional.empty();
        }
    }

    /**
     * Get the underlying graph repository for advanced queries.
     * This should be used with caution as it provides direct access to the graph.
     * 
     * @return Optional containing the GraphRepository if initialized
     */
    public Optional<GraphRepository> getRepository() {
        if (!initialized) {
            return Optional.empty();
        }
        return Optional.of(repository);
    }

    /**
     * Get statistics about the loaded graph.
     * 
     * @return GraphStatistics object with node and edge counts
     */
    public GraphStatistics getStatistics() {
        if (!initialized) {
            return new GraphStatistics(0, 0, 0, 0);
        }

        int totalNodes = repository.getNodes().size();
        int totalEdges = repository.getAllEdges().size();

        // Count class nodes
        long classNodes = repository.getNodes().stream()
                .filter(node -> node instanceof JavaClassNode)
                .count();

        // Count file nodes
        long fileNodes = repository.getNodes().stream()
                .filter(node -> node instanceof ProjectFile)
                .count();

        return new GraphStatistics(totalNodes, totalEdges, (int) classNodes, (int) fileNodes);
    }

    /**
     * Find all classes that have a specific tag.
     * 
     * @param tagName The tag name to search for
     * @return List of JavaClassNode with the specified tag
     */
    public java.util.List<JavaClassNode> findClassesByTag(String tagName) {
        if (!initialized) {
            return java.util.Collections.emptyList();
        }

        return repository.getNodes().stream()
                .filter(node -> node instanceof JavaClassNode)
                .map(node -> (JavaClassNode) node)
                .filter(classNode -> classNode.hasTag(tagName))
                .toList();
    }

    /**
     * Find all classes that have a specific tag with a specific value.
     * 
     * @param tagName  The tag name
     * @param tagValue The tag value (as string for comparison)
     * @return List of JavaClassNode with the specified tag and value
     */
    public java.util.List<JavaClassNode> findClassesByTagValue(String tagName, String tagValue) {
        if (!initialized) {
            return java.util.Collections.emptyList();
        }

        // Format tag as "name=value" or just search for classes with the tag
        return repository.getNodes().stream()
                .filter(node -> node instanceof JavaClassNode)
                .map(node -> (JavaClassNode) node)
                .filter(classNode -> classNode.hasTag(tagName))
                .toList();
    }

    /**
     * Get all unique tag names from all classes in the database.
     * 
     * @return Set of all tag names found
     */
    public java.util.Set<String> getAllTagNames() {
        if (!initialized) {
            return java.util.Collections.emptySet();
        }

        return repository.getNodes().stream()
                .filter(node -> node instanceof JavaClassNode)
                .map(node -> (JavaClassNode) node)
                .flatMap(classNode -> classNode.getTags().stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all edges (dependencies/relationships) for a specific class.
     * 
     * @param fullyQualifiedName The class to query
     * @return List of edges where this class is source or target
     */
    public java.util.List<com.analyzer.api.graph.GraphEdge> getClassRelationships(String fullyQualifiedName) {
        if (!initialized) {
            return java.util.Collections.emptyList();
        }

        Optional<JavaClassNode> classNode = findClassNode(fullyQualifiedName);
        if (classNode.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        String nodeId = classNode.get().getId();
        return repository.getAllEdges().stream()
                .filter(edge -> edge.getSource().getId().equals(nodeId) ||
                        edge.getTarget().getId().equals(nodeId))
                .toList();
    }

    /**
     * Statistics about the loaded graph database.
     */
    public static class GraphStatistics {
        private final int totalNodes;
        private final int totalEdges;
        private final int classNodes;
        private final int fileNodes;

        public GraphStatistics(int totalNodes, int totalEdges, int classNodes, int fileNodes) {
            this.totalNodes = totalNodes;
            this.totalEdges = totalEdges;
            this.classNodes = classNodes;
            this.fileNodes = fileNodes;
        }

        public int getTotalNodes() {
            return totalNodes;
        }

        public int getTotalEdges() {
            return totalEdges;
        }

        public int getClassNodes() {
            return classNodes;
        }

        public int getFileNodes() {
            return fileNodes;
        }

        @Override
        public String toString() {
            return String.format("GraphStatistics{totalNodes=%d, totalEdges=%d, classNodes=%d, fileNodes=%d}",
                    totalNodes, totalEdges, classNodes, fileNodes);
        }
    }
}
