# LocalCache Feature Implementation

## Overview

The LocalCache feature optimizes the Java Architecture Analyzer by caching expensive operations that are repeatedly performed on the same resource during multi-pass analysis. This eliminates redundant file I/O, parsing, and bytecode reading operations.

## Problem Statement

During multi-pass analysis, multiple inspectors process the same ProjectFile or JavaClassNode. This resulted in:
- Reading the same `.class` file multiple times
- Parsing the same Java source file with JavaParser multiple times  
- Loading bytecode with ASM ClassReader repeatedly
- Re-reading source file content multiple times
- Re-creating ResourceLocation objects repeatedly

## Solution: Per-Item LocalCache

A `LocalCache` bean is registered in PicoContainer's analysis container that:
- Lives for the duration of processing a single ProjectFile or JavaClassNode
- Caches expensive operations (file reading, parsing, bytecode loading)
- Is automatically reset before analyzing each new item
- Is injected into abstract inspector implementations via constructor injection

## Architecture

### Core Components

#### 1. LocalCache Class (`analyzer-core/src/main/java/com/analyzer/core/cache/LocalCache.java`)

**Purpose:** Provides caching of commonly accessed resources during item analysis.

**Key Methods:**
```java
// Cache CompilationUnit (JavaParser results)
CompilationUnit getOrParseCompilationUnit(Supplier<CompilationUnit> parser)

// Cache binary class bytes
byte[] getOrLoadClassBytes(Supplier<byte[]> loader)

// Cache ASM ClassReader
ClassReader getOrCreateClassReader(Supplier<ClassReader> creator)

// Cache source file content
String getOrLoadSourceContent(Supplier<String> loader)

// Cache ResourceLocation
Object getOrResolveLocation(Supplier<Object> resolver)

// Extension point for custom caching
<T> T getOrCompute(String key, Supplier<T> supplier)

// Reset all caches (called before each item)
void reset()
```

**Design Pattern:** Hybrid storage approach
- **Type-safe dedicated fields** for common types (5 core types)
- **Map-based extension point** for custom caching needs

#### 2. PicoContainer Registration (`PicoContainerConfig.java`)

```java
// Register LocalCache in analysis container
boolean cacheEnabled = Boolean.parseBoolean(
    System.getProperty("analyzer.cache.enabled", "true"));
container.addComponent(LocalCache.class, new LocalCache(cacheEnabled));
```

#### 3. Cache Reset Integration (`AnalysisEngine.java`)

The cache is automatically reset before analyzing each item:

```java
private Set<String> analyzeProjectFileWithTrackingAndCollection(...) {
    localCache.reset();  // Reset cache for this item
    // ... analysis logic
}

private Set<String> analyzeClassNodeWithTracking(...) {
    localCache.reset();  // Reset cache for this item
    // ... analysis logic
}
```

### Abstract Inspector Updates

#### AbstractJavaParserInspector
**Updated to:**
- Accept `LocalCache` in constructor
- Cache `CompilationUnit` to avoid re-parsing

```java
protected AbstractJavaParserInspector(ResourceResolver resourceResolver, LocalCache localCache) {
    super(resourceResolver, localCache);
    this.javaParser = new JavaParser();
    this.localCache = localCache;
}

@Override
protected final void analyzeSourceFile(...) {
    CompilationUnit cu = localCache.getOrParseCompilationUnit(() -> {
        String content = readFileContent(sourceLocation);
        return javaParser.parse(content).getResult().orElse(null);
    });
    // ... use cached CompilationUnit
}
```

#### AbstractSourceFileInspector
**Updated to:**
- Accept `LocalCache` in constructor
- Cache `ResourceLocation` and source content

```java
protected AbstractSourceFileInspector(ResourceResolver resourceResolver, LocalCache localCache) {
    this.resourceResolver = resourceResolver;
    this.localCache = localCache;
}

protected String readFileContent(ResourceLocation sourceLocation) {
    return localCache.getOrLoadSourceContent(() -> {
        // ... load and return content
    });
}
```

#### AbstractASMClassInspector
**Updated to:**
- Accept `LocalCache` in constructor  
- Cache class bytes and `ClassReader`

```java
protected AbstractASMClassInspector(ProjectFileRepository projectFileRepository,
        ResourceResolver resourceResolver, LocalCache localCache) {
    super(projectFileRepository);
    this.resourceResolver = resourceResolver;
    this.localCache = localCache;
}

protected void analyzeClassWithASM(...) {
    byte[] classBytes = localCache.getOrLoadClassBytes(() -> 
        classInputStream.readAllBytes());
    
    ClassReader classReader = localCache.getOrCreateClassReader(() -> 
        new ClassReader(classBytes));
    // ... use cached ClassReader
}
```

#### AbstractBinaryClassInspector
**Updated to:**
- Accept `LocalCache` in constructor
- Cache binary file bytes

```java
protected AbstractBinaryClassInspector(ResourceResolver resourceResolver, LocalCache localCache) {
    this.resourceResolver = resourceResolver;
    this.localCache = localCache;
}
```

## Configuration

### application.properties

```properties
# Enable per-item caching to optimize repeated access to the same resources
# Set to false for debugging or troubleshooting cache-related issues
analyzer.cache.enabled=true
```

### Command-line Override

```bash
# Disable caching
java -Danalyzer.cache.enabled=false -jar analyzer-app.jar

# Enable caching (default)
java -Danalyzer.cache.enabled=true -jar analyzer-app.jar
```

