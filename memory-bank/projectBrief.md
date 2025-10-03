# Java Architecture Analyzer - Project Brief

## Project Overview
**Name**: Java Architecture Analyzer  
**Type**: Static analysis tool for Java applications  
**Delivery**: Maven module and command-line application  

## Purpose
Create a comprehensive inventory system for Java applications that analyzes both source code and compiled bytecode to provide insights into application structure, patterns, and characteristics.

## Core Requirements

### Primary Command: `inventory`
The main functionality revolves around the `inventory` command which creates a detailed spreadsheet inventory of Java classes with configurable inspector analysis.

### Command Parameters
- `--source`: Optional path to source code directory
- `--binary`: Optional path to JAR/WAR/EAR/ZIP files or directories
- `--output`: CSV output file (default: inventory.csv)
- `--encoding`: File encoding (default: platform encoding)
- `--java_version`: Java version for source analysis (mandatory with --source)
- `--inspector`: Comma-separated list of inspector names (optional, default: all)

### Key Features
1. **Unified Analysis**: Combines source code and bytecode analysis
2. **Modular Inspectors**: Extensible inspector pattern for different analysis types
3. **Flexible Input**: Supports various file sources (directories, JARs, WARs)
4. **CSV Export**: Structured output for further analysis
5. **Plugin System**: Support for custom inspector plugins

## Success Criteria
- Accurate class discovery from multiple sources
- Reliable inspector execution with proper error handling
- Clear CSV output with all analysis results
- Extensible architecture for future inspectors
- Maven-based distribution and CLI execution

## Target Users
- Software architects analyzing legacy applications
- DevOps teams performing application audits
- Development teams understanding codebase structure
- Security analysts reviewing application composition
