# Java Refactoring MCP Server

A standalone Model Context Protocol (MCP) server that provides Java refactoring capabilities using Eclipse JDT (Java Development Tools). This module enables AI assistants to perform automated refactoring operations on Java codebases.

## Overview

This MCP server implements a simplified Model Context Protocol over stdio, providing Java refactoring capabilities. It allows AI models to:

- Rename Java elements (classes, methods, fields, variables)
- Parse Java code using Eclipse JDT AST parser
- Validate refactoring operations before execution
- Provide detailed feedback on refactoring results

## Technology Stack

- **Eclipse JDT**: Java Development Tools for parsing and analysis
- **Jackson**: JSON processing
- **Java 21**: Required JDK version
- **SLF4J/Logback**: Logging

## Architecture

The module follows a clean architecture pattern:

```
analyzer-refactoring-mcp/
├── src/main/java/com/analyzer/refactoring/mcp/
│   ├── RefactoringMcpServer.java          # MCP server (stdio transport)
│   └── service/
│       └── JdtRefactoringService.java     # JDT refactoring service
└── pom.xml
```

## Building the Module

```bash
# Build the entire project including this module
mvn clean install

# Build only this module
cd analyzer-refactoring-mcp
mvn clean package
```

The build produces: `target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar`

## Running the Server

The server communicates over stdin/stdout using JSON-RPC messages:

```bash
java -cp target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar com.analyzer.refactoring.mcp.RefactoringMcpServer
```

## MCP Protocol

The server implements a simplified MCP protocol with the following methods:

### initialize

Initialize the MCP server and get capabilities.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {}
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "1.0.0",
    "serverInfo": {
      "name": "java-refactoring-mcp",
      "version": "1.0.0"
    },
    "capabilities": {
      "tools": true
    }
  }
}
```

### tools/list

List available refactoring tools.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "rename_java_element",
        "description": "Rename a Java element and update all references",
        "inputSchema": {
          "type": "object",
          "properties": {
            "projectPath": {"type": "string", "description": "..."},
            "filePath": {"type": "string", "description": "..."},
            "elementName": {"type": "string", "description": "..."},
            "newName": {"type": "string", "description": "..."},
            "updateReferences": {"type": "boolean", "description": "..."}
          },
          "required": ["projectPath", "filePath", "elementName", "newName"]
        }
      }
    ]
  }
}
```

### tools/call

Call a refactoring tool.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "rename_java_element",
    "arguments": {
      "projectPath": "/path/to/project",
      "filePath": "src/main/java/com/example/MyClass.java",
      "elementName": "MyClass",
      "newName": "MyRenamedClass",
      "updateReferences": true
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "success": true,
    "messages": [
      "Refactoring simulation completed successfully",
      "Would rename 'MyClass' to 'MyRenamedClass'",
      "Update references: true"
    ],
    "changes": {
      "src/main/java/com/example/MyClass.java": "Element renamed"
    }
  }
}
```

## Integration with Cline/Claude Desktop

### MCP Configuration

Add to your MCP settings configuration file:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-cp",
        "/path/to/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar",
        "com.analyzer.refactoring.mcp.RefactoringMcpServer"
      ]
    }
  }
}
```

### Usage Example

Once configured, AI assistants can use the refactoring tools via MCP:

```
User: "Rename the class UserService to UserServiceImpl in my project"

AI: I'll use the rename_java_element tool via MCP.
    [Sends MCP message to call the tool with appropriate parameters]
    
    The refactoring completed successfully. The class has been renamed 
    and all references have been updated.
```

## Eclipse JDT Integration

The service uses Eclipse JDT's AST parser to analyze Java code:

```java
ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
parser.setSource(source.toCharArray());
parser.setKind(ASTParser.K_COMPILATION_UNIT);
parser.setResolveBindings(false);
parser.setUnitName(unitName);

CompilationUnit cu = (CompilationUnit) parser.createAST(null);
```

### Example Code from Your Requirement

The module is designed to eventually support the full Eclipse JDT refactoring workflow:

```java
// Create a descriptor for the rename refactoring
RenameJavaElementDescriptor descriptor = new RenameJavaElementDescriptor();
descriptor.setJavaElement(elementToRename);
descriptor.setNewName(newName);
descriptor.setUpdateReferences(true);

// Create the refactoring instance
Refactoring refactoring = descriptor.createRefactoring();

// Check initial conditions
RefactoringStatus status = refactoring.checkInitialConditions(new NullProgressMonitor());

// Perform the refactoring
PerformRefactoringOperation op = new PerformRefactoringOperation(
    refactoring, CheckConditionsOperation.ALL_CONDITIONS);
op.run(new NullProgressMonitor());
```

## Current Implementation Status

### Implemented
- ✅ Standalone MCP server with stdio transport
- ✅ JSON-RPC message handling
- ✅ Tool discovery and listing
- ✅ Basic Java file parsing with JDT AST
- ✅ Input validation
- ✅ Error handling and reporting

### Simulation Mode
The current implementation provides a **simulation mode** that:
- Validates inputs (project path, file path, element names)
- Parses Java files using Eclipse JDT AST parser
- Returns success messages indicating what would be renamed

### Future Enhancements

To implement full refactoring capabilities:

- [ ] Full Eclipse workspace integration
- [ ] Actual rename refactoring execution
- [ ] Reference finding and updating
- [ ] Extract Method refactoring
- [ ] Move Class refactoring
- [ ] Change Method Signature
- [ ] Extract Interface
- [ ] Pull Up/Push Down members
- [ ] Refactoring preview and rollback

## Development

### Adding New Refactoring Tools

1. Add a method in `JdtRefactoringService`
2. Add a tool definition in `RefactoringMcpServer.handleListTools()`
3. Add a case in `RefactoringMcpServer.handleToolCall()`

Example structure:

```java
private JsonNode handleExtractMethod(JsonNode id, JsonNode arguments) {
    // Parse arguments
    // Call refactoring service
    // Return result
}
```

### Running Tests

```bash
mvn test
```

### Logging

Configure logging in `src/main/resources/logback.xml`:

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="com.analyzer.refactoring.mcp" level="DEBUG"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

## Limitations

1. **Workspace Setup**: Full Eclipse JDT refactoring requires a complete Eclipse workspace with project metadata. The current implementation works in simulation mode.

2. **Classpath Resolution**: For full refactoring with reference updates, the entire project classpath must be resolved.

3. **Build Tool Integration**: Best results require integration with the project's build tool (Maven/Gradle) for dependency resolution.

## Troubleshooting

### Server Won't Start
- Ensure Java 21 or later is installed
- Check that all dependencies are properly resolved: `mvn dependency:tree`

### Cannot Parse Java Files
- Verify the file paths are correct and relative to the project root
- Ensure the Java files are syntactically correct

### Missing Dependencies
- Run `mvn clean install` from the parent project directory
- Check that the Spring milestones repository is accessible

## Contributing

Contributions are welcome! Please:

1. Follow the existing code structure
2. Add unit tests for new functionality
3. Update documentation
4. Follow Java coding conventions

## License

This module is part of the Java Architecture Analyzer project.

## Related Documentation

- [Eclipse JDT Documentation](https://www.eclipse.org/jdt/)
- [Model Context Protocol Specification](https://spec.modelcontextprotocol.io/)
- [Parent Project README](../README.md)

## Notes

This implementation was designed to use Spring AI MCP, but that project has been archived and moved. The current standalone implementation provides a working MCP server that can be extended with full Eclipse workspace integration for production use.
