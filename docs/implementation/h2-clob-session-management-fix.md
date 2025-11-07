# H2 CLOB Session Management Fix

**Date:** November 7, 2025  
**Issue:** H2 database CLOB lazy-loading causing "Connection is broken: session closed" errors  
**Solution:** Single session management for snapshot operations

## Problem Description

### Original Error
```
org.h2.jdbc.JdbcSQLNonTransientException: IO Exception: 
"java.io.IOException: org.h2.message.DbException: Connection is broken: 
""session closed"" [90067-224]"; "SPACE(1034 /* table: -3 id: 321609 */)" [90031-224]
```

The error occurred when loading graph data from H2 database in the `H2GraphDatabase.snapshot()` method. The application would crash during the migration plan loading phase when trying to read node properties, metrics, and tags from CLOB columns.

### Root Cause

H2 stores CLOB (Character Large Object) data separately from regular columns and streams it on demand. The original implementation had a critical architectural flaw:

1. **Repository methods used short-lived sessions** - Each `findAll()`, `findByType()`, etc. call opened and immediately closed a SqlSession
2. **MyBatis returns lazy CLOB references** - The actual CLOB data isn't read until you access the field
3. **Session closes before data is read** - When the repository method returns, the session closes, invalidating all CLOB streams
4. **Accessing CLOB data fails** - When the application later tries to deserialize properties/metrics/tags, the connection is already closed

This is a classic lazy-loading problem compounded by try-with-resources session management.

## Solution Architecture

### Design Decision

Instead of forcing CLOB materialization at the repository level (which would require modifying 9+ methods and mixing concerns), we implemented **session lifecycle management at the orchestration level** where the snapshot operation coordinates multiple queries.

### Key Principle

> **Keep the SqlSession open for the entire snapshot operation, ensuring CLOB data remains accessible throughout the multi-step process.**

## Implementation

### 1. Repository Layer - Session-Managed Methods

Added overloaded methods in `H2GraphStorageRepository` that accept a SqlSession parameter:

```java
// Session-managed versions (called by H2GraphDatabase)
public List<GraphNodeEntity> findAll(final SqlSession session) {
    final NodeMapper mapper = session.getMapper(NodeMapper.class);
    return mapper.findAll();
}

public List<GraphNodeEntity> findNodesByType(final SqlSession session, final String nodeType) {
    final NodeMapper mapper = session.getMapper(NodeMapper.class);
    return mapper.findByType(nodeType);
}

public List<GraphEdgeEntity> findAllEdges(final SqlSession session) {
    final EdgeMapper mapper = session.getMapper(EdgeMapper.class);
    return mapper.findAll();
}

public List<GraphEdgeEntity> findEdgesByType(final SqlSession session, final String edgeType) {
    final EdgeMapper mapper = session.getMapper(EdgeMapper.class);
    return mapper.findByType(edgeType);
}
```

**Benefits:**
- Clean separation of concerns - session management at the orchestration level
- Legacy methods remain unchanged for backward compatibility
- No forced CLOB materialization logic polluting the repository layer
- Flexible - caller controls session lifecycle

### 2. Database Layer - SessionManagedRepository Usage

**Note:** This section describes the initial implementation. See "Enhanced Architecture" section below for the final, cleaner implementation.

Modified `H2GraphDatabase.loadDataIntoMemoryRepository()` to use SessionManagedRepository:

```java
private void loadDataIntoMemoryRepository(
        final GraphRepository targetRepo,
        final LoadOptions options) {
    
    // Use SessionManagedRepository - demonstrates proper usage pattern
    try (final SessionManagedRepository repo = createSessionManagedRepository()) {
        // Load nodes
        final List<GraphNodeEntity> nodeEntities = loadNodeEntities(repo, options);
        
        // Convert and add nodes (CLOBs still accessible)
        final Map<String, GraphNode> nodeMap = convertAndAddNodes(
                nodeEntities, targetRepo, options.getProjectRoot());
        
        // Load edges with same session
        final List<GraphEdgeEntity> edgeEntities = loadEdgeEntities(repo);
        
        // Convert and add edges
        convertAndAddEdges(edgeEntities, nodeMap, targetRepo);
        
        // Repository closes here, releasing session
    }
}
```

**Key Points:**
- Single try-with-resources wraps the entire snapshot operation
- All queries use the same session
- CLOB data is deserialized while the connection is still open
- Session closes only after all entity-to-domain conversion is complete

### 3. Helper Methods Updated

Updated `loadNodeEntities()` and `loadEdgeEntities()` to accept and pass through the SqlSession:

```java
private List<GraphNodeEntity> loadNodeEntities(
        final SqlSession session,
        final LoadOptions options) {
    // Uses session parameter instead of creating new sessions
    if (options.shouldLoadAllNodes()) {
        return h2Repository.findAll(session);
    } else if (options.getNodeTypeFilters() != null && !options.getNodeTypeFilters().isEmpty()) {
        List<GraphNodeEntity> nodeEntities = new ArrayList<>();
        for (String nodeType : options.getNodeTypeFilters()) {
            nodeEntities.addAll(h2Repository.findNodesByType(session, nodeType));
        }
        return nodeEntities;
    }
    // ...
}
```

