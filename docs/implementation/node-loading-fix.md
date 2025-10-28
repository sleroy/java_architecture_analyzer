# Node Loading Fix - Zero Nodes Loaded Issue

## Issue Description

When loading nodes from the H2 database, the system reported:
```
Existing database contains: 97 nodes, 0 edges, 233 tags
Loading nodes from database...
Loaded 0 nodes from database
```

Despite having 97 nodes in the database, zero nodes were being loaded into memory.

## Root Cause

The issue was in both `InventoryCommand.java` and `JsonExportCommand.java`. The code used a hardcoded list of node types to query the database:

```java
// OLD CODE - HARDCODED NODE TYPES
for (String type : Arrays.asList("java", "xml", "properties", "yaml", "json", "file")) {
    nodeEntities.addAll(dbRepo.findNodesByType(type));
}
```

The actual node types in the database didn't match any of these hardcoded values, resulting in zero nodes being loaded.

## Solution

### 1. Added `findAll()` Method to GraphRepository

Added a new method to `GraphRepository.java` to retrieve all nodes regardless of type:

```java
/**
 * Find all nodes in the database.
 *
 * @return List of all nodes
 */
public List<GraphNodeEntity> findAll() {
    try (SqlSession session = config.openSession()) {
        NodeMapper mapper = session.getMapper(NodeMapper.class);
        return mapper.findAll();
    }
}
```

### 2. Updated InventoryCommand

Modified `loadDataIntoEngine()` method in `InventoryCommand.java`:

```java
// NEW CODE - LOAD ALL NODES
List<GraphNodeEntity> nodeEntities = dbRepo.findAll();
```

### 3. Updated JsonExportCommand

Modified `loadDataFromDatabase()` method in `JsonExportCommand.java`:

```java
// NEW CODE - LOAD ALL NODES (when no filter specified)
if (nodeTypeFilters != null && !nodeTypeFilters.isEmpty()) {
    // Filter by specific types
    nodeEntities = new ArrayList<>();
    for (String nodeType : nodeTypeFilters) {
        nodeEntities.addAll(dbRepo.findNodesByType(nodeType));
    }
} else {
    // Load all nodes from database
    nodeEntities = dbRepo.findAll();
}
```

## Files Modified

1. `analyzer-core/src/main/java/com/analyzer/core/db/repository/GraphRepository.java`
   - Added `findAll()` method

2. `analyzer-app/src/main/java/com/analyzer/cli/InventoryCommand.java`
   - Updated `loadDataIntoEngine()` to use `findAll()`

3. `analyzer-app/src/main/java/com/analyzer/cli/JsonExportCommand.java`
   - Updated `loadDataFromDatabase()` to use `findAll()` when no filters specified

## Benefits

1. **Reliability**: All nodes are now loaded regardless of their type
2. **Maintainability**: No need to maintain a hardcoded list of node types
3. **Future-proof**: New node types are automatically supported
4. **Performance**: Single query instead of multiple queries for different types
5. **Consistency**: Both incremental analysis and JSON export now work correctly

## Testing

To verify the fix works:

```bash
# Run inventory command (should now load all 97 nodes)
mvn exec:java -Dexec.mainClass="com.analyzer.cli.Main" \
  -Dexec.args="inventory --project /path/to/project"

# Export to JSON (should now export all nodes)
mvn exec:java -Dexec.mainClass="com.analyzer.cli.Main" \
  -Dexec.args="json_export --project /path/to/project"
```

Expected output should show all nodes being loaded:
```
Loaded 97 nodes from database
```

## Impact

- ✅ Incremental analysis now properly loads existing nodes
- ✅ JSON export now exports all nodes
- ✅ No breaking changes to existing functionality
- ✅ Node type filtering still works when explicitly requested
