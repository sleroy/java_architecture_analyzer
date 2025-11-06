# Conditional Block Execution with `enable_if` - Implementation Summary

**Date:** January 11, 2025  
**Feature:** Conditional block execution using JEXL expressions  
**Status:** Core implementation complete

## Overview

Implemented a flexible conditional execution system for migration blocks using Apache Commons JEXL for expression evaluation. Blocks can now be conditionally executed based on variable values in the MigrationContext.

## Implementation Components

### 1. Expression Evaluator (`ExpressionEvaluator.java`)
- **Location:** `analyzer-core/src/main/java/com/analyzer/migration/expression/`
- **Purpose:** Evaluates boolean expressions using JEXL against MigrationContext variables
- **Features:**
  - Thread-safe static utility class
  - Access to all MigrationContext variables
  - Support for complex boolean expressions
  - Graceful error handling with configurable defaults
  - Expression syntax validation

### 2. MigrationBlock Interface Enhancement
- **Added Methods:**
  - `getEnableIf()`: Returns the conditional expression (null if always enabled)
  - `isEnabled(MigrationContext)`: Evaluates the condition and returns true/false
- **Default Behavior:** Blocks without `enable_if` always execute (backward compatible)

### 3. BlockDTO Update
- Added `@JsonProperty("enable_if")` setter to parse from YAML
- Stores condition in flexible properties map

### 4. TaskExecutor Enhancement
- Checks `block.isEnabled(context)` before execution
- Skips blocks when condition evaluates to false
- Logs skipped blocks with their conditions
- Creates `BlockResult` with `skipped=true` for tracking
- Fires lifecycle events for skipped blocks

### 5. BlockResult Enhancement
- Added `skipped` field to track conditionally skipped blocks
- Skipped blocks still appear in execution results
- Success remains `true` for skipped blocks

### 6. MigrationPlanConverter Update
- Extracts `enable_if` from BlockDTO properties
- Passes to block builders during conversion
- Currently implemented for CommandBlock (pattern for other blocks)

### 7. Dependency Addition
- Added Apache Commons JEXL 3.3 to `analyzer-core/pom.xml`

## Supported Expression Syntax

### Variable References
```yaml
enable_if: "migrate_db"           # Simple boolean variable
enable_if: "migrate_db == true"    # Explicit boolean comparison
```

### Numeric Comparisons
```yaml
enable_if: "ejb_count > 50"
enable_if: "complexity_score >= 7"
enable_if: "phase_number == 1"
```

### String Comparisons
```yaml
enable_if: "environment == 'production'"
enable_if: "database_type != 'h2'"
```

### Logical Operators
```yaml
enable_if: "migrate_db && backup_enabled"
enable_if: "ejb_count > 100 || force_migration"
enable_if: "!skip_validation"
```

### Complex Expressions
```yaml
enable_if: "(ejb_count > 100 || complexity_score >= 7) && environment != 'dev'"
enable_if: "migrate_db && (database_type == 'mysql' || database_type == 'postgresql')"
```

### Nested Object Access
```yaml
enable_if: "project.name == 'example/springboot'"
enable_if: "user.home != null"
```

## Usage Example

### YAML Migration Plan
```yaml
migration-plan:
  name: "Example Migration with Conditional Blocks"
  version: "1.0.0"
  
  variables:
    migrate_db: true
    backup_enabled: false
    environment: "production"
    ejb_count: 150
  
  phases:
    - id: "phase-1"
      name: "Conditional Migration Phase"
      tasks:
        - id: "task-1"
          name: "Database Migration Task"
          blocks:
            # This block only executes if migrate_db is true
            - type: "COMMAND"
              name: "export-database-schema"
              enable_if: "migrate_db == true"
              command: |
                mysqldump --no-data -h localhost -u ${DB_USER} \
                  ${DB_NAME} > schema.sql
              
            # This block only executes if backup is enabled AND we're migrating the DB
            - type: "COMMAND"
              name: "backup-before-migration"
              enable_if: "migrate_db && backup_enabled"
              command: |
                mysqldump -h localhost -u ${DB_USER} ${DB_NAME} > backup.sql
              
            # This block only executes in production AND if EJB count is high
            - type: "COMMAND"
              name: "complex-migration"
              enable_if: "(ejb_count > 100) && (environment == 'production')"
              command: |
                ./scripts/complex-migration.sh
              
            # This block always executes (no enable_if)
            - type: "COMMAND"
              name: "always-run"
              command: |
                echo "This always runs"
```

