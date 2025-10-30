-- Migration script: Move tags from node_tags table to nodes.tags column
-- This script converts the normalized tag storage to JSON array storage
-- Run this script after adding the tags column to the nodes table

-- Step 1: Update nodes table with JSON array of tags
UPDATE nodes n
SET tags = (
    SELECT JSON_ARRAY(
        SELECT nt.tag 
        FROM node_tags nt 
        WHERE nt.node_id = n.id 
        ORDER BY nt.tag
    )
    FROM node_tags nt
    WHERE nt.node_id = n.id
    GROUP BY nt.node_id
)
WHERE EXISTS (
    SELECT 1 FROM node_tags nt WHERE nt.node_id = n.id
);

-- Step 2: Set empty JSON array for nodes with no tags
UPDATE nodes
SET tags = '[]'
WHERE tags IS NULL;

-- Verification queries (run these to verify migration):
-- SELECT COUNT(*) as nodes_with_tags FROM nodes WHERE tags IS NOT NULL AND tags != '[]';
-- SELECT COUNT(DISTINCT node_id) as old_nodes_with_tags FROM node_tags;
-- SELECT id, tags FROM nodes WHERE tags IS NOT NULL AND tags != '[]' LIMIT 10;

-- After verifying data migration is successful, you can:
-- 1. Drop the node_tags table: DROP TABLE IF EXISTS node_tags;
-- 2. Remove TagMapper.xml and TagMapper.java
-- 3. Update application code to use the new tags column
