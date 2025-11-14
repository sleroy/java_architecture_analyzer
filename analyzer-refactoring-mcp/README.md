# Java Refactoring MCP Server

A Spring Boot-based Model Context Protocol (MCP) server that provides Java refactoring capabilities using Eclipse JDT, OpenRewrite, and H2 graph database integration. This server enables AI assistants like Amazon Q and Cline to perform automated refactoring operations and query rich project metadata.

## 🚀 Quick Start

### Prerequisites
- **Java 21+** installed
- **Maven** installed  
- Project built: `mvn clean install` from project root

### 1. Build the Server

```bash
cd analyzer-refactoring-mcp
mvn clean package
```

### 2. Run the Server

```bash
java -jar target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=stdio \
  --project.path=/path/to/your/java/project
```

### 3. Configure Your AI Tool

See detailed setup instructions below for:
- [Amazon Q Developer](#amazon-q-developer-setup)
- [Cline (VS Code)](#cline-setup)
- [Claude Desktop](#claude-desktop-setup)

---

## 📖 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [How It Works](#how-it-works)
- [Installation](#installation)
- [Configuration](#configuration)
- [Setup Guides](#setup-guides)
  - [Amazon Q Developer](#amazon-q-developer-setup)
  - [Cline](#cline-setup)
  - [Claude Desktop](#claude-desktop-setup)
- [Available Tools](#available-tools)
- [Database Integration](#database-integration)
- [Security](#security)
- [Troubleshooting](#troubleshooting)

---

## Overview

This MCP server bridges AI assistants with powerful Java refactoring tools. It provides:

- **30+ Refactoring Operations**: Rename, move, extract, and more using Eclipse JDT
- **Code Analysis**: Search and transform code patterns with OpenRewrite
- **Graph Database Integration**: Query class metrics, dependencies, and tags from H2
- **Security-First**: All operations restricted to your project directory
- **Token Optimization**: 85-95% reduction in tokens for metadata queries

## Features

### Core Capabilities
- ✅ **Eclipse JDT Refactoring**: Industry-standard refactoring operations
- ✅ **OpenRewrite Integration**: Pattern matching and code transformation
- ✅ **H2 Database Queries**: Access pre-analyzed project metadata
- ✅ **Path Security**: Operations restricted to configured project root
- ✅ **Compact Metadata**: Dramatically reduced token usage for class analysis
- ✅ **Batch Operations**: Process multiple files efficiently

### Database Integration (New!)
- ✅ **Rich Metadata Access**: Query JavaClassNode and ProjectFile data
- ✅ **Metrics & Tags**: Access complexity metrics, stereotypes, and tags
- ✅ **Dependency Analysis**: Query class dependencies and relationships
- ✅ **Graceful Fallback**: Works in AST-only mode without database

### Security
- ✅ **Project Root Enforcement**: All paths validated against project root
- ✅ **Traversal Prevention**: Blocks `../` and absolute path attacks
- ✅ **Symbolic Link Resolution**: Safe handling of symlinks

---

## How It Works

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    AI Assistant                          │
│              (Amazon Q / Cline / Claude)                 │
└───────────────────┬─────────────────────────────────────┘
                    │ MCP Protocol (STDIO/SSE)
                    ↓
┌─────────────────────────────────────────────────────────┐
│              MCP Server (Spring Boot)                    │
├─────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────┐  ┌────────────────┐ │
│  │ Security     │  │ Database    │  │ Tool Registry  │ │
│  │ Validator    │  │ Service     │  │                │ │
│  └──────────────┘  └─────────────┘  └────────────────┘ │
│                                                          │
│  ┌──────────────┐  ┌─────────────┐  ┌────────────────┐ │
│  │ Eclipse JDT  │  │ OpenRewrite │  │ EJB Migration  │ │
│  │ Refactoring  │  │ Engine      │  │ Tools          │ │
│  └──────────────┘  └─────────────┘  └────────────────┘ │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ↓
┌─────────────────────────────────────────────────────────┐
│              Your Java Project                           │
├─────────────────────────────────────────────────────────┤
│  src/main/java/...                                       │
│  .analyzer/graph.db     ← H2 Database (optional)        │
└─────────────────────────────────────────────────────────┘
```

### Workflow

1. **AI Request**: AI assistant sends MCP request (e.g., "extract metadata for MyClass")
2. **Security Check**: Server validates all paths against project root
3. **Tool Execution**: Appropriate tool (JDT/OpenRewrite/Analysis) processes request
4. **Database Query** (if available): Enriches response with graph metadata
5. **Response**: Returns JSON response to AI assistant

---

## Installation

### Build from Source

```bash
# Clone the repository (if not already done)
cd /path/to/java_architecture_analyzer

# Build the entire project
mvn clean install

# Or build only the MCP server
cd analyzer-refactoring-mcp
mvn clean package
```

This creates: `target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar`

### System Requirements

- **Java**: Version 21 or later
- **Memory**: Minimum 512MB, recommended 1GB+
- **OS**: Linux, macOS, or Windows

---

## Configuration

### Project Path (Required)

The server requires a project root path for security and database access. Configure using one of:

#### Option 1: Command-Line Argument (Recommended)

```bash
java -jar analyzer-refactoring-mcp.jar --project.path=/path/to/project
```

#### Option 2: Environment Variable

```bash
export PROJECT_PATH=/path/to/your/project
java -jar analyzer-refactoring-mcp.jar
```

#### Option 3: System Property

```bash
java -Dproject.path=/path/to/project -jar analyzer-refactoring-mcp.jar
```

#### Option 4: Default

If not specified, uses current working directory (`$PWD`)

### Database Setup (Optional but Recommended)

For enhanced metadata features, generate the H2 database:

```bash
# Run the analyzer on your project first
cd /path/to/your/project
java -jar /path/to/analyzer-app.jar

# This creates: .analyzer/graph.db.mv.db
```

**Without Database**: Server works in AST-only mode (still functional, but no graph metadata)  
**With Database**: Full metadata access including metrics, tags, and dependencies

---

## Setup Guides

### Amazon Q Developer Setup

Amazon Q Developer can use MCP servers to extend its capabilities with custom tools.

#### Step 1: Locate Amazon Q Configuration

The configuration file location depends on your setup:

**For Amazon Q in VS Code:**
```
~/.aws/amazonq/mcp.json
```

**For Amazon Q Developer CLI:**
```
~/.amazonq/mcp.json
```

#### Step 2: Add MCP Server Configuration

Edit the MCP configuration file and add:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar",
        "--spring.profiles.active=stdio",
        "--project.path=/absolute/path/to/your/java/project"
      ],
      "env": {
        "SPRING_OUTPUT_ANSI_ENABLED": "NEVER"
      }
    }
  }
}
```

**Important**: 
- Replace `/absolute/path/to/analyzer-refactoring-mcp/...` with the actual path to your JAR
- Replace `/absolute/path/to/your/java/project` with your project root
- Use absolute paths (no `~` or relative paths)

#### Step 3: Restart Amazon Q

1. Reload VS Code window (or restart Amazon Q CLI)
2. Amazon Q will automatically load the MCP server
3. Verify by asking: "What refactoring tools are available?"

#### Step 4: Test the Integration

Try these commands with Amazon Q:

```
"Extract metadata for the class com.example.MyService"
"Find all classes with @Stateless annotation"
"Rename method getUserName to getUsername in UserService"
```

#### Amazon Q Configuration Example

Complete example with multiple settings:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-Xmx1g",
        "-jar",
        "/home/user/projects/java_architecture_analyzer/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar",
        "--spring.profiles.active=stdio",
        "--project.path=/home/user/projects/my-java-app"
      ],
      "env": {
        "SPRING_OUTPUT_ANSI_ENABLED": "NEVER",
        "JAVA_TOOL_OPTIONS": "-Dfile.encoding=UTF-8"
      }
    }
  }
}
```

---

### Cline Setup

Cline is an AI coding assistant for VS Code that supports MCP servers.

#### Step 1: Locate Cline Configuration

**macOS/Linux:**
```
~/.config/Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json
```

**Windows:**
```
%APPDATA%\Code\User\globalStorage\saoudrizwan.claude-dev\settings\cline_mcp_settings.json
```

#### Step 2: Add MCP Server Configuration

Edit `cline_mcp_settings.json`:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar",
        "--spring.profiles.active=stdio",
        "--project.path=/absolute/path/to/your/java/project"
      ],
      "disabled": false,
      "alwaysAllow": []
    }
  }
}
```

#### Step 3: Restart VS Code

Close and reopen VS Code to reload Cline with the new MCP server.

#### Step 4: Verify in Cline

1. Open Cline panel in VS Code
2. Check MCP servers section - should show "java-refactoring"
3. Green indicator means server is connected

#### Cline-Specific Features

- **Auto-approval**: Add tool names to `alwaysAllow` array to skip confirmations
- **Disable temporarily**: Set `"disabled": true` to disable without removing config

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [...],
      "disabled": false,
      "alwaysAllow": [
        "extractClassMetadataCompact",
        "searchJavaPattern"
      ]
    }
  }
}
```

---

### Claude Desktop Setup

#### Step 1: Locate Claude Configuration

**macOS:**
```
~/Library/Application Support/Claude/claude_desktop_config.json
```

**Windows:**
```
%APPDATA%\Claude\claude_desktop_config.json
```

**Linux:**
```
~/.config/Claude/claude_desktop_config.json
```

#### Step 2: Add MCP Server Configuration

Edit `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/analyzer-refactoring-mcp/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar",
        "--spring.profiles.active=stdio",
        "--project.path=/absolute/path/to/your/java/project"
      ]
    }
  }
}
```

#### Step 3: Restart Claude Desktop

Quit and restart Claude Desktop application.

#### Step 4: Verify

Ask Claude: "What MCP tools do you have access to?" - should list refactoring tools.

---

## Available Tools

### EJB Migration Tools (9 tools)

Perfect for modernizing legacy Java EE applications:

1. **extractClassMetadataCompact** - Extract compact class metadata (85-95% token savings)
2. **migrateStatelessEjbToService** - Convert @Stateless EJB to Spring @Service
3. **addTransactionalToMethods** - Add @Transactional to transactional methods
4. **batchReplaceAnnotations** - Replace annotations in bulk (@EJB → @Autowired)
5. **convertToConstructorInjection** - Convert field to constructor injection
6. **migrateSecurityAnnotations** - Convert @RolesAllowed to Spring Security
7. **removeEjbInterfaces** - Remove EJB Home/Remote interfaces
8. **analyzeAntiPatterns** - Detect EJB anti-patterns
9. **getDependencyGraph** - Extract class dependency graph

### OpenRewrite Tools (5 tools)

Search and transform Java code:

1. **searchJavaPattern** - Find patterns (annotations, methods, classes)
2. **searchReplaceJavaPattern** - Search and transform code
3. **annotationSearch** - Find classes with specific annotations
4. **interfaceSearch** - Find implementing classes
5. **methodAnnotationSearch** - Find annotated methods

### JDT Refactoring Tools (14 tools)

Eclipse-powered refactoring operations:

**Rename Operations:**
- renameType, renameMethod, renameField
- renamePackage, renameCompilationUnit
- renameEnumConstant, renameModule
- renameJavaProject, renameSourceFolder, renameResource

**Code Movement:**
- moveElements, moveStaticMembers
- copyElements, deleteElements

### Graph Database Query Tools (7 tools)

Query project metadata from the H2 graph database:

1. **queryGraphStatistics** - Get overall database statistics (node/edge counts)
2. **queryClassMetrics** - Get metrics for a specific class (complexity, LOC, coupling)
3. **queryClassDependencies** - Get all dependencies for a class (incoming/outgoing)
4. **queryClassRelationships** - Get all graph relationships (edges) for a class
5. **queryClassesByTag** - Find classes by tag using natural language (AI-enhanced)
6. **queryClassesByProperty** - Find classes by property name and optional value
7. **queryMetricValues** - Query metric values across all classes using natural language (AI-enhanced)

**AI-Enhanced Tools:**

The `queryMetricValues` tool uses Bedrock AI to understand natural language metric queries:
- **Example queries**: "complexity metrics", "lines of code", "coupling metrics"
- **Returns**: All classes with values for matched metrics, plus statistical analysis
- **Statistics**: min, max, average, median, standard deviation for each metric
- **Use cases**: Understand metric distributions, identify outliers, find high-complexity classes

```bash
# Example usage with AI assistant:
"Show me all complexity metrics across the codebase"
"What are the lines of code values for all classes?"
"Find coupling metrics and show me the statistics"
```

---

## Database Integration

### What is the Database?

The H2 database contains pre-analyzed project metadata:
- Class metrics (complexity, LOC, method counts)
- Dependency relationships
- Tags and stereotypes
- Package information

### Generating the Database

Run the analyzer application on your project:

```bash
cd /path/to/your/project
java -jar /path/to/analyzer-app/target/analyzer-app.jar

# Creates: .analyzer/graph.db.mv.db
```

### Using Database Features

When database is available, tools return enhanced metadata:

```json
{
  "success": true,
  "fullyQualifiedName": "com.example.UserService",
  "astMetadata": {
    "annotations": ["Service", "Transactional"],
    "methods": ["getUser", "saveUser", "deleteUser"],
    "fields": ["userRepository"]
  },
  "graphMetadata": {
    "classNode": {
      "sourceType": "SOURCE",
      "methodCount": 15,
      "fieldCount": 3,
      "metrics": {
        "complexity": 42,
        "loc": 250
      },
      "tags": {
        "layer": "service",
        "domain": "user-management"
      }
    },
    "projectFile": {
      "packageName": "com.example.service",
      "extension": "java"
    }
  }
}
```

### Token Savings

The `extractClassMetadataCompact` tool:

- **Without tool**: 2000-5000 tokens (full source code)
- **With tool**: 150-300 tokens (compact JSON metadata)
- **Savings**: 85-95% token reduction

This allows AI to analyze 10-20x more classes within the same token budget!

---

## Security

### Path Validation

**All file operations are validated**:

```
✅ ALLOWED: /project/src/main/java/MyClass.java
✅ ALLOWED: /project/test/MyTest.java
❌ BLOCKED: /etc/passwd
❌ BLOCKED: /project/../../../etc/passwd
❌ BLOCKED: /home/user/other-project/File.java
```

### Security Features

1. **Project Root Enforcement**: All paths must be within configured project root
2. **Path Normalization**: Resolves `.` and `..` before validation
3. **Symlink Resolution**: Follows symlinks to verify final destination
4. **Absolute Path Detection**: Blocks absolute paths outside project
5. **Traversal Prevention**: Rejects `../` escape attempts

### Read-Only Database

The MCP server has **read-only** access to the database. It cannot:
- Modify existing analysis data
- Write new nodes or edges
- Update metrics or tags
- Delete any data

---

## Troubleshooting

### Common Issues

#### 1. Server Won't Start

**Error**: `Error: Unable to access jarfile`

**Solution**: Verify JAR path is correct and use absolute paths:
```bash
ls -la /absolute/path/to/analyzer-refactoring-mcp/target/*.jar
```

#### 2. Database Not Found

**Log Output**:
```
✗ Graph database not found - operating in AST-only mode
  To enable graph features, run the analyzer application first
```

**Solution**: Generate database:
```bash
cd /path/to/your/project
java -jar /path/to/analyzer-app.jar
# Verify: ls -la .analyzer/graph.db.mv.db
```

#### 3. Security Violation

**Error**: `Access denied: Path '/etc/passwd' is outside project root`

**Solution**: Ensure all operations target files within your project:
```bash
# Correct project path in configuration:
--project.path=/absolute/path/to/your/actual/project
```

#### 4. Tools Not Appearing in AI Assistant

**Steps**:
1. Verify MCP configuration syntax (valid JSON)
2. Check all paths are absolute (no `~` or relative paths)
3. Restart AI assistant completely
4. Check server logs: `tail -f logs/mcp-server.log`

#### 5. Java Version Error

**Error**: `Unsupported class file major version 65`

**Solution**: Ensure Java 21+ is being used:
```bash
java -version  # Should show 21 or higher
```

### Getting Help

1. **Check Logs**: 
   - Server logs: `./logs/mcp-server.log`
   - AI assistant logs (varies by tool)

2. **Verify Configuration**:
   ```bash
   # Test server locally
   java -jar path/to/mcp-server.jar --project.path=$PWD
   # Press Ctrl+C after 2-3 seconds if it starts successfully
   ```

3. **Common Configuration Mistakes**:
   - Using `~` instead of absolute path
   - Wrong JAR filename or path
   - Missing `--project.path` parameter
   - Project path doesn't exist

---

## Performance

- **Startup Time**: 2-5 seconds (including database load)
- **Database Load**: 1-3 seconds for typical projects (~1000 classes)
- **Tool Execution**: <100ms for most operations
- **Memory Usage**: ~500MB base + graph size
- **Recommended**: 1GB heap for projects with 5000+ classes

### Performance Tuning

Add JVM options to configuration:

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-Xmx2g",
        "-XX:+UseG1GC",
        "-jar",
        "/path/to/mcp-server.jar",
        ...
      ]
    }
  }
}
```

---

## Related Documentation

- **STDIO Setup**: [STDIO_SETUP.md](STDIO_SETUP.md) - Detailed STDIO transport configuration
- **SSE Setup**: [SSE_SETUP.md](SSE_SETUP.md) - HTTP/SSE transport for debugging
- **Eclipse JDT**: [https://www.eclipse.org/jdt/](https://www.eclipse.org/jdt/)
- **OpenRewrite**: [https://docs.openrewrite.org/](https://docs.openrewrite.org/)
- **Spring AI MCP**: [https://docs.spring.io/spring-ai/reference/api/mcp/](https://docs.spring.io/spring-ai/reference/api/mcp/)
- **Parent Project**: [../README.md](../README.md)

---

## License

This module is part of the Java Architecture Analyzer project.

---

## Quick Reference

### Essential Commands

```bash
# Build
mvn clean package

# Run (with database features)
java -jar target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=stdio \
  --project.path=/path/to/project

# Generate database
cd /path/to/project && java -jar /path/to/analyzer-app.jar

# View logs
tail -f logs/mcp-server.log
```

### Configuration Template

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "/ABSOLUTE/PATH/TO/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar",
        "--spring.profiles.active=stdio",
        "--project.path=/ABSOLUTE/PATH/TO/YOUR/PROJECT"
      ]
    }
  }
}
```

**Remember**: 
- ✅ Use absolute paths everywhere
- ✅ Include `--project.path` parameter
- ✅ Set `spring.profiles.active=stdio` for MCP integration
- ✅ Restart AI assistant after configuration changes
