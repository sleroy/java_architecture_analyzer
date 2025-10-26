# Binary Class Coupling Graph Inspector

## Overview

The `BinaryClassCouplingGraphInspector` is a binary (bytecode) inspector that builds a coupling graph between JavaClassNode instances by creating edges in the GraphRepository.

## Purpose

This inspector analyzes class dependencies at the bytecode level and creates directed edges representing coupling relationships between classes. Unlike `ClassMetricsInspectorV2` which only counts dependencies, this inspector creates actual graph edges that can be traversed and analyzed.

## Edge Types

The inspector creates three types of edges:

| Edge Type | Description | Example |
|-----------|-------------|---------|
| `extends` | Inheritance relationship | `class Dog extends Animal` |
| `implements` | Interface implementation | `class ArrayList implements List` |
| `uses` | General usage dependency | Field types, method parameters, return types |

## Dependencies

### Requires
- `java.class_node.binary` - JavaClassNode instances must exist (created by BinaryJavaClassNodeInspector or BinaryJavaClassNodeInspectorV2)

### Produces
- `java.class.coupling.edges.created` - Number of edges created for each class

## Architecture

```
BinaryClassCouplingGraphInspector
  └── extends AbstractASMClassInspector (class-centric)
      └── uses ASM to analyze bytecode
          └── creates edges in GraphRepository
```

## What It Analyzes

### 1. Superclass Relationships
```java
class MyService extends BaseService {  // creates "extends" edge
    // ...
}
```

### 2. Interface Implementations
```java
class MyService implements Service, Lifecycle {  // creates 2 "implements" edges
    // ...
}
```

### 3. Field Dependencies
```java
class MyService {
    private UserRepository userRepo;  // creates "uses" edge to UserRepository
    // ...
}
```

### 4. Method Signature Dependencies
```java
class MyService {
    public OrderResponse processOrder(OrderRequest req) {  // creates "uses" edges to OrderRequest and OrderResponse
        // ...
    }
}
```

## Filtering

The inspector automatically filters out:
- **java.*** and **javax.*** packages (JDK classes)
- **Self-references** (class depending on itself)
- **Duplicate edges** (same dependency type between same classes)
- **Primitive types** (int, boolean, etc.)

## Usage Example

```java
// The inspector runs automatically as part of the analysis pipeline
// It requires JavaClassNode instances to be created first

// Example: Run analysis with graph inspectors enabled
AnalysisEngine engine = new AnalysisEngine(/* dependencies */);
engine.analyze(projectFiles);

// Query the resulting graph
GraphRepository graphRepo = // ... get from container
Graph<GraphNode, GraphEdge> couplingGraph = graphRepo.buildGraph(
    Set.of("java_class"),  // node types
    Set.of("extends", "implements", "uses")  // edge types
);

// Find all classes that MyService depends on
Optional<JavaClassNode> myService = graphRepo.findClassByFqn("com.example.MyService");
if (myService.isPresent()) {
    Set<GraphEdge> outgoingEdges = couplingGraph.outgoingEdgesOf(myService.get());
    // Analyze dependencies...
}
```

## Output

For each analyzed class, the inspector:
1. Creates edges to all dependency targets found in the graph
2. Logs trace messages for each edge created
3. Sets the `java.class.coupling.edges.created` property with the edge count

## Limitations

- Only creates edges to classes that exist as JavaClassNode instances in the graph
- External dependencies (third-party libraries not in the analyzed codebase) are logged but no edges are created
- Does not analyze method body dependencies (only signatures)

## Integration Points

### Before This Inspector
1. `BinaryJavaClassNodeInspector` or `BinaryJavaClassNodeInspectorV2` - Creates JavaClassNode instances

### After This Inspector
- Graph analysis tools
- Dependency visualization
- Coupling metrics calculation
- Architecture validation rules

## Performance Considerations

- Efficient: Uses Set to track processed dependencies and avoid duplicates
- Minimal overhead: Only processes each unique dependency once per class
- Scalable: Only creates edges between classes in the analyzed codebase

## Future Enhancements

Possible improvements:
1. Add method-body dependency analysis (method calls, field accesses)
2. Support for annotation dependencies
3. Create edges for external dependencies with special marking
4. Add weight/strength properties to edges based on usage frequency
5. Distinguish between different types of "uses" (field, parameter, return type, local variable)

## Related Inspectors

- `BinaryJavaClassNodeInspector` - Creates JavaClassNode instances (legacy, file-centric)
- `BinaryJavaClassNodeInspectorV2` - Creates/validates JavaClassNode instances (class-centric)
- `ClassMetricsInspectorV2` - Calculates efferent coupling count (but doesn't create edges)
- `JavaImportGraphInspector` - Creates import edges at file level (not class level)
