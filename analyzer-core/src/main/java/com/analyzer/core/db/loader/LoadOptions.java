package com.analyzer.core.db.loader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Configuration options for loading data from the database into a
 * GraphRepository.
 */
public class LoadOptions {

    private final List<String> nodeTypeFilters;
    private final List<String> edgeTypeFilters;
    private final Path projectRoot;
    private final boolean loadAllNodes;
    private final boolean loadAllEdges;

    private LoadOptions(Builder builder) {
        this.nodeTypeFilters = builder.nodeTypeFilters;
        this.edgeTypeFilters = builder.edgeTypeFilters;
        this.projectRoot = builder.projectRoot;
        this.loadAllNodes = builder.loadAllNodes;
        this.loadAllEdges = builder.loadAllEdges;
    }

    public List<String> getNodeTypeFilters() {
        return nodeTypeFilters;
    }

    public List<String> getEdgeTypeFilters() {
        return edgeTypeFilters;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public boolean shouldLoadAllNodes() {
        return loadAllNodes;
    }

    public boolean shouldLoadAllEdges() {
        return loadAllEdges;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates default options that load common node and edge types.
     */
    public static LoadOptions defaultOptions() {
        return builder()
                .withCommonNodeTypes()
                .withCommonEdgeTypes()
                .withProjectRoot(Paths.get("."))
                .build();
    }

    /**
     * Creates options that load all nodes and edges with optional filtering.
     */
    public static LoadOptions allTypes(Path projectRoot) {
        return builder()
                .loadAllNodes()
                .loadAllEdges()
                .withProjectRoot(projectRoot)
                .build();
    }

    public static class Builder {
        private List<String> nodeTypeFilters;
        private List<String> edgeTypeFilters;
        private Path projectRoot = Paths.get(".");
        private boolean loadAllNodes = false;
        private boolean loadAllEdges = false;

        public Builder withNodeTypeFilters(List<String> nodeTypes) {
            this.nodeTypeFilters = nodeTypes;
            return this;
        }

        public Builder withEdgeTypeFilters(List<String> edgeTypes) {
            this.edgeTypeFilters = edgeTypes;
            return this;
        }

        public Builder withProjectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder loadAllNodes() {
            this.loadAllNodes = true;
            return this;
        }

        public Builder loadAllEdges() {
            this.loadAllEdges = true;
            return this;
        }

        /**
         * Configures to load common node types used in typical analysis.
         */
        public Builder withCommonNodeTypes() {
            this.nodeTypeFilters = List.of("java", "xml", "properties", "yaml", "json", "file");
            return this;
        }

        /**
         * Configures to load common edge types used in typical analysis.
         */
        public Builder withCommonEdgeTypes() {
            this.edgeTypeFilters = List.of("depends_on", "contains", "calls", "extends", "implements", "uses",
                    "imports");
            return this;
        }

        public LoadOptions build() {
            return new LoadOptions(this);
        }
    }
}