### Execution Output
```
INFO  [TaskExecutor] Executing task: Database Migration Task
INFO  [TaskExecutor] Executing block 1/4: export-database-schema (COMMAND)
INFO  [CommandBlock] Executing command: mysqldump --no-data...
INFO  [TaskExecutor] Skipping block (condition not met): backup-before-migration - enable_if: migrate_db && backup_enabled
INFO  [TaskExecutor] Executing block 3/4: complex-migration (COMMAND)
INFO  [CommandBlock] Executing command: ./scripts/complex-migration.sh
INFO  [TaskExecutor] Executing block 4/4: always-run (COMMAND)
INFO  [TaskExecutor] Task completed successfully: Database Migration Task (4 blocks)
```

## Block Implementation Status

### ✅ Fully Implemented
- **CommandBlock** - Complete with `enable_if` support
- **MigrationBlock Interface** - Default methods implemented
- **TaskExecutor** - Conditional execution logic complete
- **BlockResult** - Skipped tracking added
- **MigrationPlanConverter** - YAML parsing for CommandBlock

### ⏳ Pending Implementation
The following blocks need the same pattern applied (add field, getter, builder method):
- GitCommandBlock
- FileOperationBlock
- GraphQueryBlock
- OpenRewriteBlock
- AiPromptBlock
- AiPromptBatchBlock
- InteractiveValidationBlock

**Pattern to Apply:**
1. Add `private final String enableIf;` field
2. Add to constructor: `this.enableIf = builder.enableIf;`
3. Add getter: `@Override public String getEnableIf() { return enableIf; }`
4. Add to Builder: `private String enableIf;` and `public Builder enableIf(String e) { this.enableIf = e; return this; }`
5. Update MigrationPlanConverter method to extract and set `enable_if`

## Available Variables

All variables in `MigrationContext.getAllVariables()` are accessible:

### Built-in Variables
- `project_root` - Project root path
- `project_name` - Project name  
- `current_date` - Current date (ISO format)
- `current_datetime` - Current date and time (ISO format)
- `project.root` - Nested project root
- `project.name` - Nested project name
- `user.home` - User home directory
- `user.name` - User name

### User-Defined Variables
- Variables from CLI properties file
- Variables from YAML plan's `variables` section
- Output variables from previous block executions

## Error Handling

### Expression Evaluation Errors
- **Missing Variable:** Logs warning, returns `false` (skips block by default)
- **Syntax Error:** Throws `ExpressionEvaluationException`
- **Unexpected Error:** Throws `ExpressionEvaluationException`

### Configuration
Can use `ExpressionEvaluator.evaluate(expr, context, defaultOnError)` to change default behavior.

## Testing Requirements

### Unit Tests Needed
1. `ExpressionEvaluatorTest` - Test all expression types
2. `CommandBlockTest` - Test enable_if integration
3. `TaskExecutorTest` - Test conditional skipping logic

### Integration Tests Needed
1. Full migration plan with conditional blocks
2. Variable evaluation from different sources
3. Complex nested expressions
4. Error handling scenarios

## Benefits

1. **Flexibility:** Execute blocks based on runtime conditions
2. **Reusability:** Same migration plan works for different scenarios
3. **Safety:** Skip destructive operations conditionally
4. **Clarity:** Explicit conditions in YAML are self-documenting
5. **Power:** Full expression language for complex logic

## Next Steps

1. ✅ Core infrastructure complete
2. ⏳ Add `enable_if` support to remaining 7 block types
3. ⏳ Write comprehensive unit tests
4. ⏳ Write integration tests
5. ⏳ Update user documentation
6. ⏳ Add examples to migration plan templates

## Implementation Files Modified

1. `analyzer-core/pom.xml` - Added JEXL dependency
2. `ExpressionEvaluator.java` - New file
3. `MigrationBlock.java` - Added `getEnableIf()` and `isEnabled()` methods
4. `BlockDTO.java` - Added `enable_if` property
5. `CommandBlock.java` - Full `enable_if` support
6. `TaskExecutor.java` - Conditional execution logic
7. `BlockResult.java` - Added `skipped` field
8. `MigrationPlanConverter.java` - YAML parsing for CommandBlock

## Backward Compatibility

✅ **Fully Backward Compatible**
- Blocks without `enable_if` behave exactly as before
- Default `getEnableIf()` returns `null` → always enabled
- No changes required to existing migration plans
