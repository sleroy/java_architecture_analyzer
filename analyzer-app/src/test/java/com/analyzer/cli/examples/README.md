# Apply Migration Command Examples

This directory contains example classes demonstrating how to programmatically invoke the `apply` migration command.

## Overview

The `apply` command enables automated execution of EJB-to-Spring Boot migration plans with comprehensive variable support. These examples show different ways to invoke the command from Java code.

## Available Examples

### 1. ListVariablesExample.java
**Purpose:** Display all variables defined in a migration plan.

**Use Case:** Discovering what variables are required before executing a migration.

**Run:**
```bash
mvn exec:java -Dexec.mainClass="com.analyzer.cli.examples.ListVariablesExample"
```

**What it demonstrates:**
- Loading a migration plan
- Using `--list-variables` flag
- Understanding variable structure

---

### 2. DryRunExample.java
**Purpose:** Validate a migration plan without executing it.

**Use Case:** Testing variable resolution and plan validation before actual migration.

**Run:**
```bash
mvn exec:java -Dexec.mainClass="com.analyzer.cli.examples.DryRunExample"
```

**What it demonstrates:**
- Using `--dry-run` flag
- Overriding variables with `-D` flags
- Using `--verbose` for detailed output
- Variable validation process

---

### 3. FullExecutionExample.java
**Purpose:** Execute a complete migration plan.

**Use Case:** Running actual migration tasks on an EJB project.

**Run:**
```bash
mvn exec:java -Dexec.mainClass="com.analyzer.cli.examples.FullExecutionExample"
```

**What it demonstrates:**
- Full plan execution
- Multiple variable overrides
- Progress tracking
- Result display
- ⚠️ **WARNING:** This executes real migration tasks!

---

### 4. PropertiesFileExample.java
**Purpose:** Use a properties file for variable configuration.

**Use Case:** Managing many variables or sharing configuration across teams.

**Run:**
```bash
mvn exec:java -Dexec.mainClass="com.analyzer.cli.examples.PropertiesFileExample"
```

**What it demonstrates:**
- Creating a properties file
- Using `--variables` flag
- Variable priority system
- Combining properties file with CLI overrides

---

## Command-Line Usage

While these examples show programmatic invocation, you can also use the command directly:

```bash
# Build the project
mvn clean package

# Run from command line
java -jar analyzer-app/target/analyzer-app-1.0.0-SNAPSHOT.jar apply \
  --project /path/to/ejb-project \
  --plan migration-plans/jboss-to-springboot-phase0-1.yaml \
  --list-variables

# Execute with variables
java -jar analyzer-app/target/analyzer-app-1.0.0-SNAPSHOT.jar apply \
  --project /path/to/ejb-project \
  --plan migration-plans/jboss-to-springboot-phase0-1.yaml \
  -Dspring_boot_version=3.2.0 \
  -Djava_version=17 \
  --verbose
```

## Variable Priority Order

Variables are resolved in the following priority (highest to lowest):

1. **CLI `-D` flags** (e.g., `-Dspring_boot_version=3.2.0`)
2. **CLI `--variable` flags** (e.g., `--variable spring_boot_version=3.2.0`)
3. **Properties file** (via `--variables` option)
4. **YAML plan defaults** (defined in the migration plan)
5. **Environment variables** (using `${env.VAR_NAME}` syntax)

## Auto-Derived Variables

The following variables are automatically set and cannot be overridden:

- `project.root` - Absolute path to the project
- `project.name` - Name of the project directory
- `plan.name` - Name of the migration plan
- `current_date` - Current date in ISO format
- `current_datetime` - Current datetime in ISO format
- `user.name` - System user name
- `user.home` - User home directory

## Common Command Options

| Option | Description | Example |
|--------|-------------|---------|
| `--project` | Path to EJB project (required) | `--project /path/to/project` |
| `--plan` | Migration plan YAML file (required) | `--plan migration-plans/plan.yaml` |
| `-D<key>=<value>` | Set variable (highest priority) | `-Dspring_boot_version=3.2.0` |
| `--variable <key>=<value>` | Set variable | `--variable java_version=17` |
| `--variables <file>` | Load variables from file | `--variables vars.properties` |
| `--list-variables` | Show plan variables | `--list-variables` |
| `--dry-run` | Validate without executing | `--dry-run` |
| `--verbose` | Show all variables | `--verbose` |
| `--status` | Check migration status | `--status` |
| `--interactive` | Enable validation prompts | `--interactive` (default: true) |

## Environment Variable Substitution

You can reference environment variables in your values:

```properties
# Direct reference
database_host=${env.DB_HOST}

# With default value
database_port=${env.DB_PORT:-5432}
```

## Creating a Properties File

Example `migration-vars.properties`:

```properties
# Spring Boot Configuration
spring_boot_version=2.7.18
java_version=8

# Project Configuration
group_id=com.example
artifact_id=my-spring-app
package_base=com.example.app

# Migration Settings
migration_branch_prefix=migration

# Database (using environment variables)
database_url=${env.DATABASE_URL}
database_user=${env.DB_USER:-postgres}
database_password=${env.DB_PASSWORD}
```

## Troubleshooting

### Missing Variables Error
If you see "unresolved variable placeholders":
1. Check the variable name in the YAML plan
2. Provide the value via `-D`, `--variable`, or properties file
3. Set environment variables if using `${env.VAR_NAME}` syntax

### Plan Not Found
If the plan file is not found:
1. Check the path is correct
2. For classpath resources, use: `migration-plans/plan.yaml` (no leading slash)
3. For file system, use absolute or relative paths

### Project Directory Error
Ensure the `--project` path:
1. Points to an existing directory
2. Contains your EJB project files
3. Is readable by the current user

## Next Steps

1. Review the task specification: `docs/implementation/cli-apply-migration-command-task.md`
2. Examine the migration plan: `analyzer-core/src/main/resources/migration-plans/jboss-to-springboot-phase0-1.yaml`
3. Run the examples to understand the workflow
4. Create your own migration plan or customize the existing one
5. Execute the migration on your EJB project

## Support

For issues or questions:
- Check the main documentation
- Review the migration plan structure
- Examine the MigrationEngine implementation
- Look at test cases in `analyzer-core/src/test/java/com/analyzer/migration/`
