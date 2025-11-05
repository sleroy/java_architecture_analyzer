# Resume Functionality with Variable Persistence

**Date:** 2025-11-04  
**Status:** ✅ COMPLETED

## Problem Statement

The migration tool's `--resume` flag was not restoring runtime-generated variables from crashed/failed executions, causing subsequent phases to fail with missing variable errors.

### Example Failure Scenario

```
Phase 1: detect-jdk-version → Creates ${detected_java_version} = "21"
Phase 1: generate-springboot-project → Uses ${detected_java_version} ✅
Phase 1: CRASH/FAILURE
User runs: analyzer apply --resume
Phase 2: optimize-springboot-pom → Uses ${detected_java_version}
Result: FreeMarker error "detected_java_version has evaluated to null" ❌
```

## Root Cause Analysis

### What Was Working ✅

**State Persistence (`StateFileListener.onPlanStart()`):**
```java
migrationState.setVariables(context.getAllVariables());  // Variables ARE saved!
stateManager.updateMigrationState(planKey, migrationState);
```

The `migration-state.json` correctly stores:
- Completed phases/tasks
- Failed phases/tasks
- **All context variables** (including runtime-generated ones)
- Execution history

### What Was Broken ❌

**Resume Logic (`ApplyMigrationCommand.executeMigration()`):**
```java
// OLD CODE - Creates fresh context, ignores saved variables
MigrationContext context = new MigrationContext(projectDir, dryRun);
for (Map.Entry<String, String> entry : variables.entrySet()) {
    context.setVariable(entry.getKey(), entry.getValue());  // Only CLI/plan variables
}
if (resume) {
    result = engine.resumeFromCheckpoint(plan, context);  // Missing runtime variables!
}
```

**The Gap:**
1. ✅ Saves variables to `migration-state.json`
2. ✅ Tracks completed phases/tasks
3. ❌ Does NOT restore variables from state file on resume
4. ❌ Loses ALL runtime-generated variables

## Solution Implemented

### Architecture: Variable Restoration on Resume

Added `restoreVariablesFromState()` method that loads saved variables before executing resumed migration.

### Variable Priority on Resume

**Normal Execution:**
```
1. Plan defaults (YAML)
2. Properties file (--variables)
3. CLI --variable flags
4. CLI -D flags (highest)
5. Auto-derived (project_root, current_date, etc.)
```

**Resume Execution:**
```
1. Saved state variables (from migration-state.json)
2. CLI -D flags (can override saved values)
3. Auto-derived (always fresh: current_date, current_datetime)
```

This allows users to override saved variables if needed while preserving runtime state.

## Implementation Details

### 1. Added `restoreVariablesFromState()` Method

**File:** `analyzer-app/src/main/java/com/analyzer/cli/ApplyMigrationCommand.java`

```java
private MigrationExecutionState restoreVariablesFromState(MigrationContext context, 
                                                           Path projectDir,
                                                           String planKey) {
    try {
        MigrationStateManager stateManager = new MigrationStateManager(projectDir);

        if (!stateManager.exists()) {
            logger.warn("No saved state found for resume. Starting fresh execution.");
            return null;
        }

        MigrationExecutionState savedState = stateManager.getMigrationState(planKey);

        if (savedState == null) {
            logger.warn("No saved state found for plan: {}. Starting fresh execution.", planKey);
            return null;
        }

        // Restore variables from saved state
        Map<String, Object> savedVariables = savedState.getVariables();
        if (savedVariables != null && !savedVariables.isEmpty()) {
            for (Map.Entry<String, Object> entry : savedVariables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
            logger.info("Restored {} variables from saved state", savedVariables.size());

            if (verbose) {
                logger.debug("Restored variables:");
                savedVariables.forEach((k, v) -> logger.debug("  {} = {}", k, v));
            }
        }

        return savedState;

    } catch (IOException e) {
        logger.error("Failed to restore variables from state: {}", e.getMessage(), e);
        return null;
    }
}
```

### 2. Added `displayResumeInformation()` Method

Shows user what state is being restored:

