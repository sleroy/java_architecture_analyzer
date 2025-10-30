-- Graph Database Schema for Java Architecture Analyzer
-- H2 Database Schema with JSON Properties

-- Core nodes table: stores all graph nodes with JSON properties
CREATE TABLE IF NOT EXISTS nodes (
    id VARCHAR(1024) PRIMARY KEY,              -- Original ID (file path or FQN)
    node_type VARCHAR(50) NOT NULL,            -- java, xml, class, package
    display_label VARCHAR(512),                -- Human-readable label
    properties CLOB,                           -- JSON document stored as CLOB for H2 compatibility
    metrics CLOB,                              -- Metrics stored as JSON (key-value pairs of metric_name: double_value)
    tags CLOB,                                 -- Tags stored as JSON array for performance (e.g., ["tag1", "tag2"])
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nodes_type ON nodes(node_type);
CREATE INDEX IF NOT EXISTS idx_nodes_created ON nodes(created_at);

-- Note: H2 doesn't support function-based indexes on JSON paths
-- JSON queries will use full table scans, but remain functional
-- For better performance on specific properties, consider adding computed columns

-- Edges table: represents relationships between nodes
CREATE TABLE IF NOT EXISTS edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id VARCHAR(1024) NOT NULL,
    target_id VARCHAR(1024) NOT NULL,
    edge_type VARCHAR(50) NOT NULL,            -- depends_on, contains, extends, implements, etc.
    metadata_json CLOB,                        -- Optional edge metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (target_id) REFERENCES nodes(id) ON DELETE CASCADE,
    CONSTRAINT unique_edge UNIQUE (source_id, target_id, edge_type)
);

CREATE INDEX IF NOT EXISTS idx_edges_source ON edges(source_id);
CREATE INDEX IF NOT EXISTS idx_edges_target ON edges(target_id);
CREATE INDEX IF NOT EXISTS idx_edges_type ON edges(edge_type);

-- Project metadata table
CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    root_path CLOB NOT NULL,
    description CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Statistics view for quick metrics
CREATE VIEW IF NOT EXISTS graph_statistics AS
SELECT 
    (SELECT COUNT(*) FROM nodes) as total_nodes,
    (SELECT COUNT(DISTINCT node_type) FROM nodes) as node_types,
    (SELECT COUNT(*) FROM edges) as total_edges,
    (SELECT COUNT(DISTINCT edge_type) FROM edges) as edge_types,
    (SELECT COUNT(*) FROM nodes WHERE tags IS NOT NULL AND tags != '[]') as nodes_with_tags;
