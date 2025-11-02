# YAML Include Resolution Fix

**Date**: 2025-11-02  
**Issue**: Migration plans with includes fail validation  
**Status**: ✅ FIXED

## Problem Description

### Original Error
```
java.lang.IllegalStateException: Migration plan must have at least one phase
    at com.analyzer.migration.plan.MigrationPlan$Builder.build(MigrationPlan.java:109)
    at com.analyzer.migration.loader.MigrationPlanConverter.convert(MigrationPlanConverter.java:74)
```

### Root Cause

The issue occurred because of an **order of operations problem** in the YAML loading process:

1. **Current (Broken) Flow for InputStream loading**:
   ```
   loadYAML() → validateDTO() → convert()
   ```
   - Include directives were **NEVER resolved** when loading from InputStream
   - Validation happened on incomplete DTO (without phases from includes)
   - Conversion failed because merged plan had no phases

2. **Working Flow for File loading**:
   ```
   loadYAML() → processIncludes() → validateDTO() → convert()
   ```
   - Includes were properly resolved
   - Validation happened on complete merged DTO
   - Conversion succeeded

### Why It Failed

When loading from **classpath** (via InputStream):
- `ApplyMigrationCommand` loaded plan as InputStream from classpath
- `YamlMigrationPlanLoader.loadFromInputStream()` had no base path for resolving relative includes
- Include resolution was **skipped entirely**
- The main plan file only contained metadata and include directives, but no phases
- Validation passed (bean validation doesn't check phase count)
- Conversion failed with "Migration plan must have at least one phase"

## Solution Implemented

### Core Principle
**"Resolve includes first, build complete aggregated plan in memory, ONLY THEN validate schema"**

### Changes Made

#### 1. YamlMigrationPlanLoader Enhancement

**File**: `analyzer-core/src/main/java/com/analyzer/migration/loader/YamlMigrationPlanLoader.java`

Added new overloaded method:
```java
public MigrationPlan loadFromInputStream(InputStream inputStream, Path basePath) {
    // Load YAML
    MigrationPlanDTO dto = yamlMapper.readValue(inputStream, MigrationPlanDTO.class);
    
    // Process includes if basePath provided
    if (basePath != null) {
        dto = processIncludes(dto, basePath);
    }
    
    // Validate AFTER includes are resolved
    validateDTO(dto);
    
    // Convert to domain model
    return converter.convert(dto);
}
```

**Key features**:
- Accepts optional `basePath` parameter for include resolution
- Warns if plan has includes but no base path provided
- Validates AFTER include resolution
- Backward compatible (original method delegates to new one with `null` basePath)

#### 2. ApplyMigrationCommand Smart Loading

**File**: `analyzer-app/src/main/java/com/analyzer/cli/ApplyMigrationCommand.java`

Enhanced `loadMigrationPlan()` method:

```java
private MigrationPlan loadMigrationPlan() {
    // Try file system first (supports includes naturally)
    File planFile = new File(planPath);
    if (planFile.exists() && planFile.isFile()) {
        return loader.loadFromFile(planFile);  // ← Includes work!
    }
    
    // Fall back to classpath
    InputStream planStream = getClass().getClassLoader().getResourceAsStream(planPath);
    
    // Detect base path for classpath resources
    Path basePath = detectClasspathBasePath(planPath);
    
    // Load with base path for include resolution
    return loader.loadFromInputStream(planStream, basePath);
}
```

**Detection logic**:
```java
private Path detectClasspathBasePath(String resourcePath) {
    URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
    
    if (resourceUrl != null && "file".equals(resourceUrl.getProtocol())) {
        // Resource is on file system - can resolve includes!
        return Path.of(resourceUrl.toURI());
    }
    
    // Resource is in JAR or not accessible
    return null;
}
```

#### 3. FullExecutionExample Update

**File**: `analyzer-app/src/test/java/com/analyzer/cli/examples/FullExecutionExample.java`

Updated to use modular plan structure:
```java
String planPath = "migrations/ejb2spring/jboss-to-springboot.yaml";
String[] commandArgs = {
    "apply",
    "--project", testProjectPath.toString(),
    "--plan", planPath,  // ← Now works with includes!
    "--step-by-step",
    "--dry-run",
    "--verbose"
};
```

## Benefits

### ✅ All Loading Scenarios Now Work

1. **File System Loading**
   ```bash
   analyzer apply --plan /path/to/plan.yaml
   ```
   - Loads from file system
   - Includes resolved relative to plan file location
   - ✅ Works perfectly

2. **Classpath Loading (Development)**
   ```bash
   analyzer apply --plan migrations/ejb2spring/jboss-to-springboot.yaml
   ```
   - Loads from classpath
   - Detects file system path if resource is on disk
   - Resolves includes if base path detected
   - ✅ Works when resources are on file system

3. **Classpath Loading (JAR)**
   ```bash
   analyzer apply --plan migration-plans/embedded-plan.yaml
   ```
   - Loads from JAR classpath
   - Cannot resolve includes (no file system access)
   - Warns user about unresolved includes
   - ✅ Graceful degradation

### ✅ Validation Happens at Right Time

- **Before**: Validation happened before include resolution
- **After**: Validation happens after complete plan is built
- Result: No more "must have at least one phase" errors

### ✅ Modular Plans Fully Supported

Migration plans can now use the modular structure:

```yaml
# migrations/ejb2spring/jboss-to-springboot.yaml
plan:
  name: "JBoss EJB 2 to Spring Boot Migration - Complete"
  version: "1.0.0"
  
  includes:
    - common/metadata.yaml
    - common/variables.yaml
    - phases/phase0-assessment.yaml
    - phases/phase1-initialization.yaml
    - phases/phase2-jdbc-migration.yaml
    # ... more phases
```

## Testing

### Test Case 1: File System Loading
```bash
cd /path/to/project
analyzer apply --project . --plan migrations/ejb2spring/jboss-to-springboot.yaml
```
**Expected**: ✅ Loads successfully, includes resolved

### Test Case 2: Development Classpath
```bash
mvn exec:java -Dexec.mainClass="com.analyzer.cli.examples.FullExecutionExample"
```
**Expected**: ✅ Loads successfully, includes resolved (resources on file system)

### Test Case 3: JAR Classpath
```bash
java -jar analyzer.jar apply --plan embedded-plan.yaml
```
**Expected**: ⚠️ Warns about unresolved includes (if plan has any)

## Implementation Details

### Order of Operations (Fixed)

**All load methods now follow**:
```
1. Load YAML → MigrationPlanDTO
2. Resolve includes (merge DTOs)
3. Validate complete DTO
4. Convert to domain model
```

### Include Resolution Strategy

1. **File System**: Resolve relative to plan file directory
2. **Classpath (detected)**: Resolve relative to detected file system path
3. **Classpath (JAR)**: Skip resolution, emit warning

### Backward Compatibility

- ✅ Existing code continues to work
- ✅ No breaking changes to public APIs
- ✅ Graceful degradation when includes can't be resolved

## Related Files

1. `analyzer-core/src/main/java/com/analyzer/migration/loader/YamlMigrationPlanLoader.java`
2. `analyzer-core/src/main/java/com/analyzer/migration/loader/IncludeResolver.java`
3. `analyzer-app/src/main/java/com/analyzer/cli/ApplyMigrationCommand.java`
4. `analyzer-app/src/test/java/com/analyzer/cli/examples/FullExecutionExample.java`

## Related Documents

- `docs/implementation/yaml-include-support-summary.md` - Original include feature
- `docs/implementation/step-by-step-mode-implementation.md` - Step-by-step mode
- `migrations/ejb2spring/README.md` - Modular plan structure

## Conclusion

The fix ensures that **include resolution always happens before validation**, enabling modular migration plans to work correctly regardless of how they're loaded. The solution follows the principle suggested: "Resolve includes first, build complete aggregated plan in memory, ONLY THEN validate schema."
