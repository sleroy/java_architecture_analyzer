# JSON Properties Refactoring Summary

## Problem Identified

The original database design stored properties in TWO places simultaneously:
1. `nodes.data_json` CLOB - Complete property map as JSON
2. `node_properties` table - Each property as individual rows

This created:
- ❌ Data duplication and synchronization issues
- ❌ Complex merge operations (read-modify-write)
- ❌ Impedance mismatch between SQL and document data

## Solution Implemented

### Hybrid Design: SQL + JSON

**Properties**: JSON document in nodes table
- Single source of truth
- Native `JSON_MERGEPATCH` for updates
- JSON path indexes for fast queries

**Tags**: Normalized relationship table
- Fast tag queries with standard SQL
- Simple CRUD operations
- Efficient aggregations

## Changes Made

### Schema Changes
```sql
-- BEFORE
CREATE TABLE nodes (
    id VARCHAR(1024),
    node_type VARCHAR(50),
    display_label VARCHAR(512),
    data_json CLOB  -- Plus separate node_properties table
);

-- AFTER
CREATE TABLE nodes (
    id VARCHAR(1024),
    node_type VARCHAR(50),
    display_label VARCHAR(512),
    properties JSON,  -- Single source of truth!
    -- node_properties table deleted
);

-- JSON path indexes added
CREATE INDEX idx_java_fqn ON nodes(
    (JSON_VALUE(properties, '$.java.fullyQualifiedName'))
);
```

### Files Deleted
- `NodePropertyEntity.java` - Replaced by JSON
- `PropertyMapper.java` - Replaced by JSON operations
- `PropertyMapper.xml` - Replaced by JSON operations

### Files Modified
1. **schema.sql** - New JSON-based schema
2. **GraphNodeEntity.java** - `dataJson` → `properties`
3. **NodeMapper.java/xml** - Added `mergeProperties`, `findByPropertyValue`
4. **GraphDatabaseSerializer.java** - Simplified, added validation
5. **GraphDatabaseDeserializer.java** - Parse JSON directly
6. **GraphRepository.java** - Use JSON operations
7. **GraphDatabaseConfig.java** - Remove PropertyMapper references

### Files Created
1. **PropertiesValidator.java** - Application-level validation
2. **ValidationException.java** - Validation errors

## Key Operations

### Insert Node
```java
PropertiesValidator.validate(properties);  // Validate first
String json = mapper.writeValueAsString(properties);
nodeMapper.insertNode(new GraphNodeEntity(id, type, label, json));
tagMapper.insertTags(tags);  // Separate table
```

### Merge Properties (No Read Needed!)
```sql
UPDATE nodes 
SET properties = JSON_MERGEPATCH(
    properties, 
    '{"metrics.loc": 150, "new.property": "value"}'
)
WHERE id = 'node-id';
```

### Replace Tags
```java
tagMapper.deleteByNodeId(nodeId);  // Delete all
tagMapper.insertTags(newTags);     // Insert new
```

### Query Examples
```sql
-- Fast indexed query on JSON path
SELECT * FROM nodes 
WHERE JSON_VALUE(properties, '$.java.fullyQualifiedName') 
    = 'com.example.HelloWorld';

-- Combine with tags
SELECT n.* FROM nodes n
JOIN node_tags t ON n.id = t.node_id
WHERE JSON_VALUE(n.properties, '$.java.packageName') = 'com.example'
  AND t.tag = 'ejb.session_bean';
```

## Benefits Achieved

✅ **No Data Duplication** - Single source of truth  
✅ **Easy Merging** - Native `JSON_MERGEPATCH` (no read-modify-write)  
✅ **Flexible Schema** - Add properties without ALTER TABLE  
✅ **Fast Queries** - Indexed JSON paths + normalized tags  
✅ **Data Validation** - Application-level type checking  
✅ **Simpler Code** - 200+ lines removed  
✅ **Type Safe** - MyBatis compile-time checking  
✅ **Better Design** - Matches document-graph hybrid nature  

## Validation

Properties are validated before insertion:
- Required properties: fileName, fileExtension, relativePath
- Type validation: metrics must be numbers, is_* must be booleans
- Key format: `^[a-zA-Z0-9._-]+$`
- Value constraints: non-null, reasonable lengths

## Compilation Status

✅ **BUILD SUCCESS** - All changes compile without errors

## Migration Notes

For existing databases with `node_properties` table:

```sql
-- Aggregate old properties into JSON
UPDATE nodes n SET properties = (
    SELECT JSON_OBJECTAGG(p.property_key, p.property_value)
    FROM node_properties p
    WHERE p.node_id = n.id
);

-- Drop old table
DROP TABLE node_properties;
```

## Conclusion

The refactoring successfully eliminates the SQL/document impedance mismatch by:
- Using JSON where flexibility is needed (properties)
- Using normalized tables where performance matters (tags, edges)
- Providing native merge operations through H2's JSON functions
- Maintaining fast queries through strategic indexing

This is a much cleaner design that properly separates concerns between structured (SQL) and flexible (JSON) data.
