# CommandBlock output-variable Feature Implementation

**Date:** 2025-11-04  
**Status:** ✅ COMPLETED

## Problem Statement

The FreeMarker template error occurred because:
1. `detect-jdk-version` COMMAND block outputs to default variable `${output}`
2. `optimize-springboot-pom` AI_ASSISTED block expects `${detected_java_version}`
3. Variable mismatch caused: `InvalidReferenceException: detected_java_version has evaluated to null`

## Solution Implemented

Added `output-variable` field to CommandBlock to allow custom naming of console output.

### YAML Syntax

```yaml
- type: "COMMAND"
  name: "detect-jdk-version"
  command: |
    DETECTED_VERSION=$(java -version 2>&1 | grep -oP 'version "\\K[0-9]+' | head -1)
    echo "$DETECTED_VERSION"
  output-variable: "detected_java_version"  # Custom variable name
```

**Result:** Console output stored as `${detected_java_version}` instead of `${output}`

## Files Modified

### 1. CommandBlock.java
**File:** `analyzer-core/src/main/java/com/analyzer/migration/blocks/automated/CommandBlock.java`

**Changes:**
- Added `outputVariableName` field
- Modified `execute()` to use custom variable name if specified
- Added `outputVariableName()` method to Builder

**Code:**
```java
// Field
private final String outputVariableName;

// In execute() method
if (captureOutput && !outputLines.isEmpty()) {
    String joinedOutput = String.join("\n", outputLines);
    // Use custom output variable name if specified, otherwise default to "output"
    String varName = (outputVariableName != null && !outputVariableName.trim().isEmpty())
            ? outputVariableName
            : "output";
    resultBuilder.outputVariable(varName, joinedOutput);
    resultBuilder.outputVariable("output_lines", outputLines);
}

// Builder method
public Builder outputVariableName(String outputVariableName) {
    this.outputVariableName = outputVariableName;
    return this;
}
```

### 2. MigrationPlanConverter.java
**File:** `analyzer-core/src/main/java/com/analyzer/migration/loader/MigrationPlanConverter.java`

**Changes:**
- Added parsing of `output-variable` property in `convertCommandBlock()`

**Code:**
```java
// Add output-variable for custom output variable name
if (props.containsKey("output-variable")) {
    builder.outputVariableName(getString(props, "output-variable"));
}
```

### 3. BlockDTO.java
**File:** `analyzer-core/src/main/java/com/analyzer/migration/loader/dto/BlockDTO.java`

**Changes:**
- Already had `output-variable` support via convenience method
- No changes needed (already present)

### 4. phase1-initialization.yaml
**File:** `migrations/ejb2spring/phases/phase1-initialization.yaml`

**Changes:**
- Added `output-variable: "detected_java_version"` to detect-jdk-version block
- Updated generate-springboot-project to use `${detected_java_version}` instead of `${output}`

**Before:**
```yaml
- type: "COMMAND"
  name: "detect-jdk-version"
  command: |
    DETECTED_VERSION=$(java -version 2>&1 | grep -oP 'version "\\K[0-9]+' | head -1)
    echo "$DETECTED_VERSION"
  working-directory: "${project_root}"
  timeout-seconds: 10

- type: "COMMAND"
  name: "generate-springboot-project"
  command: |
    DETECTED_JAVA=$(echo "${output}" | tr -d '[:space:]')
    echo "Using detected Java version: $DETECTED_JAVA"
    curl '...&javaVersion='"$DETECTED_JAVA"'...' -o springboot-baseline.zip
```

**After:**
```yaml
- type: "COMMAND"
  name: "detect-jdk-version"
  command: |
    DETECTED_VERSION=$(java -version 2>&1 | grep -oP 'version "\\K[0-9]+' | head -1)
    echo "$DETECTED_VERSION"
  working-directory: "${project_root}"
  timeout-seconds: 10
  output-variable: "detected_java_version"

- type: "COMMAND"
  name: "generate-springboot-project"
  command: |
    DETECTED_JAVA=$(echo "${detected_java_version}" | tr -d '[:space:]')
    echo "Using detected Java version: $DETECTED_JAVA"
    curl '...&javaVersion='"$DETECTED_JAVA"'...' -o springboot-baseline.zip
```

