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

### Required Dependencies for New Inspector Base Classes ✅ READY FOR INTEGRATION
Base classes implemented and ready for concrete implementations with these libraries:

- **JavaParser**: Java source code parsing (com.github.javaparser:javaparser-core)
  - Ready: `JavaParserInspector` base class implemented
- **Apache BCEL**: Bytecode Engineering Library (org.apache.bcel:bcel)  
  - Ready: `BCELInspector` base class implemented
- **Javassist**: Java bytecode manipulation (org.javassist:javassist)
  - Ready: `JavassistInspector` base class implemented
- **SonarSource Java**: Advanced Java parsing (org.sonarsource.java:java-frontend)
  - Ready: `SonarParserInspector` base class implemented
- **Roaster**: Code generation and manipulation (org.jboss.forge.roaster:roaster-api)
  - Ready: `RoasterInspector` base class implemented

**Integration Status**: All dependencies can now be added to pom.xml as concrete inspectors are implemented

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

## ✅ NEW Technical Insights from Refactoring

### Advanced Error Handling Patterns
- **Consistent Error Strategy**: All new base classes use `InspectorResult.error()` pattern
- **Input Validation**: Comprehensive null checks and parameter validation in constructors
- **Exception Translation**: IOException and general Exception properly caught and converted
- **Graceful Degradation**: Errors don't propagate but are contained within inspector results

### Template Method Pattern Implementation
- **Consistent Structure**: All base classes follow template method pattern for extensibility
- **Final Methods**: Core workflow methods marked final, extensible methods protected
- **Abstract Extension Points**: Clear abstract methods for subclass implementation
- **Resource Management**: Automatic resource cleanup in template methods

### Package Architecture Evolution
- **Logical Separation**: `core/binary/` and `core/source/` separate concerns clearly
- **Scalability**: Structure supports easy addition of new inspector categories
- **Dependency Management**: Core classes isolated from implementation-specific dependencies
- **Plugin Compatibility**: Package restructuring maintains plugin loading compatibility

### Quality Assurance Patterns
- **Parameter Validation**: All constructors validate inputs with meaningful error messages
- **Null Safety**: Comprehensive null checking throughout the hierarchy
- **Type Safety**: Proper use of generics and type parameters
- **Documentation Standards**: Consistent JavaDoc patterns across all base classes

### Resource Management Best Practices
- **Stream Handling**: Proper try-with-resources usage in content reading methods
- **UTF-8 Default**: Consistent encoding handling for source file analysis
- **Memory Efficiency**: Content processing designed for streaming large files
- **Error Recovery**: Resource access failures handled gracefully without system failure

## Performance Considerations
- **Lazy Loading**: Classes and resources loaded on-demand
- **Stream Processing**: Avoid loading entire files into memory
- **Caching**: Resource metadata cached to avoid repeated file system access
- **Batch Processing**: Efficient processing of multiple classes

## Security Considerations
- **Sandbox**: Plugin loading with restricted permissions
- **Input Validation**: Comprehensive validation of file paths and URIs
- **Resource Limits**: Memory and time limits for inspector execution
