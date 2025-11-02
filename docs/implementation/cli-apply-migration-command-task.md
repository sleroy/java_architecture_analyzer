# CLI Apply Migration Command - Implementation Task

**Version:** 1.0.0  
**Date:** 2025-10-31  
**Status:** Ready for Implementation

---

## Overview

Implement the `apply` CLI command to execute migration plans with full variable support, enabling users to run the EJB-to-Spring Boot migration engine from the command line.

---

## Command Specification

### Basic Syntax

```bash
java-architecture-analyzer apply [OPTIONS]
```

### Required Parameters

- `--project <path>` or `-p <path>` - Path to the EJB project to migrate
- `--plan <file>` or `-m <file>` - Migration plan YAML file (file path or classpath resource)

### Variable Parameters

- `-D<key>=<value>` - Maven-style property (e.g., `-Djava_version=11`)
- `--variable <key>=<value>` - Explicit variable (can be repeated)
- `--variables <file>` - Load variables from properties file
- `--list-variables` - Show all variables defined in plan with defaults

### Execution Control Parameters

- `--task <taskId>` - Execute specific task only (e.g., `TASK-000`)
- `--phase <phaseId>` - Execute specific phase only (e.g., `0`)
- `--resume` or `-r` - Resume from last checkpoint
- `--dry-run` - Preview execution without making changes
- `--interactive` or `-i` - Interactive mode (pause between tasks)
- `--status` or `-s` - Show current migration progress and exit

### Optional Parameters

- `--database <path>` or `-d <path>` - Custom H2 database path (default: `{project}/.migration-progress.db`)
- `--verbose` or `-v` - Enable verbose logging output

---

## Usage Examples

### Example 1: Basic Execution
```bash
java-architecture-analyzer apply \
  --project /home/user/semeru-ejb \
  --plan jboss-to-springboot-phase0-1.yaml
```

### Example 2: Override Variables
```bash
java-architecture-analyzer apply \
  --project /home/user/acme-ejb \
  --plan jboss-to-springboot-phase0-1.yaml \
  -Djava_version=11 \
  -Dspring_boot_version=3.1.5 \
  -Dgroup_id=com.acme \
  -Dartifact_id=acme-springboot
```

### Example 3: Use Properties File
```bash
# migration-vars.properties:
# java_version=17
# spring_boot_version=3.2.0
# group_id=com.mycompany

java-architecture-analyzer apply \
  --project /home/user/my-ejb \
  --plan jboss-to-springboot-phase0-1.yaml \
  --variables migration-vars.properties
```

### Example 4: Execute Single Task
```bash
java-architecture-analyzer apply \
  --project /home/user/semeru-ejb \
  --plan jboss-to-springboot-phase0-1.yaml \
  --task TASK-000
```

### Example 5: Interactive Mode
```bash
java-architecture-analyzer apply \
  --project /home/user/semeru-ejb \
  --plan jboss-to-springboot-phase0-1.yaml \
  --interactive
```

### Example 6: Check Status
```bash
java-architecture-analyzer apply \
  --project /home/user/semeru-ejb \
  --status
```

### Example 7: List Available Variables
```bash
java-architecture-analyzer apply \
  --plan jboss-to-springboot-phase0-1.yaml \
  --list-variables
```

---

## Implementation Details

### File Structure

```
analyzer-app/src/main/java/com/analyzer/cli/
├── AnalyzerCLI.java                    # UPDATE: Add ApplyMigrationCommand
└── ApplyMigrationCommand.java          # NEW: Main command class
```

### Variable Resolution Priority

Variables are resolved in the following order (highest to lowest priority):

1. Command-line `-D` flags
2. Command-line `--variable` flags
3. Properties file from `--variables`
4. YAML plan defaults from `variables:` section
5. Auto-derived variables
6. Error if required variable missing

### Auto-Derived Variables

These variables are automatically created:

| Variable | Source | Example |
|----------|--------|---------|
| `${project.root}` | `--project` flag | `/home/user/my-ejb-app` |
| `${project.name}` | Directory name | `my-ejb-app` |
| `${plan.name}` | Plan metadata | `JBoss EJB 2 to Spring Boot Migration` |
| `${current_datetime}` | Current timestamp | `2025-10-31T10:15:23` |
| `${user.name}` | System property | `sleroy` |
| `${user.home}` | System property | `/home/sleroy` |

### Environment Variable Support

Support `${env.VAR_NAME}` and `${env.VAR_NAME:-default}` syntax:

