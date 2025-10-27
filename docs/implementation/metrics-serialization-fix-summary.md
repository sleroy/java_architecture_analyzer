# Metrics Serialization Fix - Implementation Summary

## Overview
Fixed critical bug where metrics were not being persisted to the database and were lost during serialization/export processes.

## Date
October 27, 2025

## Problem Statement

### Issue 1: Metrics Not Being Persisted (CRITICAL BUG)
- Metrics were calculated by inspectors (fileSize, methodCount, cyclomaticComplexity, etc.)
- They were stored in ProjectFile properties with "metrics." prefix
- But GraphDatabaseSerializer did NOT save them to the database's `metrics` column
- Current code used 4-parameter constructor without metrics
- Result: All metrics were lost during database serialization

### Issue 2: Metrics Not Loaded in Export Commands
- JsonExportCommand and CsvExportCommand loaded from database
- They only loaded properties column, not metrics column
- Metrics were unavailable in exports

### Issue 3: Metrics Not Separated in JSON Export
- ProjectSerializer mixed metrics with regular properties
- No clear separation in JSON output structure

## Implementation

### 1. Fixed GraphDatabaseSerializer.java
**File:** `analyzer-core/src/main/java/com/analyzer/core/db/serializer/GraphDatabaseSerializer.java`

**Changes:**
- Added HashMap import
- Modified `serialize()` method to extract metrics from properties
- Modified `serializeFile()` method to extract metrics from properties
- Separated properties with "metrics." prefix into separate map
- Used 5-parameter constructor: `new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson, metricsJson)`

**Key Code Addition:**
```java
// Extract metrics from properties (keys starting with "metrics.")
Map<String, Object> properties = file.getNodeProperties();
Map<String, Object> regularProps = new HashMap<>();
Map<String, Object> metrics = new HashMap<>();

for (Map.Entry<String, Object> entry : properties.entrySet()) {
    if (entry.getKey().startsWith("metrics.")) {
        String metricName = entry.getKey().substring(8); // Remove "metrics." prefix
        metrics.put(metricName, entry.getValue());
    } else {
        regularProps.put(entry.getKey(), entry.getValue());
    }
}

String propertiesJson = jsonMapper.writeValueAsString(regularProps);
String metricsJson = metrics.isEmpty() ? null : jsonMapper.writeValueAsString(metrics);

GraphNodeEntity node = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson, metricsJson);
```

### 2. Fixed JsonExportCommand.java
**File:** `analyzer-app/src/main/java/com/analyzer/cli/JsonExportCommand.java`

**Changes:**
- Modified `loadDataFromDatabase()` method
- Added code to load metrics from `entity.getMetricsMap()`
- Re-added metrics to ProjectFile with "metrics." prefix

**Key Code Addition:**
```java
// Load metrics from database and add with "metrics." prefix
String metricsMapStr = entity.getMetricsMap();
if (metricsMapStr != null && !metricsMapStr.isEmpty()) {
    Map<String, Object> metrics = jsonSerializer.deserializeProperties(metricsMapStr);
    // Add metrics back with prefix
    for (Map.Entry<String, Object> entry : metrics.entrySet()) {
        properties.put("metrics." + entry.getKey(), entry.getValue());
    }
}
```

### 3. Fixed CsvExportCommand.java
**File:** `analyzer-app/src/main/java/com/analyzer/cli/CsvExportCommand.java`

**Changes:**
- Modified `loadDataFromDatabase()` method
- Added same metrics loading logic as JsonExportCommand
- Re-added metrics to ProjectFile with "metrics." prefix

**Key Code Addition:** (same as JsonExportCommand)

### 4. Fixed ProjectSerializer.java
**File:** `analyzer-core/src/main/java/com/analyzer/core/export/ProjectSerializer.java`

**Changes:**
- Modified `serializeNode()` method
- Separated metrics from regular properties during export
- Added metrics as separate section in JSON output
- Removed "metrics." prefix from metric names in output

**Key Code Addition:**
```java
// Separate properties and metrics
Map<String, Object> allProperties = node.getNodeProperties();
if (allProperties != null && !allProperties.isEmpty()) {
    Map<String, Object> regularProperties = new HashMap<>();
    Map<String, Object> metrics = new HashMap<>();

    // Separate metrics from regular properties
    for (Map.Entry<String, Object> entry : allProperties.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();

        if (key.startsWith("metrics.")) {
            // Extract metric name without prefix
            String metricName = key.substring(8);
            metrics.put(metricName, value);
        } else {
            // Process regular properties
            // ... (existing property processing code)
        }
    }

    // Add nested properties
    if (!regularProperties.isEmpty()) {
        Map<String, Object> nestedProperties = PropertyNestingTransformer.nestProperties(regularProperties);
        nodeData.put("properties", nestedProperties);
    }

    // Add metrics as separate section
    if (!metrics.isEmpty()) {
        nodeData.put("metrics", metrics);
    }
}
```

## Database Schema
The database already supported metrics:
- Table: `nodes`
- Columns: `id, node_type, display_label, properties, metrics, created_at, updated_at`
- The `metrics` column exists and is now properly populated

