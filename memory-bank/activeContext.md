# Active Context

## Current Focus
**Primary Task**: Implement missing inspector base classes to complete the specification from purpose.md

## Recent Discoveries
1. **Command Parameter**: The `--war` parameter mentioned in purpose.md is redundant - the existing `--binary` parameter already handles WAR files effectively
2. **Architecture Assessment**: Core system is well-designed with solid inspector pattern implementation
3. **Missing Components**: 6 inspector base classes need implementation to meet full specification

## Next Steps (Priority Order)

### Phase 1: Essential Source Inspectors
1. **`RegExpFileInspector`** - Pattern matching inspector (HIGH PRIORITY)
   - Returns boolean if regex pattern matches in source file
   - Foundation for many pattern-based analyses
   
2. **`CountRegexpInspector`** - Pattern counting inspector (HIGH PRIORITY)
   - Returns count of regex pattern occurrences
   - Essential for metrics like annotation usage, API calls
   
3. **`TextFileInspector`** - Content extraction inspector (MEDIUM PRIORITY)
   - Returns string content of source file
   - Useful for full-text analysis and documentation extraction

### Phase 2: Advanced Parsing
4. **`JavaParserInspector`** - Modern Java parsing (MEDIUM PRIORITY)
   - Use JavaParser library for AST-based analysis
   - Most popular choice for source code analysis
   
5. **`BCELInspector`** - Alternative bytecode analysis (MEDIUM PRIORITY)
   - Apache BCEL for bytecode analysis
   - Alternative to ASM for specific use cases

### Phase 3: Specialized Tools
6. **`JavassistInspector`** - Runtime-focused bytecode (LOW PRIORITY)
   - Javassist for dynamic bytecode manipulation
   - More focused on runtime scenarios

## Active Decisions

### Remove `--war` Parameter
- **Decision**: Update purpose.md to remove redundant --war parameter
- **Rationale**: Current --binary parameter handles WAR files effectively
- **Impact**: Simplifies CLI interface, maintains functionality

### Inspector Implementation Strategy
- **Approach**: Prioritize by usage frequency and implementation complexity
- **Pattern**: Follow existing template method pattern from ASMInspector
- **Testing**: Each inspector gets comprehensive unit tests

## Important Patterns & Preferences

### Error Handling
- All inspectors must handle errors gracefully
- Return InspectorResult.error() rather than throwing exceptions
- Log errors with context but continue analysis

### Resource Management
- Use try-with-resources for all stream operations
- Stream-based processing to handle large files
- UTF-8 encoding as default for source files

### Testing Strategy
- Unit tests for each inspector with positive/negative cases
- Test fixtures in src/test/resources
- Integration tests via AnalysisEngine

## Recent Events (Sliding Window - Last 10)
1. **2025-01-10**: Analyzed purpose.md specification vs current implementation
2. **2025-01-10**: Identified 6 missing inspector base classes  
3. **2025-01-10**: Created memory bank structure for project documentation
4. **2025-01-10**: Planned implementation phases for missing inspectors
5. **2025-01-10**: Decided to remove redundant --war parameter
