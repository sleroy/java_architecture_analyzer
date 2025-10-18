# Technical Context

## Technology Stack
- **Java 11+**: Primary language with modern features
- **Maven 3.x**: Build and dependency management
- **Picocli**: CLI framework
- **SLF4J + Logback**: Logging
- **JUnit 5**: Testing framework
- **ASM**: Bytecode analysis (current)

## Ready Dependencies
Base classes implemented for:
- **JavaParser**: Java source parsing
- **Apache BCEL**: Bytecode engineering
- **Javassist**: Runtime bytecode manipulation
- **SonarSource**: Advanced parsing
- **Roaster**: Code generation

## Architecture Constraints
- Plugin loading from JAR files
- URI-based resource access
- Stateless inspectors for concurrency
- Stream-based processing for memory efficiency
- Comprehensive error handling without system failure

## Quality Patterns
- Template method pattern in base classes
- Consistent error handling (InspectorResult.error())
- Input validation and null safety
- Resource management (try-with-resources)