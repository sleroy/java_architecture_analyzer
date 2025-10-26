# Two-Level Metrics Implementation Summary

## Completed: 2025-10-26

### Overview

Successfully implemented a **two-level metrics system** where:
- **Class-level metrics** (detailed) are stored on JavaClassNode
- **File-level metrics** (aggregated) are stored on ProjectFile for quick insights

### Implementation Details

#### 1. Base Infrastructure ✅ COMPLETE

**File**: `AbstractClassLoaderBasedInspector.java`

Added helper methods for metric aggregation:
```java
- aggregateMaxMetric(projectFile, metricName, value)
  // Tracks maximum value seen - for identifying most complex class

- aggregateAvgMetric(projectFile, metricName, value)  
  // Computes running average with min/max tracking

- aggregateSumMetric(projectFile, metricName, value)
  // Accumulates total values

- updateMinMax(projectFile, metricName, value)
  // Helper for min/max tracking
```

**Key Features**:
- Automatic counting of analyzed classes (`metricName.classes_analyzed`)
- Support for both int and double values
- Consistent property naming: `metricName.max`, `metricName.avg`, etc.

#### 2. InterfaceNumberInspector ✅ COMPLETE

**Aggregation Strategy**: MAX

**Class-Level Metrics** (on JavaClassNode):
```
interfaces.total_count
interfaces.direct_count  
interfaces.inherited_count
interfaces.complexity_score
interfaces.has_framework
interfaces.framework_types
```

**File-Level Aggregates** (on ProjectFile):
```
interfaces.total_count.max           // Highest interface count
interfaces.total_count.classes_analyzed  // Number of classes

interfaces.direct_count.max
interfaces.direct_count.classes_analyzed

interfaces.inherited_count.max
interfaces.inherited_count.classes_analyzed

interfaces.complexity_score.max
interfaces.complexity_score.classes_analyzed
```

**Rationale**: MAX strategy identifies the class with the most interfaces, helping spot over-engineered or complex abstractions at file level.

#### 3. InheritanceDepthInspector ✅ COMPLETE

**Aggregation Strategy**: MAX

**Class-Level Metrics** (on JavaClassNode):
```
inheritance.depth
inheritance.is_deep
inheritance.superclass_fqn
inheritance.root_class
```

**File-Level Aggregates** (on ProjectFile):
```
inheritance.depth.max                // Deepest inheritance
inheritance.depth.classes_analyzed   // Number of classes
```

**Rationale**: MAX strategy identifies the deepest inheritance hierarchy, helping spot potential maintenance issues.

**Backward Compatibility**: Maintains legacy properties on ProjectFile decorator for existing code compatibility.

### Property Naming Convention

#### Pattern Established

**Class-level** (JavaClassNode):
- Plain property name: `metric.name`
- Examples: `inheritance.depth`, `interfaces.total_count`

**File-level** (ProjectFile - aggregated):
- Aggregation suffix: `metric.name.{agg}`
- Tracking counter: `metric.name.classes_analyzed`
- Examples:
  - `inheritance.depth.max`
  - `interfaces.total_count.max`
  - `code.quality.score.avg`
  - `code.quality.score.min`
  - `code.quality.score.max`

### Benefits Achieved

✅ **Quick File-Level Insights**: CSV exports show aggregate metrics immediately  
✅ **Detailed Class Analysis**: Full per-class metrics available when needed  
✅ **Performance**: Computed in single pass, no post-processing needed  
✅ **Architectural Analysis**: Identify hotspots at file level  
✅ **Backward Compatible**: Doesn't break existing code  
✅ **Consistent Pattern**: Reusable aggregation helpers  

### Usage Example

**CSV Export Output**:
```csv
File,InheritanceDepthMax,InterfacesMax,ClassesAnalyzed
src/MyService.java,3,5,2
src/ComplexClass.java,7,12,1
```

**Class-Level Access**:
```java
JavaClassNode classNode = graphRepository.getNodeById("com.example.MyClass");
int depth = classNode.getIntProperty("inheritance.depth");
int interfaces = classNode.getIntProperty("interfaces.total_count");
```

