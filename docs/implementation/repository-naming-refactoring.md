# Repository Naming Refactoring

## Summary

Renamed the database repository class to eliminate confusion with the in-memory GraphRepository interface.

## Problem

The system had two classes both named "GraphRepository" which was confusing:
1. `com.analyzer.api.graph.GraphRepository` - Interface for in-memory graph storage
2. `com.analyzer.core.db.repository.GraphRepository` - H2 database persistence class

This naming collision made the code confusing and unclear about which component was being used.

## Solution

Renamed the database repository class to `H2GraphStorageRepository` to clearly indicate its purpose:
- **Old name**: `com.analyzer.core.db.repository.GraphRepository`
- **New name**: `com.analyzer.core.db.repository.H2GraphStorageRepository`

## Files Changed

### 1. Renamed File
- `analyzer-core/src/main/java/com/analyzer/core/db/repository/GraphRepository.java`
  → `analyzer-core/src/main/java/com/analyzer/core/db/repository/H2GraphStorageRepository.java`

### 2. Updated Class Definition
```java
// OLD
public class GraphRepository {
    private static final Logger logger = LoggerFactory.getLogger(GraphRepository.class);
    
    public GraphRepository(GraphDatabaseConfig config) { ... }
}

// NEW
public class H2GraphStorageRepository {
    private static final Logger logger = LoggerFactory.getLogger(H2GraphStorageRepository.class);
    
    public H2GraphStorageRepository(GraphDatabaseConfig config) { ... }
}
```

### 3. Updated References in Command Files

**InventoryCommand.java**:
```java
// Import
import com.analyzer.core.db.repository.H2GraphStorageRepository;

// Usage
H2GraphStorageRepository dbRepository = new H2GraphStorageRepository(dbConfig);

// Method signature
private void loadDataIntoEngine(H2GraphStorageRepository dbRepo, AnalysisEngine analysisEngine)

// Statistics
H2GraphStorageRepository repo = new H2GraphStorageRepository(dbConfig);
```

**JsonExportCommand.java**:
```java
// Usage
var dbRepository = new com.analyzer.core.db.repository.H2GraphStorageRepository(dbConfig);

// Method signature
private GraphRepository loadDataFromDatabase(
    com.analyzer.core.db.repository.H2GraphStorageRepository dbRepo,
    Path projectRoot,
    List<String> nodeTypeFilters,
    List<String> edgeTypeFilters)
```

**CsvExportCommand.java**:
```java
// Usage
com.analyzer.core.db.repository.H2GraphStorageRepository dbRepository = 
    new com.analyzer.core.db.repository.H2GraphStorageRepository(dbConfig);

// Method signature
private GraphRepository loadDataFromDatabase(
    com.analyzer.core.db.repository.H2GraphStorageRepository dbRepo)
```

## Benefits

1. **Clarity**: The name now clearly indicates this is for H2 database storage
2. **No Confusion**: No more ambiguity between the interface and the implementation
3. **Intent**: The name "H2GraphStorageRepository" explicitly states its purpose
4. **Documentation**: Self-documenting code that explains its role in the architecture

## Architecture Clarification

After this refactoring, the architecture is clearer:

```
┌─────────────────────────────────────┐
│  Runtime Analysis                   │
│                                     │
│  Uses: InMemoryGraphRepository      │
│  Implements: GraphRepository        │ ← Interface
│  (Fast, in-memory operations)       │
└─────────────────────────────────────┘
              ↓
        (persist on save)
              ↓
┌─────────────────────────────────────┐
│  Persistence Layer                  │
│                                     │
│  Uses: H2GraphStorageRepository     │ ← Standalone class
│  (Durable H2 database storage)      │
└─────────────────────────────────────┘
```

## Compilation

All files compile successfully after the renaming:
- ✅ analyzer-core
- ✅ analyzer-inspectors  
- ✅ analyzer-ejb2spring
- ✅ analyzer-app

## Related Documentation

See `docs/implementation/graph-repository-architecture-analysis.md` for detailed analysis of why we use a hybrid approach (in-memory + database) rather than database-only implementation.
