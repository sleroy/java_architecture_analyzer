# Class-Centric Refactoring: Next Steps & Implementation Roadmap

**Date:** 2025-10-20  
**Status:** Planning Phase  
**Goal:** Migrate from ProjectFile-centric to JavaClassNode-centric architecture

## 📊 Current State Analysis

### ✅ What's Already Done
Based on Memory Bank and refactoring plans:

1. **Architecture Simplification Complete** (2025-10-15)
   - @InspectorDependencies modernized
   - InspectorDependencyResolver cleaned
   - Clean dependency architecture established

2. **Already Refactored Inspectors** (From refactoring plan)
   - `EntityBeanJavaSourceInspector` ✅
   - `SessionBeanJavaSourceInspector` ✅

3. **Existing Infrastructure**
   - `JavaClassNode` exists with basic properties
   - `ClassNodeRepository` exists (DelegatingClassNodeRepository)
   - `GraphRepository` (InMemoryGraphRepository) operational
   - Graph-based analysis system functional

### ❌ What's Missing (Critical Gaps)

1. **Core Infrastructure Gaps** (Phase 1)
   - JavaClassNode lacks tag support (only has properties)
   - JavaClassNode lacks class-level metric property constants
   - ProjectFile still mixes tags and properties in single Map
   - Repository interfaces not fully defined (NodeRepository pattern)

2. **Inspector Framework Gaps** (Phase 2-3)
   - No `AbstractClassNodeInspector` base class
   - No `AbstractBinaryClassNodeInspector` base class
   - Current inspectors extend old bases (AbstractASMInspector, AbstractJavaParserInspector)
   - Inspector interface doesn't have `getTargetNodeType()` method

3. **Execution Engine Gaps** (Phase 4) ⚠️ **CRITICAL**
   - AnalysisEngine only runs inspectors on ProjectFile
   - No multi-phase execution (ProjectFile inspectors → ClassNode inspectors)
   - No type-based inspector selection
   - InspectorRegistry lacks type-based lookup

4. **Inspectors Needing Migration** (Phase 3)
   - **24 inspectors** need refactoring (see detailed list below)

## 🎯 Recommended Implementation Strategy

### Strategy 1: Big Bang (NOT RECOMMENDED)
**Approach:** Implement all phases simultaneously  
**Risk:** High - Too many moving parts  
**Timeline:** 2-3 weeks  
**Recommendation:** ❌ Too risky

### Strategy 2: Phased Baby Steps (RECOMMENDED) ✅
**Approach:** Implement phases sequentially with validation  
**Risk:** Low - Each phase is validated before next  
**Timeline:** 3-4 weeks with solid foundation  
**Recommendation:** ✅ **This is the way**

### Strategy 3: Proof of Concept First (ALTERNATIVE)
**Approach:** Create POC with 1-2 inspectors, then scale  
**Risk:** Medium - Might need rework  
**Timeline:** 1 week POC + 2 weeks implementation  
**Recommendation:** ⚠️ Consider if uncertain about architecture

## 📋 Detailed Phase-by-Phase Roadmap

### Phase 1: Core Infrastructure (MUST DO FIRST)
**Duration:** 3-5 days  
**Risk:** Low  
**Breaking Changes:** Yes (but backward compatible)

#### 1.1 Update GraphNode Interface
```java
public interface GraphNode {
    // Existing methods...
    
    // ADD: Tag support
    void addTag(String tag);
    boolean hasTag(String tag);
    Set<String> getTags();
    void removeTag(String tag);
}
```

**Files to Modify:**
- `src/main/java/com/analyzer/core/graph/GraphNode.java`
- `src/main/java/com/analyzer/core/graph/BaseGraphNode.java` (implement tag methods)

#### 1.2 Refactor ProjectFile
**Objective:** Separate tags from properties

```java
public class ProjectFile implements GraphNode {
    private final Set<String> tags = new ConcurrentHashSet<>();
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    
    // Keep ONLY file-level properties:
    // - fileSize, filePath, modificationDate
    // - relativePath, fileName, fileExtension
    // - sourceJarPath, jarEntryPath
    
    // REMOVE class-level property methods
}
```

