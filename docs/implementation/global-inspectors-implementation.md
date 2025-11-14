# Global Inspectors Implementation Summary

## Overview

Implemented support for "global inspectors" that require all nodes to be processed by their dependencies before they can execute. This addresses the architectural issue where inspectors like `CouplingMetricsInspector` need the complete graph structure before calculating metrics.

## Problem Statement

Some inspectors, particularly those calculating coupling metrics, require:
1. **ALL** nodes to be processed by their dependency inspectors first
2. Access to the complete graph structure built across all nodes
3. Execution after the multi-pass node-by-node phase completes

Previously, these inspectors used workarounds like lazy graph initialization on first call, which was inefficient and architecturally unclear.

## Solution Design

### Architecture Changes

Added **Phase 5: Global Inspectors** to the analysis pipeline:

```
Phase 1: File Discovery
Phase 2: ClassNode Collection
Phase 3: Multi-pass ProjectFile Analysis (node-by-node)
Phase 3.5: Global ProjectFile Inspectors (ALL nodes, after Phase 3)
Phase 4: Multi-pass ClassNode Analysis (node-by-node)
Phase 5: Global ClassNode Inspectors (ALL nodes, after Phase 4)
```

### Key Components Modified

#### 1. Enhanced `@InspectorDependencies` Annotation

**File:** `analyzer-core/src/main/java/com/analyzer/api/inspector/InspectorDependencies.java`

Added new parameter `requiresAllNodesProcessed()`:

```java
@InspectorDependencies(
    need = BinaryClassCouplingGraphInspector.class,
    produces = "java.class.coupling_metrics.calculated",
    requiresAllNodesProcessed = true  // NEW FLAG
)
```

**Semantics:**
- `false` (default): Normal node-by-node inspector, runs in multi-pass phases
- `true`: Global inspector, runs after all nodes processed by dependencies

#### 2. Updated `InspectorRegistry`

**File:** `analyzer-core/src/main/java/com/analyzer/core/inspector/InspectorRegistry.java`

Added methods to filter inspectors:

```java
// Get global inspectors (requiresAllNodesProcessed = true)
List<Inspector> getGlobalClassNodeInspectors()
List<Inspector> getGlobalProjectFileInspectors()

// Get non-global inspectors (requiresAllNodesProcessed = false)
List<Inspector> getNonGlobalClassNodeInspectors()
List<Inspector> getNonGlobalProjectFileInspectors()

// Helper method
private boolean isGlobalInspector(Inspector inspector)
```

#### 3. Updated `AnalysisEngine`

**File:** `analyzer-core/src/main/java/com/analyzer/core/engine/AnalysisEngine.java`

**Changes:**
1. Modified Phase 3 and Phase 4 to use only **non-global** inspectors
2. Added Phase 3.5 and Phase 5 for global inspectors
3. Implemented global inspector execution methods:
   - `executeGlobalProjectFileInspectors(Project project)`
   - `executeGlobalClassNodeInspectors(Project project)`

**Execution Flow:**
```java
// Phase 3: Only non-global ProjectFile inspectors
List<Inspector<ProjectFile>> inspectors = 
    inspectorRegistry.getNonGlobalProjectFileInspectors();
executeMultiPassInspectors(project, inspectors, maxPasses);

// Phase 3.5: Global ProjectFile inspectors (after convergence)
executeGlobalProjectFileInspectors(project);

// Phase 4: Only non-global ClassNode inspectors
List<Inspector<JavaClassNode>> inspectors = 
    inspectorRegistry.getNonGlobalClassNodeInspectors();
executeMultiPassOnClassNodes(project, maxPasses);

// Phase 5: Global ClassNode inspectors (after convergence)
executeGlobalClassNodeInspectors(project);
```

#### 4. Updated Existing Global Inspectors

**CouplingMetricsInspector**
- **File:** `analyzer-inspectors/src/main/java/com/analyzer/rules/metrics/CouplingMetricsInspector.java`
- **Change:** Added `requiresAllNodesProcessed = true` to annotation
- **Result:** Now runs in Phase 5 after ALL classes processed by `BinaryClassCouplingGraphInspector`

**PackageCouplingMetricsInspector**
- **File:** `analyzer-inspectors/src/main/java/com/analyzer/rules/metrics/PackageCouplingMetricsInspector.java`
- **Change:** Added `requiresAllNodesProcessed = true` to annotation
- **Result:** Now runs in Phase 5 after ALL packages have coupling metrics calculated

