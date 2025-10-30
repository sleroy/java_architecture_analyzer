package com.analyzer.core.db.loader;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.serialization.JsonSerializationService;

import java.nio.file.Path;

/**
 * Factory interface for creating GraphNode instances from database entities.
 * Each node type can provide its own factory implementation to handle
 * type-specific construction logic.
 */
public interface NodeFactory {

    /**
     * Creates a GraphNode instance from a database entity.
     * 
     * @param entity         The database entity containing node data
     * @param jsonSerializer Service for deserializing JSON properties
     * @param projectRoot    The project root path for relative path resolution
     * @return A fully constructed GraphNode instance
     * @throws Exception if node creation fails
     */
    GraphNode createFromEntity(
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer,
            Path projectRoot) throws Exception;
}
