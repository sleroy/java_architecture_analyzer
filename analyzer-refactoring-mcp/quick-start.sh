#!/bin/bash

# Quick Start Script for Java Refactoring MCP Server
# This script helps you quickly configure the MCP server for use with AI tools

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR" && pwd )"
JAR_PATH="$PROJECT_ROOT/target/analyzer-refactoring-mcp-1.0.0-SNAPSHOT.jar"

echo "================================================"
echo "Java Refactoring MCP Server - Quick Start"
echo "================================================"
echo ""

# Step 1: Build the server
echo "Step 1: Building the MCP server..."
if [ ! -f "pom.xml" ]; then
    echo "Error: pom.xml not found. Are you in the correct directory?"
    exit 1
fi

mvn clean package -DskipTests
echo "✓ Build complete"
echo ""

# Step 2: Verify JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: JAR file not found at $JAR_PATH"
    exit 1
fi
echo "✓ JAR file found at: $JAR_PATH"
echo ""

# Step 3: Generate configuration
echo "Step 3: Generating MCP configuration..."
echo ""

cat > /tmp/mcp_config.json << EOF
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "$JAR_PATH"
      ],
      "env": {
        "SPRING_OUTPUT_ANSI_ENABLED": "NEVER"
      }
    }
  }
}
EOF

echo "MCP Configuration generated!"
echo ""
echo "================================================"
echo "Configuration for Cline (VS Code)"
echo "================================================"
echo ""
echo "Location: ~/.config/Code/User/globalStorage/saoudrizwan.claude-dev/settings/cline_mcp_settings.json"
echo ""
echo "Add this configuration:"
cat /tmp/mcp_config.json
echo ""
echo ""

echo "================================================"
echo "Configuration for Claude Desktop"
echo "================================================"
echo ""
if [[ "$OSTYPE" == "darwin"* ]]; then
    CLAUDE_CONFIG="$HOME/Library/Application Support/Claude/claude_desktop_config.json"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    CLAUDE_CONFIG="$APPDATA/Claude/claude_desktop_config.json"
else
    CLAUDE_CONFIG="$HOME/.config/Claude/claude_desktop_config.json"
fi

echo "Location: $CLAUDE_CONFIG"
echo ""
echo "Add this configuration:"
cat /tmp/mcp_config.json
echo ""
echo ""

# Step 4: Test the server
echo "================================================"
echo "Step 4: Testing the server..."
echo "================================================"
echo ""
echo "Starting server for 3 seconds to verify it works..."
timeout 3s java -jar "$JAR_PATH" || true
echo ""
echo "✓ Server appears to be working!"
echo ""

# Step 5: Instructions
echo "================================================"
echo "Next Steps"
echo "================================================"
echo ""
echo "1. Copy the configuration above to your AI tool's config file"
echo "2. Restart your AI tool (Cline, Claude Desktop, etc.)"
echo "3. Verify the MCP server appears in the tool's MCP servers list"
echo "4. Try a test command like: 'List available refactoring tools'"
echo ""
echo "For detailed instructions, see: docs/CONFIGURATION.md"
echo ""
echo "Available Tools:"
echo "  - renameType, renameMethod, renameField"
echo "  - renamePackage, renameCompilationUnit, renameJavaProject"
echo "  - renameEnumConstant, renameModule, renameResource"
echo "  - renameSourceFolder"
echo "  - deleteElements, copyElements, moveElements"
echo "  - moveStaticMembers"
echo ""
echo "================================================"
echo "Quick Start Complete!"
echo "================================================"

# Cleanup
rm /tmp/mcp_config.json
