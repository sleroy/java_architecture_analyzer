package com.analyzer.core.db.loader;

import com.analyzer.api.graph.ImportedClassGraphNode;
import com.analyzer.core.db.entity.GraphNodeEntity;

import java.nio.file.Path;


public class ImportedNodeNodeFactory extends DefaultGraphNodeFactory<ImportedClassGraphNode> {

    @Override
    protected ImportedClassGraphNode createNode(GraphNodeEntity entity, Path projectRoot) {
        // Create JavaClassNode using the entity ID (which is the fully qualified name)
        return new ImportedClassGraphNode(entity.getId());
    }
}
