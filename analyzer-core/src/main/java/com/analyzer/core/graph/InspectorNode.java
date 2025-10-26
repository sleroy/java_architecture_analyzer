package com.analyzer.core.graph;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a node in the dependency graph (an inspector).
 */
public record InspectorNode(String inspectorName, String className, Set<String> requiredTags,
                            Set<String> producedTags) {
    public InspectorNode(String inspectorName, String className, Set<String> requiredTags,
                         Set<String> producedTags) {
        this.inspectorName = inspectorName;
        this.className = className;
        this.requiredTags = new HashSet<>(requiredTags);
        this.producedTags = new HashSet<>(producedTags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InspectorNode that = (InspectorNode) o;
        return Objects.equals(inspectorName, that.inspectorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inspectorName);
    }

    @Override
    public String toString() {
        return inspectorName;
    }
}
