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
     * @param value The value to match
     * @return List of matching nodes
     */
    List<GraphNodeEntity> findByPropertyValue(@Param("jsonPath") String jsonPath, @Param("value") String value);

    /**
     * Merge properties into a node using JSON_MERGEPATCH.
     *
     * @param nodeId The node ID
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
}
