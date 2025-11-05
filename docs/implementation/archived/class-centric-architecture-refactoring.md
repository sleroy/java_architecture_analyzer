# Class-Centric Architecture Refactoring Plan

## Executive Summary

This document outlines a complete architectural refactoring to fix the fundamental design issue where class-level metadata is incorrectly assigned to `ProjectFile` instead of `JavaClassNode`.

**Key Decision:** Complete refactor with breaking changes allowed.

## 🎯 Core Design Principles

1. **ProjectFile** = Physical file representation + file-level metadata/tags
2. **JavaClassNode** = Logical class representation + class-level metadata/tags
3. **Inspectors** = Type-specific analyzers that scan one node type
4. **Repositories** = Node lookup and creation services (injected into inspectors)
5. **Tags** = Simple labels (Set<String>)
6. **Properties** = Rich metadata (Map<String, Object>)

## 📊 Current Architecture Problems

### Problem 1: Metadata Misplacement
```java
// CURRENT (WRONG)
public class ClassMetricsInspector extends AbstractASMInspector {
    protected void visitEnd() {
        // Attaching class metrics to ProjectFile ❌
        setTag(InspectorTags.TAG_METRICS_METHOD_COUNT, methodCount);
        setTag(InspectorTags.TAG_METRICS_FIELD_COUNT, fieldCount);
        setTag(InspectorTags.TAG_METRICS_CYCLOMATIC_COMPLEXITY, complexity);
    }
}
```

### Problem 2: Mixed Tags and Properties in ProjectFile
```java
// ProjectFile currently mixes tags and properties in same Map
private final Map<String, Object> tags; // Should be Set<String> for tags
```

### Problem 3: JavaClassNode Underutilized
```java
// JavaClassNode exists but has no metrics properties
// All metrics end up on ProjectFile instead
```

## 🏗️ Target Architecture

```mermaid
flowchart TD
    subgraph Collectors["COLLECTOR LAYER"]
        PFC[ProjectFileCollector<br/>Discovers files]
        CFC[ClassFileCollector<br/>Creates JavaClassNodes]
    end
    
    subgraph Nodes["NODE LAYER"]
        PF[ProjectFile<br/>tags + file properties]
        CN[JavaClassNode<br/>tags + class properties]
    end
    
    subgraph Repositories["REPOSITORY LAYER"]
        PFR[ProjectFileRepository]
        CNR[ClassNodeRepository]
        GR[GraphRepository]
    end
    
    subgraph Inspectors["INSPECTOR LAYER"]
        PFI[File Inspectors<br/>analyze ProjectFile]
        CNI[Class Inspectors<br/>analyze JavaClassNode]
    end
    
    PFC -->|creates| PF
    CFC -->|creates| CN
    PF -.references.-> CN
    
    PFI -->|@Inject| PFR
    CNI -->|@Inject| CNR
    CNI -->|@Inject| GR
    
    PFI -->|analyzes| PF
    CNI -->|analyzes| CN
```

## 📝 Implementation Phases

### Phase 1: Core Infrastructure

#### 1.1 Update GraphNode Interface
```java
public interface GraphNode {
    String getId();
    String getNodeType();
    Map<String, Object> getNodeProperties();
    String getDisplayLabel();
    
    // Add tag support
    void addTag(String tag);
    boolean hasTag(String tag);
    Set<String> getTags();
}
```

#### 1.2 Refactor ProjectFile
**Changes:**
- Separate `Set<String> tags` from `Map<String, Object> properties`
- Remove class-level metadata methods
- Keep only file-level metadata

```java
public class ProjectFile implements GraphNode {
    private final Set<String> tags = new HashSet<>();
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    
    // File-level properties only:
    // - fileSize, filePath, modificationDate, encoding
    // - relativePath, fileName, fileExtension
    // - sourceJarPath, jarEntryPath (for JAR files)
}
```

#### 1.3 Enhance JavaClassNode
**Changes:**
- Add tag support
- Add all class-level property constants
- Add convenience methods for metrics

```java
public class JavaClassNode extends BaseGraphNode {
    // Property keys for class metrics
    public static final String PROP_METHOD_COUNT = "methodCount";
    public static final String PROP_FIELD_COUNT = "fieldCount";
    public static final String PROP_CYCLOMATIC_COMPLEXITY = "cyclomaticComplexity";
    public static final String PROP_WEIGHTED_METHODS = "weightedMethods";
    public static final String PROP_EFFERENT_COUPLING = "efferentCoupling";
    public static final String PROP_AFFERENT_COUPLING = "afferentCoupling";
    
    // Convenience methods
    public void setMethodCount(int count) {
        setProperty(PROP_METHOD_COUNT, count);
    }
    
    public int getMethodCount() {
        return getIntProperty(PROP_METHOD_COUNT, 0);
    }
}
```