**Files to Modify:**
- `src/main/java/com/analyzer/core/model/ProjectFile.java`

**Testing:** Update ProjectFilePropertiesAndTagsTest.java

#### 1.3 Enhance JavaClassNode
**Objective:** Add all class-level metadata support

```java
public class JavaClassNode extends BaseGraphNode {
    // Property constants for class metrics
    public static final String PROP_METHOD_COUNT = "methodCount";
    public static final String PROP_FIELD_COUNT = "fieldCount";
    public static final String PROP_CYCLOMATIC_COMPLEXITY = "cyclomaticComplexity";
    public static final String PROP_WEIGHTED_METHODS = "weightedMethods";
    public static final String PROP_EFFERENT_COUPLING = "efferentCoupling";
    public static final String PROP_AFFERENT_COUPLING = "afferentCoupling";
    public static final String PROP_INHERITANCE_DEPTH = "inheritanceDepth";
    public static final String PROP_INTERFACE_COUNT = "interfaceCount";
    
    // Convenience methods
    public void setMethodCount(int count) { setProperty(PROP_METHOD_COUNT, count); }
    public int getMethodCount() { return getIntProperty(PROP_METHOD_COUNT, 0); }
    // ... etc for all properties
}
```

**Files to Modify:**
- `src/main/java/com/analyzer/core/graph/JavaClassNode.java`

**Deliverables:**
- [ ] GraphNode interface updated with tag support
- [ ] BaseGraphNode implements tag methods
- [ ] ProjectFile refactored (tags + properties separated)
- [ ] JavaClassNode enhanced with metric properties
- [ ] All existing tests pass
- [ ] New tests for tag/property separation

---

### Phase 2: Repository Layer (DO SECOND)
**Duration:** 2-3 days  
**Risk:** Low  
**Breaking Changes:** No (new code)

#### 2.1 Create Repository Interfaces
**Files to Create:**
- `src/main/java/com/analyzer/core/graph/NodeRepository.java` (generic interface)

**Files to Enhance:**
- `src/main/java/com/analyzer/core/graph/ProjectFileRepository.java` (extend NodeRepository)
- `src/main/java/com/analyzer/core/graph/ClassNodeRepository.java` (extend NodeRepository)

**Code Pattern:**
```java
public interface NodeRepository<T extends GraphNode> {
    Optional<T> findById(String id);
    T getOrCreate(String id);
    List<T> findAll();
    List<T> findByTag(String tag);
    void save(T node);
}

public interface ClassNodeRepository extends NodeRepository<JavaClassNode> {
    Optional<JavaClassNode> findByFqn(String fqn);
    List<JavaClassNode> findByPackage(String packageName);
    JavaClassNode getOrCreateByFqn(String fqn);
}
```

#### 2.2 Implement/Update Repositories
**Files to Create:**
- Keep existing `InMemoryProjectFileRepository.java`
- Enhance existing `DelegatingClassNodeRepository.java` to implement new interface

**Deliverables:**
- [ ] NodeRepository interface created
- [ ] ProjectFileRepository extends NodeRepository
- [ ] ClassNodeRepository extends NodeRepository
- [ ] InMemoryProjectFileRepository implements interface
- [ ] DelegatingClassNodeRepository enhanced
- [ ] Repository unit tests

---

### Phase 3: Inspector Framework (DO THIRD)
**Duration:** 3-4 days  
**Risk:** Medium  
**Breaking Changes:** Yes (but gradual migration)

#### 3.1 Update Inspector Interface
```java
public interface Inspector<T extends GraphNode> {
    void decorate(T node, ProjectFileDecorator decorator);
    String getName();
    RequiredTags getDependencies();
    boolean supports(T node);
    Class<T> getTargetNodeType(); // NEW
}
```

**Files to Modify:**
- `src/main/java/com/analyzer/core/inspector/Inspector.java`

#### 3.2 Create Base Inspector Classes
**Files to Create:**
- `src/main/java/com/analyzer/inspectors/core/AbstractClassNodeInspector.java`
- `src/main/java/com/analyzer/inspectors/core/AbstractBinaryClassNodeInspector.java`

