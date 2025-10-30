# Property Storage Refactoring

## Overview
Refactored `ProjectFile` and verified `JavaClassNode` to use the property storage mechanism provided by `BaseGraphNode` instead of declaring fields directly.

## Date
October 29, 2025

## Objective
Consolidate data storage in graph nodes to use the unified property storage system from `BaseGraphNode`, improving consistency and maintainability across the codebase.

## Changes Made

### 1. ProjectFile Refactoring

#### Fields Moved to Properties
The following fields were converted from Java fields to properties stored in `BaseGraphNode`'s property map:

- `filePath` (Path → String) - Stored as string, converted to Path in getter
- `relativePath` (String)
- `fileName` (String)
- `fileExtension` (String)
- `discoveredAt` (Date)
- `sourceJarPath` (String, nullable)
- `jarEntryPath` (String, nullable)
- `isVirtual` (boolean)

#### Property Constants Added
```java
public static final String PROP_FILE_PATH = "filePath";
public static final String PROP_RELATIVE_PATH = "relativePath";
public static final String PROP_FILE_NAME = "fileName";
public static final String PROP_FILE_EXTENSION = "fileExtension";
public static final String PROP_DISCOVERED_AT = "discoveredAt";
public static final String PROP_SOURCE_JAR_PATH = "sourceJarPath";
public static final String PROP_JAR_ENTRY_PATH = "jarEntryPath";
public static final String PROP_IS_VIRTUAL = "isVirtual";
```

#### Fields Kept as Java Fields
These @JsonIgnore transient runtime state fields remain as Java fields (NOT stored in properties):
- `inspectorExecutionTimes` - Runtime cache for convergence detection
- `cachedFileModificationTime` - Computed cache for file modification tracking

**Rationale**: These are transient runtime state that should not be persisted. Storing them in the property map would unnecessarily serialize them and complicate the design.

#### Updated Methods
- All getter methods now retrieve from property storage
- `getFilePath()` converts stored String back to Path
- `getFileModificationTime()` updated to use `getFilePath()` instead of direct field access
- `getDisplayLabel()` and `toString()` updated to use getters
- Both constructors updated to store values in properties

### 2. JavaClassNode Verification

**Status**: ✅ Already correctly implemented

JavaClassNode was already using the property storage pattern correctly:
- All domain data stored as properties (using property constants)
- Only transient fields are Java fields: `inspectorExecutionTimes` and `lastModified`
- No refactoring needed

### 3. Package Class

**Status**: Identified but not refactored in this change

The `Package` class extends `BaseGraphNode` and has fields that could potentially be refactored:
- `packageName` 
- `sourceLocation`
- `classes` (List<ProjectFile>) - relationship data
- `inspectorResults` (Map) - inspector-specific data

**Recommendation**: Consider refactoring in a future change if consistency across all BaseGraphNode subclasses is desired.

## Design Decisions

### 1. Path Storage Strategy
Store file paths as String in properties and convert to/from Path in getters/setters. This simplifies serialization and works well with the property storage system.

### 2. Transient State Handling
Keep @JsonIgnore runtime state fields as Java fields rather than properties because:
- They shouldn't be serialized
- They're computed/cached values, not domain data
- Using properties would add unnecessary complexity

### 3. Backward Compatibility
Maintained exact same public API (getters/setters) so existing code using ProjectFile continues to work without changes.

### 4. Dual Property Storage
For backward compatibility, file metadata is stored in both:
- The new property constants (PROP_FILE_NAME, etc.)
- The legacy InspectorTags constants (TAG_FILE_NAME, etc.)

This ensures any code using either approach continues to work.

## Testing

### Build Status
✅ All modules compile successfully

### Test Results
- **analyzer-core**: 81 tests, 0 failures
- **analyzer-inspectors**: All tests pass
- **analyzer-ejb2spring**: No tests
- **analyzer-app**: 6 tests, 0 failures

**Total**: All tests pass successfully

### Impact Analysis
Found 67 usages of `getFilePath()`, `getRelativePath()`, `getFileName()`, `getFileExtension()`, and `getDiscoveredAt()` across the codebase. All continue to work correctly because we maintained the same getter API.

## Benefits

1. **Consistency**: ProjectFile now follows the same pattern as other graph nodes
2. **Flexibility**: Properties can be dynamically added/queried without code changes
3. **Serialization**: Unified property storage simplifies JSON serialization
4. **Maintainability**: Clearer separation between persistent data and transient state
5. **Extensibility**: Easy to add new properties without changing class structure

## Files Modified

1. `analyzer-core/src/main/java/com/analyzer/core/model/ProjectFile.java`
   - Added property constants
   - Converted fields to property storage
   - Updated all constructors and methods
   - Kept transient state as Java fields

## Migration Notes

### For Developers

If you're creating new graph node classes that extend `BaseGraphNode`:

1. **Store domain data as properties**, not fields
2. **Define property constants** for type-safe access
3. **Keep transient runtime state as @JsonIgnore Java fields**
4. **Use getters/setters** that delegate to property storage
5. **Update JSON constructors** to restore properties correctly

### Example Pattern

```java
public class MyNode extends BaseGraphNode {
    // Property constants
    public static final String PROP_MY_VALUE = "myValue";
    
    // Transient state only
    @JsonIgnore
    private final Map<String, Object> cache = new HashMap<>();
    
    public MyNode(String id) {
        super(id, "myType");
    }
    
    public String getMyValue() {
        return getStringProperty(PROP_MY_VALUE, "default");
    }
    
    public void setMyValue(String value) {
        setProperty(PROP_MY_VALUE, value);
    }
}
```

## Future Considerations

1. Consider refactoring `Package` class for consistency
2. Evaluate if other classes extending `BaseGraphNode` need similar refactoring
3. Document the property storage pattern in architecture documentation
4. Consider deprecating direct field access patterns in favor of properties

## Related Documentation

- `analyzer-core/src/main/java/com/analyzer/api/graph/BaseGraphNode.java` - Base property storage implementation
- `analyzer-core/src/main/java/com/analyzer/api/graph/GraphNode.java` - Graph node interface
