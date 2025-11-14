# MCP Server Configuration Guide

This document provides advanced configuration options for the Java Refactoring MCP Server. For basic setup instructions, see the [main README](../README.md).

## Quick Links

- **Basic Setup**: See [README - Setup Guides](../README.md#setup-guides)
- **Amazon Q Setup**: [README - Amazon Q Developer](../README.md#amazon-q-developer-setup)
- **Cline Setup**: [README - Cline](../README.md#cline-setup)
- **Claude Desktop Setup**: [README - Claude Desktop](../README.md#claude-desktop-setup)

---

## Advanced Configuration

### Environment Variables

Customize server behavior with environment variables in your MCP configuration:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [...],
      "env": {
        "SPRING_OUTPUT_ANSI_ENABLED": "NEVER",
        "PROJECT_PATH": "/path/to/your/project",
        "LOGGING_LEVEL_ROOT": "INFO",
        "LOGGING_LEVEL_COM_ANALYZER_REFACTORING_MCP": "DEBUG"
      }
    }
  }
}
```

**Available Environment Variables:**
- `PROJECT_PATH` - Project root directory (alternative to --project.path)
- `SPRING_OUTPUT_ANSI_ENABLED` - Disable ANSI colors (recommended: NEVER)
- `LOGGING_LEVEL_ROOT` - Root logging level (INFO, DEBUG, WARN, ERROR)
- `LOGGING_LEVEL_COM_ANALYZER_REFACTORING_MCP` - MCP server logging level

### JVM Options

Add JVM options for performance tuning:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-Xmx2g",
        "-Xms512m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=200",
        "-jar",
        "/path/to/mcp-server.jar",
        "--spring.profiles.active=stdio",
        "--project.path=/path/to/project"
      ]
    }
  }
}
```

**Recommended JVM Options:**
- `-Xmx2g` - Maximum heap size (adjust based on project size)
- `-Xms512m` - Initial heap size
- `-XX:+UseG1GC` - Use G1 garbage collector (better for server workloads)
- `-XX:MaxGCPauseMillis=200` - Target max GC pause time

### Spring Profiles

The server supports multiple Spring profiles:

**STDIO Profile (Default for MCP)**:
```bash
--spring.profiles.active=stdio
```
- Minimal console output
- Logs to file (./logs/mcp-server.log)
- Optimized for MCP STDIO transport

**HTTP Profile (For Debugging)**:
```bash
--spring.profiles.active=http
```
- Enables web server on port 8080
- SSE endpoint at /sse
- CORS enabled for development
- See [SSE_SETUP.md](../SSE_SETUP.md) for details

### Database Configuration

When H2 database is available, you can configure additional options:

```bash
--spring.datasource.url=jdbc:h2:file:/path/to/.analyzer/graph.db
--spring.datasource.username=sa
--spring.datasource.password=
```

**Note**: Database configuration is automatically handled based on `--project.path`. Manual configuration is rarely needed.

---

## Tool-Specific Configuration

### Cline Auto-Approval

Skip confirmation prompts for trusted tools:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [...],
      "alwaysAllow": [
        "extractClassMetadataCompact",
        "searchJavaPattern",
        "getDependencyGraph"
      ]
    }
  }
}
```

### Amazon Q Environment Settings

Amazon Q may require additional environment configuration:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [...],
      "env": {
        "SPRING_OUTPUT_ANSI_ENABLED": "NEVER",
        "JAVA_TOOL_OPTIONS": "-Dfile.encoding=UTF-8"
      }
    }
  }
}
```

---

## Development Mode

### Running from Source

For development, run directly from Maven:

```bash
cd analyzer-refactoring-mcp
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=stdio --project.path=/path/to/project"
```

### Debug Configuration

Enable debug output:

```bash
java -jar mcp-server.jar \
  --spring.profiles.active=stdio \
  --project.path=/path/to/project \
  --logging.level.com.analyzer.refactoring.mcp=DEBUG \
  --logging.level.org.springframework.ai.mcp=DEBUG
```

### Hot Reload (With Spring DevTools)

Add to pom.xml for development:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

Then changes to code will auto-reload (requires IDE support).

---

## Security Configuration

### Restrict File Operations

The PathSecurityValidator restricts operations to the project root by default. This cannot be disabled for security reasons.

### Custom Security Rules

If you need additional security restrictions, you can:

1. Fork the project
2. Modify `PathSecurityValidator.java`
3. Add custom validation rules
4. Rebuild and deploy

**Warning**: Removing security restrictions is not recommended.

---

## Performance Tuning

### For Large Projects (5000+ classes)

```json
{
  "command": "java",
  "args": [
    "-Xmx4g",
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-jar",
    "/path/to/mcp-server.jar",
    "--project.path=/path/to/large/project"
  ]
}
```

### For Small Projects (<1000 classes)

```json
{
  "command": "java",
  "args": [
    "-Xmx512m",
    "-jar",
    "/path/to/mcp-server.jar",
    "--project.path=/path/to/small/project"
  ]
}
```

### Database Loading Performance

For projects with large databases, you can:

1. **Disable database features** (AST-only mode):
   - Simply don't generate the `.analyzer/graph.db` file
   - Server will run in AST-only mode (still functional)

2. **Filter loaded data** (requires code changes):
   - Modify `DatabaseConfiguration.java`
   - Add node type filters to LoadOptions
   - Rebuild the server

---

## Troubleshooting

For troubleshooting, see the main [README - Troubleshooting](../README.md#troubleshooting) section.

### Additional Debug Steps

**Check server startup**:
```bash
# Test server locally
java -jar /path/to/mcp-server.jar --project.path=$PWD
# Wait 3-5 seconds, then Ctrl+C if no errors
```

**View detailed logs**:
```bash
tail -f logs/mcp-server.log
```

**Test MCP communication**:
```bash
# Send test message (will fail but tests server starts)
echo '{"jsonrpc":"2.0","id":1,"method":"initialize"}' | \
  java -jar /path/to/mcp-server.jar --project.path=$PWD
```

---

## Related Documentation

- **Main README**: [../README.md](../README.md) - Complete setup guide
- **STDIO Setup**: [../STDIO_SETUP.md](../STDIO_SETUP.md) - Detailed STDIO transport
- **SSE Setup**: [../SSE_SETUP.md](../SSE_SETUP.md) - HTTP/SSE for debugging
- **Parent Project**: [../../README.md](../../README.md) - Main project documentation
