package com.analyzer.core.db.loader;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.serialization.JsonSerializationService;

import java.nio.file.Path;

/**
 * Generic fallback factory for unknown node types.
 * This factory is used when no specific factory is registered for a node type.
 * It throws an exception to indicate that a proper factory should be
 * registered.
 */
public class GenericNodeFactory implements NodeFactory {

    @Override
    public GraphNode createFromEntity(
            GraphNodeEntity entity,
            JsonSerializationService jsonSerializer,
            Path projectRoot) throws Exception {

        throw new UnsupportedOperationException(
                String.format(
                        "No factory registered for node type '%s' (ID: %s). " +
                                "Please register a NodeFactory for this type in NodeTypeRegistry.",
                        entity.getNodeType(),
                        entity.getId()));
    }
}
