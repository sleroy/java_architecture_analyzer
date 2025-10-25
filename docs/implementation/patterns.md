# Design Patterns & Best Practices

**Last Updated:** October 21, 2025

## Inspector Development Patterns

### 1. Class-Centric Architecture

**Core Principle:** Inspectors receive pre-created `JavaClassNode` instances and write directly to them via `NodeDecorator`.

**Benefits:**
- Eliminates manual node creation
- Type-safe property access
- Cleaner separation of concerns
- Easier testing and mocking

**Template:**
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
        return new Visitor(classNode, decorator);
    }
}
```

### 2. Dependency Injection Pattern

**Use PicoContainer's `@Inject` annotation:**
```java
@Inject
public YourInspector(ProjectFileRepository projectFileRepo,
                     ResourceResolver resolver,
                     OptionalDependency optional) {
    super(projectFileRepo, resolver);
    this.optional = optional;
}
```

**Common Dependencies:**
- `ProjectFileRepository` - Required for class-centric inspectors
- `ResourceResolver` - File and resource access
- `JARClassLoaderService` - Runtime class loading
- `ClassNodeRepository` - Direct node access (legacy)

### 3. Visitor Pattern (ASM)

**Structured Analysis:**
```java
private static class Visitor extends ASMClassNodeVisitor {
    private final NodeDecorator<JavaClassNode> decorator;
    
    @Override
    public MethodVisitor visitMethod(...) {
        return new MethodAnalyzer();
    }
    
    private class MethodAnalyzer extends MethodVisitor {
        @Override
        public void visitMethodInsn(...) {
            // Analyze method calls
            if (matchesPattern(owner, name)) {
                decorator.enableTag("pattern-detected");
            }
        }
    }
}
```

### 4. Property Nesting Pattern

**Hierarchical Properties:**
```java
// Flat properties
decorator.setProperty("count", 5);

// Nested properties (auto-creates structure)
decorator.setProperty("ejb.session.type", "stateless");
decorator.setProperty("ejb.transaction.type", "required");

// Results in:
// {
//   "count": 5,
//   "ejb": {
//     "session": {"type": "stateless"},
//     "transaction": {"type": "required"}
//   }
// }
```

### 5. Multi-Strategy Analysis Pattern

**Combine multiple analysis approaches:**
```java
protected void analyzeClass(JavaClassNode node, 
                           NodeDecorator<JavaClassNode> decorator) {
    // Strategy 1: Runtime reflection (most accurate)
    try {
        Class<?> clazz = loadClass(node.getFqn());
        analyzeWithReflection(clazz, decorator);
    } catch (ClassNotFoundException e) {
        // Strategy 2: ASM bytecode (fallback)
        analyzeWithASM(node, decorator);
    }
    
    // Strategy 3: Source code (additional context)
    if (hasSourceCode(node)) {
        analyzeWithJavaParser(node, decorator);
    }
}
```

## Testing Patterns

### 1. Test Sample Organization

**Directory Structure:**
```
src/test/resources/test_samples/
  ├── ejb/
  │   ├── StatelessBean.java
  │   └── StatefulBean.java
  ├── jdbc/
  │   ├── DaoWithConnection.java
  │   └── DaoWithLeaks.java
  └── patterns/
      ├── FactoryPattern.java
      └── SingletonPattern.java
```

### 2. Comprehensive Test Pattern

```java
@Test
void shouldDetectEjbStatelessBean() {
    // Arrange
    ProjectFile testFile = createTestFile("ejb/StatelessBean.java");
    JavaClassNode node = new JavaClassNode("com.example.StatelessBean");
    when(projectFileRepo.getOrCreateClass(anyString())).thenReturn(node);
    
    // Act
    inspector.inspect(testFile);
    
    // Assert - Multiple aspects
    assertThat(node.hasTag("ejb.session.stateless"))
        .as("Should detect @Stateless annotation")
        .isTrue();
    assertThat(node.getProperty("ejb.session.type"))
        .as("Should set session type property")
        .isEqualTo("stateless");
    assertThat(node.getProperty("migration.recommendation"))
        .as("Should provide migration guidance")
        .isEqualTo("Convert to @Service with @Transactional");
}
```

### 3. Parameterized Test Pattern

```java
@ParameterizedTest
@CsvSource({
    "StatelessBean.java, ejb.session.stateless, stateless",
    "StatefulBean.java, ejb.session.stateful, stateful",
    "MessageDrivenBean.java, ejb.mdb, message-driven"
})
void shouldDetectEjbTypes(String fileName, String expectedTag, String expectedType) {
    // Arrange, Act, Assert
}
```

## Code Organization Patterns

### 1. Inner Class Organization

```java
public class ComplexInspector extends AbstractASMClassInspector {
    