```yaml
variables:
  db_user: "${env.DB_USER}"
  db_password: "${env.DB_PASSWORD}"
  db_host: "${env.DB_HOST:-localhost}"  # Default to localhost
```

---

## Implementation Steps

### Step 1: Create ApplyMigrationCommand.java

**Location:** `analyzer-app/src/main/java/com/analyzer/cli/ApplyMigrationCommand.java`

**Key Components:**
- Picocli `@Command` annotation with all options
- Parameter fields with `@Option` annotations
- `call()` method implementing main logic
- Variable resolution methods
- Validation methods
- Display methods

**Dependencies:**
- `YamlMigrationPlanLoader` - Load migration plans
- `MigrationPlanConverter` - Convert DTOs to domain objects
- `MigrationEngine` - Execute plans
- `MigrationContext` - Manage execution context
- `ConsoleProgressListener` - Display progress

### Step 2: Implement Core Methods

#### `call()` Method Flow

```java
@Override
public Integer call() throws Exception {
    try {
        // 1. Handle --list-variables mode
        if (listVariables) {
            return handleListVariables();
        }
        
        // 2. Handle --status mode
        if (statusOnly) {
            return handleStatus();
        }
        
        // 3. Validate parameters
        validateParameters();
        
        // 4. Load migration plan
        MigrationPlan plan = loadMigrationPlan();
        
        // 5. Build variable map
        Map<String, String> variables = buildVariableMap(plan);
        
        // 6. Validate variables
        validateVariables(variables, plan);
        
        // 7. Create migration context
        MigrationContext context = createContext(variables);
        
        // 8. Setup migration engine
        MigrationEngine engine = setupEngine(plan, context);
        
        // 9. Execute migration
        ExecutionResult result = executeMigration(engine, plan, context);
        
        // 10. Return exit code
        return result.isSuccess() ? 0 : 1;
        
    } catch (Exception e) {
        displayError(e);
        return 1;
    }
}
```

#### Variable Resolution Methods

```java
private Map<String, String> buildVariableMap(MigrationPlan plan) {
    Map<String, String> variables = new LinkedHashMap<>();
    
    // 1. Start with plan defaults
    if (plan.getVariables() != null) {
        variables.putAll(plan.getVariables());
    }
    
    // 2. Add properties file variables
    if (variablesFile != null) {
        Properties props = loadPropertiesFile(variablesFile);
        props.forEach((k, v) -> variables.put(k.toString(), v.toString()));
    }
    
    // 3. Add explicit --variable flags
    if (explicitVariables != null) {
        variables.putAll(explicitVariables);
    }
    
    // 4. Add -D flags (highest priority)
    if (definedProperties != null) {
        variables.putAll(definedProperties);
    }
    
    // 5. Add auto-derived variables
    addAutoDerivedVariables(variables, plan);
    
    // 6. Resolve environment variables
    resolveEnvironmentVariables(variables);
    
    return variables;
}

private void addAutoDerivedVariables(Map<String, String> variables, MigrationPlan plan) {
    variables.put("project.root", projectPath.getAbsolutePath());
    variables.put("project.name", projectPath.getName());
    variables.put("plan.name", plan.getName());
    variables.put("current_datetime", LocalDateTime.now().toString());
    variables.put("user.name", System.getProperty("user.name"));
    variables.put("user.home", System.getProperty("user.home"));
}

private void resolveEnvironmentVariables(Map<String, String> variables) {
    Pattern pattern = Pattern.compile("\\$\\{env\\.([^:}]+)(?::([^}]+))?\\}");
    
    variables.replaceAll((key, value) -> {
        if (value == null) return null;
        
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String defaultValue = matcher.group(2);
            
            String envValue = System.getenv(envVar);
            String replacement = (envValue != null) ? envValue : 
                               (defaultValue != null) ? defaultValue : 
                               matcher.group(0);
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    });
}
```

#### Validation Methods

