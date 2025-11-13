# Search Java Pattern Tool

## Overview

The `search_java_pattern` tool is a new MCP tool that searches for Java code patterns based on LST (Lossless Semantic Tree) node types. This tool is currently in skeleton/prototype phase and returns mock data.

## Tool Signature

```java
public String searchJavaPattern(
    String projectPath,
    String patternDescription,
    String nodeType,
    List<String> filePaths  // optional
)
```

## Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `projectPath` | String | Yes | Absolute path to the Java project root directory |
| `patternDescription` | String | Yes | Description of the pattern to search for (e.g., 'singleton classes', 'static methods') |
| `nodeType` | String | Yes | Type of LST node to search for (see supported types below) |
| `filePaths` | List<String> | No | Optional list of relative file paths to search in. If not provided, searches all Java files in project |

## Supported Node Types

The tool supports the following OpenRewrite LST node types:

- `Binary`
- `Block`
- `ClassDeclaration`
- `CompilationUnit`
- `Expression`
- `FieldAccess`
- `Identifier`
- `MethodDeclaration`
- `MethodInvocation`
- `NewClass`
- `Statement`
- `VariableDeclarations`

## Response Format

The tool returns a JSON object with the following structure:

```json
{
  "matches": [
    {
      "nodeId": "node-123",
      "nodeType": "j.ClassDeclaration",
      "className": "MySingleton",
      "methodName": "findById",
      "fieldName": "maxConnections",
      "location": {
        "file": "/path/to/file.java",
        "line": 5,
        "column": 1
      }
    }
  ]
}
```

### Response Fields

| Field | Type | Optional | Description |
|-------|------|----------|-------------|
| `nodeId` | String | No | Unique identifier for the matched node |
| `nodeType` | String | No | The LST node type (prefixed with "j.") |
| `className` | String | Yes | Class name (for class-related nodes) |
| `methodName` | String | Yes | Method name (for method-related nodes) |
| `fieldName` | String | Yes | Field name (for field-related nodes) |
| `location` | Object | No | Location information |
| `location.file` | String | No | Full path to the source file |
| `location.line` | int | No | Line number (1-based) |
| `location.column` | int | No | Column number (1-based) |

## Current Status

**⚠️ PROTOTYPE PHASE**: This tool currently returns mock/fake data for testing purposes. The actual implementation that searches Java source files using OpenRewrite AST traversal is not yet implemented.

### Mock Behavior

The tool currently returns different mock results based on the `nodeType` parameter:

- **ClassDeclaration**: Returns a mock "MySingleton" class match
- **MethodInvocation**: Returns a mock "findById" method invocation in UserService
- **FieldAccess**: Returns a mock "maxConnections" field access in Configuration
- **Other types**: Returns a generic mock match

## Example Usage

```bash
# Example using the MCP tool
{
  "tool": "searchJavaPattern",
  "arguments": {
    "projectPath": "/home/user/my-project",
    "patternDescription": "singleton classes",
    "nodeType": "ClassDeclaration",
    "filePaths": [
      "src/main/java/com/example/MySingleton.java"
    ]
  }
}
```

## Implementation Files

- **Tool Class**: `com.analyzer.refactoring.mcp.tool.SearchJavaPatternTool`
- **Configuration**: `com.analyzer.refactoring.mcp.config.ToolConfiguration`
- **Registration**: Added to `danTools()` bean method

## Future Implementation

When implementing the actual search functionality, the following steps will be needed:

1. Use OpenRewrite's `JavaParser` to parse Java source files
2. Create a visitor that traverses the AST looking for nodes matching the specified type
3. Apply pattern matching logic based on the `patternDescription`
4. Extract location information from matched nodes
5. Generate unique node IDs for each match
6. Return structured results with proper metadata

The result model classes (`PatternSearchResult`, `PatternMatch`, `LocationInfo`) are already in place and ready to use.

## Testing

The tool can be tested by:

1. Rebuilding the MCP server: `mvn clean package -DskipTests`
2. Restarting the MCP server with STDIO profile
3. Invoking the tool through an MCP client
4. Verifying mock data is returned in the expected format

## Notes

- The optional fields (`className`, `methodName`, `fieldName`) should be populated based on the node type and context
- Node IDs should be unique and stable across multiple searches
- The tool extends `BaseRefactoringTool` for JSON serialization support
- Uses Spring AI's `@Tool` and `@ToolParam` annotations for MCP exposure
