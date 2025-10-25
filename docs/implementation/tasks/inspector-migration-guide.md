# Inspector Migration Guide: NodeDecorator Refactoring

## Overview
This guide provides step-by-step instructions for migrating inspectors from the old `ProjectFileDecorator` signature to the new `NodeDecorator<T>` signature.

## Migration Goals

1. **Separate Properties from Tags**
   - Properties: Data and metrics (methodCount, complexity, etc.)
   - Tags: Boolean flags (EJB_DETECTED, METRICS_ANALYZED, etc.)

2. **Type-Safe Decorator**
   - Generic `NodeDecorator<T extends GraphNode>` works with any node type
   - Enforces proper usage of properties vs tags

3. **Cleaner API**
   - `enableTag(name)` / `disableTag(name)` for flags
   - `setProperty(name, value)` for data
   - Aggregation methods: `setMaxProperty()`, `orProperty()`, `andProperty()`

## Migration Patterns

### Pattern 1: Direct Inspector Implementation (Inspectors that directly implement `Inspector<ProjectFile>`)

#### Step 1: Update Import Statement
```java
// OLD
import com.analyzer.core.export.ProjectFileDecorator;

// NEW
import com.analyzer.core.export.NodeDecorator;
```

#### Step 2: Change Method Signature
```java
// OLD
@Override
public void decorate(ProjectFile projectFile, ProjectFileDecorator decorator) {
    // ...
}

// NEW
@Override
public void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator) {
    // ...
}
```

#### Step 3: Update Property/Tag Setting

**For Boolean Flags (Tags):**
```java
// OLD
projectFile.addTag("SOME_FLAG");
// or
decorator.setTag("SOME_FLAG", true);

// NEW
decorator.enableTag("SOME_FLAG");
```

**For Data/Metrics (Properties):**
```java
// OLD
projectFile.setProperty("methodCount", 42);
// or
decorator.setTag("methodCount", 42);

// NEW
decorator.setProperty("methodCount", 42);
```

**For Error Handling:**
```java
// OLD
projectFileDecorator.error("Error message");

// NEW
decorator.error("Error message");
```

**Remove notApplicable() calls:**
```java
// OLD
if (!condition) {
    decorator.notApplicable();
    return;
}

// NEW
if (!condition) {
    return;  // Just return, no need to mark as not applicable
}
```

### Pattern 2: AbstractProjectFileInspector Subclass

For inspectors extending `AbstractProjectFileInspector`, the signature is already correct. Only need to update the **parameter type** in the protected method:

#### Step 1: Update Protected Method Parameter
```java
// OLD
protected void analyzeProjectFile(ProjectFile file, ProjectFileDecorator decorator) {
    decorator.setTag("someTag", value);
}

// NEW
protected void analyzeProjectFile(ProjectFile file, NodeDecorator<ProjectFile> decorator) {
    decorator.setProperty("someProperty", value);  // For data
    // OR
    decorator.enableTag("someTag");  // For boolean flags
}
```

#### Step 2: Update Property/Tag Usage
- Change `decorator.setTag(name, value)` to either:
  - `decorator.setProperty(name, value)` if it's data/metrics
  - `decorator.enableTag(name)` if it's a boolean flag

## Decision Tree: Property vs Tag

```
Is this a boolean flag (presence/absence)?
├─ YES → Use decorator.enableTag("FLAG_NAME")
│         Examples: EJB_DETECTED, METRICS_ANALYZED, HIGH_COMPLEXITY
│
└─ NO → Is this data or a metric?
         ├─ YES → Use decorator.setProperty("propertyName", value)
         │         Examples: methodCount=42, complexity="HIGH", className="Foo"
         │
         └─ Unclear? → Use setProperty() (safer default)
```

## Complete Migration Examples

### Example 1: EntityBeanJavaSourceInspector (AbstractProjectFileInspector subclass)

```java
// BEFORE
protected void analyzeProjectFile(ProjectFile file, ProjectFileDecorator decorator) {
    decorator.setTag("EJB_ENTITY_BEAN", true);
    decorator.setTag("ejbType", "ENTITY");
    decorator.setTag("methodCount", 15);
}

// AFTER
protected void analyzeProjectFile(ProjectFile file, NodeDecorator<ProjectFile> decorator) {
    decorator.enableTag("EJB_ENTITY_BEAN");           // Boolean flag
    decorator.setProperty("ejbType", "ENTITY");       // Data
    decorator.setProperty("methodCount", 15);         // Metric
}
```

### Example 2: FileMetricsInspector (Direct Inspector implementation)

```java
// BEFORE
@Override
public void decorate(ProjectFile file, ProjectFileDecorator decorator) {
    long size = Files.size(file.getFilePath());
    decorator.setTag("fileSize", size);
    decorator.setTag("linesOfCode", lineCount);
}

// AFTER
@Override
public void inspect(ProjectFile file, NodeDecorator<ProjectFile> decorator) {
    long size = Files.size(file.getFilePath());
    decorator.setProperty("fileSize", size);      // Metrics are properties
    decorator.setProperty("linesOfCode", lineCount);
}
```

