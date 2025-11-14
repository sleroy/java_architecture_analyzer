# Binary Class Coupling Graph Inspector

## Overview

The `BinaryClassCouplingGraphInspector` is a binary (bytecode) inspector that builds a coupling graph between JavaClassNode instances by creating edges in the GraphRepository.

## Purpose

This inspector analyzes class dependencies at the bytecode level and creates directed edges representing coupling relationships between classes. Unlike `ClassMetricsInspectorV2` which only counts dependencies, this inspector creates actual graph edges that can be traversed and analyzed.

## Edge Types

The inspector creates **`uses`** edges for all type dependencies. The specific relationship kind is stored as an edge property:

| Relationship Kind (Edge Property) | Description | Example |
|----------------------------------|-------------|---------|
| `extends` | Inheritance relationship | `class Dog extends Animal` |
| `implements` | Interface implementation | `class ArrayList implements List` |
| *(default - no property)* | Direct type usage | Field types, method parameters, return types |
| `annotated_with` | Annotation usage | `@Service`, `@Autowired`, `@Column` |
| `type_parameter` | Generic type parameter | `List<String>` - the String is a type parameter |
| `throws` | Exception in throws clause | `void process() throws IOException` |
| `type_variable` | Type variable reference | `<T extends Comparable>` - references Comparable |

**Edge Properties:**

All property names are defined as constants in `BinaryClassCouplingGraphInspector`:

- `PROP_RELATIONSHIP_KIND` (`relationshipKind`): The specific type of relationship (extends, implements, etc.)
- `PROP_CONTAINER_TYPE` (`containerType`): For type parameters, the generic container class (e.g., "java.util.List")
- `PROP_TYPE_ARGUMENT_INDEX` (`typeArgumentIndex`): For type parameters, the position in the type argument list (0-based)
- `PROP_WILDCARD_KIND` (`wildcardKind`): For wildcard bounds, either "extends" or "super"

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

### 5. Exception Dependencies
```java
class MyService {
    public void processData() throws IOException, SQLException {  // creates "throws" edges to IOException and SQLException
        // ...
    }
}
```

### 6. Annotation Dependencies
```java
@Service  // creates "annotated_with" edge to org.springframework.stereotype.Service
class MyService {
    @Autowired  // creates "annotated_with" edge to org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepo;
    
    @Transactional  // creates "annotated_with" edge to org.springframework.transaction.annotation.Transactional
    public void saveUser(User user) { }
}
```

### 7. Generic Type Parameter Dependencies
```java
class MyService {
    // List<User> involves TWO types: List and User
    // Creates TWO edges:
    private List<User> users;  
    // 1. "uses" edge to java.util.List (from descriptor)
    // 2. "type_parameter" edge to User (from signature)
    
    // Map<String, Order> involves THREE types: Map, String, and Order
    // Creates THREE edges:
    private Map<String, Order> orders;
    // 1. "uses" edge to java.util.Map (from descriptor)
    // 2. "type_parameter" edge to String (from signature)
    // 3. "type_parameter" edge to Order (from signature)
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
- External dependencies (third-party libraries not in the analyzed codebase) are automatically created as nodes
- Does not analyze method body dependencies (only signatures)

### Generic Type Handling - Implemented Solution

The inspector now uses a **unified type parser** that preserves structural relationships through edge properties:

**Implementation**:
```java
// Example field with generics
private SingularAttribute<City, Province> attribute;

// Parsed using TypeParser.parseType(descriptor, signature)
// - Unified parsing of both descriptor and signature
// - Complete type structure including generics

// Edges created (3 total, all "uses" edges):
// 1. "uses" edge to SingularAttribute
//    - No special properties (direct usage)
//
// 2. "uses" edge to City
//    - PROP_RELATIONSHIP_KIND: "type_parameter"
//    - PROP_CONTAINER_TYPE: "javax.persistence.metamodel.SingularAttribute"
//    - PROP_TYPE_ARGUMENT_INDEX: 0
//
// 3. "uses" edge to Province
//    - PROP_RELATIONSHIP_KIND: "type_parameter"
//    - PROP_CONTAINER_TYPE: "javax.persistence.metamodel.SingularAttribute"
//    - PROP_TYPE_ARGUMENT_INDEX: 1
```

**What Works**:
- ✅ All types involved in generic declarations are captured (container + type arguments)
- ✅ Each type gets its own "uses" edge in the graph
- ✅ Edge properties preserve the structural relationships (containerType, typeArgumentIndex)
- ✅ Nested generics are handled recursively with proper container tracking
- ✅ Wildcard bounds are captured (`? extends Number`, `? super T`)
- ✅ Type variable references are captured with special relationshipKind
- ✅ Array component types are handled recursively
- ✅ Unified parser eliminates descriptor/signature parsing inconsistencies

**Complete Example - Nested Generics**:
```java
private Map<String, List<User>> usersByName;

// Edges created (4 total):
// 1. uses -> Map (direct usage)
// 2. uses -> String (PROP_RELATIONSHIP_KIND: type_parameter, PROP_CONTAINER_TYPE: Map, PROP_TYPE_ARGUMENT_INDEX: 0)
// 3. uses -> List (PROP_RELATIONSHIP_KIND: type_parameter, PROP_CONTAINER_TYPE: Map, PROP_TYPE_ARGUMENT_INDEX: 1)
// 4. uses -> User (PROP_RELATIONSHIP_KIND: type_parameter, PROP_CONTAINER_TYPE: List, PROP_TYPE_ARGUMENT_INDEX: 0)
```

**Graph Queries**:
With edge properties, you can now:
- Find all type parameters of a specific container: Query edges where `PROP_CONTAINER_TYPE = "java.util.List"`
- Reconstruct generic structure: Follow edges and reconstruct `List<String>` from edges
- Identify wildcard usage: Query edges where `PROP_WILDCARD_KIND` property exists
- Find nested generic relationships: Query chains of type_parameter edges
- Query by relationship kind: Filter edges by `PROP_RELATIONSHIP_KIND` value

**Technical Implementation**:
- `TypeInfo` class: Represents complete type structure (class, generics, arrays, wildcards)
- `TypeParser` class: Unified parser using ASM SignatureVisitor
- Recursive processing: `processTypeInfo()` and `processTypeArgument()` methods
- Edge properties: All metadata stored as GraphEdge properties

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