### Phase 2: Repository Layer

#### 2.1 Create Repository Interfaces
```java
public interface NodeRepository<T extends GraphNode> {
    Optional<T> findById(String id);
    T getOrCreate(String id);
    List<T> findAll();
    List<T> findByTag(String tag);
    void save(T node);
}

public interface ProjectFileRepository extends NodeRepository<ProjectFile> {
    List<ProjectFile> findByExtension(String extension);
    Optional<ProjectFile> findByPath(Path path);
}

public interface ClassNodeRepository extends NodeRepository<JavaClassNode> {
    Optional<JavaClassNode> findByFqn(String fqn);
    List<JavaClassNode> findByPackage(String packageName);
    JavaClassNode getOrCreateByFqn(String fqn);
}

public interface GraphRepository {
    <T extends GraphNode> Optional<T> findNodeById(String id, Class<T> type);
    List<GraphNode> findNodesByType(String nodeType);
    void createEdge(GraphNode from, GraphNode to, String edgeType);
}
```

#### 2.2 Implement Repositories
- `InMemoryProjectFileRepository`
- `InMemoryClassNodeRepository` (enhance existing)
- Integrate with existing `InMemoryGraphRepository`

### Phase 3: Inspector Refactoring

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

#### 3.2 Create Base Inspector Classes
```java
public abstract class AbstractProjectFileInspector implements Inspector<ProjectFile> {
    @Override
    public Class<ProjectFile> getTargetNodeType() {
        return ProjectFile.class;
    }
}

public abstract class AbstractClassNodeInspector implements Inspector<JavaClassNode> {
    @Override
    public Class<JavaClassNode> getTargetNodeType() {
        return JavaClassNode.class;
    }
}

public abstract class AbstractBinaryClassInspector extends AbstractClassNodeInspector {
    @Inject
    protected ClassNodeRepository classNodeRepository;
    
    @Inject
    protected ProjectFileRepository projectFileRepository;
    
    @Inject
    protected ResourceResolver resourceResolver;
}
```

#### 3.3 Refactor ASM-Based Inspectors

**Inspectors to Migrate:**
1. ClassMetricsInspector → AbstractBinaryClassInspector
2. MethodCountInspector → AbstractBinaryClassInspector
3. BinaryJavaClassNodeInspector → AbstractBinaryClassInspector
4. TypeInspectorASMInspector → AbstractBinaryClassInspector
5. BinaryClassFQNInspector → AbstractBinaryClassInspector
6. EjbBinaryClassInspector → AbstractBinaryClassInspector
7. ProgrammaticTransactionUsageInspector → AbstractBinaryClassInspector
8. EjbCreateMethodUsageInspector → AbstractBinaryClassInspector
9. JdbcDataAccessPatternInspector → AbstractBinaryClassInspector
10. StatefulSessionStateInspector → AbstractBinaryClassInspector

**Migration Pattern:**
```java
// BEFORE
public class ClassMetricsInspector extends AbstractASMInspector {
    @Override
    public boolean supports(ProjectFile file) {
        return file.hasTag(InspectorTags.TAG_JAVA_IS_BINARY);
    }
    
    @Override
    protected ASMClassVisitor createClassVisitor(
            ProjectFile projectFile, 
            ProjectFileDecorator decorator) {
        return new ClassMetricsVisitor(projectFile, decorator);
    }
    
    private static class ClassMetricsVisitor extends ASMClassVisitor {
        @Override
        public void visitEnd() {
            setTag(TAG_METRICS_METHOD_COUNT, methodCount); // ❌ Wrong
        }
    }
}

// AFTER
public class ClassMetricsInspector extends AbstractBinaryClassInspector {
    @Inject
    private ClassNodeRepository classNodeRepository;
    
    @Override
    public boolean supports(JavaClassNode classNode) {
        ProjectFile file = projectFileRepository
            .findById(classNode.getProjectFileId())
            .orElse(null);
        return file != null && file.hasTag(InspectorTags.TAG_JAVA_IS_BINARY);
    }
    
    @Override
    public void decorate(JavaClassNode classNode, ProjectFileDecorator decorator) {
        ProjectFile file = projectFileRepository
            .findById(classNode.getProjectFileId())
            .orElseThrow();
            
        try (InputStream is = resourceResolver.getInputStream(file)) {
            ClassReader reader = new ClassReader(is);
            ClassMetricsVisitor visitor = new ClassMetricsVisitor(classNode);
            reader.accept(visitor, 0);
        }
    }
    
    private static class ClassMetricsVisitor extends ClassVisitor {
        private final JavaClassNode classNode;
        
        @Override
        public void visitEnd() {
            classNode.setMethodCount(methodCount);        // ✓ Correct
            classNode.setFieldCount(fieldCount);          // ✓ Correct
            classNode.setEfferentCoupling(couplings);     // ✓ Correct
        }
    }
}
```

