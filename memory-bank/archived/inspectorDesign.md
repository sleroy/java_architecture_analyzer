# Inspector Design

## Base Class Hierarchy
```
Inspector (interface)
├── SourceFileInspector (abstract)
│   ├── RegExpFileInspector
│   ├── CountRegexpInspector  
│   ├── TextFileInspector
│   └── JavaParserInspector
└── BinaryClassInspector (abstract)
    ├── ASMInspector
    ├── BCELInspector
    └── JavassistInspector
```

## Implementation Status
✅ All base classes implemented with:
- Template method pattern
- Consistent error handling (InspectorResult.error())
- Resource management (try-with-resources)
- Input validation and null safety

## Extension Points
- Extend appropriate base class
- Implement getName() and abstract analysis methods
- Follow error handling patterns
- Package as JAR for plugin loading