```java
private void validateParameters() {
    // Validate project path
    if (!projectPath.exists()) {
        throw new IllegalArgumentException(
            "Project path does not exist: " + projectPath.getAbsolutePath()
        );
    }
    
    if (!projectPath.isDirectory()) {
        throw new IllegalArgumentException(
            "Project path must be a directory: " + projectPath.getAbsolutePath()
        );
    }
    
    if (!projectPath.canRead()) {
        throw new IllegalArgumentException(
            "Cannot read project directory: " + projectPath.getAbsolutePath()
        );
    }
    
    // Validate variables file if provided
    if (variablesFile != null) {
        if (!variablesFile.exists()) {
            throw new IllegalArgumentException(
                "Variables file does not exist: " + variablesFile.getAbsolutePath()
            );
        }
        if (!variablesFile.canRead()) {
            throw new IllegalArgumentException(
                "Cannot read variables file: " + variablesFile.getAbsolutePath()
            );
        }
    }
}

private void validateVariables(Map<String, String> variables, MigrationPlan plan) {
    List<String> missingVariables = new ArrayList<>();
    
    // Check for unresolved placeholders
    for (Map.Entry<String, String> entry : variables.entrySet()) {
        if (entry.getValue() != null && 
            entry.getValue().matches(".*\\$\\{[^}]+\\}.*")) {
            missingVariables.add(entry.getKey() + ": " + entry.getValue());
        }
    }
    
    if (!missingVariables.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        sb.append("Required variables are missing or unresolved:\n\n");
        for (String var : missingVariables) {
            sb.append("  ").append(var).append("\n");
        }
        sb.append("\nUse -D or --variable to provide values.\n");
        sb.append("Use --list-variables to see all variables in the plan.");
        throw new IllegalArgumentException(sb.toString());
    }
    
    // Warn about unused variables
    Set<String> planVars = plan.getVariables() != null ? 
        plan.getVariables().keySet() : Collections.emptySet();
    
    variables.keySet().stream()
        .filter(k -> !planVars.contains(k))
        .filter(k -> !isAutoDerivedVariable(k))
        .forEach(k -> 
            logger.warn("Variable '{}' provided but not defined in plan", k)
        );
}

private boolean isAutoDerivedVariable(String key) {
    return key.startsWith("project.") || 
           key.equals("plan.name") || 
           key.equals("current_datetime") ||
           key.equals("user.name") ||
           key.equals("user.home");
}
```

### Step 3: Implement Display Methods

```java
private int handleListVariables() throws IOException {
    MigrationPlan plan = loadMigrationPlan();
    
    System.out.println("Variables defined in plan:");
    System.out.println();
    
    Map<String, String> vars = plan.getVariables();
    if (vars == null || vars.isEmpty()) {
        System.out.println("  (no variables defined)");
        return 0;
    }
    
    int maxKeyLength = vars.keySet().stream()
        .mapToInt(String::length)
        .max()
        .orElse(20);
    
    vars.forEach((key, value) -> {
        String paddedKey = String.format("%-" + maxKeyLength + "s", key);
        
        if (value.startsWith("${") && value.endsWith("}")) {
            System.out.printf("  %s: %s (required - provide via CLI)%n", 
                paddedKey, value);
        } else {
            System.out.printf("  %s: %s (default)%n", 
                paddedKey, value);
        }
    });
    
    System.out.println();
    System.out.println("Auto-derived variables:");
    System.out.println("  project.root: (from --project flag)");
    System.out.println("  project.name: (from --project directory name)");
    System.out.println("  plan.name: (from plan metadata)");
    System.out.println("  current_datetime: (current timestamp)");
    System.out.println("  user.name: (system user)");
    System.out.println("  user.home: (home directory)");
    
    return 0;
}

private void displayError(Exception e) {
    System.err.println();
    System.err.println("ERROR: " + e.getMessage());
    System.err.println();
    
    if (verbose) {
        e.printStackTrace(System.err);
    } else {
        System.err.println("Use --verbose for full stack trace");
    }
}
```

### Step 4: Update AnalyzerCLI.java

Add `ApplyMigrationCommand.class` to the subcommands list:

```java
@Command(
    name = "java-architecture-analyzer",
    description = "Static analysis tool for Java applications",
    version = "1.0.0-SNAPSHOT",
    mixinStandardHelpOptions = true,
    subcommands = {
        InventoryCommand.class,
        CsvExportCommand.class,
        JsonExportCommand.class,
        InspectorDependencyGraphCommand.class,
        ApplyMigrationCommand.class,  // ADD THIS LINE
        HelpCommand.class
    }
)
```

---

## Testing Plan

### Test 1: Basic Execution with Defaults
```bash
java-architecture-analyzer apply \
  --project /path/to/test-ejb-project \
  --plan jboss-to-springboot-phase0-1.yaml \
  --task TASK-000 \
  --dry-run
```

**Expected:**
- Loads plan successfully
- Uses default variables from YAML
- Shows what would be executed
- No actual changes made

