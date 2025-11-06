# EJB 2 to Spring Boot Migration Examples

This directory contains example configurations for running the EJB 2 to Spring Boot migration tool on different projects.

## Overview

The migration tool uses YAML configuration files to customize the migration process for specific projects. Example configurations demonstrate:

- How to configure project paths
- How to override default variables
- How to adapt the migration for different project structures
- How to ensure source files are not modified during testing

## Available Examples

### demo-ejb2-project-example.yaml

Example configuration for migrating the `demo-ejb2-project` included in this repository.

**Key Features:**
- Configures migration for the demo EJB 2.0 project
- Creates migrated Spring Boot project in separate directory
- Preserves original demo project files (read-only)
- Includes EJB 2.0 CMP, MDB, and Session Bean examples

**Usage:**

```bash
# From the java_architecture_analyzer root directory
cd /path/to/java_architecture_analyzer

# Run migration on demo project
./analyzer-app/target/analyzer-app apply \
  --project demo-ejb2-project \
  --plan migrations/ejb2spring/jboss-to-springboot.yaml \
  --config migrations/ejb2spring/examples/demo-ejb2-project-example.yaml
```

**What it does:**
1. Reads source files from `demo-ejb2-project/src/main/java/`
2. Creates new Spring Boot project at `demo-ejb2-project/target/example/springboot/`
3. Migrates EJB components to Spring equivalents
4. Generates Spring Boot POM with appropriate dependencies
5. Does NOT modify any files in the original demo project
6. Output in target/ folder follows Maven convention and is cleaned with `mvn clean`

**Project Structure After Migration:**

```
demo-ejb2-project/
├── pom.xml                    # Original (unchanged)
├── README.md                  # Original (unchanged)
├── src/                       # Original source (unchanged)
│   └── main/
│       └── java/
└── target/                    # Maven build directory (gitignored)
    └── example/springboot/     # NEW - Migrated Spring Boot project
        ├── pom.xml            # Generated Spring Boot POM
        ├── src/
        │   └── main/
        │       ├── java/      # Migrated Java sources
        │       └── resources/
        └── docs/              # Migration documentation
```

**Benefits of using target/ directory:**
- Follows Maven convention (target/ is standard build output)
- Automatically gitignored (no need to track generated files)
- Easily cleaned with `mvn clean`
- Clearly separates source from generated artifacts

## Configuration Variables

Each example can override these key variables:

### Project Paths

```yaml
variables:
  # Root directory of the source project (read-only)
  project_root: "${user.dir}/demo-ejb2-project"
  
  # Output directory for migration artifacts (Maven convention)
  migration_output_dir: "${user.dir}/demo-ejb2-project/target"
  
  # Name of the generated Spring Boot project directory
  artifact_id: "example/springboot"
```

### Spring Boot Configuration

```yaml
variables:
  # Spring Boot version to use
  spring_boot_version: "3.5.7"
  
  # Java version for compilation
  java_version: "21"
  
  # Maven coordinates for the new project
  group_id: "com.example.ejbapp"
  package_base: "com.example.ejbapp"
```

### Maven Environment

```yaml
variables:
  # Path to Java installation
  maven_java_home: "/usr/lib/jvm/java-21-openjdk-amd64/"
  
  # Path to Maven installation
  maven_home: "/home/sleroy/mvn"
  
  # Maven JVM options
  maven_opts: "-Xmx1024m"
```

### Feature Flags

```yaml
variables:
  # Enable/disable database operations
  database_enabled: false
  
  # Enable dependency analysis
  read_dependencies: false
  
  # Enable dry-run mode (no file modifications)
  dry_run: false
```

## Creating Your Own Example

To create an example configuration for your own project:

1. **Copy an existing example:**
   ```bash
   cp demo-ejb2-project-example.yaml my-project-example.yaml
   ```

2. **Update the project path:**
   ```yaml
   variables:
     project_root: "/path/to/your/ejb2/project"
   ```

3. **Customize artifact details:**
   ```yaml
   variables:
     artifact_id: "your-springboot-app"
     group_id: "com.yourcompany"
     package_base: "com.yourcompany"
   ```

4. **Adjust Maven paths for your system:**
   ```yaml
   variables:
     maven_java_home: "/path/to/your/jdk"
     maven_home: "/path/to/your/maven"
   ```

5. **Run the migration:**
   ```bash
   analyzer apply \
     --project /path/to/your/ejb2/project \
     --plan migrations/ejb2spring/jboss-to-springboot.yaml \
     --config migrations/ejb2spring/examples/my-project-example.yaml
   ```

## Testing Examples

For testing the migration framework without modifying source files:

```bash
# Run with test configuration (read-only)
analyzer apply \
  --project demo-ejb2-project \
  --plan analyzer-core/src/test/resources/test-plans/demo-ejb2-test-plan.yaml
```

The test plan:
- Validates project structure
- Counts source files
- Verifies no modifications were made
- Does NOT perform actual migration

## Important Notes

### Read-Only Source Protection

All example configurations are designed to:
- **NEVER modify original source files**
- Create all migration artifacts in separate directories
- Use only read operations on the source project
- Allow safe testing and experimentation

### Git Integration

The migration tool can optionally:
- Create Git branches for migration steps
- Commit migration artifacts
- Track migration history

To enable Git integration:
```yaml
variables:
  git_enabled: true
  migration_branch_prefix: "migration"
```

### AI-Assisted Migration

The tool uses AI to assist with:
- Code pattern recognition
- Dependency mapping
- Best practice application
- Migration complexity analysis

AI providers can be configured via environment variables:
```bash
export AI_BACKEND=AMAZON_Q
export AWS_REGION=us-east-1
```

## Troubleshooting

### Issue: Project not found

**Error:** `ERROR: demo-ejb2-project not found at /path/to/project`

**Solution:** 
- Verify the `project_root` variable points to the correct directory
- Use absolute paths or ensure relative paths are correct
- Check that the project directory exists and contains `pom.xml`

### Issue: Maven errors

**Error:** Maven validation or compilation failures

**Solution:**
- Verify `maven_java_home` points to a valid JDK installation
- Ensure `maven_home` points to your Maven installation
- Check that Java and Maven versions are compatible
- Review Maven output for specific dependency issues

### Issue: Permission denied

**Error:** Cannot write to output directory

**Solution:**
- Ensure you have write permissions in the output directory
- Check that the `artifact_id` directory doesn't already exist
- Verify disk space is available

## Additional Resources

- **Main Migration Plan:** `migrations/ejb2spring/jboss-to-springboot.yaml`
- **Phase Documentation:** `migrations/ejb2spring/phases/`
- **Common Variables:** `migrations/ejb2spring/common/variables.yaml`
- **Test Plans:** `analyzer-core/src/test/resources/test-plans/`

## Support

For issues or questions:
1. Review the phase-specific YAML files in `migrations/ejb2spring/phases/`
2. Check the implementation documentation in `docs/implementation/`
3. Review test cases in `analyzer-core/src/test/`
4. Examine the demo-ejb2-project README for EJB 2.0 concepts

---

**Last Updated:** January 2025  
**Migration Tool Version:** 2.0.0
