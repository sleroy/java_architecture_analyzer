# Project Brief

## Overview
**Java Architecture Analyzer** - Static analysis tool for Java applications
**Delivery**: Maven module and CLI application

## Purpose
Create comprehensive inventory system analyzing both source code and compiled bytecode for application structure insights.

## Core Command: `inventory`
```bash
java -jar analyzer.jar inventory \
  --source src/main/java \
  --binary lib/*.jar \
  --output analysis.csv
```

## Key Features
- Unified source and bytecode analysis
- Modular inspector pattern
- CSV export for integration
- Plugin system for custom inspectors

## Success Criteria
- Accurate class discovery from multiple sources
- Reliable inspector execution with error handling
- Clear CSV output with all analysis results
- Extensible architecture for future inspectors