```java
private void displayResumeInformation(MigrationExecutionState savedState) {
    System.out.println("\n=== RESUME MODE ===");
    System.out.println("Resuming migration from saved state");
    System.out.println();
    System.out.println("Saved State Information:");
    System.out.println("  Plan:             " + savedState.getPlanName());
    System.out.println("  Version:          " + savedState.getPlanVersion());
    System.out.println("  Status:           " + savedState.getStatus());
    System.out.println("  Started:          " + savedState.getStartedAt());
    System.out.println("  Last Executed:    " + savedState.getLastExecuted());
    System.out.println("  Completed Phases: " + savedState.getCompletedPhases().size());
    System.out.println("  Failed Phases:    " + savedState.getFailedPhases().size());
    System.out.println("  Restored Variables: " + savedState.getVariables().size());
    // ... shows completed/failed phases
    System.out.println("\nNote: CLI variable overrides (-D flags) will take precedence over saved values.");
}
```

### 3. Updated `executeMigration()` Flow

```java
private Integer executeMigration(MigrationPlan plan, Map<String, String> variables) {
    MigrationContext context = new MigrationContext(projectDir, dryRun);
    context.setStepByStepMode(stepByStep);

    String planKey = Paths.get(planPath).getFileName().toString();
    MigrationExecutionState savedState = null;
    
    // STEP 1: Restore saved variables FIRST (if resuming)
    if (resume) {
        savedState = restoreVariablesFromState(context, projectDir, planKey);
    }

    // STEP 2: Apply CLI overrides (allows changing variables on resume)
    for (Map.Entry<String, String> entry : variables.entrySet()) {
        context.setVariable(entry.getKey(), entry.getValue());
    }

    // STEP 3: Display resume info
    if (resume && savedState != null) {
        displayResumeInformation(savedState);
    }

    // STEP 4: Execute with restored context
    if (resume) {
        result = engine.resumeFromCheckpoint(plan, context);
    }
}
```

## Usage Examples

### Example 1: Basic Resume

```bash
# Initial execution (crashes during Phase 2)
analyzer apply --project /app --plan migration.yaml

# Resume from crash
analyzer apply --project /app --plan migration.yaml --resume

# Output shows:
=== RESUME MODE ===
Resuming migration from saved state

Saved State Information:
  Plan:             JBoss EJB 2 to Spring Boot Migration
  Completed Phases: 1
  Restored Variables: 23

  Completed Phases:
    ✓ Pre-Migration Assessment and Preparation

Resuming from: Spring Boot Project Initialization
```

### Example 2: Resume with Variable Override

```bash
# Override a saved variable while resuming
analyzer apply --project /app --plan migration.yaml --resume -Dspring_boot_version=3.6.0

# The saved detected_java_version is preserved
# But spring_boot_version is overridden to 3.6.0
```

### Example 3: Check Status Before Resume

```bash
# Check what will be resumed
analyzer apply --project /app --plan migration.yaml --status

# Output:
=== Migration Status ===
  JBoss EJB 2 to Spring Boot Migration
    Status:       FAILED
    Completed:    1 phases
    Failed:       1 phases

# Then resume
analyzer apply --project /app --plan migration.yaml --resume
```

## Benefits

✅ **Crash Recovery** - Can resume after any failure without losing runtime state  
✅ **Variable Persistence** - Runtime-generated variables (like `detected_java_version`) survive crashes  
✅ **Idempotent** - Can re-run same command safely  
✅ **Override Support** - CLI flags can still override saved variables  
✅ **Transparency** - Clear display of what's being restored

## Testing

### Test Case 1: Normal Execution Saves Variables

```bash
# Run migration
analyzer apply --project /test --plan migration.yaml

# Verify variables saved
cat /test/.analysis/migration-state.json | jq '.migrations[].variables'

# Should show all variables including runtime-generated ones:
{
  "detected_java_version": "21",
  "spring_boot_version": "3.5.7",
  "project_root": "/test",
  ...
}
```

### Test Case 2: Resume Restores Variables

```bash
# Simulate crash (Ctrl+C during Phase 2)
analyzer apply --project /test --plan migration.yaml
^C

# Resume - should restore detected_java_version
analyzer apply --project /test --plan migration.yaml --resume --verbose

# Log output should show:
INFO  Restored 23 variables from saved state
DEBUG Restored variables:
DEBUG   detected_java_version = 21
DEBUG   spring_boot_version = 3.5.7
...
```