## How It Works

### Flow Diagram

```
┌─────────────────────────────────────────┐
│ detect-jdk-version (COMMAND)            │
│   command: echo "21"                    │
│   output-variable: "detected_java_ver"  │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│ CommandBlock.execute()                  │
│   1. Runs command                       │
│   2. Captures output: "21\n"            │
│   3. Checks outputVariableName          │
│   4. Stores as custom name              │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│ MigrationContext                        │
│   ${detected_java_version} = "21\n"     │
│   ${output_lines} = ["21"]              │
│   ${exit_code} = 0                      │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│ optimize-springboot-pom (AI_ASSISTED)   │
│   Prompt uses: ${detected_java_version} │
│   Template renders: "21\n"              │
│   ✅ No FreeMarker error!                │
└─────────────────────────────────────────┘
```

### Backward Compatibility

Blocks **without** `output-variable` continue to work as before:
```yaml
- type: "COMMAND"
  name: "some-command"
  command: "echo hello"
  # No output-variable specified
  # Output stored as ${output} (default)
```

## Benefits

✅ **Semantic Variable Names**: Use meaningful names like `detected_java_version`  
✅ **Backward Compatible**: Existing blocks work without changes  
✅ **Simple**: One optional YAML field  
✅ **Type Safe**: Java code validation  
✅ **Clean**: No complex mapping syntax  

## Example Usage

### Example 1: Detect Java Version
```yaml
- type: "COMMAND"
  name: "detect-jdk"
  command: "java -version 2>&1 | grep -oP 'version \"\\K[0-9]+' | head -1"
  output-variable: "java_major_version"
  # Creates: ${java_major_version} = "21"
```

### Example 2: Count Files
```yaml
- type: "COMMAND"
  name: "count-sources"
  command: "find . -name '*.java' | wc -l"
  output-variable: "java_file_count"
  # Creates: ${java_file_count} = "42"
```

### Example 3: Git Commit Hash
```yaml
- type: "COMMAND"
  name: "get-commit"
  command: "git rev-parse --short HEAD"
  output-variable: "git_commit_sha"
  # Creates: ${git_commit_sha} = "f603dff"
```

## Testing

### Unit Test (Not Yet Implemented)
```java
@Test
void testOutputVariableMapping() {
    CommandBlock block = CommandBlock.builder()
        .name("test-custom-var")
        .command("echo '42'")
        .outputVariableName("my_answer")
        .build();
    
    MigrationContext context = createTestContext();
    BlockResult result = block.execute(context);
    
    assertTrue(result.isSuccess());
    assertEquals("42", result.getOutputVariables().get("my_answer"));
    // Standard variables still present
    assertEquals("42", result.getOutputVariables().get("output_lines"));
    assertEquals(0, result.getOutputVariables().get("exit_code"));
}
```

### Integration Test
Run the migration plan:
```bash
mvn clean install
cd /path/to/test/project
analyzer apply --project . --plan migrations/ejb2spring/jboss-to-springboot.yaml
```

Expected behavior:
1. `detect-jdk-version` outputs "21" to `${detected_java_version}`
2. `optimize-springboot-pom` uses `${detected_java_version}` successfully
3. No FreeMarker template error
4. Migration proceeds to next phase

## Impact

- **Scope**: CommandBlock only
- **Risk**: LOW - Backward compatible, simple feature
- **Maintenance**: Easy to understand and extend
- **Documentation**: Self-explanatory YAML syntax

## Related Issues

- Fixes FreeMarker `InvalidReferenceException: detected_java_version has evaluated to null`
- Enables semantic variable naming in migration plans
- Improves readability of YAML plans

## Next Steps

1. ✅ Code implementation complete
2. ✅ YAML migration plan updated
3. ⏳ Test end-to-end migration
4. ⏳ Add unit tests
5. ⏳ Update migration plan schema documentation

## Related Documentation

- `docs/implementation/phase1-jdk-detection-final-solution.md` - Original JDK detection solution
- `docs/implementation/phase1-determinism-issues-and-fixes.md` - Comprehensive problem analysis
- `migrations/migration-plan-schema.json` - Should be updated with output-variable property