**Code Pattern:**
```java
public abstract class AbstractClassNodeInspector implements Inspector<JavaClassNode> {
    @Inject
    protected ClassNodeRepository classNodeRepository;
    
    @Inject
    protected ProjectFileRepository projectFileRepository;
    
    @Override
    public Class<JavaClassNode> getTargetNodeType() {
        return JavaClassNode.class;
    }
    
    @Override
    public boolean supports(JavaClassNode classNode) {
        return true; // Override if needed
    }
}
```

#### 3.3 Migrate 1-2 Proof-of-Concept Inspectors
**Recommended POC Inspectors:**
1. `MethodCountInspector` (simple metrics)
2. `InheritanceDepthInspector` (already uses ClassLoader pattern)

**Migration Pattern:**
```java
// BEFORE
public class MethodCountInspector extends AbstractJavaParserInspector {
    @Override
    public void inspect(ProjectFile projectFile) {
        projectFile.setTag(TAG_METRICS_METHOD_COUNT, count); // ❌
    }
}

// AFTER
public class MethodCountInspector extends AbstractClassNodeInspector {
    @Override
    public void decorate(JavaClassNode classNode, ProjectFileDecorator decorator) {
        classNode.setMethodCount(count); // ✅
    }
}
```

**Deliverables:**
- [ ] Inspector interface updated
- [ ] AbstractClassNodeInspector created
- [ ] AbstractBinaryClassNodeInspector created
- [ ] 2 POC inspectors migrated and tested
- [ ] Migration pattern documented

---

### Phase 4: Execution Engine (CRITICAL - DO FOURTH) ⚠️
**Duration:** 4-5 days  
**Risk:** HIGH  
**Breaking Changes:** Yes (core engine change)

#### 4.1 Update InspectorRegistry
**File:** `src/main/java/com/analyzer/core/inspector/InspectorRegistry.java`

```java
public class InspectorRegistry {
    private final Map<Class<?>, List<Inspector<?>>> inspectorsByType = new HashMap<>();
    
    // NEW: Type-based inspector lookup
    public <T extends GraphNode> List<Inspector<T>> getInspectorsForType(Class<T> nodeType) {
        return inspectorsByType.getOrDefault(nodeType, Collections.emptyList())
            .stream()
            .map(i -> (Inspector<T>) i)
            .collect(Collectors.toList());
    }
    
    // NEW: Index inspectors by target type
    public void indexInspectorsByType() {
        for (Inspector<?> inspector : inspectors) {
            Class<?> targetType = inspector.getTargetNodeType();
            inspectorsByType.computeIfAbsent(targetType, k -> new ArrayList<>())
                .add(inspector);
        }
    }
}
```

#### 4.2 Update AnalysisEngine (CRITICAL)
**File:** `src/main/java/com/analyzer/core/engine/AnalysisEngine.java`

**Current (WRONG):**
```java
public void runAnalysis() {
    // Only runs on ProjectFiles
    for (ProjectFile file : project.getFiles()) {
        for (Inspector inspector : inspectors) {
            inspector.decorate(file, decorator);
        }
    }
}
```

**Target (CORRECT):**
```java
public void runAnalysis() {
    // PHASE 1: Run ProjectFile inspectors
    List<Inspector<ProjectFile>> fileInspectors = 
        inspectorRegistry.getInspectorsForType(ProjectFile.class);
    
    for (ProjectFile file : projectFileRepository.findAll()) {
        for (Inspector<ProjectFile> inspector : fileInspectors) {
            if (inspector.supports(file)) {
                inspector.decorate(file, createDecorator(file));
            }
        }
    }
    
    // PHASE 2: Run JavaClassNode inspectors
    List<Inspector<JavaClassNode>> classInspectors = 
        inspectorRegistry.getInspectorsForType(JavaClassNode.class);
    
    for (JavaClassNode classNode : classNodeRepository.findAll()) {
        for (Inspector<JavaClassNode> inspector : classInspectors) {
            if (inspector.supports(classNode)) {
                inspector.decorate(classNode, createDecorator(classNode));
            }
        }
    }
}
```