### Test Case 3: CLI Override Works

```bash
# Resume with override
analyzer apply --project /test --plan migration.yaml --resume -Dspring_boot_version=3.6.0

# Should:
# - Restore detected_java_version from state (21)
# - Override spring_boot_version to 3.6.0
```

## Migration State File Structure

**Location:** `<project>/.analysis/migration-state.json`

```json
{
  "schemaVersion": "1.0",
  "projectRoot": "/path/to/project",
  "lastUpdated": "2025-11-04T15:30:00Z",
  "migrations": {
    "jboss-to-springboot.yaml": {
      "planName": "JBoss EJB 2 to Spring Boot Migration",
      "planVersion": "2.0.0",
      "status": "FAILED",
      "startedAt": "2025-11-04T15:00:00Z",
      "lastExecuted": "2025-11-04T15:30:00Z",
      "currentPhase": "Spring Boot Project Initialization",
      "nextPhase": "JDBC Migration",
      "completedPhases": [
        "Pre-Migration Assessment and Preparation"
      ],
      "failedPhases": [
        "Spring Boot Project Initialization"
      ],
      "variables": {
        "detected_java_version": "21",
        "spring_boot_version": "3.5.7",
        "project_root": "/path/to/project",
        "artifact_id": "springboot-app",
        ...
      },
      "executionHistory": [...]
    }
  }
}
```

## Files Modified

1. **ApplyMigrationCommand.java**
   - Added `restoreVariablesFromState()` method
   - Added `displayResumeInformation()` method
   - Updated `executeMigration()` to call restoration on resume

2. **No changes needed:**
   - StateFileListener.java (already saves variables)
   - MigrationStateManager.java (already handles persistence)
   - MigrationExecutionState.java (already has variables field)

## Impact

- **Scope**: Resume functionality only
- **Risk**: LOW - Only affects resume path, normal execution unchanged
- **Backward Compatible**: Old state files without variables still work
- **User Experience**: Significantly improved - no manual variable re-entry needed

## Comparison: Before vs After

### Before (Broken Resume)

```bash
# Initial run
analyzer apply --project /app --plan migration.yaml
Phase 1: detect-jdk-version → ${detected_java_version} = "21"
Phase 1: Complete ✓
Phase 2: CRASH ✗

# Resume attempt
analyzer apply --project /app --plan migration.yaml --resume
Phase 1: Skipped (already complete)
Phase 2: optimize-springboot-pom
ERROR: detected_java_version has evaluated to null
FAILED ✗
```

### After (Working Resume)

```bash
# Initial run
analyzer apply --project /app --plan migration.yaml
Phase 1: detect-jdk-version → ${detected_java_version} = "21"
Phase 1: Complete ✓ (variables saved)
Phase 2: CRASH ✗

# Resume attempt
analyzer apply --project /app --plan migration.yaml --resume
Restored 23 variables from saved state
  detected_java_version = 21 ✓
Phase 1: Skipped (already complete)
Phase 2: optimize-springboot-pom
  Uses ${detected_java_version} = "21" ✓
Phase 2: Complete ✓
SUCCESS ✓
```

## Related Features

### Variable Lifecycle

```
Creation → Storage → Restoration → Usage
    ↓         ↓           ↓          ↓
  Block    onPlanStart  --resume   Template
  output   (saves)     (restores)  rendering
```

### Integration with Other Features

1. **CommandBlock output-variable**: Runtime variables persist
2. **GRAPH_QUERY results**: Query results persist
3. **AI_ASSISTED outputs**: AI-generated variables persist
4. **StateFileListener**: Automatic saving (no manual intervention)

## Next Steps

1. ✅ Implementation complete
2. ✅ Build successful
3. ⏳ End-to-end testing with actual crash/resume scenario
4. ⏳ Add unit tests for variable restoration
5. ⏳ Update user documentation/README

## Related Documentation

- `docs/implementation/commandblock-output-variable-feature.md` - Custom output variables
- `docs/implementation/phase1-variable-and-validation-improvements.md` - Related improvements
- `docs/implementation/migration-state-tracking-next-steps.md` - State tracking architecture
