# Reference Documentation

Supporting documentation and historical context for the Java Architecture Analyzer project.

## Contents

### EJB2.md
Comprehensive reference for EJB 2.x technology:
- EJB 2.x architecture and components
- Session beans, entity beans, message-driven beans
- Home and remote interfaces
- Deployment descriptors (ejb-jar.xml)
- Migration considerations to Spring/modern Java

**Use case:** Understanding legacy EJB 2.x code when writing migration inspectors.

### stereotypes.md
Stereotype patterns and classifications:
- Definition of architectural stereotypes
- Common patterns (DAO, Service, Repository, Controller, etc.)
- Detection rules and heuristics
- Tag naming conventions

**Use case:** Reference when implementing stereotype detection inspectors.

### reasoning.md
Design rationale and architectural decisions:
- Why certain architectural choices were made
- Trade-offs considered
- Historical context for design patterns
- Lessons learned from implementation

**Use case:** Understanding the "why" behind current architecture.

## When to Use

- **During inspector development** - Reference EJB or stereotype patterns
- **During migration planning** - Understand legacy technology
- **For architectural context** - Learn design rationale
- **Historical research** - Understand evolution of the codebase

## See Also

- [Implementation Guide](../implementation/guide.md) - Current architecture and patterns
- [Best Practices](../implementation/patterns.md) - Proven development patterns
- [Specifications](../spec/) - System specifications
