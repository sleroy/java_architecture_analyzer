# Product Context

## Why This Project Exists

### Problem Statement
Software architects and development teams often need to analyze existing Java applications to understand their structure, dependencies, and characteristics. This is particularly challenging when dealing with:

- **Legacy Applications**: Undocumented codebases requiring reverse engineering
- **Application Migration**: Moving from older frameworks to modern architectures  
- **Security Audits**: Identifying components and potential vulnerabilities
- **Technical Debt Assessment**: Understanding code quality and maintainability
- **Dependency Analysis**: Mapping external libraries and internal components

### Current Challenges
- **Manual Analysis**: Time-consuming manual code reviews
- **Tool Fragmentation**: Multiple tools needed for different analysis types
- **Binary vs Source**: Separate tools for compiled vs source code analysis
- **Scale Issues**: Difficulty analyzing large enterprise applications
- **Custom Requirements**: Need for project-specific analysis metrics

## How It Should Work

### User Experience Goals

#### Simple Command-Line Interface
```bash
# Basic analysis of a project
java -jar analyzer.jar inventory --source src/main/java --binary target/classes

# Comprehensive analysis with specific inspectors
java -jar analyzer.jar inventory \
  --source src/main/java \
  --binary lib/*.jar \
  --inspector cloc,type,annotations \
  --output project-analysis.csv
```

#### Clear, Actionable Output
- **CSV Format**: Easy to import into Excel, databases, or analysis tools
- **Structured Data**: Each row represents a class with all analysis results
- **Error Transparency**: Clear indication of what succeeded/failed and why

#### Flexible Input Sources
- **Source Code**: Direct analysis of Java source files
- **Compiled Code**: Analysis of JAR, WAR, EAR files
- **Mixed Mode**: Combined source and binary analysis for complete picture
- **Batch Processing**: Handle multiple projects or modules

### Core Workflows

#### 1. Legacy Application Assessment
**Scenario**: Understanding an inherited codebase
**Input**: WAR file from production deployment
**Process**: Extract classes, analyze structure, identify frameworks
**Output**: Inventory of all classes with type information, complexity metrics

#### 2. Migration Planning  
**Scenario**: Planning framework migration
**Input**: Source code and dependency JARs
**Process**: Identify API usage patterns, count dependencies
**Output**: Analysis of which components use deprecated APIs

#### 3. Security Audit
**Scenario**: Identifying potential security issues
**Input**: Application JARs and source code
**Process**: Pattern matching for security anti-patterns, dependency analysis
**Output**: List of classes with potential security concerns

#### 4. Architecture Documentation
**Scenario**: Generating current-state architecture documentation
**Input**: Multi-module Maven project
**Process**: Class discovery, dependency mapping, package analysis
**Output**: Comprehensive inventory for architecture visualization tools

## Target Outcomes

### Primary Value Propositions
1. **Unified Analysis**: Single tool for both source and binary analysis
2. **Extensibility**: Plugin system for custom analysis requirements
3. **Scale**: Handle enterprise-scale applications efficiently
4. **Integration**: CSV output integrates with existing analysis workflows

### Success Metrics
- **Time Savings**: Reduce manual analysis time by 80%
- **Completeness**: Capture 100% of classes in scope with detailed metadata
- **Accuracy**: Reliable analysis results with clear error reporting
- **Adoption**: Easy enough for non-experts to use effectively

### Quality Attributes
- **Performance**: Handle applications with 10,000+ classes
- **Reliability**: Graceful handling of corrupt or unusual class files
- **Usability**: Self-documenting CLI with helpful error messages
- **Maintainability**: Clean architecture supporting future enhancements

## User Personas

### Primary: Software Architect
**Background**: Senior developer responsible for application architecture
**Goals**: Understand legacy systems, plan migrations, assess technical debt
**Pain Points**: Time-consuming manual analysis, fragmented tooling
**Success Criteria**: Comprehensive analysis results in minutes not days

### Secondary: DevOps Engineer
**Background**: Responsible for application deployment and security
**Goals**: Inventory applications for security and compliance audits
**Pain Points**: Limited visibility into application internals
**Success Criteria**: Automated analysis that integrates with CI/CD pipelines

### Tertiary: Development Team Lead
**Background**: Managing development team working on existing applications
**Goals**: Understand codebase structure, identify refactoring opportunities
**Pain Points**: Difficulty getting team-wide view of code quality
**Success Criteria**: Regular analysis reports to track improvement over time
