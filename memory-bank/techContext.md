# Technical Context

## Technology Stack

### Core Technologies
- **Java**: Primary development language
- **Maven**: Build and dependency management
- **Picocli**: Command-line interface framework
- **SLF4J + Logback**: Logging framework
- **JUnit 5**: Testing framework

### Current Dependencies
- **ASM**: Bytecode analysis library (org.ow2.asm)
- **Picocli**: CLI framework
- **SLF4J**: Logging abstraction
- **Logback**: Logging implementation

### Planned Dependencies
Additional libraries needed for missing inspector implementations:

- **JavaParser**: Java source code parsing (com.github.javaparser:javaparser-core)
- **Apache BCEL**: Bytecode Engineering Library (org.apache.bcel:bcel)
- **Javassist**: Java bytecode manipulation (org.javassist:javassist)
- **SonarSource Java**: Advanced Java parsing (org.sonarsource.java:java-frontend)
- **Roaster**: Code generation and manipulation (org.jboss.forge.roaster:roaster-api)

## Development Environment
- **Build Tool**: Maven 3.x
- **Java Version**: Java 11+ (supports modern language features)
- **IDE**: IntelliJ IDEA / Eclipse / VS Code
- **Testing**: JUnit 5 with Maven Surefire plugin

## Architecture Constraints
- **Plugin Loading**: Dynamic JAR loading from plugins directory
- **Resource Access**: URI-based resource resolver system
- **Error Handling**: Comprehensive error reporting without failing entire analysis
- **Memory Management**: Stream-based processing for large files
- **Thread Safety**: Inspectors must be stateless for concurrent execution

## Performance Considerations
- **Lazy Loading**: Classes and resources loaded on-demand
- **Stream Processing**: Avoid loading entire files into memory
- **Caching**: Resource metadata cached to avoid repeated file system access
- **Batch Processing**: Efficient processing of multiple classes

## Security Considerations
- **Sandbox**: Plugin loading with restricted permissions
- **Input Validation**: Comprehensive validation of file paths and URIs
- **Resource Limits**: Memory and time limits for inspector execution
