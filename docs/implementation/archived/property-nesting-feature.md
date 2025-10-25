# Property Nesting Feature

## Overview

The Property Nesting feature automatically transforms flat property keys with dots (e.g., `file.type`, `ejb.version`) into nested JSON structures when serializing project analysis data. This makes the JSON output more organized, hierarchical, and easier to navigate.

## Problem Solved

Previously, when inspectors set properties on graph nodes using dotted keys, the JSON output would show them as flat key-value pairs:

```json
{
  "id": "node-123",
  "type": "java",
  "properties": {
    "file.type": "java",
    "file.size": 1024,
    "ejb.type": "stateless",
    "ejb.version": "3.0",
    "metrics.complexity": 12,
    "metrics.lines": 145
  }
}
```

This structure is flat and doesn't leverage the semantic grouping implied by the dot notation.

## Solution

With the new `PropertyNestingTransformer`, dotted property keys are automatically converted into nested structures:

```json
{
  "id": "node-123",
  "type": "java",
  "properties": {
    "file": {
      "type": "java",
      "size": 1024
    },
    "ejb": {
      "type": "stateless",
      "version": "3.0"
    },
    "metrics": {
      "complexity": 12,
      "lines": 145
    }
  }
}
```

## Implementation

### Core Component

**Class:** `PropertyNestingTransformer`  
**Location:** `src/main/java/com/analyzer/core/export/PropertyNestingTransformer.java`

**Key Methods:**
- `nestProperties(Map<String, Object>)` - Transforms flat map to nested structure
- `flattenProperties(Map<String, Object>)` - Inverse operation (nested to flat)

### Integration Point

**Class:** `ProjectSerializer`  
**Method:** `serializeNode(ProjectFile)`

The transformation is applied automatically during JSON serialization:

```java
// Add properties - transform dotted keys into nested structures
Map<String, Object> properties = file.getNodeProperties();
if (properties != null && !properties.isEmpty()) {
    Map<String, Object> nestedProperties = PropertyNestingTransformer.nestProperties(properties);
    nodeData.put("properties", nestedProperties);
}
```

## Transformation Rules

### Rule 1: Flat Keys Remain Flat
Keys without dots remain at the root level:
```
Input:  { "name": "Test", "version": 1 }
Output: { "name": "Test", "version": 1 }
```

### Rule 2: Single-Level Nesting
Keys with one dot create one level of nesting:
```
Input:  { "file.type": "java", "file.size": 1024 }
Output: { "file": { "type": "java", "size": 1024 } }
```

### Rule 3: Multi-Level Nesting
Keys with multiple dots create deep nesting:
```
Input:  { "ejb.metadata.type": "stateless" }
Output: { "ejb": { "metadata": { "type": "stateless" } } }
```

### Rule 4: Mixed Keys
Flat and dotted keys coexist naturally:
```
Input:  { "name": "Test", "file.type": "java", "complexity": 5 }
Output: { "name": "Test", "complexity": 5, "file": { "type": "java" } }
```

### Rule 5: Null Values Preserved
Null values are maintained in the nested structure:
```
Input:  { "file.type": "java", "file.metadata": null }
Output: { "file": { "type": "java", "metadata": null } }
```

## Usage Guidelines for Inspector Developers

### Recommended Property Naming Convention

Use dotted notation to organize related properties:

```java
// EJB-related properties
node.setProperty("ejb.type", "stateless");
node.setProperty("ejb.version", "3.0");
node.setProperty("ejb.transactionType", "CONTAINER");

// Metrics
node.setProperty("metrics.complexity", 12);
node.setProperty("metrics.lines", 145);
node.setProperty("metrics.methods", 8);

// Spring conversion info
node.setProperty("spring.conversionTarget", "SERVICE");
node.setProperty("spring.estimatedEffort", "LOW");
```

### Grouping Strategy

Organize properties into logical groups:

