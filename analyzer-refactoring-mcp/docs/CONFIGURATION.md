# Configuring AI Tools to Use the Refactoring MCP Server

This guide explains how to configure various AI tools (Amazon Q, Claude Desktop, Cline, etc.) to use the Java Refactoring MCP Server.

## Prerequisites

1. Build the MCP server:
```bash
cd analyzer-refactoring-mcp
mvn clean package
```

2. Note the JAR location:
```bash
target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar
```

## Running the MCP Server

The MCP server uses STDIO transport and can be started with:

```bash
java -jar /path/to/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar
```

## Configuration for Different AI Tools

### Amazon Q Developer (VS Code Extension)

Amazon Q doesn't directly support MCP servers yet. However, you can:

1. **Use Amazon Q Agent Blocks** (if available):
   - Create custom agent blocks that invoke the MCP server
   - See `docs/implementation/amazon-q-agent-blocks-design.md` for design ideas

2. **Alternative: Use Cline with Amazon Bedrock**:
   - Configure Cline to use Bedrock models
   - Add MCP server to Cline's configuration (see below)

### Cline (VS Code Extension)

Cline has built-in MCP support. Add this to your Cline MCP settings:

**Location**: VS Code Settings → Extensions → Cline → MCP Servers

**Configuration** (`~/.config/Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json`):

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"
      ],
      "env": {
        "SPRING_OUTPUT_ANSI_ENABLED": "NEVER"
      }
    }
  }
}
```

**Update the path**:
```json
"/home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"
```

### Claude Desktop

Add to Claude Desktop's MCP configuration:

**Location**: `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS)  
or `%APPDATA%\Claude\claude_desktop_config.json` (Windows)  
or `~/.config/Claude/claude_desktop_config.json` (Linux)

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"
      ],
      "env": {
        "SPRING_OUTPUT_ANSI_ENABLED": "NEVER"
      }
    }
  }
}
```

### Generic MCP Client Configuration

For any MCP-compatible client, use this configuration pattern:

```json
{
  "command": "java",
  "args": ["-jar", "/path/to/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"],
  "transport": "stdio",
  "env": {
    "SPRING_OUTPUT_ANSI_ENABLED": "NEVER"
  }
}
```

## Available Tools

Once configured, the following tools will be available to the AI:

### Rename Tools
- `renameType` - Rename Java types (classes, interfaces, enums, records, annotations)
- `renameMethod` - Rename methods with virtual method hierarchy support
- `renameField` - Rename fields with optional getter/setter updates
- `renamePackage` - Rename packages and update all references
- `renameCompilationUnit` - Rename Java source files
- `renameJavaProject` - Rename Eclipse project metadata
- `renameEnumConstant` - Rename enum constants
- `renameModule` - Rename Java 9+ modules
- `renameResource` - Rename non-Java resources
- `renameSourceFolder` - Rename source folders

### Other Tools
- `deleteElements` - Delete Java elements and resources
- `copyElements` - Copy elements to new locations
- `moveElements` - Move elements with reference updates
- `moveStaticMembers` - Move static members between classes

## Tool Usage Examples

### Using with Cline

After configuration, you can ask Cline to perform refactorings:

```
"Rename the class CustomerService to ClientService in the project at /path/to/project"

"Move the utility methods from Utils.java to MathUtils.java in /path/to/project"

"Delete the deprecated OrderProcessor class from /path/to/project"
```

Cline will automatically use the appropriate MCP tools.

### Verifying Configuration

1. **Restart the AI tool** after adding the configuration

2. **Check tool availability**:
   - In Cline: Look for the MCP server icon in the sidebar
   - In Claude Desktop: Tools should appear in the tool list

3. **Test a simple command**:
   ```
   "List the available refactoring tools"
   ```

## Troubleshooting

### Server doesn't start

**Issue**: JAR file not found
```
Solution: Verify the absolute path to the JAR file
```

**Issue**: Java not found
```
Solution: Ensure Java 21+ is installed and in PATH
java --version
```

**Issue**: Spring Boot startup errors
```
Solution: Check application.properties and dependencies
mvn dependency:tree
```

### Tools not available

**Issue**: MCP server not connected
```
Solution: 
1. Check the AI tool's MCP server status
2. Verify the configuration file location
3. Restart the AI tool
4. Check server logs in the AI tool's output panel
```

**Issue**: Tools appear but fail
```
Solution:
1. Ensure the project path is correct
2. Verify Eclipse .project file exists
3. Check file permissions
```

### Performance Issues

**Issue**: Slow tool responses
```
Solution:
1. Increase JVM heap size:
   "args": ["-Xmx2g", "-jar", "..."]
2. Ensure adequate disk I/O
3. Close other Java processes
```

## Environment Variables

You can customize the server behavior with environment variables:

```json
"env": {
  "SPRING_OUTPUT_ANSI_ENABLED": "NEVER",
  "LOGGING_LEVEL_ROOT": "INFO",
  "LOGGING_LEVEL_COM_ANALYZER": "DEBUG"
}
```

## Development Mode

For development, you can run the server directly:

```bash
cd analyzer-refactoring-mcp
mvn spring-boot:run
```

Then configure the MCP client to use:
```json
{
  "command": "mvn",
  "args": ["-f", "/path/to/analyzer-refactoring-mcp/pom.xml", "spring-boot:run"],
  "cwd": "/path/to/analyzer-refactoring-mcp"
}
```

## Security Considerations

⚠️ **Important Security Notes**:

1. The MCP server has full access to the file system
2. Only use with trusted AI tools and projects
3. Review refactoring operations before applying
4. Keep backups of your code
5. Use version control (git) to track changes

## Next Steps

1. ✅ Configure your AI tool using the instructions above
2. ✅ Test with a simple refactoring operation
3. ✅ Review the tool documentation in the Spring AI docs
4. ✅ Read the Spring AI 1.0.3 update documentation: `docs/implementation/spring-ai-1.0.3-tool-updates.md`

## Support

For issues or questions:
- Check the troubleshooting section above
- Review Spring AI documentation: https://docs.spring.io/spring-ai/
- Check MCP specification: https://modelcontextprotocol.io/

## Related Documentation

- [Spring AI 1.0.3 Tool Updates](../docs/implementation/spring-ai-1.0.3-tool-updates.md)
- [Amazon Q Agent Blocks Design](../docs/implementation/amazon-q-agent-blocks-design.md)
- [MCP Server README](README.md)
