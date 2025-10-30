# Metrics Storage Fix - Database Schema Alignment

## Overview
Fixed the metrics storage implementation to properly separate metrics from properties in the database, ensuring alignment between `GraphNode`/`BaseGraphNode` interfaces and the H2 database schema.

## Problem Statement

The system had inconsistencies in how metrics were being stored:

1. **H2GraphStorageRepository** was incorrectly storing metrics inside the properties JSON instead of the dedicated metrics column
2. **NodeMapper.xml** was using incorrect type mappings (GraphNode interface instead of GraphNodeEntity)
3. **TagMapper.xml** had similar type mapping issues
4. Metrics were being mixed with properties, violating the architectural separation

## Changes Made

### 1. H2GraphStorageRepository.java

**File**: `analyzer-core/src/main/java/com/analyzer/core/db/H2GraphStorageRepository.java`

#### Changed: saveNode() method

**Before**:
```java
// Include metrics in properties if present
if (node.getMetrics() != null) {
    properties.put("metrics", node.getMetrics());  // WRONG: Metrics in properties
}

String propertiesJson = jsonSerializer.serializeProperties(properties);
GraphNodeEntity nodeEntity = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson);
```

**After**:
```java
// Extract metrics separately - DO NOT include in properties
Map<String, Double> metricsMap = null;
if (node.getMetrics() != null) {
    metricsMap = node.getMetrics().getAllMetrics();
}

// Serialize properties and metrics separately
String propertiesJson = jsonSerializer.serializeProperties(properties);
String metricsJson = null;
if (metricsMap != null && !metricsMap.isEmpty()) {
    // Cast Map<String, Double> to Map<String, Object> for serialization
    Map<String, Object> metricsAsObjects = new HashMap<>(metricsMap);
    metricsJson = jsonSerializer.serializeProperties(metricsAsObjects);
}

GraphNodeEntity nodeEntity = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson, metricsJson);
```

**Added Import**:
```java
import java.util.HashMap;
```

### 2. NodeMapper.xml

**File**: `analyzer-core/src/main/resources/mybatis/mappers/NodeMapper.xml`

#### Changed: ResultMap and Parameter Types

**Before**:
```xml
<resultMap id="NodeResultMap" type="GraphNode">
```

**After**:
```xml
<resultMap id="NodeResultMap" type="com.analyzer.core.db.entity.GraphNodeEntity">
```

#### Changed all parameterType declarations:

- `insertNode`: `GraphNode` → `com.analyzer.core.db.entity.GraphNodeEntity`
- `mergeNode`: `GraphNode` → `com.analyzer.core.db.entity.GraphNodeEntity`
- `updateNode`: `GraphNode` → `com.analyzer.core.db.entity.GraphNodeEntity`

### 3. TagMapper.xml

**File**: `analyzer-core/src/main/resources/mybatis/mappers/TagMapper.xml`

#### Changed: ResultMap and Parameter Types

**Before**:
```xml
<resultMap id="TagResultMap" type="NodeTag">
<insert id="insertTag" parameterType="NodeTag">
```

**After**:
```xml
<resultMap id="TagResultMap" type="com.analyzer.core.db.entity.NodeTagEntity">
<insert id="insertTag" parameterType="com.analyzer.core.db.entity.NodeTagEntity">
```

## Architecture

### Metrics Flow

```
GraphNode (interface)
    └─> BaseGraphNode.getMetrics() → Returns Metrics interface
        └─> Metrics.getAllMetrics() → Returns Map<String, Double>
            └─> H2GraphStorageRepository.saveNode()
                ├─> Serializes metrics to JSON separately
                └─> Stores in GraphNodeEntity.metricsMap
                    └─> MyBatis saves to nodes.metrics column (CLOB)
```

### Data Separation

**Properties Column** (nodes.properties):
- Domain-specific properties
- Configuration data
- Metadata
- Business attributes

**Metrics Column** (nodes.metrics):
- Numeric measurements
- Quality metrics
- Complexity scores
- Count statistics

## Database Schema

The H2 database schema already had the correct structure:

```sql
CREATE TABLE IF NOT EXISTS nodes (
    id VARCHAR(1024) PRIMARY KEY,
    node_type VARCHAR(50) NOT NULL,
    display_label VARCHAR(512),
    properties CLOB,                    -- JSON for properties
    metrics CLOB,                        -- JSON for metrics (separate!)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Validation

### Metrics API (BaseGraphNode)

```java
public Metrics getMetrics() {
    return new Metrics() {
        @Override
        public Number getMetric(String metricName) {
            return metrics.getOrDefault(metricName, 0.0);
        }

        @Override
        public void setMetric(String metricName, Number value) {
            if (value == null) {
                metrics.remove(metricName);
            } else {
                metrics.put(metricName, value.doubleValue());
            }
        }

        @Override
        public Map<String, Double> getAllMetrics() {
            return Collections.unmodifiableMap(metrics);
        }
    };
}
```

### JSON Serialization

Metrics are serialized as:
```json
{
  "cyclomatic_complexity": 15.0,
  "lines_of_code": 250.0,
  "method_count": 12.0
}
```

Properties remain separate:
```json
{
  "java": {
    "fullyQualifiedName": "com.example.MyClass",
    "package": "com.example"
  }
}
```

## Testing Recommendations

1. **Unit Tests**: Verify metrics are stored in separate column
2. **Integration Tests**: Save and load nodes with metrics
3. **Serialization Tests**: Ensure JSON format is correct
4. **Migration Tests**: Verify existing data isn't affected

## Benefits

1. ✅ **Clear Separation**: Properties and metrics are architecturally distinct
2. ✅ **Type Safety**: Metrics are always numbers (Double)
3. ✅ **Query Optimization**: Can query metrics column independently
4. ✅ **Schema Alignment**: Code matches database structure
5. ✅ **Maintainability**: Clear data model boundaries

## Related Files

- `analyzer-core/src/main/java/com/analyzer/api/graph/GraphNode.java`
- `analyzer-core/src/main/java/com/analyzer/api/graph/BaseGraphNode.java`
- `analyzer-core/src/main/java/com/analyzer/api/metrics/Metrics.java`
- `analyzer-core/src/main/java/com/analyzer/core/db/entity/GraphNodeEntity.java`
- `analyzer-core/src/main/resources/db/schema.sql`

## Migration Notes

No data migration required since this fix prevents future data from being stored incorrectly. Existing data with metrics in properties should be handled by:

1. Loading nodes will read from metrics column (empty for old data)
2. Next save will properly separate metrics
3. Properties will be cleaned of any metrics data

## Date
October 30, 2025
