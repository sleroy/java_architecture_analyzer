package com.analyzer.core.export;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.model.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProjectSerializer {

    private final ObjectMapper mapper;
    private final File outputDir;
    private final GraphRepository graphRepository;

    public ProjectSerializer(File outputDir, GraphRepository graphRepository) {
        this.outputDir = outputDir;
        this.graphRepository = Objects.requireNonNull(graphRepository, "GraphRepository cannot be null");
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void serialize(Project project) throws IOException {
        clearOutputDirectory();
        createDirectories();

        // Serialize all nodes from GraphRepository (includes both ProjectFiles and
        // ClassNodes)
        serializeNodes();

        // Serialize edges
        serializeEdges();

        // Serialize project metadata
        serializeProject(project);
    }

    private void serializeNodes() throws IOException {
        var nodes = graphRepository.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        for (GraphNode node : nodes) {
            serializeNode(node);
        }
    }

    private void serializeEdges() throws IOException {
        var edges = graphRepository.getAllEdges();
        if (edges == null || edges.isEmpty()) {
            return;
        }

        for (GraphEdge edge : edges) {
            serializeEdge(edge);
        }
    }

    private void serializeEdge(GraphEdge edge) throws IOException {
        String edgeType = edge.getEdgeType();
        String id = edge.getId();

        Map<String, Object> edgeData = new HashMap<>();
        edgeData.put("id", id);
        edgeData.put("sourceId", edge.getSource().getId());
        edgeData.put("targetId", edge.getTarget().getId());
        edgeData.put("edgeType", edgeType);
        edgeData.put("properties", edge.getProperties());

        // Create subdirectory for edge type
        Path edgeTypeDir = outputDir.toPath().resolve("edges").resolve(edgeType);
        Files.createDirectories(edgeTypeDir);

        File edgeFile = edgeTypeDir.resolve("edge-" + id + ".json").toFile();
        mapper.writeValue(edgeFile, edgeData);
    }

    private void clearOutputDirectory() throws IOException {
        if (outputDir.exists()) {
            // Recursively delete contents
            try (var paths = Files.walk(outputDir.toPath())) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    private void createDirectories() throws IOException {
        Files.createDirectories(outputDir.toPath().resolve("nodes"));
        Files.createDirectories(outputDir.toPath().resolve("edges"));
    }

    private void serializeNode(GraphNode node) throws IOException {
        String nodeType = node.getNodeType();
        String id = node.getId();

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("id", id);
        nodeData.put("nodeType", nodeType);

        // Add display label
        nodeData.put("displayLabel", node.getDisplayLabel());

        // Add tags
        nodeData.put("tags", node.getTags());

        // Add properties - transform dotted keys into nested structures
        Map<String, Object> properties = node.getNodeProperties();
        if (properties != null && !properties.isEmpty()) {
            Map<String, Object> processedProperties = new HashMap<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String) {
                    String strValue = ((String) value).trim();
                    if (strValue.startsWith("{") && strValue.endsWith("}")) {
                        try {
                            Object parsedJson = mapper.readValue(strValue, Object.class);
                            processedProperties.put(key, parsedJson);
                        } catch (IOException e) {
                            // Not valid JSON, keep original value
                            processedProperties.put(key, value);
                        }
                    } else {
                        processedProperties.put(key, value);
                    }
                } else {
                    processedProperties.put(key, value);
                }
            }
            Map<String, Object> nestedProperties = PropertyNestingTransformer.nestProperties(processedProperties);
            nodeData.put("properties", nestedProperties);
        }

        // Organize nodes by their type (project_file, class_node, etc.)
        Path nodeDir = outputDir.toPath().resolve("nodes").resolve(nodeType);
        Files.createDirectories(nodeDir);

        // Generate safe file name from id
        String safeId = id.replaceAll("[^a-zA-Z0-9._-]", "_");
        File nodeFile = nodeDir.resolve("node-" + safeId + ".json").toFile();
        mapper.writeValue(nodeFile, nodeData);
    }

    private void serializeProject(Project project) throws IOException {
        Map<String, Object> projectData = new HashMap<>();
        projectData.put("name", project.getProjectName());
        projectData.put("rootPath", project.getProjectPath().toString());
        projectData.put("fileCount", project.getProjectFiles().size());

        File projectFile = outputDir.toPath().resolve("project.json").toFile();
        mapper.writeValue(projectFile, projectData);
    }
}