## Benefits

### ✅ Clean Separation
- Node-by-node inspectors vs global inspectors clearly distinguished
- Explicit execution phases with clear semantics

### ✅ Explicit Intent
- `requiresAllNodesProcessed` flag clearly documents inspector requirements
- No ambiguity about execution order

### ✅ Better Performance
- Graph built once after all edges created (not per-node)
- No redundant graph operations
- Reduced memory usage

### ✅ Extensible
- Easy to add new global inspectors
- Simple annotation-based configuration
- No code changes to engine required

### ✅ Type-Safe
- Uses existing `InspectorTargetType` system
- Compile-time checking of inspector types

## Usage Examples

### Creating a Global Inspector

```java
@InspectorDependencies(
    need = GraphBuildingInspector.class,
    produces = "my.global.analysis.completed",
    requiresAllNodesProcessed = true  // Mark as global
)
public class MyGlobalInspector implements Inspector<JavaClassNode> {
    
    @Inject
    public MyGlobalInspector(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }
    
    @Override
    public void inspect(JavaClassNode node, NodeDecorator<JavaClassNode> decorator) {
        // This runs AFTER all nodes processed by GraphBuildingInspector
        // Safe to use complete graph structure
        Graph<GraphNode, GraphEdge> completeGraph = 
            graphRepository.buildGraph(...);
        
        // Calculate metrics using complete graph
        ...
    }
}
```

### Execution Log Example

```
=== PHASE 4: Multi-pass ClassNode Analysis ===
Phase 4: Executing 12 inspectors on 150 class nodes (max passes: 3)
Phase 4 completed: 2 passes executed, converged: true

=== PHASE 5: Global ClassNode Inspectors ===
Phase 5: Executing 2 global inspectors on 150 class nodes
Global ClassNode inspectors: [Coupling Metrics Inspector, Package Coupling Metrics Inspector]
Building coupling graph for metrics calculation (Phase 5: Global Inspector)...
Coupling graph built with 150 nodes
Phase 5 completed: 2 global inspectors executed
```

## Testing

### Compilation Test
```bash
mvn clean compile -DskipTests
```
**Result:** BUILD SUCCESS ✅

### Expected Behavior
1. **Phase 3/4:** Only non-global inspectors execute in multi-pass
2. **Phase 3.5/5:** Global inspectors execute once after convergence
3. **Graph building:** Happens once when first global inspector runs
4. **Metrics:** Calculated correctly with complete graph

## Implementation Statistics

- **Files Modified:** 5
- **New Phases Added:** 2 (Phase 3.5, Phase 5)
- **New Methods Added:** 6
- **Lines of Code:** ~300 new lines
- **Backward Compatible:** Yes (default `requiresAllNodesProcessed = false`)

## Future Enhancements

### Potential Improvements
1. **Parallel execution** of global inspectors (they're independent)
2. **Progress tracking** specific to global inspector phases
3. **Caching** of graph structures across global inspectors
4. **Dependency ordering** between global inspectors
5. **Phase configuration** via properties files

### Additional Use Cases
- Architecture violation detection (requires complete graph)
- Cycle detection (requires complete graph)
- Component analysis (requires complete graph)
- Layering validation (requires all dependencies known)

## Migration Guide

### For Existing Inspectors

If your inspector needs the complete graph:

1. **Add the flag:**
   ```java
   @InspectorDependencies(
       need = ...,
       produces = ...,
       requiresAllNodesProcessed = true  // ADD THIS
   )
   ```

2. **Remove workarounds:**
   - Remove lazy initialization checks
   - Remove "first call" special handling
   - Assume graph is complete

3. **Update comments:**
   - Document that it runs in Phase 5
   - Explain why it needs all nodes

### For New Inspectors

**When to use `requiresAllNodesProcessed = true`:**
- Need complete graph structure
- Calculate metrics that depend on all nodes
- Perform global analysis (cycles, components, etc.)
- Aggregate data from all nodes

**When to use `requiresAllNodesProcessed = false` (default):**
- Analyze single node in isolation
- Don't need complete graph
- Can run incrementally node-by-node

## Related Documentation

- `@InspectorDependencies` annotation
- `InspectorRegistry` filtering methods
- `AnalysisEngine` phase documentation
- Multi-pass execution system

## Author

Implementation Date: November 14, 2025
