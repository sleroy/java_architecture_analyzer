# JBoss EJB 2 to Spring Boot Migration Plan

This directory contains a comprehensive migration plan for converting JBoss EJB 2 applications to Spring Boot.

## Configuration

### Spring Boot Version

The migration plan uses **Spring Boot 3.5.7** by default, which is a stable and well-supported version.

To change the Spring Boot version, update the variable in `common/variables.yaml`:

```yaml
migration-plan:
  variables:
    spring_boot_version: "3.5.7"  # Change this to your desired version (>= 3.4.0)
    java_version: "17"             # Java 17+ required for Spring Boot 3.x
```

Or override via command line:

```bash
analyzer apply --project /path/to/project \
               --plan migrations/ejb2spring/jboss-to-springboot.yaml \
               -Dspring_boot_version=3.5.7 \
               -Djava_version=17
```

### Important Version Requirements

- **Spring Boot >= 3.4.0** - Earlier versions are no longer supported by Spring Initializr
- **Java >= 17** - Required for Spring Boot 3.x
- **Java 11** - Only works with Spring Boot 2.x (deprecated)

### Spring Boot 2.x Migration Path

If you need to use Spring Boot 2.x:

1. Spring Boot 2.7.x is EOL (End of Life) and not supported by Spring Initializr
2. Consider upgrading directly to Spring Boot 3.4.0+
3. Manual migration required if Spring Boot 2.x is mandatory

## Migration Plan Structure

```
migrations/ejb2spring/
├── jboss-to-springboot.yaml    # Main migration plan
├── README.md                    # This file
├── common/
│   ├── metadata.yaml           # Plan metadata
│   ├── variables.yaml          # Configuration variables
│   └── templates/              # Code templates
└── phases/
    ├── phase0-assessment.yaml
    ├── phase1-initialization.yaml
    ├── phase1b-refactoring-setup.yaml
    ├── phase2-jdbc-migration.yaml
    └── ...
```

## Key Variables

All variables are defined in `common/variables.yaml`:

| Variable | Default | Description |
|----------|---------|-------------|
| `spring_boot_version` | 3.5.7 | Spring Boot version for the migration |
| `java_version` | 17 | Java version (17+ for Spring Boot 3.x) |
| `group_id` | br.com.semeru | Maven groupId for new project |
| `artifact_id` | semeru-springboot | Maven artifactId for new project |
| `package_base` | br.com.semeru | Base package for Java classes |
| `database_enabled` | false | Enable database schema operations |
| `read_dependencies` | false | Extract and analyze dependencies |

## Usage

### Basic Migration

```bash
# Run complete migration with default settings
analyzer apply --project /path/to/ejb-project \
               --plan migrations/ejb2spring/jboss-to-springboot.yaml
```

### Custom Spring Boot Version

```bash
# Use specific Spring Boot version
analyzer apply --project /path/to/ejb-project \
               --plan migrations/ejb2spring/jboss-to-springboot.yaml \
               -Dspring_boot_version=3.5.7 \
               -Djava_version=17
```

### Step-by-Step Migration

```bash
# Interactive mode with manual confirmation before each step
analyzer apply --project /path/to/ejb-project \
               --plan migrations/ejb2spring/jboss-to-springboot.yaml \
               --step-by-step
```

## Migration Phases

1. **Phase 0: Pre-Migration Assessment** - Analyze EJB components and dependencies
2. **Phase 1: Spring Boot Initialization** - Create Spring Boot project structure
3. **Phase 2: JDBC Migration** - Convert database access to Spring JDBC
4. **Phase 2B: Entity Bean Migration** - Convert entity beans to JPA entities
5. **Phase 3: Session Bean Migration** - Convert session beans to Spring services
6. **Phase 3B: Message-Driven Beans** - Convert MDBs to Spring messaging
7. **Phase 3C: EJB Interface Removal** - Remove EJB-specific interfaces
8. **Phase 4-8: Integration** - SOAP, REST, configuration, testing, packaging
9. **Phase 9: JDK Upgrade** - Upgrade from Java 8 to Java 21
10. **Phase 10: Legacy Antipatterns** - Fix common antipatterns

## Requirements

- Java 17 or higher
- Maven 3.6+
- Git (for version control integration)
- Amazon Q CLI (for AI-assisted code generation)

## Troubleshooting

### Spring Initializr Version Error

If you see: `Invalid Spring Boot version '2.7.18', Spring Boot compatibility range is >=3.4.0`

**Solution**: Update `spring_boot_version` to 3.4.0 or higher in `common/variables.yaml`

### Curl Command Failures

If the Spring Initializr download fails, verify:
1. Network connectivity to https://start.spring.io
2. Spring Boot version is >= 3.4.0
3. All dependency names are valid (check Spring Initializr documentation)

## Documentation

- [Migration Plan Schema](../SCHEMA_VALIDATION.md)
- [Implementation Details](../../docs/implementation/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review execution logs in `.analysis/` directory
3. Consult the implementation documentation in `docs/implementation/`
