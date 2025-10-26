# Two-Level Metrics Design

## Overview

Implement a dual-level metrics system where:
- **Class-level metrics** provide detailed per-class analysis (stored on JavaClassNode)
- **File-level metrics** provide aggregate summaries for quick file-level insights (stored on ProjectFile)

## Aggregation Strategies by Metric Type

### 1. InheritanceDepthInspector
- **Class-level**: Store exact depth per class
- **File-level**: `MAX` - The deepest inheritance in the file
- **Rationale**: Identifies the most complex inheritance pattern in a file

```java
// Class level: JavaClassNode
inheritance.depth = 3

// File level: ProjectFile (aggregated)
inheritance.depth.max = 3
inheritance.depth.classes_analyzed = 2
```

### 2. InterfaceNumberInspector
- **Class-level**: Store interface count per class
- **File-level**: `MAX` - The class with most interfaces
- **Rationale**: Identifies the most interface-heavy class in a file

```java
// Class level: JavaClassNode
interfaces.total_count = 5
interfaces.direct_count = 2
interfaces.inherited_count = 3

// File level: ProjectFile (aggregated)
interfaces.total_count.max = 5
interfaces.direct_count.max = 2
interfaces.classes_analyzed = 2
```

### 3. CodeQualityInspector
- **Class-level**: Store quality score per class
- **File-level**: `AVG` - Average quality across all classes
- **Rationale**: Provides overall file quality assessment

```java
// Class level: JavaClassNode
code.quality.score = 7.5

// File level: ProjectFile (aggregated)
code.quality.score.avg = 7.3
code.quality.score.min = 6.0
code.quality.score.max = 8.5
code.quality.classes_analyzed = 3
```

### 4. TypeUsageInspector
- **Class-level**: Store type usage metrics per class
- **File-level**: `MAX` - Most complex type usage
- **Rationale**: Identifies the class with highest type complexity

```java
// Class level: JavaClassNode
types.total_unique = 15
types.complexity_score = 8.5

// File level: ProjectFile (aggregated)
types.total_unique.max = 15
types.complexity_score.max = 8.5
types.classes_analyzed = 2
```

## Implementation Pattern

### Base Aggregation Helper

Create helper methods in base classes:

```java
protected void aggregateMaxMetric(ProjectFile projectFile, 
                                   String metricName, 
                                   double classValue) {
    Double currentMax = projectFile.getDoubleProperty(metricName + ".max");
    if (currentMax == null || classValue > currentMax) {
        projectFile.setProperty(metricName + ".max", classValue);
    }
    
    // Track count
    Integer count = projectFile.getIntegerProperty(metricName + ".classes_analyzed", 0);
    projectFile.setProperty(metricName + ".classes_analyzed", count + 1);
}

protected void aggregateAvgMetric(ProjectFile projectFile,
                                   String metricName,
                                   double classValue) {
    // Get current sum and count
    Double currentSum = projectFile.getDoubleProperty(metricName + ".sum", 0.0);
    Integer count = projectFile.getIntegerProperty(metricName + ".count", 0);
    
    // Update
    projectFile.setProperty(metricName + ".sum", currentSum + classValue);
    projectFile.setProperty(metricName + ".count", count + 1);
    projectFile.setProperty(metricName + ".avg", (currentSum + classValue) / (count + 1));
    
    // Track min/max for additional insights
    updateMinMax(projectFile, metricName, classValue);
}
```

### Property Naming Convention

**Class-level** (on JavaClassNode):
- `inheritance.depth`
- `interfaces.total_count`
- `code.quality.score`

**File-level** (on ProjectFile - aggregated):
- `inheritance.depth.max`
- `inheritance.depth.classes_analyzed`
- `interfaces.total_count.max`
- `interfaces.classes_analyzed`
- `code.quality.score.avg`
- `code.quality.score.min`
- `code.quality.score.max`
- `code.quality.classes_analyzed`

## Migration Steps

1. Update each inspector to:
   - Store detailed metrics on JavaClassNode
   - Compute and store aggregates on ProjectFile
   - Use proper property naming conventions

2. Add aggregation helper methods to base classes

3. Update tests to verify both levels

4. Document property names in InspectorTags

## Benefits

✅ **Quick File-Level Insights**: Aggregates provide immediate understanding
✅ **Detailed Class Analysis**: Full per-class metrics available when needed
✅ **CSV Export Friendly**: File-level aggregates perfect for reports
✅ **Architectural Analysis**: Can identify hotspots at file level
✅ **Backward Compatible**: Doesn't break existing code using JavaClassNode metrics
