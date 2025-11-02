# Migration Plans

This directory contains external migration plans for various migration scenarios. Migration plans are stored outside the application codebase to allow for version control, sharing, and customization without rebuilding the application.

## Directory Structure

```
migrations/
├── README.md                          # This file
└── ejb2spring/                        # EJB 2 to Spring Boot migration
    ├── README.md                      # Detailed documentation
    ├── jboss-to-springboot.yaml       # Main migration plan
    ├── common/                        # Shared configuration
    │   ├── variables.yaml
    │   └── metadata.yaml
    └── phases/                        # Phase-specific tasks
        ├── phase0-assessment.yaml
        └── phase1-initialization.yaml
```

## Available Migration Plans

### EJB 2 to Spring Boot (`ejb2spring/`)

Comprehensive migration from JBoss EJB 2.x applications to Spring Boot 2.7.

**Features:**
- Modular YAML structure with includes
- Pre-migration assessment and analysis
- Automated Git branching and documentation
- Step-by-step Spring Boot project initialization
- AI-assisted dependency mapping

**Status:** Phase 0-1 Complete (Assessment & Initialization)

**Documentation:** See [ejb2spring/README.md](ejb2spring/README.md)

## Usage

### Basic Command

```bash
./analyzer apply \
  --project /path/to/project \
  --plan migrations/<migration-type>/<plan-file>.yaml
```

### Example

```bash
./analyzer apply \
  --project /path/to/jboss-app \
  --plan migrations/ejb2spring/jboss-to-springboot.yaml
```

## Creating New Migration Plans

### Directory Structure

1. Create a new directory for your migration type:
   ```bash
   mkdir -p migrations/<migration-type>/{common,phases}
   ```

2. Create the main plan file:
   ```bash
   touch migrations/<migration-type>/main-plan.yaml
   ```

3. Add common configuration:
   ```bash
   touch migrations/<migration-type>/common/{variables,metadata}.yaml
   ```

4. Add phase files:
   ```bash
   touch migrations/<migration-type>/phases/phase0-<name>.yaml
   ```

### Plan Structure

Follow this structure for consistency:

```yaml
migration-plan:
  name: "Migration Plan Name"
  version: "1.0.0"
  description: "Description"
  
  includes:
    - "common/metadata.yaml"
    - "common/variables.yaml"
    - "phases/phase0-xxx.yaml"
  
  phases: []
```

### Include Mechanism

The analyzer supports single-level YAML includes:

- **Relative paths**: Resolved from the main plan's directory
- **Single-level only**: Included files cannot have nested includes
- **Merge order**: Includes processed first, main file last (main overrides)

**Supported merging:**
- Variables: Later definitions override earlier ones
- Metadata: Non-null fields merged, main takes precedence
- Phases: Appended in order from all files

## Best Practices

### Version Control

✅ **DO:**
- Keep migration plans in version control
- Use semantic versioning for plan versions
- Document changes in plan descriptions
- Create branches for plan modifications

❌ **DON'T:**
- Hard-code project-specific paths
- Include sensitive credentials
- Create deeply nested directory structures

### Organization

✅ **DO:**
- Group related tasks into phases
- Use descriptive file names
- Keep individual files focused and manageable
- Document each phase's purpose

❌ **DON'T:**
- Create monolithic single-file plans
- Mix unrelated migration concerns
- Overuse variables (keep it simple)

### Variables

✅ **DO:**
- Use `${variable_name}` syntax
- Define common variables in `common/variables.yaml`
- Allow command-line overrides
- Use meaningful variable names

❌ **DON'T:**
- Hard-code values that may change
- Create circular variable references
- Use variables for static content

## Migration Plan Features

### Block Types Supported

- **COMMAND**: Execute shell commands
- **GIT**: Git operations
- **FILE_OPERATION**: Create/modify files
- **GRAPH_QUERY**: Query code graph database
- **AI_PROMPT**: AI-assisted analysis
- **INTERACTIVE_VALIDATION**: Manual checkpoints
- **OPENREWRITE**: Code refactoring recipes

### Execution Modes

- **Full plan**: Execute all phases and tasks
- **Phase-specific**: `--phase phase-0`
- **Task-specific**: `--task task-000`
- **Dry-run**: `--dry-run` (validation only)

### Variable Override

Priority order (highest to lowest):
1. Command-line `-D` flags
2. Command-line `--variable` flags
3. Properties file `--variables`
4. Main plan file
5. Included files

## Troubleshooting

### Common Issues

**Issue**: `File not found: migrations/xxx/plan.yaml`
**Solution**: Ensure path is correct and file exists

**Issue**: `Include file not found: common/variables.yaml`
**Solution**: Check relative path from main plan file

**Issue**: `Nested includes are not allowed`
**Solution**: Remove `includes` section from included files

**Issue**: `Unresolved variable: ${my_var}`
**Solution**: Define variable in plan or via command-line

### Debugging

Enable verbose logging:
```bash
./analyzer apply --plan <path> --verbose
```

Validate plan without execution:
```bash
./analyzer apply --plan <path> --dry-run
```

List all variables:
```bash
./analyzer apply --plan <path> --list-variables
```

## Contributing

When adding new migration plans:

1. Follow the established directory structure
2. Document the migration in a README.md
3. Provide example usage
4. Include troubleshooting section
5. Test with dry-run mode first

## Support

- **Documentation**: See individual migration plan READMEs
- **Examples**: Check `analyzer-app/src/test/java/com/analyzer/cli/examples/`
- **Issues**: Report via project issue tracker

## Version History

- **1.0.0** (2025-01-30): Initial structure with EJB 2 to Spring Boot migration
