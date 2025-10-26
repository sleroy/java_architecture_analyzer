package com.analyzer.core.db.mapper;

import com.analyzer.core.db.entity.NodeTagEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for node tag operations.
 */
public interface TagMapper {

    /**
     * Insert a new tag for a node.
     *
     * @param tag The tag entity to insert
     */
    void insertTag(NodeTagEntity tag);

    /**
     * Insert multiple tags for a node in batch.
     *
     * @param tags List of tag entities to insert
     */
    void insertTags(@Param("tags") List<NodeTagEntity> tags);

    /**
     * Find all tags for a specific node.
     *
     * @param nodeId The node ID
     * @return List of tags for this node
     */
    List<NodeTagEntity> findByNodeId(@Param("nodeId") String nodeId);

    /**
     * Find all nodes with a specific tag.
     *
     * @param tag The tag to search for
     * @return List of tag entities
     */
    List<NodeTagEntity> findByTag(@Param("tag") String tag);

    /**
     * Find all unique tags in the database.
     *
     * @return List of unique tag strings
     */
    List<String> findAllUniqueTags();

    /**
     * Check if a node has a specific tag.
     *
     * @param nodeId The node ID
     * @param tag The tag to check
     * @return true if the node has this tag
     */
    boolean hasTag(@Param("nodeId") String nodeId, @Param("tag") String tag);

    /**
     * Delete a specific tag from a node.
     *
     * @param nodeId The node ID
     * @param tag The tag to delete
     */
    void deleteTag(@Param("nodeId") String nodeId, @Param("tag") String tag);

    /**
     * Delete all tags for a specific node.
     *
     * @param nodeId The node ID
     */
    void deleteByNodeId(@Param("nodeId") String nodeId);

    /**
     * Delete all tags.
     */
    void deleteAll();

    /**
     * Count total number of tag entries.
     *
     * @return Total tag count
     */
    int countTags();

    /**
     * Count how many nodes have a specific tag.
     *
     * @param tag The tag
     * @return Count of nodes with this tag
     */
    int countNodesWithTag(@Param("tag") String tag);
}
