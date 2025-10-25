# Java Architecture Analyzer - Documentation

## Quick Start

1. **[Project Overview](../README.md)** - Main project documentation
2. **[Specifications](spec/)** - Core system specifications
3. **[Implementation Guide](implementation/guide.md)** - Current implementation status and patterns

## Documentation Structure

### `/spec` - System Specifications
Defines WHAT the system does:
- **inspectors.md** - Inspector patterns and contracts
- **analyses.md** - Analysis types and workflows  
- **refactorings.md** - Refactoring strategies

### `/implementation` - Implementation Details
Describes HOW the system works:
- **guide.md** - Current architecture and implementation status
- **patterns.md** - Key design patterns and best practices
- **archived/** - Historical implementation documents

### `/reference` - Reference Material
Supporting documentation:
- **ejb-migration.md** - EJB to Spring migration specifics
- **stereotypes.md** - Stereotype definitions and usage

## Key Concepts

### Inspector Architecture
Inspectors analyze Java classes and emit tags/properties. See [spec/inspectors.md](spec/inspectors.md).

### Graph Model
The system uses a graph to represent:
- **Nodes**: Classes, packages, files
- **Edges**: Dependencies, relationships
- **Tags**: Analysis results attached to nodes

### Analysis Workflow
1. Discover Java classes (binary or source)
2. Run inspectors in dependency order
3. Build graph with tags and properties
4. Export results (CSV, GraphML, JSON)

## Contributing

When updating documentation:
1. Keep specifications in `/spec` - these define the contract
2. Update implementation details in `/implementation`
3. Archive outdated plans rather than deleting them
4. Maintain this README as the entry point
