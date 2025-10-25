package com.analyzer.core.export;

import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ProjectSerializer {
    
    private final ObjectMapper mapper;
    private final File outputDir;
    
    public ProjectSerializer(File outputDir) {
        this.outputDir = outputDir;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    public void serialize(Project project) throws IOException {
        clearOutputDirectory();
        createDirectories();
        
        // Serialize nodes
        for (ProjectFile file : project.getProjectFiles().values()) {
            serializeNode(file);
        }
        
        // Serialize project metadata
        serializeProject(project);
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
    
    private void serializeNode(ProjectFile file) throws IOException {
        String type = determineNodeType(file);
        String id = generateNodeId(file);
        
        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("id", id);
        nodeData.put("type", type);
        nodeData.put("fileName", file.getFileName());
        nodeData.put("relativePath", file.getRelativePath());
        nodeData.put("fullyQualifiedName", file.getProperty("fullyQualifiedName"));
        
        // Add tags
        nodeData.put("tags", file.getTags());
        
        // Add properties - transform dotted keys into nested structures
        Map<String, Object> properties = file.getNodeProperties();
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
        
        Path nodeDir = outputDir.toPath().resolve("nodes").resolve(type);
        Files.createDirectories(nodeDir);
        
        File nodeFile = nodeDir.resolve("node-" + id + ".json").toFile();
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
    
    private String determineNodeType(ProjectFile file) {
        String fileName = file.getFileName().toLowerCase();
        if (fileName.endsWith(".java")) return "java";
        if (fileName.endsWith(".xml")) return "xml";
        if (fileName.endsWith(".properties")) return "properties";
        return "file";
    }
    
    private String generateNodeId(ProjectFile file) {
        return String.valueOf(Math.abs(file.getFilePath().toString().hashCode()));
    }
}