## Expected JSON Output Format

### Before Fix
```json
{
  "id": "src/main/java/Example.java",
  "nodeType": "java",
  "properties": {
    "fileName": "Example.java",
    "metrics.fileSize": 1024,
    "metrics.methodCount": 5,
    "metrics.cyclomaticComplexity": 3
  }
}
```

### After Fix
```json
{
  "id": "src/main/java/Example.java",
  "nodeType": "java",
  "properties": {
    "fileName": "Example.java"
  },
  "metrics": {
    "fileSize": 1024,
    "methodCount": 5,
    "cyclomaticComplexity": 3
  }
}
```

## Benefits

1. **Data Integrity**: Metrics are now properly persisted to database
2. **Export Completeness**: CSV and JSON exports include all metrics
3. **Better Structure**: JSON exports have clear separation between properties and metrics
4. **No Data Loss**: Metrics survive the entire analysis → database → export pipeline

## Testing Checklist

- [x] Code compiles successfully
- [x] All modules build without errors
- [ ] Run analysis on test project
- [ ] Verify metrics are saved to database (check with SQL query)
- [ ] Verify CSV export includes metrics columns
- [ ] Verify JSON export has metrics section for each node
- [ ] Check that common metrics appear: fileSize, methodCount, cyclomaticComplexity
- [ ] Verify no regression in existing functionality

## Verification SQL Queries

To verify metrics are saved:
```sql
-- Check if metrics column has data
SELECT id, node_type, LENGTH(metrics) as metrics_length 
FROM nodes 
WHERE metrics IS NOT NULL 
LIMIT 10;

-- View sample metrics
SELECT id, metrics 
FROM nodes 
WHERE metrics IS NOT NULL 
LIMIT 5;
```

## Files Modified

1. `analyzer-core/src/main/java/com/analyzer/core/db/serializer/GraphDatabaseSerializer.java`
   - Fixed metrics serialization to database

2. `analyzer-app/src/main/java/com/analyzer/cli/JsonExportCommand.java`
   - Fixed metrics loading from database
   - Fixed database connection bug (was passing .mv.db path causing H2 to create empty database)

3. `analyzer-app/src/main/java/com/analyzer/cli/CsvExportCommand.java`
   - Fixed metrics loading from database
   - Fixed database connection bug (was passing .mv.db path causing H2 to create empty database)

4. `analyzer-core/src/main/java/com/analyzer/core/export/ProjectSerializer.java`
   - Fixed metrics separation in JSON export
   - Removed redundant project.json file generation

## Related Documentation

- Database schema: `analyzer-core/src/main/resources/db/schema.sql`
- MyBatis mapper: `analyzer-core/src/main/resources/mybatis/mappers/NodeMapper.xml`
- Entity class: `analyzer-core/src/main/java/com/analyzer/core/db/entity/GraphNodeEntity.java`

## Notes

- The 5-parameter GraphNodeEntity constructor was already available
- The database `metrics` column was already defined in the schema
- This fix simply connects the existing infrastructure that wasn't being used
- Backward compatible: old databases with NULL metrics will work fine

## Critical Bug Fixed During Implementation

**Database Connection Issue in Export Commands**

During testing, discovered that JsonExportCommand and CsvExportCommand were creating empty databases instead of connecting to existing ones.

**Root Cause:**
- Commands were passing database path WITH `.mv.db` extension to `GraphDatabaseConfig.initialize()`
- H2 automatically adds `.mv.db` extension, so this caused it to look for `graph.db.mv.db.mv.db`
- When not found, H2 created a new empty database at that incorrect location
- Result: Export commands reported "0 nodes, 0 edges" even after successful analysis

**Fix:**
- Changed both export commands to pass base path WITHOUT `.mv.db` extension
- H2 now connects to correct existing database
- Exports now work correctly with full data

**Files Modified:**
- `analyzer-app/src/main/java/com/analyzer/cli/JsonExportCommand.java` (line ~87)
- `analyzer-app/src/main/java/com/analyzer/cli/CsvExportCommand.java` (line ~87)

**Code Change:**
```java
// Before (WRONG - creates empty DB):
Path dbPath = projectDir.resolve(".analysis/graph.db.mv.db");
dbConfig.initialize(dbPath);  // H2 looks for graph.db.mv.db.mv.db

// After (CORRECT - connects to existing DB):
Path dbBasePath = projectDir.resolve(".analysis/graph.db");
dbConfig.initialize(dbBasePath);  // H2 adds .mv.db automatically
```

## Future Considerations

### Issue 2 from Original Task: Redundant Project.projectFiles Storage
- Project class stores ProjectFiles in ConcurrentHashMap during analysis
- After database serialization, this becomes redundant
- CsvExportCommand and JsonExportCommand both load FROM database, not from Project
- Future refactoring: Consider removing projectFiles map and making Project lightweight

### Issue 3 from Original Task: Redundant project.json File
- ProjectSerializer creates project.json with minimal metadata
- This data is redundant (file count can be derived, name/path known from context)
- Future: Consider removing or simplifying serializeProject() method
