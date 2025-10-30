package com.analyzer.core.db.loader;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.serialization.JsonSerializationService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Factory for creating ProjectFile instances from database entities.
 * Handles ProjectFile-specific construction including JAR-internal files
 * and filesystem path resolution.
 * 
 * This factory extends DefaultGraphNodeFactory which handles common
 * initialization logic (properties, metrics). This factory provides
 * ProjectFile-specific instantiation logic for handling different
 * filesystem types and JAR files.
 */
public class ProjectFileFactory extends DefaultGraphNodeFactory<ProjectFile> {

    /**
     * Creates a ProjectFile instance with special handling for JAR files
     * and different filesystem types.
     *
     * @param entity      The database entity containing the node data
     * @param projectRoot The project root path for relative path resolution
     * @return A new ProjectFile instance
     * @throws Exception if file creation fails
     */
    @Override
    protected ProjectFile createNode(GraphNodeEntity entity, Path projectRoot) throws Exception {
        Path filePath = Paths.get(entity.getId());

        // Check if paths are from different filesystem types
        boolean isDifferentFilesystem = !filePath.getFileSystem().equals(projectRoot.getFileSystem());

        if (isDifferentFilesystem) {
            // For JAR-internal files or different filesystems, use alternative construction
            // Properties will be applied by the base factory after this returns
            return createProjectFileFromProperties(entity.getId(), projectRoot);
        } else {
            // Regular filesystem path - use standard constructor
            return new ProjectFile(filePath, projectRoot, null, null);
        }
    }

    /**
     * Override property application to handle JAR-specific properties.
     * First applies base properties, then handles any ProjectFile-specific logic.
     */
    @Override
    protected void applyProperties(
            GraphNode node,
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer) {

        // First apply base properties using parent implementation
        super.applyProperties(node, entity, jsonSerializer);

        // ProjectFile-specific property handling can be added here if needed
        // Currently, properties are correctly handled by the base implementation
    }

    /**
     * Creates a ProjectFile from stored properties when direct Path construction
     * fails.
     * This handles cases like JAR-internal files or paths from different
     * filesystems.
     */
    private ProjectFile createProjectFileFromProperties(String nodeId, Path projectRoot) {
        // For JAR files and different filesystems, create a minimal ProjectFile
        // The properties will be set by the base factory's applyProperties method

        // Extract basic information from the nodeId (file path)
        String fileName = new java.io.File(nodeId).getName();

        // Extract file extension
        String fileExtension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            fileExtension = fileName.substring(lastDotIndex + 1);
        }

        // Use the JSON creator constructor which doesn't call relativize()
        return new ProjectFile(
                nodeId, // nodeId
                nodeId, // relativePath (will be overwritten by properties)
                fileName, // fileName
                fileExtension, // fileExtension
                new java.util.Date(), // lastModified
                null, // sourceJarPath (will be set from properties)
                null, // jarEntryPath (will be set from properties)
                false, // fromJar (will be set from properties)
                new HashSet<>(), // tags (will be set separately)
                new HashMap<>() // properties (will be set by base factory)
        );
    }
}
