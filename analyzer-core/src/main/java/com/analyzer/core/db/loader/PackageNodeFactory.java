package com.analyzer.core.db.loader;

import com.analyzer.api.graph.PackageNode;
import com.analyzer.core.db.entity.GraphNodeEntity;

import java.nio.file.Path;

/**
 * Factory for creating PackageNode instances from database entities.
 * PackageNode uses the package name as its ID.
 * 
 * This factory extends DefaultGraphNodeFactory which handles all common
 * initialization logic (properties, metrics). This factory only needs to
 * provide the node instantiation logic.
 */
public class PackageNodeFactory extends DefaultGraphNodeFactory<PackageNode> {

    /**
     * Creates a PackageNode instance using the entity ID as the package name.
     * The base factory will handle property and metric initialization.
     *
     * @param entity      The database entity containing the node ID (package name)
     * @param projectRoot The project root path (not used for PackageNode)
     * @return A new PackageNode instance
     */
    @Override
    protected PackageNode createNode(GraphNodeEntity entity, Path projectRoot) {
        // Create PackageNode using the entity ID (which is the package name)
        return new PackageNode(entity.getId());
    }
}
