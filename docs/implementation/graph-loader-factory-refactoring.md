# Graph Loader Factory Pattern Refactoring

## Overview

Refactored `GraphDatabaseLoader.convertAndAddNodes()` to support all GraphNode types (ProjectFile, JavaClassNode, Package, etc.) using the Factory Pattern integrated into NodeTypeRegistry.

## Problem

The original `convertAndAddNodes()` method was hardcoded to only create `ProjectFile` instances:
- Could not load `JavaClassNode` or other GraphNode types from database
- Contained ProjectFile-specific logic (JAR paths, filesystem handling)
- Violated Open/Closed Principle - not extensible for new node types
- Required modification for each new node type added to the system

## Solution

Implemented a Factory Pattern merged into the existing `NodeTypeRegistry`:

### 1. Created NodeFactory Interface
```java
public interface NodeFactory {
    GraphNode createFromEntity(
        GraphNodeEntity entity,
        JsonSerializationService jsonSerializer,
        Path projectRoot) throws Exception;
}
```

### 2. Implemented Type-Specific Factories

- **ProjectFileFactory**: Handles ProjectFile creation with JAR paths and filesystem logic
- **JavaClassNodeFactory**: Simple creation from fully qualified name
- **GenericNodeFactory**: Fallback that throws exception for unregistered types

### 3. Enhanced NodeTypeRegistry

Extended the existing registry to include factory registration:
```java
// Register all known node types with their factories
static {
    register(ProjectFile.class, "file", new ProjectFileFactory());
    register(JavaClassNode.class, "java_class", new JavaClassNodeFactory());
}

public static GraphNode createFromEntity(
    GraphNodeEntity entity,
    JsonSerializationService jsonSerializer,
    Path projectRoot) throws Exception {
    
    NodeFactory factory = FACTORY_MAP.get(entity.getNodeType());
    if (factory == null) {
        factory = new GenericNodeFactory();
    }
    return factory.createFromEntity(entity, jsonSerializer, projectRoot);
}
```

### 4. Simplified GraphDatabaseLoader

The `convertAndAddNodes()` method is now simple and generic:
```java
private static Map<String, GraphNode> convertAndAddNodes(
        List<GraphNodeEntity> nodeEntities,
        GraphRepository targetRepo,
        JsonSerializationService jsonSerializer,
        Path projectRoot) {

    Map<String, GraphNode> nodeMap = new HashMap<>();

    for (GraphNodeEntity entity : nodeEntities) {
        try {
            // Use NodeTypeRegistry factory to create the appropriate node type
            GraphNode node = NodeTypeRegistry.createFromEntity(
                    entity, jsonSerializer, projectRoot);

            targetRepo.addNode(node);
            nodeMap.put(entity.getId(), node);
        } catch (Exception e) {
            logger.warn("Failed to load node {}: {}", entity.getId(), e.getMessage(), e);
        }
    }

    return nodeMap;
}
```

## Benefits

✅ **Extensible**: New node types only need a factory implementation and registration  
✅ **Maintainable**: ProjectFile-specific logic isolated in ProjectFileFactory  
✅ **Type-safe**: Each factory knows its node type's requirements  
✅ **Clean**: Separates concerns - loader orchestrates, factories construct  
✅ **Centralized**: Single location (NodeTypeRegistry) for all node type configuration  
✅ **Open/Closed**: Open for extension (add new factories), closed for modification  

## Files Created

1. `analyzer-core/src/main/java/com/analyzer/core/db/loader/NodeFactory.java`
2. `analyzer-core/src/main/java/com/analyzer/core/db/loader/ProjectFileFactory.java`
3. `analyzer-core/src/main/java/com/analyzer/core/db/loader/JavaClassNodeFactory.java`
4. `analyzer-core/src/main/java/com/analyzer/core/db/loader/GenericNodeFactory.java`

## Files Modified

1. `analyzer-core/src/main/java/com/analyzer/core/graph/NodeTypeRegistry.java` - Added factory registration and creation
2. `analyzer-core/src/main/java/com/analyzer/core/db/loader/GraphDatabaseLoader.java` - Simplified convertAndAddNodes()

## Adding New Node Types

To add support for a new GraphNode type:

1. Create a factory implementing `NodeFactory`
2. Register it in `NodeTypeRegistry`:
   ```java
   register(PackageNode.class, "package", new PackageNodeFactory());
   ```

That's it! No changes needed to GraphDatabaseLoader.

## Testing

- All 81 existing tests pass
- Compilation successful
- No breaking changes to existing functionality

## Date

October 29, 2025
