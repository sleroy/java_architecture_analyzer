package com.analyzer.core.db.loader;

import com.analyzer.core.AnalysisConstants;

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

    // Database connection parameters
    private final Path databasePath;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    private LoadOptions(Builder builder) {
        this.nodeTypeFilters = builder.nodeTypeFilters;
        this.edgeTypeFilters = builder.edgeTypeFilters;
        this.projectRoot = builder.projectRoot;
        this.loadAllNodes = builder.loadAllNodes;
        this.loadAllEdges = builder.loadAllEdges;
        this.databasePath = builder.databasePath;
        this.jdbcUrl = builder.jdbcUrl;
        this.username = builder.username;
        this.password = builder.password;
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

    public Path getDatabasePath() {
        return databasePath;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
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

        // Database connection parameters with defaults
        private Path databasePath;
        private String jdbcUrl;
        private String username = "sa";
        private String password = "";

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
            this.databasePath = projectRoot.resolve(AnalysisConstants.ANALYSIS_DIR).resolve(AnalysisConstants.GRAPH_DB_NAME);
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
         * Set the database path for H2 database connection.
         *
         * @param databasePath Path to the H2 database file (without extension)
         * @return this Builder
         */
        public Builder withDatabasePath(Path databasePath) {
            this.databasePath = databasePath;
            return this;
        }

        /**
         * Set a custom JDBC URL. If not provided, will be derived from databasePath.
         *
         * @param jdbcUrl Custom JDBC URL
         * @return this Builder
         */
        public Builder withJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }

        /**
         * Set the database username.
         *
         * @param username Database username (defaults to "sa")
         * @return this Builder
         */
        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        /**
         * Set the database password.
         *
         * @param password Database password (defaults to empty string)
         * @return this Builder
         */
        public Builder withPassword(String password) {
            this.password = password;
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