### Example 3: Using Aggregation Methods

```java
// For maximum values across multiple inspectors
decorator.setMaxProperty("maxComplexity", 50);

// For boolean OR (any inspector sets true = stays true)
decorator.orProperty("hasIssues", true);

// For boolean AND (all must be true)
decorator.andProperty("fullyValid", isValid);

// For complexity levels (NONE < LOW < MEDIUM < HIGH < CRITICAL)
decorator.setMaxComplexityProperty("overallComplexity", "HIGH");
```

## Common Pitfalls

### ❌ Don't: Set properties directly on ProjectFile
```java
// WRONG
projectFile.setProperty("methodCount", 42);
projectFile.addTag("SOME_FLAG");
```

### ✅ Do: Use the decorator
```java
// CORRECT
decorator.setProperty("methodCount", 42);
decorator.enableTag("SOME_FLAG");
```

### ❌ Don't: Mix tags and properties
```java
// WRONG - methodCount is data, not a flag
decorator.enableTag("methodCount");
```

### ✅ Do: Use appropriate method for data type
```java
// CORRECT
decorator.setProperty("methodCount", 42);  // Data
decorator.enableTag("METRICS_CALCULATED"); // Flag
```

## Batch Migration Strategy

### Phase 1: Direct Inspector Implementations (9 files) - IN PROGRESS
Update inspectors that directly implement `Inspector<ProjectFile>`:
- [x] FileMetricsInspector
- [x] JavaVersionInspector
- [x] JavaSourceFileDetector
- [x] LegacyFrameworkDetector
- [x] FileExtensionDetector
- [x] SourceFileTagDetector
- [ ] ApplicationServerConfigDetector
- [ ] EjbDeploymentDescriptorDetector
- [ ] FilenameInspector

### Phase 2: AbstractProjectFileInspector Subclasses (~60 files)
Update protected method parameter in all subclasses:
- Search pattern: `protected void analyzeProjectFile\(ProjectFile \w+, ProjectFileDecorator`
- Replace with: `protected void analyzeProjectFile(ProjectFile \w+, NodeDecorator<ProjectFile>`
- Update all `decorator.setTag()` calls appropriately

### Phase 3: ASM/Binary Inspectors
These will eventually extend `AbstractBinaryClassNodeInspector` and work with `JavaClassNode`:
- Will be migrated as part of class-centric refactoring
- For now, update to compile with current architecture

## Automated Migration Script (Conceptual)

```bash
# Step 1: Update imports
find src/main/java -name "*.java" -exec sed -i 's/import com.analyzer.core.export.ProjectFileDecorator/import com.analyzer.core.export.NodeDecorator/g' {} \;

# Step 2: Update method signatures (direct implementations)
find src/main/java -name "*.java" -exec sed -i 's/public void decorate(ProjectFile \([a-zA-Z]*\), ProjectFileDecorator/public void inspect(ProjectFile \1, NodeDecorator<ProjectFile>/g' {} \;

# Step 3: Update method signatures (AbstractProjectFileInspector subclasses)
find src/main/java -name "*.java" -exec sed -i 's/protected void analyzeProjectFile(ProjectFile \([a-zA-Z]*\), ProjectFileDecorator/protected void analyzeProjectFile(ProjectFile \1, NodeDecorator<ProjectFile>/g' {} \;

# Step 4: Update tag setting (requires manual review)
# decorator.setTag("name", value) → determine if property or tag
```

## Verification Checklist

After migrating an inspector:
- [ ] Import statement updated to `NodeDecorator`
- [ ] Method signature changed to `inspect()` or parameter updated
- [ ] All `decorator.setTag()` reviewed and changed to:
  - `decorator.setProperty()` for data/metrics
  - `decorator.enableTag()` for boolean flags
- [ ] All `decorator.error()` calls preserved
- [ ] No `projectFile.setProperty()` or `projectFile.addTag()` calls remain
- [ ] Inspector compiles without errors

## Testing After Migration

1. **Compile:** `mvn compile -DskipTests`
2. **Run Tests:** `mvn test`
3. **Verify Output:** Check that properties and tags are correctly set
4. **Check Logs:** Ensure no new errors in inspector execution

## Roll-back Strategy

If issues arise:
1. Keep `ProjectFileDecorator` class temporarily
2. Create adapter: `NodeDecorator` → `ProjectFileDecorator`
3. Gradually migrate once adapter is proven
4. Remove adapter once all inspectors migrated

## Additional Resources

- **Core Files:**
  - `src/main/java/com/analyzer/core/export/NodeDecorator.java` - New decorator implementation
  - `src/main/java/com/analyzer/core/inspector/Inspector.java` - Updated interface
  - `src/main/java/com/analyzer/inspectors/core/AbstractProjectFileInspector.java` - Base class

- **Example Migrations:**
  - `src/main/java/com/analyzer/rules/metrics/FileMetricsInspector.java`
  - `src/main/java/com/analyzer/inspectors/core/detection/JavaSourceFileDetector.java`
  - `src/main/java/com/analyzer/rules/graph/JavaImportGraphInspector.java`
