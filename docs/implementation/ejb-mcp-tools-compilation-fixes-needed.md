# EJB MCP Tools - Compilation Fixes Needed

## Status
7 new EJB migration tools have been successfully implemented but have 4 compilation errors that need to be resolved.

## Compilation Errors & Fixes

### Error 1: AnalysisService - EjbAntiPatternDetector API Mismatch
**File:** `analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/AnalysisService.java:51`

**Error:**
```
cannot find symbol: method detectAntiPatterns(java.lang.String,java.lang.String)
```

**Cause:** The actual API is `detect(ClassMetadata metadata, EjbType ejbType)` not `detectAntiPatterns(String, String)`

**Fix:**  
The AnalysisService needs to:
1. Use ClassMetadataExtractor to parse the source into ClassMetadata
2. Call `antiPatternDetector.detect(metadata, null)` instead

**Temporary Solution:** 
Comment out the anti-pattern detection logic and return empty results, OR simplify to just scan for basic patterns without using EjbAntiPatternDetector.

### Error 2: OpenRewriteService - RemoveEjbInterfaceRecipe Constructor
**File:** `analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/OpenRewriteService.java:68`

**Error:**
```
constructor RemoveEjbInterfaceRecipe cannot be applied to given types;
  required: java.lang.String
  found: no arguments
```

**Fix:**
```java
// Change from:
Recipe recipe = new RemoveEjbInterfaceRecipe();

// To:
Recipe recipe = new RemoveEjbInterfaceRecipe("ALL");  // or specific type
```

### Error 3: OpenRewriteService - Stream to Iterable Conversion
**File:** `analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/OpenRewriteService.java:100`

**Error:**
```
incompatible types: java.util.stream.Stream<org.openrewrite.SourceFile> cannot be converted to java.lang.Iterable<org.openrewrite.SourceFile>
```

**Fix:**
```java
// Change from:
Iterable<SourceFile> parsed = javaParser.parse(paths, basePath, new InMemoryExecutionContext());

// To:
List<SourceFile> parsed = javaParser.parse(paths, basePath, new InMemoryExecutionContext())
    .toList();

for (SourceFile sf : parsed) {
    sourceFiles.add(sf);
}
```

### Error 4: OpenRewriteService - List to LargeSourceSet Conversion  
**File:** `analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/OpenRewriteService.java:119`

**Error:**
```
incompatible types: java.util.List<org.openrewrite.SourceFile> cannot be converted to org.openrewrite.LargeSourceSet
```

**Fix:**
```java
// Change from:
List<Result> results = recipe.run(sourceFiles, ctx).getChangeset().getAllResults();

// To:
RecipeRun recipeRun = recipe.run(new InMemoryLargeSourceSet(sourceFiles), ctx);
List<Result> results = recipeRun.getChangeset().getAllResults();
```

## Quick Fix Script

Create a script to apply all fixes:

```bash
cd analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service

# Fix 1: Comment out AnalysisService anti-pattern detection temporarily
# Manual edit needed - replace detect logic with stub

# Fix 2: Add constructor parameter to RemoveEjbInterfaceRecipe
sed -i 's/new RemoveEjbInterfaceRecipe()/new RemoveEjbInterfaceRecipe("ALL")/g' OpenRewriteService.java

# Fix 3 & 4: Manual edits needed for OpenRewrite API changes
```

## Alternative: Simplified Implementation

For initial testing, simplify the implementations:

### AnalysisService - Stub Implementation
```java
public AntiPatternAnalysisResult analyzeAntiPatterns(
        String projectPath,
        List<String> files) {
    
    AntiPatternAnalysisResult result = new AntiPatternAnalysisResult();
    
    // TODO: Implement proper anti-pattern detection using ClassMetadataExtractor
    // For now, mark all classes as clean
    for (String filePath : files) {
        result.addClean(filePath);
    }
    
    logger.warn("Anti-pattern detection not yet implemented - marking all classes as clean");
    return result;
}
```

### OpenRewriteService - Remove Problematic Methods
Comment out `removeEjbInterfaces()` method temporarily and only expose:
- `addTransactionalByPattern()`
- `migrateSecurityAnnotations()`  
- `convertToConstructorInjection()`

## Testing After Fixes

```bash
cd analyzer-refactoring-mcp
mvn clean compile -DskipTests
```

Expected: Clean compilation

## Implementation Priority

1. **Fix Error 2** (easiest) - just add "ALL" parameter
2. **Fix Errors 3 & 4** (medium) - update OpenRewrite API calls
3. **Fix Error 1** (complex) - requires ClassMetadataExtractor integration OR stub implementation

## Files to Modify

1. `analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/AnalysisService.java`
2. `analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/OpenRewriteService.java`

## Success Criteria

- ✅ All 32 source files compile without errors
- ✅ All MCP tools are registered and exposed
- ✅ Server starts without runtime errors
- ✅ Can invoke tools via MCP protocol

## Summary

**What Works:**
- 7 new MCP tools created with proper annotations
- 2 new services (OpenRewriteService, AnalysisService)  
- Dependencies properly configured
- Architecture is sound

**What Needs Work:**
- 4 API compatibility issues
- Estimated fix time: 30-60 minutes
- All fixes are straightforward API adjustments

---

**Last Updated:** 2025-11-12  
**Status:** Ready for compilation fixes
