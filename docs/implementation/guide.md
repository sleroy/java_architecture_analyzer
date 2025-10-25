# Implementation Guide

**Last Updated:** October 21, 2025

## Current Status

### 🎯 Project Status: Phase 3 Complete
- **Test Suite:** 146/146 tests passing (100%)
- **Architecture:** Class-centric migration complete for ASM inspectors
- **Next Phase:** Additional inspector migrations (52 remaining inspectors)

### Completed Phases

#### Phase 1: EJB Migration Foundation ✅
- 83/83 tests passing
- Core EJB inspectors implemented
- Graph nodes and edge types defined
- 60+ EJB-specific tags created

#### Phase 2: Data Access & Metrics ✅
- **Substage 1:** JDBC & Data Access (41/41 tests)
- **Substage 4:** ClassLoader-Based Metrics (22/22 tests)

#### Phase 3: ASM Inspector Migration ✅
- Migrated 4 key inspectors to class-centric architecture
- EjbBinaryClassInspector, StatefulSessionStateInspector
- EjbCreateMethodUsageInspector, JdbcDataAccessPatternInspector
- All compile without errors

### In Progress

#### Phase 2: Substage 2-3 🚧
- JBoss EJB Configuration Inspector (P2-04)
- JDBC Transaction Pattern Inspector (P2-05)
- Connection Pool Performance Inspector (P2-06)

## Architecture Overview

### Inspector Hierarchy

```
Inspector (interface)
  ├── AbstractJavaClassInspector (class-centric base)
  │   ├── AbstractASMClassInspector (bytecode analysis)
  │   │   └── Your ASM-based inspectors
  │   └── AbstractJavaParserInspector (source analysis)
  │       └── Your source-based inspectors
  └── PackageInspector (package-level analysis)
```

### Graph Model

**Nodes:**
- `JavaClassNode` - Represents a Java class with properties and tags
- `PackageNode` - Represents a Java package
- `InspectorNode` - Represents an inspector in dependency graph

**Edges:**
- `GraphEdge` - Generic edge type
- `TagDependencyEdge` - Inspector dependencies

**Repositories:**
- `ClassNodeRepository` - CRUD for JavaClassNode
- `ProjectFileRepository` - File + class node management
- `GraphRepository` - Full graph operations

## Inspector Development

### ASM Inspector Pattern (Recommended)

**When to use:** Binary class analysis, method detection, annotation scanning

```java
public class YourInspector extends AbstractASMClassInspector {
    
    @Inject
    public YourInspector(ProjectFileRepository projectFileRepo, 
                         ResourceResolver resolver) {
        super(projectFileRepo, resolver);
    }
    
    @Override
    protected ASMClassNodeVisitor createClassVisitor(
            JavaClassNode classNode, 
            NodeDecorator<JavaClassNode> decorator) {
        return new YourVisitor(classNode, decorator);
    }
    
    private static class YourVisitor extends ASMClassNodeVisitor {
        private final NodeDecorator<JavaClassNode> decorator;
        
        public YourVisitor(JavaClassNode node, 
                          NodeDecorator<JavaClassNode> decorator) {
            super(Opcodes.ASM9, node);
            this.decorator = decorator;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, 
                                         String descriptor, 
                                         String signature, 
                                         String[] exceptions) {
            // Your method analysis
            decorator.enableTag("your-tag");
            decorator.setProperty("your-property", value);
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}
```

### JavaParser Inspector Pattern

**When to use:** Source code analysis, structural patterns, code quality

```java
public class YourInspector extends AbstractJavaParserInspector {
    
    @Inject
    public YourInspector(ProjectFileRepository projectFileRepo,
                        ResourceResolver resolver) {
        super(projectFileRepo, resolver);
    }
    
    @Override
    protected void inspectCompilationUnit(CompilationUnit cu,
                                         JavaClassNode classNode,
                                         NodeDecorator<JavaClassNode> decorator) {
        // Your structural analysis
        cu.accept(new YourVisitor(decorator), null);
    }
}
```

### ClassLoader Inspector Pattern

**When to use:** Runtime type analysis, inheritance depth, interface counting

```java
public class YourInspector extends AbstractClassLoaderBasedInspector {
    
    @Inject
    public YourInspector(ProjectFileRepository projectFileRepo,
                        JARClassLoaderService classLoaderService) {
        super(projectFileRepo, classLoaderService);
    }
    
    @Override
    protected void inspectLoadedClass(Class<?> loadedClass,
                                     JavaClassNode classNode,
                                     NodeDecorator<JavaClassNode> decorator) {
        // Your runtime analysis
        int depth = calculateDepth(loadedClass);
        decorator.setProperty("inheritance-depth", depth);
    }
}
```

## Property & Tag Management

### Writing Properties

```java
// Type-safe property writing
decorator.setProperty("property-name", value);
decorator.setProperty("nested.property", value);  // Creates nesting

// Numeric properties
decorator.setProperty("count", 42);
decorator.setProperty("ratio", 0.85);

// Complex properties
decorator.setProperty("metadata", Map.of("key", "value"));
```

