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

-- Migration Progress Tracking Tables

-- Migration plans execution tracking
CREATE TABLE IF NOT EXISTS migration_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    plan_name VARCHAR(255) NOT NULL,
    phase_name VARCHAR(255) NOT NULL,
    task_id VARCHAR(100) NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    task_type VARCHAR(50) NOT NULL,              -- AUTOMATED_REFACTORING, AUTOMATED_OPERATIONS, AI_ASSISTED, ANALYSIS, VALIDATION
    status VARCHAR(50) NOT NULL,                 -- PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message CLOB,
    metadata_json CLOB,                          -- Additional task metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT unique_task_execution UNIQUE (project_id, plan_name, task_id)
);

CREATE INDEX IF NOT EXISTS idx_migration_progress_project ON migration_progress(project_id);
CREATE INDEX IF NOT EXISTS idx_migration_progress_plan ON migration_progress(plan_name);
CREATE INDEX IF NOT EXISTS idx_migration_progress_status ON migration_progress(status);
CREATE INDEX IF NOT EXISTS idx_migration_progress_task_id ON migration_progress(task_id);

-- Task dependencies for execution ordering
CREATE TABLE IF NOT EXISTS task_dependencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT,
    plan_name VARCHAR(255) NOT NULL,
    task_id VARCHAR(100) NOT NULL,
    depends_on_task_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT unique_dependency UNIQUE (project_id, plan_name, task_id, depends_on_task_id)
);

CREATE INDEX IF NOT EXISTS idx_task_dependencies_task ON task_dependencies(task_id);
CREATE INDEX IF NOT EXISTS idx_task_dependencies_depends ON task_dependencies(depends_on_task_id);

-- Block execution history for detailed tracking
CREATE TABLE IF NOT EXISTS block_execution_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    migration_progress_id BIGINT NOT NULL,
    block_type VARCHAR(50) NOT NULL,             -- CommandBlock, FileOperationBlock, GraphQueryBlock, etc.
    block_name VARCHAR(255),
    execution_order INT NOT NULL,
    status VARCHAR(50) NOT NULL,                 -- SUCCESS, FAILED, SKIPPED
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    execution_time_ms BIGINT,
    result_json CLOB,                            -- Block execution result as JSON
    error_message CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (migration_progress_id) REFERENCES migration_progress(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_block_execution_progress ON block_execution_history(migration_progress_id);
CREATE INDEX IF NOT EXISTS idx_block_execution_order ON block_execution_history(execution_order);
CREATE INDEX IF NOT EXISTS idx_block_execution_status ON block_execution_history(status);