### Test 2: Variable Override
```bash
java-architecture-analyzer apply \
  --project /path/to/test-ejb-project \
  --plan jboss-to-springboot-phase0-1.yaml \
  -Djava_version=11 \
  -Dspring_boot_version=3.1.5 \
  --list-variables
```

**Expected:**
- Shows overridden variables
- Displays correct priority resolution

### Test 3: Properties File
Create `test-vars.properties`:
```properties
java_version=17
spring_boot_version=3.2.0
group_id=com.test
artifact_id=test-app
```

```bash
java-architecture-analyzer apply \
  --project /path/to/test-ejb-project \
  --plan jboss-to-springboot-phase0-1.yaml \
  --variables test-vars.properties \
  --list-variables
```

**Expected:**
- Loads variables from file
- Shows all resolved variables

### Test 4: Missing Required Variable
```bash
# Assume plan requires DB_USER variable
java-architecture-analyzer apply \
  --project /path/to/test-ejb-project \
  --plan jboss-to-springboot-phase0-1.yaml
```

**Expected:**
- Clear error message about missing variables
- Suggestions on how to provide them

### Test 5: Execute Full Task
```bash
java-architecture-analyzer apply \
  --project /path/to/test-ejb-project \
  --plan jboss-to-springboot-phase0-1.yaml \
  --task TASK-000 \
  --verbose
```

**Expected:**
- Executes TASK-000 completely
- Shows progress with ConsoleProgressListener
- Creates baseline branch
- Generates documentation
- Returns exit code 0 on success

### Test 6: Interactive Mode
```bash
java-architecture-analyzer apply \
  --project /path/to/test-ejb-project \
  --plan jboss-to-springboot-phase0-1.yaml \
  --task TASK-000 \
  --interactive
```

**Expected:**
- Pauses after each block
- Waits for user confirmation
- Allows review of changes

### Test 7: Status Check
```bash
java-architecture-analyzer apply \
  --project /path/to/test-ejb-project \
  --status
```

**Expected:**
- Shows current migration progress
- Lists completed/pending tasks
- Shows last checkpoint info

---

## Error Handling

### Missing Project Path
```
ERROR: Project path does not exist: /nonexistent/path

Verify the path and try again.
```

### Invalid Plan File
```
ERROR: Migration plan file not found: invalid-plan.yaml

Available classpath resources:
  - /migration-plans/jboss-to-springboot-phase0-1.yaml

Provide a valid file path or resource name.
```

### Missing Variables
```
ERROR: Required variables are missing or unresolved:

  DB_USER: ${env.DB_USER}
    → Provide via: -DDB_USER=myuser
    or export environment variable: export DB_USER=myuser
    
  DB_PASSWORD: ${env.DB_PASSWORD}
    → Provide via: -DDB_PASSWORD=mypass
    or export environment variable: export DB_PASSWORD=mypass

Use --list-variables to see all variables in the plan.
```

### Task Not Found
```
ERROR: Task 'TASK-999' not found in migration plan

Available tasks:
  - TASK-000: Project Baseline Documentation
  - TASK-001: Create Migration Branch Structure
  - TASK-002: Dependency Analysis and Mapping
  - TASK-100: Create Spring Boot Parent POM
  - TASK-101: Create Spring Boot Main Application Class
```

---

## Success Criteria

The command is considered complete when:

- ✅ All parameters parse correctly
- ✅ Variable resolution works with proper priority
- ✅ `-D`, `--variable`, and `--variables` flags work
- ✅ `--list-variables` displays correctly
- ✅ Auto-derived variables are created
- ✅ Environment variables are resolved
- ✅ Missing variables are detected with helpful errors
- ✅ Plan loads from file or classpath
- ✅ MigrationEngine executes successfully
- ✅ ConsoleProgressListener displays progress
- ✅ All execution modes work (full, task, phase, interactive, dry-run, status)
- ✅ Exit codes are correct (0=success, 1=failure)
- ✅ TASK-000 executes successfully end-to-end
- ✅ Command integrates with AnalyzerCLI

---

## Exit Codes

- `0` - Success
- `1` - General error (validation, execution failure)
- `2` - Invalid arguments (handled by Picocli)

---

## Notes

- Command should be well-documented with Picocli descriptions
- All error messages should be user-friendly and actionable
- Verbose mode should provide detailed diagnostic information
- The command should feel natural to Maven/Gradle users
- Variable syntax matches Spring Boot conventions

---

**Document Version:** 1.0.0  
**Last Updated:** 2025-10-31  
**Status:** Ready for Implementation