### Phase 4: Execution Engine Updates ⚠️ CRITICAL

The **AnalysisEngine** requires significant updates to support the new architecture. This is a critical change that affects how inspectors are executed.

#### 4.1 Current AnalysisEngine Issues
```java
// CURRENT (WRONG) - Single-phase execution
public class AnalysisEngine {
    public void runAnalysis() {
        // Runs all inspectors on ProjectFiles only
        for (ProjectFile file : project.getFiles()) {
            for (Inspector inspector : inspectors) {
                if (inspector.supports(file)) {
                    inspector.decorate(file, decorator);
                }
            }
        }
    }
}
```

**Problems:**
1. Only executes inspectors on ProjectFile
2. No support for JavaClassNode inspectors
3. No type-based inspector selection
4. No multi-phase execution

#### 4.2 Updated AnalysisEngine Architecture
```java
// NEW (CORRECT) - Multi-phase, type-aware execution
public class AnalysisEngine {
    private InspectorRegistry inspectorRegistry;
    private ProjectFileRepository projectFileRepository;
    private ClassNodeRepository classNodeRepository;
    private GraphRepository graphRepository;
    
    public void runAnalysis() {
        // PHASE 1: Run ProjectFile inspectors
        // These create tags/properties on ProjectFile nodes
        List<Inspector<ProjectFile>> fileInspectors = 
            inspectorRegistry.getInspectorsForType(ProjectFile.class);
        
        for (ProjectFile file : projectFileRepository.findAll()) {
            for (Inspector<ProjectFile> inspector : fileInspectors) {
                if (inspector.canProcess(file)) {
                    ProjectFileDecorator decorator = createDecorator(file);
                    inspector.decorate(file, decorator);
                }
            }
        }
        
        // PHASE 2: Run JavaClassNode inspectors
        // These create tags/properties on JavaClassNode nodes
        // These may reference ProjectFile via repositories
        List<Inspector<JavaClassNode>> classInspectors = 
            inspectorRegistry.getInspectorsForType(JavaClassNode.class);
        
        for (JavaClassNode classNode : classNodeRepository.findAll()) {
            for (Inspector<JavaClassNode> inspector : classInspectors) {
                if (inspector.canProcess(classNode)) {
                    ProjectFileDecorator decorator = createDecorator(classNode);
                    inspector.decorate(classNode, decorator);
                }
            }
        }
        
        // FUTURE: Phase 3, 4, etc. for other node types (EjbNode, MethodNode)
    }
    
    private <T extends GraphNode> ProjectFileDecorator createDecorator(T node) {
        // Create decorator that can handle any GraphNode type
        return new ProjectFileDecorator(node, graphRepository);
    }
}
```

#### 4.3 Update InspectorRegistry
```java
public class InspectorRegistry {
    private final Map<Class<?>, List<Inspector<?>>> inspectorsByType = new HashMap<>();
    
    // NEW: Type-based inspector lookup
    public <T extends GraphNode> List<Inspector<T>> getInspectorsForType(Class<T> nodeType) {
        return inspectors.stream()
            .filter(i -> i.getTargetNodeType().equals(nodeType))
            .map(i -> (Inspector<T>) i)
            .toList();
    }
    
    // NEW: Cache inspectors by type for performance
    public void indexInspectorsByType() {
        for (Inspector<?> inspector : inspectors) {
            Class<?> targetType = inspector.getTargetNodeType();
            inspectorsByType.computeIfAbsent(targetType, k -> new ArrayList<>())
                .add(inspector);
        }
    }
}
```

#### 4.4 Update ProjectFileDecorator
The decorator needs to support multiple node types:
```java
public class ProjectFileDecorator {
    [ERROR] Failed to process response: Bedrock is unable to process your request.

### Phase 5: Export Layer Updates

#### 5.1 Update CSV Exporter
```java
public class CsvExporter {
    // Export ProjectFiles
    public void exportProjectFiles(List<ProjectFile> files) {
        // Columns: filePath, fileSize, encoding, tags
    }
    
