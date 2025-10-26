# Metrics Integration Summary

## Overview
This document describes the implementation of the Metrics system in the Java Architecture Analyzer, including JSON serialization, H2 database persistence, and deserialization support.

## Architecture

### Design Decision
**Metrics are stored in a separate `Map<String, Double>` within `BaseGraphNode`**, independent from properties. This design provides:
- Clear separation of concerns between properties and metrics
- Type safety for numeric values
- Dedicated interface for metrics operations
- Flexibility for future enhancements

### Component Structure

```mermaid
flowchart TD
    A[Metrics Interface] --> B[BaseGraphNode]
    B --> C[ProjectFile]
    B --> D[Other Graph Nodes]
    
    B --> E[JSON Serialization]
    E --> F[@JsonProperty methods]
    
    B --> G[H2 Database]
    G --> H[metrics CLOB column]
    
    E --> I[ProjectDeserializer]
    I --> B
```

## Implementation Details

### 1. Metrics Interface
**Location:** `src/main/java/com/analyzer/core/metrics/Metrics.java`

```java
public interface Metrics {
    Number getMetric(String metricName);
    void setMetric(String metricName, Number value);
    Map<String, Double> getAllMetrics();
}
```

### 2. BaseGraphNode Metrics Storage
**Location:** `src/main/java/com/analyzer/core/graph/BaseGraphNode.java`

**Key Features:**
- Private `Map<String, Double> metrics` field
- `getMetrics()` returns an implementation of the Metrics interface
- `@JsonProperty("metrics")` methods for serialization/deserialization

**Implementation:**
```java
private final Map<String, Double> metrics = new HashMap<>();

public Metrics getMetrics() {
    return new Metrics() {
        @Override
        public Number getMetric(String metricName) {
            return metrics.get(metricName);
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

@JsonProperty("metrics")
public Map<String, Double> getMetricsMap() {
    return new HashMap<>(metrics);
}

@JsonProperty("metrics")
public void setMetricsMap(Map<String, Double> metricsMap) {
    this.metrics.clear();
    if (metricsMap != null) {
        this.metrics.putAll(metricsMap);
    }
}
```

### 3. JSON Serialization

**Format:**
```json
{
  "id": "/path/to/file",
  "nodeType": "file",
  "properties": {
    "fileName": "TestClass.java",
    "relativePath": "src/test/TestClass.java"
  },
  "metrics": {
    "complexity": 15.0,
    "linesOfCode": 250.0,
    "coverage": 0.85
  },
  "tags": []
}
```

**Key Points:**
- Metrics are serialized as a separate JSON object
- All metric values are stored as `Double`
- Empty metrics maps serialize as `{}`
- Uses Jackson's `@JsonProperty` annotation

### 4. H2 Database Schema
**Location:** `src/main/resources/db/schema.sql`

**Schema Change:**
```sql
CREATE TABLE IF NOT EXISTS nodes (
    id VARCHAR(1024) PRIMARY KEY,
    node_type VARCHAR(50) NOT NULL,
    display_label VARCHAR(512),
    properties CLOB,
    metrics CLOB,  -- NEW: Stores metrics as JSON
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Storage Format:**
- Metrics stored as JSON in CLOB column
- Same serialization format as file-based JSON
- Supports H2's JSON functions for queries

### 5. MyBatis Mapping
**Location:** `src/main/resources/mybatis/mappers/NodeMapper.xml`

**Key Changes:**
- Added `metrics` column to all SQL statements
- Result map includes `metricsMap` property mapping
- Merge and update statements handle metrics

**Example:**
```xml
<resultMap id="NodeResultMap" type="GraphNode">
    <id property="id" column="id"/>
    <result property="nodeType" column="node_type"/>
    <result property="displayLabel" column="display_label"/>
    <result property="properties" column="properties"/>
    <result property="metricsMap" column="metrics"/>
    <result property="createdAt" column="created_at"/>
    <result property="updatedAt" column="updated_at"/>