    // Main inspector logic
    @Override
    protected ASMClassNodeVisitor createClassVisitor(...) {
        return new ClassAnalyzer(...);
    }
    
    // Visitor implementations
    private static class ClassAnalyzer extends ASMClassNodeVisitor {
        @Override
        public MethodVisitor visitMethod(...) {
            return new MethodAnalyzer();
        }
    }
    
    private static class MethodAnalyzer extends MethodVisitor {
        // Method analysis logic
    }
    
    // Data classes (POJOs)
    private static class AnalysisResult {
        private final String type;
        private final int complexity;
        // ... getters
    }
}
```

### 2. Metadata Caching Pattern

**For expensive operations:**
```java
public class TypeInspector extends AbstractASMClassInspector {
    private static final Map<String, TypeInfo> TYPE_CACHE = new ConcurrentHashMap<>();
    
    private TypeInfo getTypeInfo(String fqn) {
        return TYPE_CACHE.computeIfAbsent(fqn, this::analyzeType);
    }
    
    private TypeInfo analyzeType(String fqn) {
        // Expensive type analysis
        return new TypeInfo(...);
    }
}
```

### 3. Constant Management

```java
public class JdbcInspector extends AbstractASMClassInspector {
    // Method signatures
    private static final String JDBC_CONNECTION = "java/sql/Connection";
    private static final String JDBC_STATEMENT = "java/sql/Statement";
    
    // Tag names
    private static final String TAG_JDBC_USAGE = "jdbc.usage";
    private static final String TAG_RESOURCE_LEAK = "jdbc.resource-leak";
    
    // Property names
    private static final String PROP_CONNECTION_COUNT = "jdbc.connection.count";
}
```

## Performance Patterns

### 1. Lazy Initialization

```java
public class HeavyInspector extends AbstractASMClassInspector {
    private volatile ClassLoader cachedClassLoader;
    
    private ClassLoader getClassLoader() {
        if (cachedClassLoader == null) {
            synchronized (this) {
                if (cachedClassLoader == null) {
                    cachedClassLoader = createClassLoader();
                }
            }
        }
        return cachedClassLoader;
    }
}
```

### 2. Early Exit Pattern

```java
@Override
public void visit(int version, int access, String name, ...) {
    // Early exit for interfaces
    if ((access & Opcodes.ACC_INTERFACE) != 0) {
        return;
    }
    
    // Early exit for abstract classes
    if ((access & Opcodes.ACC_ABSTRACT) != 0) {
        return;
    }
    
    // Continue with analysis
    super.visit(version, access, name, ...);
}
```

### 3. Batch Processing Pattern

```java
@Override
protected void finalizeInspection() {
    // Process accumulated data in batch
    accumulatedData.forEach(this::processItem);
    accumulatedData.clear();
}
```

## Error Handling Patterns

### 1. Graceful Degradation

```java
protected void analyzeClass(JavaClassNode node, NodeDecorator<JavaClassNode> decorator) {
    try {
        // Preferred approach
        performDetailedAnalysis(node, decorator);
    } catch (DetailedAnalysisException e) {
        logger.warn("Detailed analysis failed, using basic analysis", e);
        performBasicAnalysis(node, decorator);
    }
}
```

### 2. Resource Management

```java
@Override
public void inspect(ProjectFile file) {
    try (InputStream is = resourceResolver.getInputStream(file.getPath())) {
        ClassReader reader = new ClassReader(is);
        reader.accept(visitor, 0);
    } catch (IOException e) {
        logger.error("Failed to inspect file: " + file.getPath(), e);
    }
}
```

### 3. Validation Pattern

```java
private void validateAndSetProperty(String key, Object value, NodeDecorator<?> decorator) {
    if (value == null) {
        logger.warn("Null value for property: " + key);
        return;
    }
    
    if (key == null || key.trim().isEmpty()) {
        logger.warn("Invalid property key");
        return;
    }
    
    decorator.setProperty(key, value);
}
```

## Migration Patterns

### 1. V2 Development Pattern

**When migrating file-centric to class-centric:**

1. Create `YourInspectorV2.java` with new architecture
2. Test and verify V2 works correctly
3. Copy V2 content to original file
4. Remove "V2" references in class name
5. Delete V2 file

### 2. Parallel Implementation

**During migration, maintain both versions briefly:**
```
YourInspector.java (old, file-centric)
YourInspectorV2.java (new, class-centric)
```

**Never commit V2 files - they're temporary working files**

### 3. Incremental Migration

**Migrate one inspector at a time:**
- Reduces risk
- Easier to track issues
- Maintains compilation state
- Allows rollback if needed

## Anti-Patterns to Avoid

### ❌ Manual Node Creation

```java
// DON'T: Create nodes manually in class-centric inspectors
JavaClassNode node = new JavaClassNode(fqn);
classNodeRepository.save(node);
```

### ❌ Direct Property Access

```java
// DON'T: Bypass NodeDecorator
node.getProperties().put("key", value);

