-- Performance Optimizations for Graph Database
-- Additional indexes to improve query performance

-- Index for tags column to speed up LIKE queries
-- Note: H2 doesn't support functional indexes on JSON, but we can create indexes on the CLOB column
CREATE INDEX IF NOT EXISTS idx_nodes_tags ON nodes(tags);

-- Index for commonly queried node types (if we know them in advance)
-- This is in addition to the existing idx_nodes_type
CREATE INDEX IF NOT EXISTS idx_nodes_type_tags ON nodes(node_type, tags);

-- Index for properties column to speed up JSON queries
CREATE INDEX IF NOT EXISTS idx_nodes_properties ON nodes(properties);

-- Index for display_label for faster searches
CREATE INDEX IF NOT EXISTS idx_nodes_display_label ON nodes(display_label);

-- Composite index for most common query patterns
CREATE INDEX IF NOT EXISTS idx_nodes_type_created ON nodes(node_type, created_at DESC);

-- Statistics to help H2 query optimizer
UPDATE INFORMATION_SCHEMA.TABLES SET TABLE_TYPE = 'TABLE' WHERE TABLE_NAME = 'NODES';

-- Analyze tables for better query planning (H2 specific)
ANALYZE TABLE nodes;
ANALYZE TABLE edges;

-- Optional: Create materialized view for tag statistics if needed
-- (Uncomment if you need fast tag statistics queries)
/*
CREATE VIEW IF NOT EXISTS tag_statistics AS
SELECT 
    TRIM(REPLACE(REPLACE(REPLACE(t.tag_value, '"', ''), '[', ''), ']', '')) as tag_name,
    COUNT(*) as usage_count
FROM nodes n,
     TABLE(X = SPLIT_PART(REPLACE(REPLACE(n.tags, '[', ''), ']', ''), ',', X)) t(tag_value)
WHERE n.tags IS NOT NULL 
  AND n.tags != '[]'
  AND TRIM(t.tag_value) != ''
GROUP BY TRIM(REPLACE(REPLACE(REPLACE(t.tag_value, '"', ''), '[', ''), ']', ''))
ORDER BY usage_count DESC;
*/
