# STDIO Profile Setup Guide

This guide explains how to configure the Java Refactoring MCP Server to run in STDIO-only mode for integration with Cline or Claude Desktop.

## Overview

The `stdio` profile configures the Spring Boot MCP server to:
- Use STDIO transport for MCP protocol communication
- Minimize console output to avoid interfering with MCP messages
- Route logs to a file instead of stdout
- Disable Spring Boot banner and unnecessary startup messages

## Prerequisites

1. **Java 21 or later** installed
2. **Maven** installed
3. **Project built**: Run `mvn clean install` from the project root

## Profile Configuration

### application-stdio.properties

The stdio profile is configured in `src/main/resources/application-stdio.properties`:

```properties
spring.ai.mcp.server.stdio=true
spring.ai.mcp.server.protocol=SYNC
spring.main.banner-mode=off
logging.level.root=WARN
logging.file.name=./logs/mcp-server.log
```

Key settings:
- `spring.ai.mcp.server.stdio=true` - Enables STDIO transport
- `spring.ai.mcp.server.protocol=SYNC` - Uses synchronous protocol for better compatibility
- `spring.main.banner-mode=off` - Disables Spring Boot banner
- `logging.file.name` - Routes logs to file instead of stdout

### Maven Profile

The `stdio` profile is defined in `pom.xml`:

```xml
<profile>
    <id>stdio</id>
    <properties>
        <spring.profiles.active>stdio</spring.profiles.active>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.analyzer.refactoring.mcp.RefactoringMcpServerApplication</mainClass>
                    <arguments>
                        <argument>--spring.profiles.active=stdio</argument>
                    </arguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

## Building the Server

Build the JAR file with the stdio profile:

```bash
cd /home/sleroy/git/java_architecture_analyzer
mvn clean package -Pstdio
```

This creates: `analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar`

## Running the Server

### Using the JAR (Recommended)

```bash
java -Dspring.profiles.active=stdio -jar /home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar
```

**Benefits:**
- Fast startup (no Maven overhead)
- Suitable for production use
- Easy to deploy and manage

### Using Maven (Development Only)

```bash
cd analyzer-refactoring-mcp
mvn spring-boot:run -Pstdio
```

**Note:** Maven launch is slower and should only be used during development.

## MCP Configuration for Cline

### Location of Configuration File

The MCP configuration file location depends on your OS:

- **Linux/Mac**: `~/.config/Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json`
- **Windows**: `%APPDATA%\Code\User\globalStorage\saoudrizwan.claude-dev\settings\cline_mcp_settings.json`

### Configuration Template (JAR-based)

Add this configuration to your `cline_mcp_settings.json`:

```json
{
  "mcpServers": {
    "java-refactoring-mcp-stdio": {
      "command": "java",
      "args": [
        "-Dspring.profiles.active=stdio",
        "-jar",
        "/home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"
      ]
    }
  }
}
```

**Important**: Replace `/home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar` with the actual absolute path on your system.

### Alternative: Using Maven (Not Recommended)

For development purposes only:

```json
{
  "mcpServers": {
    "java-refactoring-mcp-stdio": {
      "command": "mvn",
      "args": [
        "spring-boot:run",
        "-Pstdio",
        "-f",
        "/home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/pom.xml"
      ],
      "env": {
        "SPRING_PROFILES_ACTIVE": "stdio"
      }
    }
  }
}
```

**Warning:** Maven-based launch is significantly slower and not recommended for regular use.

## MCP Configuration for Claude Desktop

### Location of Configuration File

- **Mac**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

### Configuration Template (JAR-based)

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-Dspring.profiles.active=stdio",
        "-jar",
        "/home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"
      ]
    }
  }
}
```

**Important**: Replace with the actual absolute path to your JAR file.

## Verification

### 1. Test the Server Locally

First, ensure the JAR is built:

```bash
cd /home/sleroy/git/java_architecture_analyzer
mvn clean package -Pstdio
```

Test the server directly:

```bash
java -Dspring.profiles.active=stdio -jar analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar
```

You should see minimal output. The server is now listening on stdin/stdout for MCP messages.

Press `Ctrl+C` to stop the server.

### 2. Check Logs

Logs are written to `./logs/mcp-server.log` in the project directory. Check this file if you encounter issues:

