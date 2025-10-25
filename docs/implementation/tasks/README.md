# Active Implementation Tasks

This directory contains active implementation specifications and task tracking. Completed tasks are archived in `../archived/`.

## Current Tasks

### 01_EJB_Graph_Nodes.md
Specification for EJB-related graph node types:
- Node structure and properties
- EJB-specific metadata
- Graph relationships

### 02_EJB_Edge_Types.md
Specification for EJB-related edge types in the dependency graph:
- Relationship types (extends, implements, calls, etc.)
- EJB-specific edges (home-to-bean, remote-to-bean)
- Edge metadata and properties

### 03_Inspector_Tag_Extensions.md
Tag extensions for EJB and Spring migration:
- 60+ EJB-specific tags
- Migration recommendation tags
- Spring equivalent tags

### inspector-migration-guide.md
Step-by-step guide for migrating inspectors from file-centric to class-centric architecture:
- Migration checklist
- Common pitfalls
- V2 temporary file pattern
- Verification steps

## Archived Tasks

Completed tasks are in `../archived/`:
- Phase 1, 2, 3 completion reports
- Historical roadmaps and plans
- Refactoring documentation
- Implementation specifications

See [../archived/](../archived/) for historical context.

## Task Workflow

1. **Active specs** remain in `/tasks` until implementation is complete
2. **Completed implementations** move to `/archived` with completion report
3. **Implementation details** update `/guide.md` and `/patterns.md`
4. **Lessons learned** captured in completion reports before archiving

## See Also

- [Implementation Guide](../guide.md) - Current architecture patterns
- [Best Practices](../patterns.md) - Proven development patterns  
- [Archived Tasks](../archived/) - Historical context