## Migration Guide for Concrete Inspectors

### Step 1: Update Constructor Signature

**Before:**
```java
@Inject
protected MyInspector(ResourceResolver resourceResolver) {
    super(resourceResolver);
}
```

**After:**
```java
@Inject
protected MyInspector(ResourceResolver resourceResolver, LocalCache localCache) {
    super(resourceResolver, localCache);
}
```

### Step 2: Handle Different Parent Classes

#### For inspectors extending AbstractJavaParserInspector:
```java
@Inject
protected MyJavaParserInspector(ResourceResolver resourceResolver, LocalCache localCache) {
    super(resourceResolver, localCache);
}
```

#### For inspectors extending AbstractASMClassInspector:
```java
@Inject
protected MyASMInspector(ProjectFileRepository repository, 
                         ResourceResolver resourceResolver,
                         LocalCache localCache) {
    super(repository, resourceResolver, localCache);
}
```

#### For inspectors extending AbstractSourceFileInspector:
```java
@Inject
protected MySourceInspector(ResourceResolver resourceResolver, LocalCache localCache) {
    super(resourceResolver, localCache);
}
```

#### For inspectors extending AbstractBinaryClassInspector:
```java
@Inject
protected MyBinaryInspector(ResourceResolver resourceResolver, LocalCache localCache) {
    super(resourceResolver, localCache);
}
```

### Step 3: Leverage Custom Caching (Optional)

If your inspector has additional caching needs:

```java
// In your inspector implementation
String customData = localCache.getOrCompute("my-cache-key", () -> {
    // Expensive computation
    return computeExpensiveData();
});
```

## Files Modified

### Core Implementation
1. `analyzer-core/src/main/java/com/analyzer/core/cache/LocalCache.java` (NEW)
2. `analyzer-core/src/main/java/com/analyzer/core/inspector/PicoContainerConfig.java`
3. `analyzer-core/src/main/java/com/analyzer/core/engine/AnalysisEngine.java`
4. `analyzer-app/src/main/resources/application.properties`

### Abstract Inspectors
1. `analyzer-inspectors/src/main/java/com/analyzer/dev/inspectors/source/AbstractJavaParserInspector.java`
2. `analyzer-inspectors/src/main/java/com/analyzer/dev/inspectors/source/AbstractSourceFileInspector.java`
3. `analyzer-inspectors/src/main/java/com/analyzer/dev/inspectors/binary/AbstractASMClassInspector.java`
4. `analyzer-inspectors/src/main/java/com/analyzer/dev/inspectors/binary/AbstractBinaryClassInspector.java`

### Concrete Inspectors (Need Migration)
The following concrete inspectors need to be updated to accept LocalCache:

**ASM-based inspectors:**
- BinaryClassFQNInspectorV2
- ClassMetricsInspectorV2
- BinaryJavaClassNodeInspectorV2
- BinaryClassCouplingGraphInspector
- TypeInspectorASMInspectorV2
- MethodCountInspectorV2

**JavaParser-based inspectors:**
- SourceJavaClassNodeInspector
- JavaSourceFileInspector
- AbstractJavaClassInspector
- CyclomaticComplexityInspector

**Source file inspectors:**
- AbstractTextFileInspector
- AbstractRegExpFileInspector (2 constructors)
- AbstractRoasterInspector
- ClocInspector
- AbstractCountRegexpInspector (2 constructors)
- AbstractSonarParserInspector

**Binary inspectors:**
- AbstractJavassistInspectorAbstract (2 constructors)
- AbstractBCELInspectorAbstract
- AbstractBinaryClassNodeInspector

## Benefits

### Performance Improvements
- **Reduced I/O:** File reading happens once per item instead of per inspector
- **Reduced CPU:** Parsing and bytecode loading happen once per item
- **Memory Efficiency:** Cache is cleared between items preventing memory bloat

### Expected Impact
For a typical project with 10 inspectors per file:
- **File I/O:** 90% reduction (1 read instead of 10)
- **Parse Operations:** 90% reduction (1 parse instead of 10)
- **Bytecode Loading:** 90% reduction (1 load instead of 10)

## Testing

### With Cache Enabled (Default)
```bash
mvn clean install
./analyzer-app/target/analyzer-app analyze /path/to/project
```

### With Cache Disabled (For Debugging)
```bash
./analyzer-app/target/analyzer-app analyze /path/to/project -Danalyzer.cache.enabled=false
```

### Verification
Monitor log output for cache-related trace messages:
```
TRACE - CompilationUnit parsed and cached
TRACE - CompilationUnit retrieved from cache
TRACE - Class bytes loaded and cached (12345 bytes)
TRACE - LocalCache reset
```

## Known Limitations

1. **Per-item scope:** Cache only lives for one ProjectFile or JavaClassNode
2. **No cross-item caching:** Different items don't share cache (by design)
3. **Memory usage:** Large files consume more memory temporarily
4. **Configuration:** Only global on/off switch (no per-inspector control)

## Future Enhancements

1. **Cache Statistics:** Track hit/miss rates for performance monitoring
2. **Selective Caching:** Per-inspector cache control
3. **Cache Size Limits:** Configurable maximum cache size
4. **Cache Preloading:** Preload common resources in batch

## References

- Original Request: User identified redundant I/O in MultiPassExecutor
- Design Document: This document
- Implementation: See modified files listed above
