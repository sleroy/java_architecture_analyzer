package com.analyzer.api.graph;

import com.analyzer.core.graph.NodeTypeRegistry;

/**
 * Concrete GraphNode implementation for imported class nodes in the import
 * graph.
 */
public class ImportedClassGraphNode extends BaseGraphNode {
    private final String fullyQualifiedClassName;

    public ImportedClassGraphNode(String fullyQualifiedClassName) {
        super(fullyQualifiedClassName, NodeTypeRegistry.getTypeId(ImportedClassGraphNode.class));
        this.fullyQualifiedClassName = fullyQualifiedClassName;
    }


    @Override
    public String getDisplayLabel() {
        String simpleName = fullyQualifiedClassName;
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleName = fullyQualifiedClassName.substring(lastDot + 1);
        }
        return simpleName + " (Class)";
    }
}