**File-Level Access**:
```java
ProjectFile file = projectFileRepository.getById("src/MyService.java");
double maxDepth = file.getDoubleProperty("inheritance.depth.max");
int classesAnalyzed = file.getIntegerProperty("inheritance.depth.classes_analyzed");
```

### Recently Completed (2025-10-26)

#### 3. TypeUsageInspector ✅ COMPLETE

**Aggregation Strategy**: MAX

**Class-Level Metrics** (on JavaClassNode):
```
types.total_unique
types.field_count
types.parameter_count
types.return_count
types.exception_count
types.annotation_count
types.primitive_count
types.reference_count
types.collection_count
types.framework_count
types.complexity_score
types.generic_count
```

**File-Level Aggregates** (on ProjectFile):
```
types.total_unique.max
types.complexity_score.max
types.field_count.max
types.parameter_count.max
types.generic_count.max
types.total_unique.classes_analyzed
```

**Rationale**: MAX strategy identifies the class with the most complex type usage, helping spot classes with excessive dependencies or over-engineered type systems.

#### 4. ThreadLocalUsageInspector ✅ COMPLETE

**Aggregation Strategy**: SUM

**Class-Level Metrics** (on JavaClassNode):
```
threadlocal.detected (boolean)
threadlocal.count
threadlocal.fields (comma-separated list)
```

**File-Level Aggregates** (on ProjectFile):
```
threadlocal.count.sum                      // Total ThreadLocal fields
threadlocal.count.classes_analyzed         // Number of classes analyzed
```

**Rationale**: SUM strategy accumulates total ThreadLocal usage across all classes in a file, helping identify files with heavy thread-local state management and potential memory leak risks.

### Remaining Work (Future Iterations)

The following inspectors could benefit from two-level metrics implementation:

1. **CodeQualityInspector** - Would use AVG aggregation (SKIPPED - different base class)
   - Class-level: Quality score per class
   - File-level: Average quality score + min/max
   - **Note**: Requires extending AbstractBedrockInspectorAbstract with aggregation helpers

2. **CyclomaticComplexityInspector** - Should use AVG or MAX
   - Class-level: Complexity per class
   - File-level: Average or max complexity

### Testing Recommendations

1. **Unit Tests**: Verify aggregation math is correct
2. **Integration Tests**: Verify both levels populated correctly
3. **CSV Export Tests**: Verify aggregates appear in exports
4. **Multi-Class Files**: Test files with multiple class definitions

### Migration Notes

For developers extending this pattern:

1. Extend `AbstractClassLoaderBasedInspector` to get aggregation helpers
2. Choose aggregation strategy (MAX, AVG, SUM)
3. Store class metrics on JavaClassNode
4. Call aggregation helper for file metrics
5. Use consistent naming: `metric.name.{agg}`
6. Document the aggregation strategy in inspector JavaDoc

### Example Template

```java
@Override
protected void analyzeLoadedClass(Class<?> loadedClass, 
                                   ProjectFile projectFile,
                                   NodeDecorator<ProjectFile> decorator) {
    // Calculate metrics
    int metricValue = computeMetric(loadedClass);
    
    // LEVEL 1: Store on JavaClassNode
    String fqn = projectFile.getStringProperty(InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME);
    graphRepository.getNodeById(fqn).ifPresent(node -> {
        if (node instanceof JavaClassNode) {
            ((JavaClassNode) node).setProperty("my.metric", metricValue);
        }
    });
    
    // LEVEL 2: Aggregate on ProjectFile
    aggregateMaxMetric(projectFile, "my.metric", metricValue);
    
    logger.debug("Aggregated metrics: max={}, count={}",
        projectFile.getDoubleProperty("my.metric.max"),
        projectFile.getIntegerProperty("my.metric.classes_analyzed"));
}
```

## Conclusion

The two-level metrics system is now operational for inheritance depth and interface counting. This provides both detailed class-level analysis and quick file-level summaries, enabling better architectural insights and more useful CSV exports.

The pattern is established and documented for future inspector implementations.
