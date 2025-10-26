# JSON Serialization Unification

## Overview

This document describes the unified JSON serialization approach implemented for all graph nodes in the Java Architecture Analyzer project.

## Problem Statement

Previously, the project had **two separate serialization implementations**:

### 1. ProjectFile Serialization (File-based)
- **Location**: `Project.java`, `ProjectDeserializer.java`
- **Method**: Jackson annotations (`@JsonCreator`, `@JsonProperty`)
- **Issues**:
  - Tight coupling between domain objects and serialization logic
  - Custom deserialization logic in `ProjectDeserializer`
  - Different ObjectMapper configurations

### 2. GraphNode Database Serialization
- **Location**: `GraphRepository.java`, `GraphNodeEntity.java`
- **Method**: Manual `ObjectMapper.writeValueAsString()` calls
- **Issues**:
  - Inline ObjectMapper creation
  - Duplicate serialization code
  - No shared configuration

## Solution: Centralized JsonSerializationService

Created a single, centralized service that handles ALL JSON serialization in the project.

### Architecture

```
JsonSerializationService
├── Single ObjectMapper instance (configured once)
├── Properties Serialization (Map ↔ JSON string)
├── Node Serialization (GraphNode ↔ JSON string)
└── Utility Methods (type conversion, pretty-print)
```

### Key Components

#### 1. JsonSerializationService
**Location**: `src/main/java/com/analyzer/core/serialization/JsonSerializationService.java`

**Responsibilities**:
- Maintain single, consistently configured ObjectMapper
- Serialize/deserialize properties maps (for database storage)
- Serialize/deserialize graph nodes (for file export)
- Provide utility methods for type conversion

**Configuration**:
```java
private static ObjectMapper createConfiguredMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.findAndRegisterModules();
    return mapper;
}
```

#### 2. Refactored Components

**GraphRepository**:
```java
private final JsonSerializationService jsonSerializer;

public GraphRepository(GraphDatabaseConfig config) {
    this(config, new JsonSerializationService());
}

// Properties serialization
String propertiesJson = jsonSerializer.serializeProperties(properties);

// Properties deserialization
return jsonSerializer.deserializeProperties(node.getProperties());
```

**ProjectDeserializer**:
```java
private final JsonSerializationService jsonSerializer;

public ProjectDeserializer() {
    this(new JsonSerializationService());
}

// Use service for type conversion
Object tagValue = jsonSerializer.convertValue(tagValueNode, Object.class);
GraphNode node = jsonSerializer.convertValue(nodeJson, GraphNode.class);
```

**ProjectFile**:
- Removed `@JsonCreator` constructor
- Removed `@JsonProperty` annotations
- Kept only `@JsonTypeInfo` from GraphNode interface (for polymorphism)
- Simplified to standard constructors

## Serialization Strategy

### Properties Serialization

**Use Case**: Storing properties in database or configuration files

**Example**:
```java
Map<String, Object> properties = new HashMap<>();
properties.put("language", "java");
properties.put("complexity", 42);

String json = jsonSerializer.serializeProperties(properties);
// Result: {"language":"java","complexity":42}

Map<String, Object> restored = jsonSerializer.deserializeProperties(json);
```

### Node Serialization

**Use Case**: Full graph node serialization for JSON export

**Example**:
```java
ProjectFile file = new ProjectFile(path, projectRoot);
file.setProperty("type", "java");

String json = jsonSerializer.serializeNode(file);
// Result includes @type field for polymorphic deserialization

GraphNode restored = jsonSerializer.deserializeNode(json);
// Automatically deserializes to correct type (ProjectFile)
```

### Polymorphic Type Handling

The `GraphNode` interface uses Jackson's `@JsonTypeInfo` for automatic type discrimination:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProjectFile.class, name = "ProjectFile"),
    @JsonSubTypes.Type(value = JavaClassNode.class, name = "JavaClassNode"),
    @JsonSubTypes.Type(value = Package.class, name = "Package")
})
public interface GraphNode {
    // ...
}
```

This allows:
- Serialization includes type information: `"@type": "ProjectFile"`
- Deserialization automatically detects concrete type
- No manual type checking needed

## Benefits

### 1. **Single Source of Truth**
- One ObjectMapper configuration for entire project
- Consistent behavior across all serialization points
- Easier to maintain and update

### 2. **Cleaner Domain Objects**
- No serialization annotations cluttering domain classes
- Separation of concerns (domain logic vs serialization)
- Easier to test and understand

### 3. **Performance**
- Single ObjectMapper instance (thread-safe, expensive to create)
- No duplicate configuration overhead
- Shared module registration

### 4. **Maintainability**
- Changes in one place
- Centralized error handling
- Consistent logging

### 5. **Testability**
- Easy to mock/stub for testing
- Comprehensive test coverage in one place
- Constructor injection supports dependency injection

## Usage Examples

### Database Operations (via GraphRepository)

```java
// Save node with properties
GraphRepository repo = new GraphRepository(config);
Map<String, Object> props = Map.of("key", "value");
repo.saveNode("nodeId", "file", "Label", props, tags);

// Load properties
Map<String, Object> props = repo.getNodeProperties("nodeId");
```

### File Export (via ProjectDeserializer)

```java
// Load project from JSON
ProjectDeserializer deserializer = new ProjectDeserializer();
Project project = deserializer.loadProject(jsonPath, graphRepo, fileRepo);

// Service internally uses JsonSerializationService
```

### Direct Node Serialization

```java
JsonSerializationService service = new JsonSerializationService();

// Serialize any GraphNode
ProjectFile file = new ProjectFile(path, root);
String json = service.serializeNode(file);

// Deserialize with type detection
GraphNode node = service.deserializeNode(json);
```

## Testing

Comprehensive test coverage in `JsonSerializationServiceTest`:
- Properties serialization/deserialization
- Node serialization/deserialization
- Polymorphic type detection
- Utility methods (convert, pretty-print)
- Round-trip consistency
- Error handling

Run tests:
```bash
mvn test -Dtest=JsonSerializationServiceTest
```

## Migration Notes

### Before
```java
// Inline ObjectMapper creation
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());
String json = mapper.writeValueAsString(properties);
```

### After
```java
// Use centralized service
JsonSerializationService service = new JsonSerializationService();
String json = service.serializeProperties(properties);
```

## Future Enhancements

1. **Caching**: Add caching for frequently serialized/deserialized objects
2. **Compression**: Support for compressed JSON storage
3. **Validation**: Schema validation before serialization
4. **Versioning**: Support for multiple JSON schema versions
5. **Custom Serializers**: Register custom serializers for specific types

## Related Files

- `src/main/java/com/analyzer/core/serialization/JsonSerializationService.java`
- `src/main/java/com/analyzer/core/db/repository/GraphRepository.java`
- `src/main/java/com/analyzer/core/model/ProjectDeserializer.java`
- `src/main/java/com/analyzer/core/model/ProjectFile.java`
- `src/main/java/com/analyzer/core/graph/GraphNode.java`
- `src/test/java/com/analyzer/core/serialization/JsonSerializationServiceTest.java`

## See Also

- [JSON Properties Refactoring](json-properties-refactoring.md)
- [H2 MyBatis Implementation](h2-mybatis-implementation.md)
- [Metrics Integration](metrics-integration-summary.md)
