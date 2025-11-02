#!/bin/bash
# Migration Plan Validation Script
# Validates all YAML migration plan files for syntax and structure errors

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="$SCRIPT_DIR"

echo "========================================="
echo "Migration Plan Validation"
echo "========================================="
echo ""

# Check if yamllint is installed
if ! command -v yamllint &> /dev/null; then
    echo "ERROR: yamllint is not installed"
    echo "Install with: pip install yamllint"
    exit 1
fi

echo "✓ yamllint is installed"
echo ""

# Count files to validate
YAML_COUNT=$(find "$MIGRATIONS_DIR" -name "*.yaml" -o -name "*.yml" | wc -l)
echo "Found $YAML_COUNT YAML files to validate"
echo ""

# Run yamllint on all YAML files
echo "Running yamllint validation..."
echo "-------------------------------------------"

if yamllint -f parsable "$MIGRATIONS_DIR"; then
    echo ""
    echo "========================================="
    echo "✓ All migration plans are valid!"
    echo "========================================="
    exit 0
else
    echo ""
    echo "========================================="
    echo "✗ Validation failed!"
    echo "========================================="
    echo ""
    echo "Common Issues:"
    echo "- Check for 'batch-variable' (should be 'items-variable')"
    echo "- Verify all AI_PROMPT_BATCH blocks have 'items-variable' and 'prompt-template'"
    echo "- Ensure CREATE_MULTIPLE has 'files' and 'base-path' properties"
    echo ""
    echo "See migrations/SCHEMA_VALIDATION.md for details"
    exit 1
fi
