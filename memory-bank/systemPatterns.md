# System Patterns

## Core Pattern: Inspector Pattern
Variation of Visitor pattern allowing different analysis operations without modifying core class structure.

## Key Components
- **Inspector Interface**: getName(), decorate(), supports()
- **Resource Resolution**: URI-based system (files, JARs, WARs)
- **Registry Pattern**: InspectorRegistry manages inspector instances
- **Template Method**: Base classes provide common workflow

## Package Structure
```
inspectors/
├── core/
│   ├── binary/     # ASM, BCEL, Javassist base classes
│   └── source/     # RegExp, JavaParser, Text base classes
└── rules/          # Concrete implementations
```

## Design Patterns
- **Factory**: InspectorRegistry, ResourceResolver
- **Strategy**: Different inspector implementations
- **Template Method**: ASMInspector, base classes
- **Composite**: CompositeResourceResolver

## Error Handling
- Graceful degradation (individual failures don't stop analysis)
- Result types: Success, Not Applicable, Error
- Comprehensive logging with context