```bash
tail -f analyzer-refactoring-mcp/logs/mcp-server.log
```

### 3. Verify in Cline/Claude Desktop

After adding the configuration:
1. Restart Cline/Claude Desktop
2. The server should appear in the available MCP servers list
3. You should see 14+ refactoring tools available

## Available Tools

The server provides these refactoring tools:

### Rename Operations (10 tools)
- `rename_type` - Rename classes, interfaces, enums
- `rename_field` - Rename class fields
- `rename_method` - Rename methods
- `rename_enum_constant` - Rename enum constants
- `rename_compilation_unit` - Rename Java files
- `rename_package` - Rename packages
- `rename_module` - Rename modules
- `rename_java_project` - Rename projects
- `rename_source_folder` - Rename source folders
- `rename_resource` - Rename resources

### Move Operations (2 tools)
- `move_elements` - Move Java elements
- `move_static_members` - Move static members

### Other Operations
- `copy_elements` - Copy Java elements
- `delete_elements` - Delete Java elements

### EJB Migration Tools
- `migrate_stateless_ejb` - Migrate EJB to Spring
- `remove_ejb_interface` - Remove EJB interfaces
- `add_transactional` - Add @Transactional
- `migrate_security_annotations` - Migrate security
- `batch_replace_annotations` - Batch annotation replacement
- `convert_to_constructor_injection` - Convert to constructor injection

### Analysis Tools
- `analyze_anti_patterns` - Detect anti-patterns
- `extract_class_metadata` - Extract class information
- `get_dependency_graph` - Get dependency graph

## Troubleshooting

### Server Won't Start

1. **Check Java Version**:
   ```bash
   java -version  # Should be 21 or later
   ```

2. **Check Maven**:
   ```bash
   mvn -version
   ```

3. **Rebuild the Project**:
   ```bash
   cd /path/to/java_architecture_analyzer
   mvn clean install
   ```

### MCP Communication Issues

1. **Check for Console Output**: The stdio profile should minimize console output. If you see Spring Boot banners or info logs, the profile may not be active.

2. **Verify Profile is Active**: Check logs at `./logs/mcp-server.log` for the line:
   ```
   The following profiles are active: stdio
   ```

3. **Check File Paths**: Ensure all paths in the configuration are absolute paths.

### Performance Issues

The JAR-based approach provides the best performance. If experiencing issues:

1. **Rebuild the JAR**: `mvn clean package -Pstdio`
2. **Verify Java version**: Ensure Java 21 or later is being used
3. **Check system resources**: Ensure sufficient memory is available

### Logs Not Appearing

Create the logs directory manually:
```bash
mkdir -p analyzer-refactoring-mcp/logs
```

## Development Tips

### Rebuilding After Code Changes

After making changes to the code, rebuild the JAR:

```bash
cd /home/sleroy/git/java_architecture_analyzer
mvn clean package -Pstdio
```

The MCP client will automatically use the updated JAR on next restart.

### Debugging

To enable debug logging, modify the command in your MCP configuration:

```json
{
  "mcpServers": {
    "java-refactoring-mcp-stdio": {
      "command": "java",
      "args": [
        "-Dspring.profiles.active=stdio",
        "-Dlogging.level.com.analyzer.refactoring=DEBUG",
        "-Dlogging.level.org.springframework.ai.mcp=DEBUG",
        "-jar",
        "/home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"
      ]
    }
  }
}
```

Then check the logs at `analyzer-refactoring-mcp/logs/mcp-server.log`.

### Testing STDIO Communication

Create a simple test script:

```bash
#!/bin/bash
# test-mcp-server.sh

JAR_PATH="/home/sleroy/git/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"

# Send initialize request
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | \
  java -Dspring.profiles.active=stdio -jar "$JAR_PATH"
```

**Note:** The server will process the message and exit since stdin closes after the echo.

## References

- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [Model Context Protocol Specification](https://spec.modelcontextprotocol.io/)
- [Cline MCP Integration](https://github.com/cline/cline)
- [Claude Desktop MCP Servers](https://github.com/anthropics/claude-desktop-mcp-servers)

## Support

For issues or questions:
1. Check `./logs/mcp-server.log` for errors
2. Review this documentation
3. Open an issue in the project repository
