# SSE/HTTP Transport Setup for MCP Server

This guide explains how to configure and use the Java Refactoring MCP Server with SSE (Server-Sent Events) transport for remote debugging from your IDE.

## Overview

The SSE transport allows you to run the MCP server as a standalone HTTP service that Cline can connect to remotely. This is ideal for:

- **Debugging from IDE**: Set breakpoints and debug server behavior
- **Development**: Make changes without restarting Cline
- **Testing**: Verify server behavior independently

## Configuration Files

### 1. MCP Configuration

The MCP server configuration has been added to `~/.aws/amazonq/mcp.json`:

```json
{
  "mcpServers": {
    "java-refactoring-mcp-sse": {
      "transport": {
        "type": "sse",
        "url": "http://localhost:8080/sse"
      },
      "env": {
        "AWS_ACCESS_KEY_ID": "your-access-key",
        "AWS_SECRET_ACCESS_KEY": "your-secret-key",
        "AWS_REGION": "us-east-1"
      }
    }
  }
}
```

**Key Configuration:**
- `transport.type`: Set to `"sse"` for Server-Sent Events
- `transport.url`: The SSE endpoint URL (default: `http://localhost:8080/sse`)
- `env`: Environment variables passed to the server (AWS credentials, etc.)

### 2. Application Profile

A new Spring profile has been created: `application-http.properties`

This profile configures the server to:
- Enable web server on port 8080
- Configure SSE transport at `/sse` endpoint
- Enable CORS for development
- Show startup banner for easier monitoring

## Running from IDE

### IntelliJ IDEA

1. **Create Run Configuration**:
   - Go to `Run` → `Edit Configurations`
   - Click `+` → `Application`
   - Name: `MCP Server (SSE)`
   - Main class: `com.analyzer.refactoring.mcp.RefactoringMcpServerApplication`
   - VM options: `-Dspring.profiles.active=http`
   - Environment variables:
     ```
     AWS_ACCESS_KEY_ID=your-key
     AWS_SECRET_ACCESS_KEY=your-secret
     AWS_REGION=us-east-1
     ```
   - Working directory: `$MODULE_DIR$`

2. **Run in Debug Mode**:
   - Set breakpoints in your code
   - Click the debug icon (or press Shift+F9)
   - Server will start on port 8080

3. **Verify Server is Running**:
   - Check console output for: `Started RefactoringMcpServerApplication`
   - Visit: `http://localhost:8080/actuator/health` (if actuator is enabled)

### VS Code

1. **Create launch.json**:

Create `.vscode/launch.json` in the project root:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "MCP Server (SSE)",
      "request": "launch",
      "mainClass": "com.analyzer.refactoring.mcp.RefactoringMcpServerApplication",
      "projectName": "analyzer-refactoring-mcp",
      "args": "",
      "vmArgs": "-Dspring.profiles.active=http",
      "env": {
        "AWS_ACCESS_KEY_ID": "your-key",
        "AWS_SECRET_ACCESS_KEY": "your-secret",
        "AWS_REGION": "us-east-1"
      }
    }
  ]
}
```

2. **Run in Debug Mode**:
   - Press F5 or go to `Run and Debug` panel
   - Select "MCP Server (SSE)" configuration
   - Click the play button

### Eclipse

1. **Create Debug Configuration**:
   - Right-click on project → `Debug As` → `Debug Configurations`
   - Create new `Java Application` configuration
   - Main class: `com.analyzer.refactoring.mcp.RefactoringMcpServerApplication`
   - Arguments tab → VM arguments: `-Dspring.profiles.active=http`
   - Environment tab → Add variables:
     - `AWS_ACCESS_KEY_ID`
     - `AWS_SECRET_ACCESS_KEY`
     - `AWS_REGION`

2. **Debug**: Click `Debug` button

## Command Line Usage

You can also run the server from command line:

```bash
# Build the project first
cd analyzer-refactoring-mcp
mvn clean package

# Run with HTTP profile
java -Dspring.profiles.active=http \
  -jar target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar
```

Or with environment variables:

```bash
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export AWS_REGION=us-east-1

java -Dspring.profiles.active=http \
  -jar target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar
```

## Using with Cline

Once the server is running:

1. **Restart Cline** (if it was already running)
2. **Verify Connection**: Cline should automatically connect to the SSE endpoint
3. **Check Server Logs**: You should see connection messages in the server console
4. **Use Tools**: Try using the MCP tools through Cline

### Switching Between STDIO and SSE

Your MCP configuration now has both modes:

- `java-refactoring-mcp-stdio`: Process-based transport (default)
- `java-refactoring-mcp-sse`: Remote SSE transport (for debugging)

To switch between them:

1. Edit `~/.aws/amazonq/mcp.json`
2. Add `"disabled": true` to the mode you don't want to use:

```json
{
  "mcpServers": {
    "java-refactoring-mcp-stdio": {
      "disabled": true,
      ...
    },
    "java-refactoring-mcp-sse": {
      "disabled": false,
      ...
    }
  }
}
```

3. Restart Cline

## Debugging Tips

### Check Server Logs

The server logs are configured in `application-http.properties`:

```properties
logging.level.com.analyzer.refactoring=INFO
logging.level.org.springframework.ai.mcp=INFO
logging.level.org.springframework.web=INFO
```

Increase log level for more details:

```properties
logging.level.com.analyzer.refactoring=DEBUG
logging.level.org.springframework.ai.mcp=DEBUG
```

### Verify Endpoint

Test the SSE endpoint manually:

```bash
curl -N http://localhost:8080/sse
```

You should see SSE stream events.

### Network Issues

If Cline can't connect:

1. **Check firewall**: Ensure port 8080 is open
2. **Verify server is running**: Check IDE console for errors
3. **Test locally**: Use curl to verify endpoint responds
4. **Check CORS**: If running from different origin, verify CORS settings

### Common Issues

**Port Already in Use**:
```
Error starting ApplicationContext. To display the conditions report re-run your application with 'debug' enabled.
Web server failed to start. Port 8080 was already in use.
```

Solution: Change port in `application-http.properties`:
```properties
server.port=8081
```

And update MCP configuration URL accordingly.

**Connection Refused**:
- Verify server is running
- Check server logs for startup errors
- Ensure correct URL in MCP configuration

**Tools Not Appearing**:
- Check server logs for tool registration
- Verify MCP capabilities are enabled
- Restart Cline after configuration changes

## Development Workflow

Recommended workflow for developing MCP server:

1. **Start server in debug mode** from IDE
2. **Make code changes** 
3. **Hot reload** (if using Spring DevTools)
   - Or restart debug session
4. **Test with Cline** immediately
5. **Set breakpoints** to debug tool execution
6. **View logs** in IDE console

## Comparison: STDIO vs SSE

| Feature | STDIO | SSE |
|---------|-------|-----|
| **Use Case** | Production | Development/Debug |
| **Starting** | Auto by Cline | Manual from IDE |
| **Debugging** | Difficult | Easy |
| **Hot Reload** | No | Yes (with DevTools) |
| **Logs** | File only | IDE console |
| **Performance** | Faster | Slightly slower |
| **Reliability** | Higher | Development-grade |

## Security Notes

### For Development

The current SSE configuration includes permissive CORS settings:

```properties
spring.web.cors.allowed-origins=*
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*
```

This is **suitable for development only**.

### For Production

If deploying SSE transport in production:

1. **Restrict CORS origins**:
   ```properties
   spring.web.cors.allowed-origins=https://your-trusted-domain.com
   ```

2. **Add authentication**:
   - Implement Spring Security
   - Use OAuth2 or API keys
   - Add request validation

3. **Use HTTPS**:
   ```properties
   server.ssl.enabled=true
   server.ssl.key-store=classpath:keystore.p12
   server.ssl.key-store-password=your-password
   ```

4. **Rate limiting**:
   - Add rate limiting to prevent abuse
   - Use tools like Bucket4j

## Next Steps

- See [README.md](README.md) for general MCP server documentation
- See [STDIO_SETUP.md](STDIO_SETUP.md) for STDIO transport setup
- See [docs/](docs/) for detailed tool documentation

## Troubleshooting

For issues:

1. Check server logs in IDE console
2. Verify MCP configuration syntax
3. Test endpoint with curl
4. Review Cline logs
5. Open an issue with logs and configuration

## References

- [Model Context Protocol Specification](https://spec.modelcontextprotocol.io/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Server-Sent Events (SSE)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