### Writing Tags

```java
// Enable tags
decorator.enableTag("tag-name");

// Common tag patterns
decorator.enableTag("ejb.session.stateless");
decorator.enableTag("jdbc.connection.leak-risk");
decorator.enableTag("pattern.dao");
```

### Tag Naming Conventions

- Use kebab-case: `ejb-stateless`, `jdbc-connection`
- Hierarchical: `ejb.session.stateless`, `pattern.dao.repository`
- Descriptive: `transaction.manual`, `resource-leak.connection`

## Testing Guidelines

### Test Structure

```java
@ExtendWith(MockitoExtension.class)
class YourInspectorTest {
    
    @Mock
    private ProjectFileRepository projectFileRepo;
    
    @Mock
    private ResourceResolver resourceResolver;
    
    private YourInspector inspector;
    
    @BeforeEach
    void setUp() {
        inspector = new YourInspector(projectFileRepo, resourceResolver);
    }
    
    @Test
    void shouldDetectPattern() {
        // Arrange
        JavaClassNode node = new JavaClassNode("com.example.TestClass");
        when(projectFileRepo.getOrCreateClass(anyString())).thenReturn(node);
        
        // Act
        inspector.inspect(testFile);
        
        // Assert
        assertThat(node.hasTag("expected-tag")).isTrue();
        assertThat(node.getProperty("expected-property")).isEqualTo(expectedValue);
    }
}
```

### Test Best Practices

1. **Use real test classes** in `src/test/resources/test_samples/`
2. **Verify both tags and properties** - don't just check tags
3. **Test edge cases** - empty methods, null values, complex patterns
4. **Use descriptive test names** - `shouldDetectDaoPatternWhenRepositoryAnnotationPresent`
5. **Validate full output** - check all properties and tags set by inspector

## Common Patterns

### XML Descriptor Parsing

**Example:** DeclarativeTransactionInspector pattern

```java
protected void parseXmlDescriptor(InputStream xmlStream, 
                                  NodeDecorator<JavaClassNode> decorator) {
    DocumentBuilder builder = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder();
    Document doc = builder.parse(xmlStream);
    
    NodeList nodes = doc.getElementsByTagName("transaction-attribute");
    // Parse and extract data
    decorator.setProperty("transaction-type", value);
}
```

### JDBC Pattern Detection

**Example:** JdbcDataAccessPatternInspector pattern

```java
@Override
public MethodVisitor visitMethod(...) {
    return new MethodVisitor(Opcodes.ASM9) {
        @Override
        public void visitMethodInsn(int opcode, String owner, 
                                   String name, String descriptor, 
                                   boolean isInterface) {
            if (isJdbcCall(owner, name)) {
                decorator.enableTag("jdbc.method." + name);
            }
        }
    };
}
```

### Multi-Approach Validation

**Example:** ClassLoader + ASM + JavaParser for comprehensive analysis

```java
// 1. Try ClassLoader (runtime)
try {
    Class<?> clazz = classLoader.loadClass(fqn);
    analyzeWithReflection(clazz);
} catch (ClassNotFoundException e) {
    // Fall back to ASM
    analyzeWithASM(classFile);
}

// 2. Always run ASM for bytecode details
analyzeWithASM(classFile);

// 3. If source available, use JavaParser for structure
if (sourceFile != null) {
    analyzeWithJavaParser(sourceFile);
}
```

## Migration Checklist

When migrating file-centric inspectors to class-centric:

- [ ] Create V2 file with new architecture
- [ ] Extend `AbstractASMClassInspector` or `AbstractJavaParserInspector`
- [ ] Update constructor injection (`ProjectFileRepository` + `ResourceResolver`)
- [ ] Implement `createClassVisitor()` with new signature
- [ ] Update property writing to use `NodeDecorator`
- [ ] Remove manual node creation logic
- [ ] Verify compilation
- [ ] Run tests (if available)
- [ ] Replace original file
- [ ] Delete V2 file
- [ ] Update documentation

## Troubleshooting

### Common Issues

**Inspector not running:**
- Check `@Component` annotation present
- Verify inspector registered in configuration
- Check dependency order in inspector graph

**Properties not appearing:**
- Ensure using `NodeDecorator.setProperty()` not direct node access
- Verify property name follows conventions
- Check export configuration includes your properties

**Tags not exported:**
- Tags must be enabled with `decorator.enableTag()`
- Verify tag name registered in system
- Check CSV export configuration

**Compilation errors:**
- Ensure correct base class (`AbstractASMClassInspector` vs file-centric)
- Verify injection parameters match constructor
- Check visitor method signatures match ASM API version

## Next Steps

1. **Complete Phase 2 remaining substages**
2. **Migrate additional inspectors** (52 remaining)
3. **Enhance test coverage** for migrated inspectors
4. **Performance optimization** for large codebases
5. **Documentation updates** as patterns evolve

## References

- [Inspector Specification](../spec/inspectors.md)
- [Analysis Specification](../spec/analyses.md)
- [Best Practices](patterns.md)
- [Archived Implementations](archived/)
