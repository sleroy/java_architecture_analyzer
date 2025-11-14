# MCP Server Documentation

This directory contains additional documentation for the Java Refactoring MCP Server.

## Documentation Index

### Main Documentation
- **[Main README](../README.md)** - Complete setup guide with Quick Start, How It Works, and Setup Guides for Amazon Q, Cline, and Claude Desktop
- **[STDIO Setup](../STDIO_SETUP.md)** - Detailed STDIO transport configuration
- **[SSE Setup](../SSE_SETUP.md)** - HTTP/SSE transport for debugging from IDE

### Advanced Topics
- **[Configuration Guide](CONFIGURATION.md)** - Advanced configuration options, JVM tuning, environment variables, and performance optimization

## Quick Navigation

### Getting Started
1. Start with the [Main README Quick Start](../README.md#quick-start)
2. Choose your AI tool:
   - [Amazon Q Developer](../README.md#amazon-q-developer-setup)
   - [Cline](../README.md#cline-setup)
   - [Claude Desktop](../README.md#claude-desktop-setup)
3. For advanced options, see [Configuration Guide](CONFIGURATION.md)

### Key Features
- **30+ Refactoring Tools** - Eclipse JDT-powered operations
- **H2 Database Integration** - Query project metadata (NEW!)
- **Security First** - All operations restricted to project root
- **Token Optimization** - 85-95% savings on metadata queries

### Database Integration (NEW!)
The MCP server now integrates with H2 database for rich metadata access:
- Class metrics (complexity, LOC, method counts)
- Dependency relationships  
- Tags and stereotypes
- See [Database Integration](../README.md#database-integration) in main README

## Troubleshooting

See [Main README - Troubleshooting](../README.md#troubleshooting) for common issues and solutions.

For advanced debugging:
- [Configuration Guide - Debug Steps](CONFIGURATION.md#additional-debug-steps)
- [SSE Setup](../SSE_SETUP.md) - Run server from IDE with breakpoints

## Related Projects

- **Parent Project**: [Java Architecture Analyzer](../../README.md)
- **Analyzer Core**: Database and graph infrastructure
- **Analyzer App**: Generate H2 database for projects

## Support

For issues or questions:
1. Check [Troubleshooting](../README.md#troubleshooting)
2. Review logs: `tail -f logs/mcp-server.log`
3. Verify configuration with test command
4. Open issue in repository
