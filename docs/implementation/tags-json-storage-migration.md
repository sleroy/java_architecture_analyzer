# Tags JSON Storage Migration

## Overview

Migrated tag storage from separate `node_tags` table to JSON array column in the `nodes` table for significantly improved performance.

## Problem Statement

The original design stored node tags in a separate `node_tags` table with a foreign key relationship. This created an N+1 query problem:
- Loading N nodes required 1 query to fetch all nodes + N additional queries to fetch tags for each node
- This significantly increased database load time, especially for large projects

## Solution

Store tags as a JSON array directly in the `nodes` table:
- Tags column: `CLOB` type storing JSON array like `["tag1", "tag2", "tag3"]`
- Eliminates separate table and N+1 queries
- Tags load automatically with node data in a single query

## Changes Made

### 1. Database Schema (`schema.sql`)
- **Added**: `tags CLOB` column to `nodes` table
- **Removed**: `node_tags` table entirely (assuming fresh database)
- **Updated**: Statistics view to count tags from JSON column

### 2. Entity Layer (`GraphNodeEntity.java`)
- **Added**: `tags` field (String) to store JSON
- **Added**: Constructor supporting tags parameter
- **Added**: `getTags()` and `setTags()` methods

### 3. Serialization Service (`JsonSerializationService.java`)
- **Added**: `serializeTags(Set<String>)` - converts tag set to JSON array
- **Added**: `deserializeTags(String)` - converts JSON array to tag set
- Uses sorted list for consistent ordering in JSON

### 4. Repository Layer (`H2GraphStorageRepository.java`)
- **Updated**: `saveNode()` to serialize tags to JSON
- **Removed**: Tag-specific methods (`getNodeTags()`, `findNodesByTag()`, `getAllTags()`)
- **Updated**: `getStatistics()` to count tags from JSON column
- **Updated**: `clearAll()` to remove TagMapper references
- **Removed**: TagMapper imports

### 5. Mapper Layer (`NodeMapper.xml`)
- **Updated**: Result map to include `tags` column
- **Updated**: All SELECT queries to include `tags` column
- **Updated**: INSERT/UPDATE/MERGE operations to handle `tags` column

### 6. Factory Layer (`DefaultGraphNodeFactory.java`)
- **Added**: `applyTags()` method to deserialize and apply tags
- **Updated**: `createFromEntity()` template method to call `applyTags()`
- **Removed**: Comment about loading tags separately

### 7. Database Class (`H2GraphDatabase.java`)
- **Removed**: Separate tag loading loop
- **Updated**: Comment explaining tags load automatically via factory

## Performance Impact

### Before (N+1 Queries)
```
SELECT * FROM nodes;                          -- 1 query
SELECT * FROM node_tags WHERE node_id = ?;    -- N queries (one per node)
```
Total: **1 + N queries**

### After (Single Query)
```
SELECT id, node_type, display_label, properties, metrics, tags, ... FROM nodes;
```
Total: **1 query**

### Performance Improvement
- **Query Count**: Reduced from O(N) to O(1)
- **Network Round Trips**: Eliminated N-1 database round trips
- **Loading Time**: Significantly faster for large projects

## Migration Path

For existing databases with data in `node_tags` table:
1. Run `migrate_tags_to_json.sql` script to copy tags
2. Verify data migration
3. Drop `node_tags` table after verification

For fresh databases:
- `node_tags` table removed from schema
- No migration needed

## Trade-offs

### Advantages
✅ Dramatic performance improvement for bulk node loads  
✅ Simpler codebase (removed TagMapper, simplified repository)  
✅ Consistent with existing JSON storage patterns (properties, metrics)  
✅ Atomic operations (tags save/load with node)  

### Disadvantages
❌ Tag-based queries require JSON functions (more complex SQL)  
❌ No FK constraint enforcement (but tags are simple strings)  
❌ Requires data migration for existing databases  

## Design Consistency

This change aligns with existing patterns in the codebase:
- **Properties**: Already stored as JSON (`properties CLOB`)
- **Metrics**: Already stored as JSON (`metrics CLOB`)
- **Tags**: Now stored as JSON (`tags CLOB`)

All node data is now consistently stored using JSON serialization.

## Implementation Notes

- Tags are serialized as sorted JSON arrays for consistent ordering
- Empty tag sets serialize to `"[]"` (not NULL)
- Deserialization handles NULL, empty strings, and `"[]"` gracefully
- Factory pattern automatically applies tags during node loading
- No code changes needed in inspectors or analysis engine

## Testing Recommendations

1. **Unit Tests**: Verify tag serialization/deserialization
2. **Integration Tests**: Test node save/load with tags
3. **Performance Tests**: Measure loading time improvement
4. **Migration Tests**: Verify data migration script (if using existing DB)

## Files Modified

- `analyzer-core/src/main/resources/db/schema.sql`
- `analyzer-core/src/main/resources/db/migrate_tags_to_json.sql` (new)
- `analyzer-core/src/main/java/com/analyzer/core/db/entity/GraphNodeEntity.java`
- `analyzer-core/src/main/java/com/analyzer/core/serialization/JsonSerializationService.java`
- `analyzer-core/src/main/java/com/analyzer/core/db/H2GraphStorageRepository.java`
- `analyzer-core/src/main/resources/mybatis/mappers/NodeMapper.xml`
- `analyzer-core/src/main/java/com/analyzer/core/db/loader/DefaultGraphNodeFactory.java`
- `analyzer-core/src/main/java/com/analyzer/core/db/H2GraphDatabase.java`

## Date
October 30, 2025