#### 4.3 Update ProjectFileDecorator
**File:** `src/main/java/com/analyzer/core/export/ProjectFileDecorator.java`

Make it support multiple node types or create a generic `NodeDecorator<T extends GraphNode>`.

**Deliverables:**
- [ ] InspectorRegistry type-based lookup
- [ ] AnalysisEngine multi-phase execution
- [ ] ProjectFileDecorator/NodeDecorator updated
- [ ] Integration tests for multi-phase execution
- [ ] Dependency resolution works across phases

---

### Phase 5: Bulk Inspector Migration (DO LAST)
**Duration:** 5-7 days  
**Risk:** Low (following established pattern)  
**Breaking Changes:** No (if backward compatible)

#### Inspectors to Migrate (24 total)

**High Priority (Metrics - 7 inspectors):**
1. MethodCountInspector ✅ (POC done in Phase 3)
2. InheritanceDepthInspector ✅ (POC done in Phase 3)
3. InterfaceNumberInspector
4. TypeUsageInspector
5. CyclomaticComplexityInspector
6. AnnotationCountInspector
7. TypeInspectorASMInspector

**Medium Priority (EJB - 10 inspectors):**
8. BusinessDelegatePatternJavaSourceInspector
9. ComplexCmpRelationshipJavaSourceInspector
10. CustomDataTransferPatternJavaSourceInspector
11. DatabaseResourceManagementInspector
12. DeclarativeTransactionJavaSourceInspector
13. EjbCreateMethodUsageInspector
14. EjbHomeInterfaceInspector
15. EjbRemoteInterfaceInspector
16. IdentifyServletSourceInspector
17. JdbcDataAccessPatternInspector

**Medium Priority (More EJB - 7 inspectors):**
18. JndiLookupInspector
19. MessageDrivenBeanInspector
20. ProgrammaticTransactionUsageInspector
21. StatefulSessionStateInspector
22. CmpFieldMappingJavaBinaryInspector
23. EjbBinaryClassInspector
24. JavaImportGraphInspector

**Migration Strategy:**
- Migrate in groups of 3-4 inspectors per day
- Test after each group
- Use automated refactoring where possible
- Follow POC pattern established in Phase 3

**Deliverables:**
- [ ] All 24 inspectors migrated
- [ ] All inspector tests updated and passing
- [ ] Migration documented
- [ ] Performance benchmarked

---

## 🚦 Decision Points & Risks

### Critical Decision 1: Backward Compatibility
**Question:** Should we maintain backward compatibility or allow breaking changes?

**Option A: Gradual Migration (RECOMMENDED)**
- Keep old inspector bases working
- New inspectors use new bases
- Migrate gradually over time
- **Risk:** Low, **Timeline:** Longer

**Option B: Big Bang Migration**
- Break old inspector bases
- Force all migration at once
- Clean cut, no legacy
- **Risk:** High, **Timeline:** Shorter

**Recommendation:** Option A - Gradual migration with deprecation warnings

### Critical Decision 2: Test Infrastructure Fix Timing
**Question:** When should we fix the 45 ASM test failures?

**Option A: Before Refactoring**
- Fix tests first
- Then refactor with confidence
- **Risk:** Low

**Option B: After Refactoring**
- Refactor first
- Fix tests with new architecture
- **Risk:** Medium

**Option C: In Parallel**
- Fix tests during Phase 3
- Validate with real tests
- **Risk:** Medium

**Recommendation:** Option C - Fix tests during Phase 3 when migrating ASM inspectors

### Risk Mitigation

**Risk 1: AnalysisEngine Breaking**
- **Mitigation:** Keep old execution path working during migration
- **Fallback:** Feature flag for old vs new execution

**Risk 2: Repository Injection Issues**
- **Mitigation:** Use existing PicoContainer patterns
- **Fallback:** Manual wiring if DI fails

**Risk 3: Performance Degradation**
- **Mitigation:** Benchmark each phase
- **Fallback:** Optimize critical paths

## 📈 Success Metrics