1. **Technology/Framework**: `ejb.*`, `spring.*`, `hibernate.*`
2. **Code Metrics**: `metrics.*`, `quality.*`
3. **File Info**: `file.*`, `source.*`
4. **Analysis Results**: `analysis.*`, `migration.*`

### Benefits

1. **Better Organization**: Related properties are grouped together
2. **Easier Navigation**: JSON structure mirrors logical relationships
3. **Query Simplification**: Tools can query `properties.ejb` to get all EJB-related data
4. **Backward Compatible**: Existing code continues to work; transformation happens during serialization

## Real-World Example

An EJB bean analyzed by multiple inspectors might have these properties set:

```java
// From EjbBinaryClassInspector
node.setProperty("ejb.type", "stateless");
node.setProperty("ejb.version", "3.0");
node.setProperty("ejb.interfaces.local", "HelloLocal");
node.setProperty("ejb.interfaces.remote", "HelloRemote");

// From CyclomaticComplexityInspector
node.setProperty("metrics.complexity", 12);

// From ClocInspector
node.setProperty("metrics.lines", 145);

// From EjbMigrationAnalyzer
node.setProperty("spring.conversionTarget", "SERVICE");
node.setProperty("spring.complexity", "LOW");
```

**Resulting JSON Structure:**

```json
{
  "id": "node-123456",
  "type": "java",
  "fileName": "HelloWorldBean.class",
  "tags": ["EJB", "STATELESS", "JAVA"],
  "properties": {
    "ejb": {
      "type": "stateless",
      "version": "3.0",
      "interfaces": {
        "local": "HelloLocal",
        "remote": "HelloRemote"
      }
    },
    "metrics": {
      "complexity": 12,
      "lines": 145
    },
    "spring": {
      "conversionTarget": "SERVICE",
      "complexity": "LOW"
    }
  }
}
```

## Testing

Comprehensive tests are available in:
- **Location:** `src/test/java/com/analyzer/core/export/PropertyNestingTransformerTest.java`
- **Coverage:** 15 test cases covering all transformation scenarios
- **Test Results:** All tests passing ✅

Key test scenarios:
- Empty and null maps
- Flat keys only
- Single-level nesting
- Multi-level nesting
- Mixed keys
- Deep nesting (5+ levels)
- Null value handling
- Round-trip transformations (nest→flatten→nest)
- Real-world examples

## Performance Considerations

- **O(n)** complexity where n is the number of properties
- Minimal memory overhead (creates new maps only for nested structures)
- No impact on existing code (transformation happens during serialization only)
- Efficient for typical use cases (10-50 properties per node)

## Future Enhancements

Potential improvements for future versions:

1. **Array Index Support**: Handle `items[0].name` notation
2. **Escape Sequences**: Support literal dots in property names
3. **Custom Separators**: Allow configuration of separator character (e.g., use `/` instead of `.`)
4. **Selective Nesting**: Configuration to nest only certain prefixes
5. **Schema Validation**: Validate nested structure against expected schema

## Migration Notes

### For Existing Code

No migration required! The feature is:
- ✅ **Backward Compatible**: Existing property setting code works unchanged
- ✅ **Opt-In**: Only affects JSON serialization output
- ✅ **Non-Breaking**: Internal property storage remains flat (Map<String, Object>)

### For New Inspectors

Recommended approach:
1. Use dotted notation for logically grouped properties
2. Follow consistent naming conventions (see Usage Guidelines above)
3. Document property structure in inspector JavaDoc

## Related Files

- **Implementation:** `src/main/java/com/analyzer/core/export/PropertyNestingTransformer.java`
- **Integration:** `src/main/java/com/analyzer/core/export/ProjectSerializer.java`
- **Tests:** `src/test/java/com/analyzer/core/export/PropertyNestingTransformerTest.java`
- **Documentation:** `docs/implementation/property-nesting-feature.md` (this file)