## Testing

### Test Results

The fix was validated with the existing integration test that was failing:

```
Test: PhaseByPhaseIntegrationTest.testPhaseMdb
Database: 83,278 nodes, 481 edges
Result: SUCCESS - All data loaded without CLOB errors
```

**Before Fix:**
```
ERROR: Connection is broken: "session closed"
Failed to load nodes
```

**After Fix:**
```
INFO: Loading nodes from database...
INFO: Loaded 83278 nodes into repository
INFO: Loading edges from database...
INFO: Loaded 481 edges into repository
```

## Architectural Benefits

1. **Clean Separation** - Session management stays at the orchestration level
2. **No CLOB Materialization Hacks** - No need for `.length()` calls or defensive reads
3. **Minimal Changes** - Only 2 classes modified, no ripple effects
4. **Backward Compatible** - Legacy repository methods still work for other use cases
5. **Principle of Least Surprise** - Session lifecycle is explicit and controlled

## Alternative Approaches Considered

### Rejected: CLOB Materialization in Repository

**Approach:** Add helper methods to force CLOB reads by calling `.length()` on each CLOB field before returning.

**Why Rejected:**
- Pollutes repository layer with H2-specific workarounds
- Requires modifying 9+ repository methods
- Mixes persistence concerns with H2 implementation details
- Less flexible - forces eager loading even when not needed
- More error-prone - easy to forget to materialize new CLOB columns

### Chosen: Session Lifecycle Management

**Approach:** Pass SqlSession to repository methods and manage lifecycle at orchestration level.

**Why Chosen:**
- Clean architecture - each layer has clear responsibilities
- Flexible - caller controls when session closes
- Minimal changes - only orchestration layer modified
- Explicit - session lifecycle is visible and intentional
- Extensible - easy to add more session-managed operations

## Lessons Learned

1. **Lazy-loading + Short Sessions = Problems** - Always consider how ORMs and database drivers handle large objects
2. **CLOB Streaming Requires Connection** - H2 CLOBs are not materialized by default
3. **Orchestration Layer is Right Place for Lifecycle** - Session management belongs where you orchestrate multiple operations
4. **Try-with-resources Gotcha** - Convenient but can hide lazy-loading issues

## Files Modified

- `analyzer-core/src/main/java/com/analyzer/core/db/H2GraphStorageRepository.java`
  - Added session-managed query methods
  - Preserved legacy methods for compatibility

- `analyzer-core/src/main/java/com/analyzer/core/db/H2GraphDatabase.java`
  - Modified `loadDataIntoMemoryRepository()` to use single session
  - Updated helper methods to accept session parameter
  - Added comprehensive Javadoc explaining CLOB handling

## Related Documentation

- [H2 MyBatis Implementation](./h2-mybatis-implementation.md) - Original H2 integration
- [JSON Serialization Unification](./json-serialization-unification.md) - Property storage design
- [Two-Level Metrics Design](./two-level-metrics-design.md) - Metrics storage in CLOBs

## Enhanced Architecture: Closeable SessionManagedRepository

After implementing the initial fix, we further improved the architecture by introducing a `Closeable` wrapper that makes session lifecycle management explicit and moves it to the caller's responsibility.

### SessionManagedRepository Pattern

Created a new `SessionManagedRepository` class that:
- Wraps `H2GraphStorageRepository` and holds a `SqlSession`
- Implements `AutoCloseable` for proper resource management
- Provides clean API methods that delegate to the underlying repository
- Enforces that session is open before allowing operations

**Usage Pattern:**
```java
try (SessionManagedRepository repo = database.createSessionManagedRepository()) {
    List<GraphNodeEntity> nodes = repo.findAllNodes();
    // Process nodes while session is still open
    // CLOBs can be accessed here
} // Session automatically closed
```

### Advantages of This Design

1. **Explicit Lifecycle** - The caller controls exactly when the session opens and closes
2. **Resource Safety** - try-with-resources ensures session is always closed
3. **Clean API** - Hides SqlSession from callers while making lifecycle explicit
4. **Flexible** - Can be used for single operations or complex multi-query workflows
5. **Self-Documenting** - The Closeable contract makes it obvious resources need management

### When to Use Each Approach

**Use SessionManagedRepository when:**
- Building new features that need database access
- You want explicit control over session lifecycle
- You're loading data that will be processed outside the database layer
- You need to perform multiple related queries

**Current H2GraphDatabase.snapshot() approach:**
- Already works correctly for its specific use case
- Keeps session management internal to the snapshot operation
- Simpler for callers that don't need to manage sessions

## Future Considerations

- **Prefer SessionManagedRepository for new code** - It's more explicit and safer
- Consider migrating other database operations to use this pattern
- Monitor for similar issues in operations that load large datasets
- Document CLOB handling patterns in architecture guidelines
- Evaluate creating similar patterns for write operations if needed