    // Export JavaClassNodes
    public void exportJavaClasses(List<JavaClassNode> classes) {
        // Columns: fqn, methodCount, complexity, coupling, tags
    }
}
```

#### 5.2 Update ProjectSerializer
```java
public class ProjectSerializer {
    public void serialize(Project project) {
        // Serialize both ProjectFiles and JavaClassNodes
        serializeProjectFiles(project.getFiles());
        serializeClassNodes(classNodeRepository.findAll());
    }
}
```

## 🔧 Migration Steps

### Step 1: Infrastructure (Do First)
1. Add tag support to GraphNode interface
2. Refactor ProjectFile to separate tags and properties
3. Enhance JavaClassNode with metric properties
4. Create repository interfaces and implementations

### Step 2: Inspector Framework (Do Second)
1. Update Inspector interface with getTargetNodeType()
2. Create AbstractProjectFileInspector
3. Create AbstractClassNodeInspector
4. Create AbstractBinaryClassInspector
5. Update InspectorRegistry for type-based lookup

### Step 3: Migrate Inspectors (Do Third)
1. Start with ClassMetricsInspector as proof-of-concept
2. Migrate remaining 9 ASM inspectors
3. Update @InspectorDependencies annotations
4. Test each inspector after migration

### Step 4: Execution Engine (Do Fourth)
1. Update AnalysisEngine to run inspectors by node type
2. Update dependency resolution
3. Update progress tracking

### Step 5: Export Layer (Do Last)
1. Update CSV exporter for both node types
2. Update JSON serializer
3. Update graph export formats

## 🧪 Testing Strategy

### Unit Tests
- Test each repository implementation
- Test inspector migration individually
- Test tag/property separation in ProjectFile

### Integration Tests
- Test complete analysis flow
- Test multi-node inspector scenarios
- Test repository injection

### Regression Tests
- Verify all metrics still calculated correctly
- Verify export formats work
- Verify graph relationships maintained

## 📊 Impact Analysis

### Files to Modify
**Core (8 files):**
- GraphNode.java
- ProjectFile.java
- JavaClassNode.java
- Inspector.java
- InspectorRegistry.java
- AnalysisEngine.java
- ProjectSerializer.java
- CsvExporter.java

**New Files (6 files):**
- ProjectFileRepository.java
- ClassNodeRepository.java (enhance existing)
- InMemoryProjectFileRepository.java
- AbstractProjectFileInspector.java
- AbstractClassNodeInspector.java
- AbstractBinaryClassInspector.java

**Inspectors to Migrate (10 files):**
- ClassMetricsInspector.java
- MethodCountInspector.java
- BinaryJavaClassNodeInspector.java
- TypeInspectorASMInspector.java
- BinaryClassFQNInspector.java
- EjbBinaryClassInspector.java
- ProgrammaticTransactionUsageInspector.java
- EjbCreateMethodUsageInspector.java
- JdbcDataAccessPatternInspector.java
- StatefulSessionStateInspector.java

**Total: ~24 files**

## 🚀 Next Steps

1. Review and approve this plan
2. Create feature branch: `feature/class-centric-architecture`
3. Begin Phase 1: Infrastructure changes
4. Implement ClassMetricsInspector as proof-of-concept
5. Continue with remaining inspector migrations
6. Update export layer
7. Comprehensive testing
8. Merge to main

## 📚 Additional Notes

### Future Extensibility
This architecture easily supports new node types:
- **EjbNode**: For EJB-specific metadata
- **MethodNode**: For method-level analysis
- **PackageNode**: For package-level metrics

### Cross-Node Analysis
Inspectors can analyze one node type and reference others:
```java
public class DependencyInspector extends AbstractClassNodeInspector {
    @Inject
    private ClassNodeRepository classNodeRepository;
    
    @Inject
    private GraphRepository graphRepository;
    
    @Override
    public void decorate(JavaClassNode classNode, ProjectFileDecorator decorator) {
        // Analyze classNode
        Set<String> deps = findDependencies(classNode);
        
        // Create relationships to other class nodes
        for (String dep : deps) {
            JavaClassNode depNode = classNodeRepository.getOrCreateByFqn(dep);
            graphRepository.createEdge(classNode, depNode, "DEPENDS_ON");
        }
    }
}
