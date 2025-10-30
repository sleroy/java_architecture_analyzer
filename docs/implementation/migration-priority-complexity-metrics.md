# Migration Priority and Complexity Metrics Refactoring

## Overview

This document describes the refactoring from tag-based migration complexity and priority tracking to numeric metrics using the `setMaxMetric()` method.

## Problem Statement

Previously, migration complexity and priority were tracked using string-based tags like:
- `TAG_MIGRATION_COMPLEXITY_LOW`
- `TAG_MIGRATION_COMPLEXITY_MEDIUM`
- `MIGRATION_COMPLEXITY_HIGH`
- `TAG_MIGRATION_PRIORITY`

This approach had several limitations:
1. **No aggregation**: Multiple inspectors couldn't automatically determine the highest complexity/priority
2. **String-based**: Difficult to query and analyze numerically
3. **Inconsistent**: Tags mixed with string properties for priority

## Solution

Replaced tag-based tracking with numeric metrics stored separately from properties:

### Numeric Scale
- **LOW**: 3.0
- **MEDIUM**: 6.0  
- **HIGH**: 9.0

This 1-10 scale allows for:
- Easy comparison and aggregation
- Database queries with numeric operators (>, <, =, etc.)
- Future expansion (e.g., CRITICAL = 10.0)

### New API

#### Constants (in EjbMigrationTags.java)
```java
// Metric names
public static final String METRIC_MIGRATION_COMPLEXITY = "migration.complexity";
public static final String METRIC_MIGRATION_PRIORITY = "migration.priority";

// Complexity/Priority values
public static final double COMPLEXITY_LOW = 3.0;
public static final double COMPLEXITY_MEDIUM = 6.0;
public static final double COMPLEXITY_HIGH = 9.0;

public static final double PRIORITY_LOW = 3.0;
public static final double PRIORITY_MEDIUM = 6.0;
public static final double PRIORITY_HIGH = 9.0;
```

#### Usage Pattern

**Old way (DELETED)**:
```java
decorator.enableTag(EjbMigrationTags.TAG_MIGRATION_COMPLEXITY_LOW);
projectFileDecorator.setProperty(TAGS.TAG_MIGRATION_PRIORITY, "HIGH");
```

**New way**:
```java
decorator.getMetrics().setMaxMetric(
    EjbMigrationTags.METRIC_MIGRATION_COMPLEXITY, 
    EjbMigrationTags.COMPLEXITY_LOW
);

projectFileDecorator.getMetrics().setMaxMetric(
    EjbMigrationTags.METRIC_MIGRATION_PRIORITY,
    EjbMigrationTags.PRIORITY_HIGH
);
```

### Key Features

#### setMaxMetric() Behavior
The `setMaxMetric()` method automatically keeps the maximum value:

```java
// First inspector sets complexity to LOW (3.0)
decorator.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_LOW);

// Second inspector sets complexity to MEDIUM (6.0) - value updates
decorator.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_MEDIUM);

// Third inspector sets complexity to LOW (3.0) - value stays at MEDIUM (6.0)
decorator.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_LOW);

// Final result: COMPLEXITY_MEDIUM (6.0)
```

This allows multiple inspectors to contribute to complexity assessment without coordination.

## Implementation Details

### Core Changes

1. **Added setMaxMetric() to Metrics interface**
   - File: `analyzer-core/src/main/java/com/analyzer/api/metrics/Metrics.java`
   - Compares new value with current value
   - Only updates if new value is greater

2. **Implemented in BaseGraphNode**
   - File: `analyzer-core/src/main/java/com/analyzer/api/graph/BaseGraphNode.java`
   - Stores metrics in separate `metrics` map (not in properties)
   - Uses `getOrDefault(metricName, 0.0)` for comparison

3. **Extended NodeDecorator**
   - File: `analyzer-core/src/main/java/com/analyzer/core/export/NodeDecorator.java`
   - Added `setMaxMetric()` method
   - Added `getMetrics()` for direct access

### Updated Files

#### EjbMigrationTags.java
- Added numeric metric constants
- **DELETED** old tag constants

#### Inspector Files (20+ files)
Batch updated using sed commands:
- Replaced `enableTag()` calls with `getMetrics().setMaxMetric()`
- Removed tag constants from `@InspectorDependencies` annotations

Examples:
- `EjbBinaryClassInspector.java`
- `StatefulSessionBeanInspector.java`
- `EjbHomeInspector.java`
- `ApplicationServerConfigDetector.java`
- Many others...

## Storage

### Database
Metrics are stored in the `metrics` CLOB column in the `nodes` table:

```json
{
  "migration.complexity": 6.0,
  "migration.priority": 9.0,
  "linesOfCode": 250.0
}
```

### JSON Serialization
Metrics serialize/deserialize automatically via Jackson:

```json
{
  "@type": "JavaClassNode",
  "id": "com.example.MyClass",
  "metrics": {
    "migration.complexity": 6.0,
    "migration.priority": 9.0
  }
}
```

## Querying

