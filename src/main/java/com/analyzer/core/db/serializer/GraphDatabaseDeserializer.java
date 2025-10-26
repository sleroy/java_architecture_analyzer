package com.analyzer.core.db.serializer;

import com.analyzer.core.db.GraphDatabaseConfig;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.entity.NodeTagEntity;
import com.analyzer.core.db.entity.ProjectEntity;
import com.analyzer.core.db.mapper.NodeMapper;
import com.analyzer.core.db.mapper.TagMapper;
import com.analyzer.core.db.mapper.ProjectMapper;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deserializes graph data from the H2 database back into Project objects.
 * Reconstructs ProjectFiles with all their properties and tags from JSON.
 */
public class GraphDatabaseDeserializer {

    private static final Logger logger = LoggerFactory.getLogger(GraphDatabaseDeserializer.class);

    private final GraphDatabaseConfig config;
    private final ObjectMapper jsonMapper;

    public GraphDatabaseDeserializer(GraphDatabaseConfig config) {
        this.config = config;
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.findAndRegisterModules();
    }

    /**
     * Deserialize a project from the database.
     *
     * @param projectId The project ID
     * @return Reconstructed Project object
     * @throws Exception if deserialization fails
     */
    public Project deserializeById(Long projectId) throws Exception {
        logger.info("Starting project deserialization for ID: {}", projectId);

        try (SqlSession session = config.openSession()) {
            ProjectMapper projectMapper = session.getMapper(ProjectMapper.class);
            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);
            TagMapper tagMapper = session.getMapper(TagMapper.class);

            // Load project metadata
            ProjectEntity projectEntity = projectMapper.findById(projectId);
            if (projectEntity == null) {
                throw new IllegalArgumentException("Project not found with ID: " + projectId);
            }

            // Create Project object
            Path projectRoot = Paths.get(projectEntity.getRootPath());
            Project project = new Project(projectRoot, projectEntity.getName());

            // Load all nodes
            List<GraphNodeEntity> nodes = nodeMapper.findAll();
            logger.info("Loading {} nodes...", nodes.size());

            int loadedCount = 0;
            for (GraphNodeEntity nodeEntity : nodes) {
                ProjectFile file = deserializeNode(nodeEntity, projectRoot, tagMapper);
                if (file != null) {
                    project.addProjectFile(file);
                    loadedCount++;
                }
            }

            logger.info("Project deserialization complete: {} files loaded", loadedCount);
            return project;
        }
    }

    /**
     * Deserialize a project by name.
     *
     * @param projectName The project name
     * @return Reconstructed Project object
     * @throws Exception if deserialization fails
     */
    public Project deserializeByName(String projectName) throws Exception {
        try (SqlSession session = config.openSession()) {
            ProjectMapper projectMapper = session.getMapper(ProjectMapper.class);
            ProjectEntity projectEntity = projectMapper.findByName(projectName);

            if (projectEntity == null) {
                throw new IllegalArgumentException("Project not found with name: " + projectName);
            }

            return deserializeById(projectEntity.getId());
        }
    }

    /**
     * Deserialize the most recently created project.
     *
     * @return Reconstructed Project object
     * @throws Exception if deserialization fails or no projects exist
     */
    public Project deserializeLatest() throws Exception {
        try (SqlSession session = config.openSession()) {
            ProjectMapper projectMapper = session.getMapper(ProjectMapper.class);
            List<ProjectEntity> projects = projectMapper.findAll();

            if (projects.isEmpty()) {
                throw new IllegalStateException("No projects found in database");
            }

            // First project in list (ordered by created_at DESC)
            return deserializeById(projects.get(0).getId());
        }
    }

    /**
     * List all projects in the database.
     *
     * @return List of project entities
     */
    public List<ProjectEntity> listProjects() {
        try (SqlSession session = config.openSession()) {
            ProjectMapper projectMapper = session.getMapper(ProjectMapper.class);
            return projectMapper.findAll();
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Deserialize a single node into a ProjectFile.
     */
    private ProjectFile deserializeNode(GraphNodeEntity nodeEntity, Path projectRoot,
                                        TagMapper tagMapper) {
        try {
            String nodeId = nodeEntity.getId();

            // Reconstruct file path from node ID
            Path filePath = Paths.get(nodeId);

            // Load properties from JSON
            Map<String, Object> properties = new HashMap<>();
            if (nodeEntity.getProperties() != null && !nodeEntity.getProperties().isEmpty()) {
                try {
                    properties = jsonMapper.readValue(
                            nodeEntity.getProperties(),
                            new TypeReference<Map<String, Object>>() {}
                    );
                } catch (Exception e) {
                    logger.warn("Failed to deserialize properties for node {}: {}", nodeId, e.getMessage());
                }
            }

            // Load tags
            Set<String> tags = tagMapper.findByNodeId(nodeId).stream()
                    .map(NodeTagEntity::getTag)
                    .collect(Collectors.toSet());

            // Create ProjectFile using JSON constructor
            ProjectFile file = new ProjectFile(
                    filePath.toString(),
                    projectRoot.relativize(filePath).toString(),
                    null, // sourceJarPath
                    null, // jarEntryPath
                    new Date(),
                    properties,
                    tags
            );

            return file;

        } catch (Exception e) {
            logger.error("Failed to deserialize node: {}", nodeEntity.getId(), e);
            return null;
        }
    }
}
