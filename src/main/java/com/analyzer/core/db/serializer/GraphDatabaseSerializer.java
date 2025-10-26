package com.analyzer.core.db.serializer;

import com.analyzer.core.db.GraphDatabaseConfig;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.db.entity.NodeTagEntity;
import com.analyzer.core.db.entity.ProjectEntity;
import com.analyzer.core.db.mapper.NodeMapper;
import com.analyzer.core.db.mapper.TagMapper;
import com.analyzer.core.db.mapper.ProjectMapper;
import com.analyzer.core.db.validation.PropertiesValidator;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serializes a Project object into the H2 graph database.
 * Converts ProjectFiles into nodes with all their properties and tags preserved.
 */
public class GraphDatabaseSerializer {

    private static final Logger logger = LoggerFactory.getLogger(GraphDatabaseSerializer.class);

    private final GraphDatabaseConfig config;
    private final ObjectMapper jsonMapper;

    public GraphDatabaseSerializer(GraphDatabaseConfig config) {
        this.config = config;
        this.jsonMapper = new ObjectMapper();
        // Explicitly register only the modules we need (avoids JAXB dependency)
        this.jsonMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Serialize an entire project into the database.
     *
     * @param project The project to serialize
     * @throws Exception if serialization fails
     */
    public void serialize(Project project) throws Exception {
        logger.info("Starting project serialization: {}", project.getProjectName());

        try (SqlSession session = config.openSession(false)) { // Manual commit
            // Get mappers
            ProjectMapper projectMapper = session.getMapper(ProjectMapper.class);
            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);
            TagMapper tagMapper = session.getMapper(TagMapper.class);

            // Save project metadata
            ProjectEntity projectEntity = new ProjectEntity(
                    project.getProjectName(),
                    project.getProjectPath().toString(),
                    "Java Architecture Analysis"
            );
            projectMapper.insertProject(projectEntity);
            logger.info("Saved project metadata: {}", project.getProjectName());

            // Serialize all project files as nodes
            int nodeCount = 0;
            int batchSize = 100;
            List<NodeTagEntity> tagBatch = new ArrayList<>();

            for (ProjectFile file : project.getProjectFiles().values()) {
                // Validate properties before serialization
                PropertiesValidator.validate(file.getNodeProperties());
                
                // Create node with JSON properties
                String nodeId = file.getId();
                String nodeType = determineNodeType(file);
                String displayLabel = file.getDisplayLabel();
                String propertiesJson = jsonMapper.writeValueAsString(file.getNodeProperties());

                GraphNodeEntity node = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson);
                nodeMapper.mergeNode(node); // Use merge to handle duplicates gracefully

                // Collect tags for batch insert
                for (String tag : file.getTags()) {
                    tagBatch.add(new NodeTagEntity(nodeId, tag));
                }

                // Batch commit to improve performance
                if (++nodeCount % batchSize == 0) {
                    if (!tagBatch.isEmpty()) {
                        tagMapper.insertTags(tagBatch);
                        tagBatch.clear();
                    }
                    session.commit();
                    logger.info("Serialized {} nodes...", nodeCount);
                }
            }

            // Insert remaining tags
            if (!tagBatch.isEmpty()) {
                tagMapper.insertTags(tagBatch);
            }

            // Final commit
            session.commit();

            logger.info("Project serialization complete: {} nodes saved", nodeCount);
        }
    }

    /**
     * Serialize a single ProjectFile as a node.
     *
     * @param file The project file to serialize
     * @throws Exception if serialization fails
     */
    public void serializeFile(ProjectFile file) throws Exception {
        try (SqlSession session = config.openSession()) {
            // Validate properties before serialization
            PropertiesValidator.validate(file.getNodeProperties());
            
            NodeMapper nodeMapper = session.getMapper(NodeMapper.class);
            TagMapper tagMapper = session.getMapper(TagMapper.class);

            // Create node with JSON properties
            String nodeId = file.getId();
            String nodeType = determineNodeType(file);
            String displayLabel = file.getDisplayLabel();
            String propertiesJson = jsonMapper.writeValueAsString(file.getNodeProperties());

            GraphNodeEntity node = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson);
            nodeMapper.insertNode(node);

            // Save tags (replace all tags)
            tagMapper.deleteByNodeId(nodeId);
            List<NodeTagEntity> tags = file.getTags().stream()
                    .map(tag -> new NodeTagEntity(nodeId, tag))
                    .toList();
            if (!tags.isEmpty()) {
                tagMapper.insertTags(tags);
            }

            session.commit();
            logger.debug("Serialized file: {} (type: {})", nodeId, nodeType);
        }
    }

    /**
     * Check if database is ready for serialization.
     *
     * @return true if database is initialized
     */
    public boolean isReady() {
        return config.isInitialized();
    }

    // ==================== HELPER METHODS ====================

    private String determineNodeType(ProjectFile file) {
        String fileName = file.getFileName().toLowerCase();
        if (fileName.endsWith(".java")) return "java";
        if (fileName.endsWith(".xml")) return "xml";
        if (fileName.endsWith(".properties")) return "properties";
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) return "yaml";
        if (fileName.endsWith(".json")) return "json";
        return "file";
    }

}