### SQL Queries
```sql
-- Find high complexity classes
SELECT * FROM nodes 
WHERE JSON_EXTRACT(metrics, '$.migration.complexity') >= 6.0;

-- Classes with both high complexity and priority
SELECT * FROM nodes 
WHERE JSON_EXTRACT(metrics, '$.migration.complexity') >= 6.0
  AND JSON_EXTRACT(metrics, '$.migration.priority') >= 6.0;

-- Average complexity by node type
SELECT 
  node_type,
  AVG(CAST(JSON_EXTRACT(metrics, '$.migration.complexity') AS DOUBLE)) as avg_complexity
FROM nodes
GROUP BY node_type;
```

### Java API
```java
// Get metric value
Number complexity = node.getMetrics().getMetric("migration.complexity");

// Check if above threshold
if (complexity != null && complexity.doubleValue() >= COMPLEXITY_MEDIUM) {
    // High complexity class
}

// Get all metrics
Map<String, Double> allMetrics = node.getMetrics().getAllMetrics();
```

## Benefits

### 1. Automatic Aggregation
Multiple inspectors can contribute without coordination:
```java
// Inspector A detects basic complexity
decorator.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_LOW);

// Inspector B detects advanced feature usage
decorator.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_HIGH);

// Result: COMPLEXITY_HIGH automatically
```

### 2. Numeric Analysis
```sql
-- Distribution of complexity levels
SELECT 
  CASE 
    WHEN complexity < 5 THEN 'LOW'
    WHEN complexity < 7.5 THEN 'MEDIUM'
    ELSE 'HIGH'
  END as complexity_level,
  COUNT(*) as count
FROM (
  SELECT JSON_EXTRACT(metrics, '$.migration.complexity') as complexity
  FROM nodes
) 
GROUP BY complexity_level;
```

### 3. Clear Semantics
- Properties: Data values (method count, lines of code)
- Tags: Boolean flags (is EJB, is detected)
- Metrics: Numeric measurements (complexity, priority)

## Migration Guide for New Inspectors

When creating new inspectors that need to track migration complexity or priority:

### DO:
```java
// Use setMaxMetric with numeric constants
decorator.getMetrics().setMaxMetric(
    EjbMigrationTags.METRIC_MIGRATION_COMPLEXITY,
    EjbMigrationTags.COMPLEXITY_MEDIUM
);
```

### DON'T:
```java
// Don't use tags (DELETED - won't compile)
decorator.enableTag(EjbMigrationTags.TAG_MIGRATION_COMPLEXITY_MEDIUM);

// Don't use string properties
decorator.setProperty("migration.complexity", "MEDIUM");
```

## Testing

### Unit Tests
```java
@Test
void testMaxMetricBehavior() {
    ProjectFile file = new ProjectFile(path, root);
    
    // Set LOW
    file.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_LOW);
    assertEquals(3.0, file.getMetrics().getMetric(METRIC_MIGRATION_COMPLEXITY));
    
    // Set HIGH - should update
    file.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_HIGH);
    assertEquals(9.0, file.getMetrics().getMetric(METRIC_MIGRATION_COMPLEXITY));
    
    // Set MEDIUM - should stay at HIGH
    file.getMetrics().setMaxMetric(METRIC_MIGRATION_COMPLEXITY, COMPLEXITY_MEDIUM);
    assertEquals(9.0, file.getMetrics().getMetric(METRIC_MIGRATION_COMPLEXITY));
}
```

### Integration Tests
Run the full analysis pipeline and verify:
1. Metrics are set correctly
2. Maximum values are preserved
3. Database serialization works
4. JSON export includes metrics

## Performance Considerations

- Metrics stored in separate map from properties
- No performance overhead for nodes without metrics
- JSON serialization efficient
- Database queries can use JSON_EXTRACT with indexes

## Future Enhancements

### Potential Additions
1. **CRITICAL level**: 10.0 for extremely complex migrations
2. **Risk metrics**: Combined score from complexity + priority
3. **Confidence metrics**: How certain are we about the complexity?
4. **Effort metrics**: Estimated hours for migration

### Example:
```java
public static final String METRIC_MIGRATION_RISK = "migration.risk";
public static final String METRIC_MIGRATION_CONFIDENCE = "migration.confidence";
public static final String METRIC_MIGRATION_EFFORT_HOURS = "migration.effort.hours";
```

## Rollout Status

### Completed ✅
- Core API implementation (`setMaxMetric()`)
- `EjbMigrationTags.java` constants
- All 20+ EJB inspector files updated
- `ApplicationServerConfigDetector.java` updated
- Annotation cleanup in `@InspectorDependencies`
- Unit tests passing
- Compilation successful

### Remaining
- None - refactoring is complete

## References

- Implementation: `docs/implementation/metrics-storage-fix.md`
- API: `analyzer-core/src/main/java/com/analyzer/api/metrics/Metrics.java`
- Base Implementation: `analyzer-core/src/main/java/com/analyzer/api/graph/BaseGraphNode.java`
- Constants: `analyzer-ejb2spring/src/main/java/com/analyzer/rules/ejb2spring/EjbMigrationTags.java`