### Phase 1 Success Criteria
- [ ] All GraphNode tests pass
- [ ] ProjectFile properly separates tags/properties
- [ ] JavaClassNode has all metric properties
- [ ] No regression in existing functionality

### Phase 2 Success Criteria
- [ ] Repository interfaces defined
- [ ] All repositories implement interfaces
- [ ] Repository unit tests pass
- [ ] Repositories injectable via @Inject

### Phase 3 Success Criteria
- [ ] 2 POC inspectors fully migrated
- [ ] POC inspectors tests pass
- [ ] Migration pattern documented
- [ ] No regression in other inspectors

### Phase 4 Success Criteria
- [ ] Multi-phase execution works
- [ ] Type-based inspector selection works
- [ ] All existing inspectors still work
- [ ] Performance acceptable (<10% regression)

### Phase 5 Success Criteria
- [ ] All 24 inspectors migrated
- [ ] All tests pass (including 45 previously failing)
- [ ] Performance benchmarked and acceptable
- [ ] Documentation complete

## 🎯 Immediate Next Steps (Today)

### Step 1: Review & Approval (1 hour)
- [ ] Review this plan with stakeholders
- [ ] Make decision on Option A vs B (Gradual vs Big Bang)
- [ ] Make decision on test fix timing
- [ ] Get approval to proceed

### Step 2: Create Feature Branch (15 minutes)
```bash
git checkout -b feature/class-centric-architecture
```

### Step 3: Begin Phase 1.1 (2-3 hours)
- [ ] Update GraphNode interface with tag support
- [ ] Update BaseGraphNode to implement tags
- [ ] Write unit tests for tag functionality
- [ ] Commit: "Phase 1.1: Add tag support to GraphNode"

### Step 4: Continue Phase 1.2 (2-3 hours)
- [ ] Refactor ProjectFile to separate tags/properties
- [ ] Update ProjectFilePropertiesAndTagsTest
- [ ] Ensure all existing tests pass
- [ ] Commit: "Phase 1.2: Refactor ProjectFile tags/properties"

### Step 5: Complete Phase 1.3 (2-3 hours)
- [ ] Add metric constants to JavaClassNode
- [ ] Add convenience methods to JavaClassNode
- [ ] Write tests for new methods
- [ ] Commit: "Phase 1.3: Enhance JavaClassNode with metrics"

**Today's Goal:** Complete Phase 1 (Core Infrastructure)

## 📅 Estimated Timeline

| Phase | Duration | Effort | Risk |
|-------|----------|--------|------|
| Phase 1: Core Infrastructure | 3-5 days | Medium | Low |
| Phase 2: Repository Layer | 2-3 days | Low | Low |
| Phase 3: Inspector Framework + POC | 3-4 days | Medium | Medium |
| Phase 4: Execution Engine | 4-5 days | High | HIGH |
| Phase 5: Bulk Migration | 5-7 days | Medium | Low |
| **TOTAL** | **17-24 days** | **High** | **Medium** |

**With Buffer:** 4-5 weeks to completion

## 🎉 Expected Outcomes

### Technical Benefits
✅ Clean separation of concerns (file vs class metadata)  
✅ Type-safe inspector system  
✅ Extensible to new node types (MethodNode, PackageNode, etc.)  
✅ Better performance with type-based filtering  
✅ Easier testing with clear node boundaries  

### Code Quality Benefits
✅ More maintainable codebase  
✅ Clearer intent (tags vs properties)  
✅ Better repository abstraction  
✅ Consistent patterns across inspectors  
✅ Reduced technical debt  

### Future Extensibility
✅ Easy to add new node types  
✅ Easy to add new inspector types  
✅ Easy to create cross-node analysis  
✅ Foundation for advanced features  

## 📚 References

- Original Plan: `docs/implementation/class-centric-architecture-refactoring.md`
- Inspector List: `docs/implementation/tasks/class-centric-refactoring-plan.md`
- Memory Bank: `memory-bank/activeContext.md`
- Current Progress: `memory-bank/progress.md`

---

**Ready to Begin?** ✅  
Start with **Phase 1.1: Update GraphNode Interface**