// DO: Use NodeDecorator
decorator.setProperty("key", value);
```

### ❌ Hardcoded Paths

```java
// DON'T: Hardcode file paths
File file = new File("/absolute/path/to/file.java");

// DO: Use ResourceResolver
InputStream is = resourceResolver.getInputStream(relativePath);
```

### ❌ Ignoring Test Failures

```java
// DON'T: Comment out failing assertions
// assertThat(node.hasTag("expected")).isTrue();

// DO: Fix the underlying issue
assertThat(node.hasTag("expected"))
    .as("Should detect pattern in test case")
    .isTrue();
```

### ❌ Over-complicated Logic

```java
// DON'T: Complex nested conditions
if (condition1) {
    if (condition2) {
        if (condition3) {
            doSomething();
        }
    }
}

// DO: Early returns
if (!condition1) return;
if (!condition2) return;
if (!condition3) return;
doSomething();
```

## Documentation Patterns

### 1. Inspector Javadoc

```java
/**
 * Detects EJB stateless session beans and provides migration recommendations.
 * 
 * <p>This inspector analyzes:
 * <ul>
 *   <li>@Stateless annotations (EJB 3.x)</li>
 *   <li>SessionBean interface implementations (EJB 2.x)</li>
 *   <li>ejb-jar.xml descriptors</li>
 * </ul>
 *
 * <p>Tags emitted:
 * <ul>
 *   <li>ejb.session.stateless</li>
 *   <li>ejb.version.3x or ejb.version.2x</li>
 * </ul>
 *
 * <p>Properties set:
 * <ul>
 *   <li>ejb.session.type = "stateless"</li>
 *   <li>migration.recommendation = "Convert to @Service"</li>
 * </ul>
 */
public class StatelessBeanInspector extends AbstractASMClassInspector {
```

### 2. Complex Method Documentation

```java
/**
 * Analyzes method bytecode to detect JDBC transaction patterns.
 * 
 * @param method The method visitor
 * @param owner The class containing the method
 * @param name The method name
 * @return MethodVisitor for continued analysis, or null to skip
 */
private MethodVisitor analyzeTransactionPattern(
        MethodVisitor method, String owner, String name) {
```

## Summary

Key takeaways:
1. **Always use class-centric architecture** for new inspectors
2. **Leverage NodeDecorator** for type-safe property access
3. **Write comprehensive tests** with real test samples
4. **Follow naming conventions** for tags and properties
5. **Handle errors gracefully** with fallback strategies
6. **Document thoroughly** for future maintenance
7. **Avoid anti-patterns** that reduce code quality
8. **Migrate incrementally** with V2 temporary files

These patterns have proven successful across 146+ tests with 100% pass rate.
