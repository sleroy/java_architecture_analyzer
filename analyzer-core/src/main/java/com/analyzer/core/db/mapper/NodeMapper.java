package com.analyzer.core.db.mapper;

import com.analyzer.core.db.entity.GraphNodeEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for graph node operations.
 */
public interface NodeMapper {

    /**
     * Insert a new node into the database.
     *
     * @param node The node entity to insert
     */
    void insertNode(GraphNodeEntity node);

    /**
     * Merge a node into the database (insert if not exists, update if exists).
     * This operation is idempotent and prevents duplicate key errors.
     *
     * @param node The node entity to merge
     */
    void mergeNode(GraphNodeEntity node);

    /**
     * Find a node by its ID.
     *
     * @param id The node ID
     * @return The node entity, or null if not found
     */
    GraphNodeEntity findById(@Param("id") String id);

    /**
     * Find all nodes of a specific type.
     *
     * @param nodeType The node type (e.g., "java", "xml", "class")
     * @return List of matching nodes
     */
    List<GraphNodeEntity> findByType(@Param("nodeType") String nodeType);

    /**
     * Find nodes by JSON property value using JSON path query.
     *
     * @param jsonPath JSON path expression (e.g., '$.java.fullyQualifiedName')
     * @param value    The value to match
     * @return List of matching nodes
     */
    List<GraphNodeEntity> findByPropertyValue(@Param("jsonPath") String jsonPath, @Param("value") String value);

    /**
     * Merge properties into a node using JSON_MERGEPATCH.
     *
     * @param nodeId          The node ID
     * @param propertiesPatch JSON string with properties to merge
     */
    void mergeProperties(@Param("nodeId") String nodeId, @Param("propertiesPatch") String propertiesPatch);

    /**
     * Find all nodes.
     *
     * @return List of all nodes
     */
    List<GraphNodeEntity> findAll();

    /**
     * Update an existing node.
     *
     * @param node The node entity with updated data
     */
    void updateNode(GraphNodeEntity node);

    /**
     * Delete a node by its ID.
     *
     * @param id The node ID
     */
    void deleteNode(@Param("id") String id);

    /**
     * Delete all nodes.
     */
    void deleteAll();

    /**
     * Count total number of nodes.
     *
     * @return Total node count
     */
    int countNodes();

    /**
     * Count nodes by type.
     *
     * @param nodeType The node type
     * @return Count of nodes of this type
     */
    int countByType(@Param("nodeType") String nodeType);

    /**
     * Check if a node exists.
     *
     * @param id The node ID
     * @return true if node exists
     */
    boolean exists(@Param("id") String id);

    /**
     * Find nodes by a single tag.
     *
     * @param tag The tag to search for
     * @return List of nodes containing this tag
     */
    List<GraphNodeEntity> findByTag(@Param("tag") String tag);

    /**
     * Find nodes having any of the provided tags.
     *
     * @param tags List of tags (OR condition)
     * @return List of nodes containing any of these tags
     */
    List<GraphNodeEntity> findByAnyTags(@Param("tags") List<String> tags);

    /**
     * Find nodes having all of the provided tags.
     *
     * @param tags List of tags (AND condition)
     * @return List of nodes containing all of these tags
     */
    List<GraphNodeEntity> findByAllTags(@Param("tags") List<String> tags);

    /**
     * Find nodes by type and having any of the provided tags.
     *
     * @param nodeType The node type
     * @param tags     List of tags (OR condition)
     * @return List of nodes of this type containing any of these tags
     */
    List<GraphNodeEntity> findByTypeAndAnyTags(@Param("nodeType") String nodeType, @Param("tags") List<String> tags);

    /**
     * Find nodes by type and having all of the provided tags.
     *
     * @param nodeType The node type
     * @param tags     List of tags (AND condition)
     * @return List of nodes of this type containing all of these tags
     */
    List<GraphNodeEntity> findByTypeAndAllTags(@Param("nodeType") String nodeType, @Param("tags") List<String> tags);

    /**
     * Count nodes by tag.
     *
     * @param tag The tag to count
     * @return Count of nodes containing this tag
     */
    int countByTag(@Param("tag") String tag);
}
