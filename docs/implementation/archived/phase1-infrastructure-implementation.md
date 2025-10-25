# Class-Centric Architecture Refactoring - Phase 1: Infrastructure Implementation

## Background

A comprehensive impact analysis has been completed and documented in `docs/implementation/class-centric-architecture-refactoring.md`. This task implements Phase 1 of the refactoring plan.

## Core Problem

Class-level metadata (method count, cyclomatic complexity, coupling metrics) is currently being assigned to `ProjectFile` nodes instead of `JavaClassNode` nodes. This is a fundamental architectural flaw affecting 10+ ASM-based inspectors.

**Example of Current Issue:**
```java
// ClassMetricsInspector.java (WRONG)
public class ClassMetricsInspector extends AbstractASMInspector {
    protected void visitEnd() {
        setTag(TAG_METRICS_METHOD_COUNT, methodCount);  // ❌ Assigns to ProjectFile
    }
}
```

## Approved Architecture

1. **ProjectFile** = Physical file + file-level metadata + Set<String> tags
2. **JavaClassNode** = Logical class + class-level metadata + Set<String> tags
3. **Inspectors** = Type-specific (scan one node type, retrieve others via @Inject)
4. **Repositories** = Node lookup services
5. **Breaking changes allowed** - Complete refactor approved

## Phase 1: Core Infrastructure (THIS TASK)

### 1.1 Update GraphNode Interface
**File:** `src/main/java/com/analyzer/core/graph/GraphNode.java`

Add tag support methods:
```java
void addTag(String tag);
boolean hasTag(String tag);
Set<String> getTags();
```

### 1.2 Refactor ProjectFile
**File:** `src/main/java/com/analyzer/core/model/ProjectFile.java`

**Changes Required:**
- Separate `Set<String> tags` from `Map<String, Object> properties`
- Remove all class-level metadata methods
- Keep only file-level properties (fileSize, filePath, encoding, etc.)
- Update `getNodeProperties()` to return properties only (not tags)

### 1.3 Enhance JavaClassNode
**File:** `src/main/java/com/analyzer/core/graph/JavaClassNode.java`

**Add:**
- Tag support (Set<String> tags)
- Property constants for all class metrics:
  - PROP_METHOD_COUNT
  - PROP_FIELD_COUNT
  - PROP_CYCLOMATIC_COMPLEXITY
  - PROP_WEIGHTED_METHODS
  - PROP_EFFERENT_COUPLING
  - PROP_AFFERENT_COUPLING
- Convenience getter/setter methods for each metric

### 1.4 Create Repository Interfaces

**New File:** `src/main/java/com/analyzer/core/graph/NodeRepository.java`
```java
public interface NodeRepository<T extends GraphNode> {
    Optional<T> findById(String id);
    T getOrCreate(String id);
    List<T> findAll();
    List<T> findByTag(String tag);
    void save(T node);
}
```

**New File:** `src/main/java/com/analyzer/core/graph/ProjectFileRepository.java`
```java
public interface ProjectFileRepository extends NodeRepository<ProjectFile> {
    List<ProjectFile> findByExtension(String extension);
    Optional<ProjectFile> findByPath(Path path);
}
```

**Enhance:** `src/main/java/com/analyzer/core/graph/ClassNodeRepository.java`
Add methods:
```java
JavaClassNode getOrCreateByFqn(String fqn);
List<JavaClassNode> findByPackage(String packageName);
```

### 1.5 Implement ProjectFileRepository

**New File:** `src/main/java/com/analyzer/core/graph/InMemoryProjectFileRepository.java`

Implement all ProjectFileRepository methods using a ConcurrentHashMap for storage.

### 1.6 Enhance Existing ClassNodeRepository Implementation

**File:** `src/main/java/com/analyzer/core/graph/DelegatingClassNodeRepository.java`

Update to implement new ClassNodeRepository methods.

## Testing Requirements

After Phase 1 changes:
1. All existing tests should pass (or be updated for new structure)
2. Verify ProjectFile has separated tags and properties
3. Verify JavaClassNode has metric properties
4. Test repository implementations (CRUD operations)

## Files to Modify (Phase 1)

1. `src/main/java/com/analyzer/core/graph/GraphNode.java` - Add tag methods
2. `src/main/java/com/analyzer/core/model/ProjectFile.java` - Refactor tags/properties
3. `src/main/java/com/analyzer/core/graph/JavaClassNode.java` - Add metrics
4. `src/main/java/com/analyzer/core/graph/ClassNodeRepository.java` - Enhance interface
5. `src/main/java/com/analyzer/core/graph/DelegatingClassNodeRepository.java` - Update impl

## Files to Create (Phase 1)

1. `src/main/java/com/analyzer/core/graph/NodeRepository.java`
2. `src/main/java/com/analyzer/core/graph/ProjectFileRepository.java`
3. `src/main/java/com/analyzer/core/graph/InMemoryProjectFileRepository.java`

## Success Criteria

- [ ] GraphNode interface has tag support methods
- [ ] ProjectFile separates tags (Set) from properties (Map)
- [ ] JavaClassNode has all metric property constants and methods
- [ ] Repository interfaces created
- [ ] InMemoryProjectFileRepository implemented
- [ ] All tests pass
- [ ] No breaking changes to inspector execution (yet - that's Phase 2+)

## Next Phases (Future Tasks)

- Phase 2: Inspector framework updates
- Phase 3: Migrate ClassMetricsInspector (proof-of-concept)
- Phase 4: Migrate remaining 9 ASM inspectors
- Phase 5: Update AnalysisEngine for multi-phase execution
- Phase 6: Export layer updates

## Reference

Complete details in: `docs/implementation/class-centric-architecture-refactoring.md`