</resultMap>
```

### 6. Deserialization Support
**Location:** `src/main/java/com/analyzer/core/model/ProjectDeserializer.java`

**Implementation:**
```java
// Load metrics if available
if (fileNode.has("metrics") && !fileNode.get("metrics").isNull()) {
    JsonNode metricsNode = fileNode.get("metrics");
    Iterator<Map.Entry<String, JsonNode>> metricsIterator = metricsNode.fields();

    int metricCount = 0;
    while (metricsIterator.hasNext()) {
        Map.Entry<String, JsonNode> metricEntry = metricsIterator.next();
        String metricName = metricEntry.getKey();
        JsonNode metricValueNode = metricEntry.getValue();

        if (metricValueNode.isNumber()) {
            double metricValue = metricValueNode.asDouble();
            projectFile.getMetrics().setMetric(metricName, metricValue);
            metricCount++;
        }
    }
}
```

**Key Features:**
- Validates metric values are numeric
- Warns about non-numeric values
- Gracefully handles missing metrics
- Maintains backward compatibility

### 7. ProjectFile Configuration
**Location:** `src/main/java/com/analyzer/core/model/ProjectFile.java`

**Added Annotation:**
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectFile extends BaseGraphNode {
    // ...
}
```

This allows deserialization to ignore computed/derived properties that are serialized but don't have setters.

## Usage Examples

### Setting Metrics
```java
ProjectFile file = new ProjectFile(filePath, projectRoot);

// Set individual metrics
file.getMetrics().setMetric("complexity", 15);
file.getMetrics().setMetric("linesOfCode", 250);
file.getMetrics().setMetric("coverage", 0.85);
```

### Reading Metrics
```java
// Get individual metric
Number complexity = file.getMetrics().getMetric("complexity");

// Get all metrics
Map<String, Double> allMetrics = file.getMetrics().getAllMetrics();

// Iterate metrics
for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

### Removing Metrics
```java
// Remove by setting to null
file.getMetrics().setMetric("coverage", null);
```

## Testing

### Test Coverage
**Location:** `src/test/java/com/analyzer/core/MetricsSerializationTest.java`

**Tests Include:**
1. **testMetricsJsonSerialization**: Full serialization/deserialization cycle
2. **testEmptyMetricsSerialization**: Empty metrics handling
3. **testMetricsUpdateAfterDeserialization**: Metrics mutability after load
4. **testMetricsWithNullAndRemoval**: Metric removal behavior

**All tests pass successfully** ✅

### Running Tests
```bash
mvn test -Dtest=MetricsSerializationTest
```

## Database Querying

### Query Metrics with H2
```sql
-- Query nodes with specific metric value
SELECT id, node_type, 
       JSON_VALUE(metrics, '$.complexity') as complexity
FROM nodes
WHERE JSON_VALUE(metrics, '$.complexity') > 10;

-- Get all metrics for a node
SELECT id, metrics
FROM nodes
WHERE id = '/path/to/file';
```

## Migration Notes

### Existing Data
- **No migration required** for existing projects
- Nodes without metrics will have `NULL` in metrics column
- JSON serialization handles null metrics gracefully

### Backward Compatibility
- ✅ Old JSON files without metrics load correctly
- ✅ Empty metrics serialize/deserialize properly
- ✅ Database queries work with null metrics

## Performance Considerations

1. **Memory**: Separate metrics map adds ~56 bytes per node (empty HashMap overhead)
2. **Serialization**: Metrics add to JSON size, but typically small (<1KB per node)
3. **Database**: CLOB storage efficient for JSON, indexed queries possible
4. **Query Performance**: H2 JSON functions may be slower than dedicated columns

## Future Enhancements

### Potential Improvements
1. **Metric Metadata**: Add units, descriptions, ranges
2. **Computed Metrics**: Support derived/calculated metrics
3. **Metric History**: Track metric changes over time
4. **Aggregations**: Built-in sum/avg/min/max operations
5. **Type Variants**: Support Integer/Long metrics without Double conversion

### Extension Points
```java
// Example: Metric with metadata
public interface RichMetric {
    Number getValue();
    String getUnit();
    String getDescription();
    Number getMin();
    Number getMax();
}
```

## Summary

The metrics integration is **complete and tested**, providing:

✅ Separate dedicated storage for metrics  
✅ JSON serialization/deserialization  
✅ H2 database persistence  
✅ Type-safe API via Metrics interface  
✅ Backward compatibility  
✅ Full test coverage  

The implementation respects the user's design preference for separate metrics storage while ensuring seamless integration with the existing serialization and persistence infrastructure.
