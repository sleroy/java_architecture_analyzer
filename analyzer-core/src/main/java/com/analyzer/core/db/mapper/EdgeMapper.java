package com.analyzer.core.db.mapper;

import com.analyzer.core.db.entity.GraphEdgeEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for graph edge operations.
 */
public interface EdgeMapper {

    /**
     * Insert a new edge into the database.
     *
     * @param edge The edge entity to insert
     */
    void insertEdge(GraphEdgeEntity edge);

    /**
     * Find an edge by its ID.
     *
     * @param id The edge ID
     * @return The edge entity, or null if not found
     */
    GraphEdgeEntity findById(@Param("id") Long id);

    /**
     * Find all edges of a specific type.
     *
     * @param edgeType The edge type (e.g., "depends_on", "contains")
     * @return List of matching edges
     */
    List<GraphEdgeEntity> findByType(@Param("edgeType") String edgeType);

    /**
     * Find all edges originating from a source node.
     *
     * @param sourceId The source node ID
     * @return List of outgoing edges
     */
    List<GraphEdgeEntity> findBySourceId(@Param("sourceId") String sourceId);

    /**
     * Find all edges targeting a specific node.
     *
     * @param targetId The target node ID
     * @return List of incoming edges
     */
    List<GraphEdgeEntity> findByTargetId(@Param("targetId") String targetId);

    /**
     * Find edge between two specific nodes.
     *
     * @param sourceId The source node ID
     * @param targetId The target node ID
     * @param edgeType The edge type
     * @return The edge entity, or null if not found
     */
    GraphEdgeEntity findEdge(@Param("sourceId") String sourceId,
                             @Param("targetId") String targetId,
                             @Param("edgeType") String edgeType);

    /**
     * Find all edges.
     *
     * @return List of all edges
     */
    List<GraphEdgeEntity> findAll();

    /**
     * Delete an edge by its ID.
     *
     * @param id The edge ID
     */
    void deleteEdge(@Param("id") Long id);

    /**
     * Delete all edges for a specific node (both incoming and outgoing).
     *
     * @param nodeId The node ID
     */
    void deleteByNodeId(@Param("nodeId") String nodeId);

    /**
     * Delete all edges.
     */
    void deleteAll();

    /**
     * Count total number of edges.
     *
     * @return Total edge count
     */
    int countEdges();

    /**
     * Count edges by type.
     *
     * @param edgeType The edge type
     * @return Count of edges of this type
     */
    int countByType(@Param("edgeType") String edgeType);
}
