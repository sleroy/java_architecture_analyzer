package com.analyzer.core.db.loader;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.db.entity.GraphNodeEntity;

import java.nio.file.Path;

/**
 * Factory for creating JavaClassNode instances from database entities.
 * JavaClassNode uses the fully qualified class name as its ID.
 * 
 * This factory extends DefaultGraphNodeFactory which handles all common
 * initialization logic (properties, metrics). This factory only needs to
 * provide the node instantiation logic.
 */
public class JavaClassNodeFactory extends DefaultGraphNodeFactory<JavaClassNode> {

    /**
     * Creates a JavaClassNode instance using the entity ID as the fully qualified
     * name.
     * The base factory will handle property and metric initialization.
     *
     * @param entity      The database entity containing the node ID (fully
     *                    qualified name)
     * @param projectRoot The project root path (not used for JavaClassNode)
     * @return A new JavaClassNode instance
     */
    @Override
    protected JavaClassNode createNode(GraphNodeEntity entity, Path projectRoot) {
        // Create JavaClassNode using the entity ID (which is the fully qualified name)
        return new JavaClassNode(entity.getId());
    }
}
