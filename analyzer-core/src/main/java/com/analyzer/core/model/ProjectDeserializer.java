package com.analyzer.core.model;

import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.serialization.JsonSerializationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Deserializes project analysis data from JSON format.
 * Companion class to AnalysisEngine.GraphSerializer for loading saved project
 * states.
 */
public class ProjectDeserializer {

    private static final Logger logger = LoggerFactory.getLogger(ProjectDeserializer.class);
    private final JsonSerializationService jsonSerializer;
    private final ObjectMapper objectMapper;

    public ProjectDeserializer() {
        this(new JsonSerializationService());
    }

    public ProjectDeserializer(JsonSerializationService jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
        this.objectMapper = jsonSerializer.getObjectMapper();
    }

    /**
     * Load a complete project analysis from JSON file.
     * 
     * @param jsonPath              path to the JSON file containing project
     *                              analysis
     * @param graphRepository       optional graph repository to populate with graph
     *                              data
     * @param projectFileRepository optional project file repository for registering
     *                              loaded files
     * @return the loaded Project object
     * @throws IOException if loading fails
     */
    public Project loadProject(Path jsonPath, GraphRepository graphRepository,
            ProjectFileRepository projectFileRepository) throws IOException {
        if (!Files.exists(jsonPath)) {
            throw new IOException("Project analysis file not found: " + jsonPath);
        }

        logger.info("Loading project analysis from: {}", jsonPath);

        JsonNode root = objectMapper.readTree(jsonPath.toFile());

        // Load project metadata
        String projectName = root.get("projectName").asText();
        String projectPathStr = root.get("projectPath").asText();
        Path projectPath = jsonPath.getParent().resolve(projectPathStr).normalize();

        Project project = new Project(projectPath, projectName, projectFileRepository);

        // Set timestamps
        if (root.has("createdAt") && !root.get("createdAt").isNull()) {
            Date createdAt = objectMapper.convertValue(root.get("createdAt"), Date.class);
            // Note: We can't set createdAt as it's final, but we can log it
            logger.debug("Original project created at: {}", createdAt);
        }

        if (root.has("lastAnalyzed") && !root.get("lastAnalyzed").isNull()) {
            Date lastAnalyzed = objectMapper.convertValue(root.get("lastAnalyzed"), Date.class);
            project.updateLastAnalyzed(); // This sets it to now, we'll need to handle this better
            logger.debug("Original analysis completed at: {}", lastAnalyzed);
        }

        // Load project-level data
        if (root.has("projectData") && !root.get("projectData").isNull()) {
            JsonNode projectDataNode = root.get("projectData");
            Map<String, Object> projectData = objectMapper.convertValue(projectDataNode, Map.class);
            for (Map.Entry<String, Object> entry : projectData.entrySet()) {
                project.setProjectData(entry.getKey(), entry.getValue());
            }
            logger.debug("Loaded {} project data entries", projectData.size());
        }

        // Load project files
        if (root.has("projectFiles") && !root.get("projectFiles").isNull()) {
            JsonNode projectFilesNode = root.get("projectFiles");
            Iterator<Map.Entry<String, JsonNode>> fieldsIterator = projectFilesNode.fields();

            int fileCount = 0;
            while (fieldsIterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = fieldsIterator.next();
                String relativePath = entry.getKey();
                JsonNode fileNode = entry.getValue();

                ProjectFile projectFile = deserializeProjectFile(fileNode, project.getProjectPath());
                project.addProjectFile(projectFile);

                // Register in repository if available
                if (projectFileRepository != null) {
                    projectFileRepository.save(projectFile);
                }

                fileCount++;
            }

            logger.info("Loaded {} project files", fileCount);
        }

        // Load graph data if available and graph repository provided
        if (graphRepository != null && root.has("graphNodes") && !root.get("graphNodes").isNull()) {
            loadGraphData(root, graphRepository);
        }

        return project;
    }

