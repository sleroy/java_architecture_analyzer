# Technical Reference (Consolidated)

## Architecture Overview

### Inspector Pattern Foundation
- **Base Classes**: 11 total (SourceFileInspector, BinaryClassInspector, ASMInspector, etc.)
- **Package Structure**: `inspectors/core/` with binary/ and source/ subdirectories
- **Key Patterns**: Factory (Registry), Strategy (Implementations), Template Method (Base classes)

### Graph System (Implemented)
- **Node Types**: JavaClassNode, CMPEntityBeanNode, StatefulSessionBeanNode, etc.
- **Edge Types**: IMPLEMENTS_INTERFACE, JNDI_LOOKUP, CMR_RELATIONSHIP, etc.
- **Integration**: GraphAwareInspector interface for relationship analysis

### Technology Stack
- **Java 11+**, **Maven 3.x**, **Picocli** (CLI)
- **ASM** (bytecode), **JavaParser** (source), **SLF4J+Logback** (logging)
- **JUnit 5** (testing), **AbstractClassLoaderBasedInspector** (runtime analysis)

### Key Dependencies
```xml
<!-- Essential for current implementation -->
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.bcel</groupId>
    <artifactId>bcel</artifactId>
</dependency>
```

## Implementation Patterns (Proven)

### Inspector Templates
- **ASM Bytecode**: CmpFieldMappingInspector pattern for method detection
- **JavaParser Source**: BusinessDelegatePatternInspector pattern for structural analysis
- **XML Parsing**: DeclarativeTransactionInspector pattern for descriptor analysis
- **Runtime ClassLoader**: InheritanceDepthInspector pattern for dynamic analysis

### Critical Success Patterns
- **supports() Method**: Use `getFilePath().toString()` for reflection-set paths
- **Tag Dependencies**: Check EjbMigrationTags constants in supports()
- **Error Handling**: InspectorResult.error() with graceful degradation
- **Testing**: Stack trace-based method detection for dynamic test expectations

### Customer Requirements
- **JDBC-Only Persistence**: No CMP/JPA patterns
- **JBoss-Only Vendor**: No WebLogic/Generic configurations
- **ClassLoader Metrics**: Runtime inheritance, interface, type usage analysis

## Migration Context

### EJB to Spring Boot Conversion
- **Reference**: Microsoft Azure blog on EJB to Spring Boot with GitHub Copilot
- **Target**: Legacy J2EE applications to Spring Boot 3/Jakarta EE
- **Focus**: Transaction boundaries, JNDI lookups, entity relationships

### Analysis Capabilities
- **Source + Bytecode**: Unified analysis from multiple sources
- **CSV Export**: Integration with analysis workflows
- **Plugin System**: Extensible architecture for custom inspectors
- **Graph Analysis**: Relationship mapping for migration planning