    /**
     * Deserialize a single ProjectFile from JSON node.
     */
    private ProjectFile deserializeProjectFile(JsonNode fileNode, Path projectBasePath) {
        try {
            // Get basic file information
            String relativePath = fileNode.get("relativePath").asText();
            String filePathStr = fileNode.get("filePath").asText();

            // Resolve file path relative to project base
            Path filePath = projectBasePath.resolve(filePathStr).normalize();

            // Create ProjectFile
            ProjectFile projectFile = new ProjectFile(filePath, projectBasePath);

            // Set additional properties if available
            if (fileNode.has("sourceJarPath") && !fileNode.get("sourceJarPath").isNull()) {
                // sourceJarPath is handled internally by ProjectFile based on file path
            }

            if (fileNode.has("discoveredAt") && !fileNode.get("discoveredAt").isNull()) {
                Date discoveredAt = objectMapper.convertValue(fileNode.get("discoveredAt"), Date.class);
                // Note: discoveredAt is set automatically in constructor
                logger.debug("File {} originally discovered at: {}", relativePath, discoveredAt);
            }

            // Load all tags
            if (fileNode.has("allTags") && !fileNode.get("allTags").isNull()) {
                JsonNode tagsNode = fileNode.get("allTags");
                Iterator<Map.Entry<String, JsonNode>> tagsIterator = tagsNode.fields();

                int tagCount = 0;
                while (tagsIterator.hasNext()) {
                    Map.Entry<String, JsonNode> tagEntry = tagsIterator.next();
                    String tagName = tagEntry.getKey();
                    JsonNode tagValueNode = tagEntry.getValue();

                    Object tagValue = jsonSerializer.convertValue(tagValueNode, Object.class);
                    if (tagValue instanceof Boolean && (Boolean) tagValue) {
                        projectFile.enableTag(tagName);
                    } else {
                        projectFile.setProperty(tagName, tagValue);
                    }
                    tagCount++;
                }

                logger.debug("Loaded {} tags for file: {}", tagCount, relativePath);
            }

            // Load metrics if available
            if (fileNode.has("metrics") && !fileNode.get("metrics").isNull()) {
                JsonNode metricsNode = fileNode.get("metrics");
                Iterator<Map.Entry<String, JsonNode>> metricsIterator = metricsNode.fields();

                int metricCount = 0;
                while (metricsIterator.hasNext()) {
                    Map.Entry<String, JsonNode> metricEntry = metricsIterator.next();
                    String metricName = metricEntry.getKey();
                    JsonNode metricValueNode = metricEntry.getValue();

                    if (metricValueNode.isNumber()) {
                        double metricValue = metricValueNode.asDouble();
                        projectFile.getMetrics().setMetric(metricName, metricValue);
                        metricCount++;
                    } else {
                        logger.warn("Skipping non-numeric metric '{}' for file: {}", metricName, relativePath);
                    }
                }

                logger.debug("Loaded {} metrics for file: {}", metricCount, relativePath);
            }

            // Load inspector execution times if available
            if (fileNode.has("allInspectorExecutionTimes") && !fileNode.get("allInspectorExecutionTimes").isNull()) {
                JsonNode executionTimesNode = fileNode.get("allInspectorExecutionTimes");
                Iterator<Map.Entry<String, JsonNode>> timesIterator = executionTimesNode.fields();

                int executionTimeCount = 0;
                while (timesIterator.hasNext()) {
                    Map.Entry<String, JsonNode> timeEntry = timesIterator.next();
                    String inspectorName = timeEntry.getKey();
                    String executionTimeStr = timeEntry.getValue().asText();

                    try {
                        // Parse the execution time and mark inspector as executed
                        // This is mainly for tracking purposes - the actual time format parsing
                        // could be enhanced if needed for exact timestamp recreation
                        projectFile.markInspectorExecuted(inspectorName, null);
                        executionTimeCount++;
                    } catch (Exception e) {
                        logger.debug("Could not parse execution time for inspector {}: {}", inspectorName,
                                e.getMessage());
                    }
                }

                logger.debug("Loaded {} inspector execution times for file: {}", executionTimeCount, relativePath);
            }

            return projectFile;

        } catch (Exception e) {
            logger.error("Error deserializing ProjectFile: {}", e.getMessage());
            throw new RuntimeException("Failed to deserialize ProjectFile", e);
        }
    }

    /**
     * Load graph data into the provided graph repository.
     */
    private void loadGraphData(JsonNode root, GraphRepository graphRepository) {
        try {
            int nodeCount = 0;
            int edgeCount = 0;

            // Load graph nodes
            if (root.has("graphNodes") && !root.get("graphNodes").isNull()) {
                JsonNode nodesArray = root.get("graphNodes");
                for (JsonNode nodeJson : nodesArray) {
                    GraphNode node = jsonSerializer.convertValue(nodeJson, GraphNode.class);
                    graphRepository.getOrCreateNode(node);
                    nodeCount++;
                }
            }

            // Load graph edges
            if (root.has("graphEdges") && !root.get("graphEdges").isNull()) {
                JsonNode edgesArray = root.get("graphEdges");
                for (JsonNode edgeJson : edgesArray) {
                    GraphEdge edge = jsonSerializer.convertValue(edgeJson, GraphEdge.class);
                    // For edges, we need to reconstruct using source and target nodes
                    // This is more complex since we need to find the actual node instances
                    // For now, we'll skip edge reconstruction as it requires proper node resolution
                    logger.debug("Skipping edge reconstruction for edge: {}", edge.getId());
                    edgeCount++;
                }
            }

            logger.info("Loaded graph data: {} nodes, {} edges", nodeCount, edgeCount);

        } catch (Exception e) {
            logger.warn("Error loading graph data: {}", e.getMessage());
        }
    }

    /**
     * Validate that a JSON file contains a valid project analysis.
     * 
     * @param jsonPath path to the JSON file to validate
     * @return validation result
     */
    public ValidationResult validateProjectFile(Path jsonPath) {
        try {
            if (!Files.exists(jsonPath)) {
                return new ValidationResult(false, "File does not exist: " + jsonPath);
            }

            JsonNode root = objectMapper.readTree(jsonPath.toFile());

            // Check required fields
            if (!root.has("projectName")) {
                return new ValidationResult(false, "Missing required field: projectName");
            }

            if (!root.has("projectPath")) {
                return new ValidationResult(false, "Missing required field: projectPath");
            }

            if (!root.has("projectFiles")) {
                return new ValidationResult(false, "Missing required field: projectFiles");
            }

            // Check if project files structure is valid
            JsonNode projectFilesNode = root.get("projectFiles");
            if (!projectFilesNode.isObject()) {
                return new ValidationResult(false, "projectFiles must be an object");
            }

            int fileCount = 0;
            Iterator<Map.Entry<String, JsonNode>> fieldsIterator = projectFilesNode.fields();
            while (fieldsIterator.hasNext()) {
                fieldsIterator.next();
                fileCount++;
            }

            return new ValidationResult(true, "Valid project analysis with " + fileCount + " files");

        } catch (Exception e) {
            return new ValidationResult(false, "JSON parsing error: " + e.getMessage());
        }
    }

    /**
     * Result of validating a project analysis file.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, message='%s'}", valid, message);
        }
    }
}
