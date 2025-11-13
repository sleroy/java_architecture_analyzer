# Next Phases Implementation Plan

## Overview

This document outlines the next implementation phases for the AI-powered Java pattern search feature. Phase 1 (Groovy dependencies and infrastructure) is complete. This plan covers Phases 2-4.

## Current Status

### ✅ Phase 1: Infrastructure (COMPLETE)
- Groovy dependencies installed
- Core services implemented (Cache, Execution, Generation)
- SearchJavaPatternTool integrated
- 28 unit tests
- Comprehensive documentation
- **Build Status**: SUCCESS

## Phase 2: OpenRewrite Integration

### Goal
Complete the end-to-end workflow by implementing actual OpenRewrite visitor execution on Java source files.

### Tasks

#### 2.1 Create OpenRewrite Integration Service
**File**: `src/main/java/com/analyzer/refactoring/mcp/service/OpenRewriteExecutionService.java`

**Responsibilities:**
- Load Java source files from projectPath
- Parse files using OpenRewrite
- Execute compiled Groovy visitors
- Collect matches with location information
- Return structured results

**Key Methods:**
```java
public List<PatternMatch> executeVisitorOnProject(
    OpenRewriteVisitorScript script,
    String projectPath,
    List<String> filePaths
) throws ExecutionException;

public List<PatternMatch> executeVisitorOnFile(
    OpenRewriteVisitorScript script,
    Path sourceFile
) throws ExecutionException;

private JavaSourceFile parseJavaFile(Path file);
private List<PatternMatch> extractMatches(JavaSourceFile sourceFile, Object visitorResult);
```

#### 2.2 Update SearchJavaPatternTool
**File**: `src/main/java/com/analyzer/refactoring/mcp/tool/openrewrite/SearchJavaPatternTool.java`

**Changes:**
- Remove placeholder `executeVisitorScript` method
- Inject `OpenRewriteExecutionService`
- Call actual execution service
- Return real pattern matches with file/line/column info

**Example:**
```java
@Autowired
public SearchJavaPatternTool(
    VisitorScriptCache scriptCache,
    GroovyScriptGenerationService scriptGenerator,
    GroovyScriptExecutionService scriptExecutor,
    OpenRewriteExecutionService openRewriteExecutor) { // NEW
    // ...
}

private PatternSearchResult executeVisitorScript(...) {
    List<PatternMatch> matches = openRewriteExecutor.executeVisitorOnProject(
        visitorScript, projectPath, filePaths);
    
    PatternSearchResult result = new PatternSearchResult();
    result.setMatches(matches);
    result.setScriptGenerated(true);
    result.setScriptSource(visitorScript.getSourceCode());
    return result;
}
```

#### 2.3 Enhance Visitor Script Generation Prompt
**File**: `src/main/java/com/analyzer/refactoring/mcp/service/GroovyScriptGenerationService.java`

**Updates to prompt:**
- Add instructions for storing matches in a specific format
- Include OpenRewrite Cursor API usage
- Add location extraction examples
- Specify result collection pattern

**Example additions:**
```
## Match Collection Format
Store each match in this format:
```groovy
def match = [
    nodeId: cursor.getValue().getId().toString(),
    nodeType: cursor.getValue().getClass().getSimpleName(),
    className: // extract from context
    location: [
        file: cursor.firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
        line: cursor.getValue().getPrefix().getLineNumber(),
        column: cursor.getValue().getPrefix().getColumn()
    ]
]
matches.add(match)
```

#### 2.4 Add Unit Tests
**File**: `src/test/java/com/analyzer/refactoring/mcp/service/OpenRewriteExecutionServiceTest.java`

**Test cases:**
- Parse simple Java file
- Execute visitor on single file
- Execute visitor on multiple files
- Extract location information
- Handle parsing errors
- Handle execution errors
- Verify match structure

**Estimated effort**: 3-4 hours

## Phase 3: Integration Testing

### Goal
Create comprehensive integration tests that verify the entire workflow from request to response.

### Tasks

#### 3.1 Mock Bedrock Response
**File**: `src/test/java/com/analyzer/refactoring/mcp/integration/MockBedrockClient.java`

**Features:**
- Provide pre-generated Groovy scripts for common patterns
- Simulate retry scenarios
- Test error handling

#### 3.2 End-to-End Integration Test
**File**: `src/test/java/com/analyzer/refactoring/mcp/integration/SearchJavaPatternIntegrationTest.java`

**Test scenarios:**
1. **Happy path**: Cache miss → Generate → Compile → Execute → Results
2. **Cache hit**: Retrieve from cache → Execute → Results
3. **Retry scenario**: Fail → Retry with error → Success
4. **Validation failure**: Generate → Compile fails → Retry
5. **Execution timeout**: Long-running script → Timeout
6. **Multiple file search**: Execute on multiple files

**Example structure:**
```java
@SpringBootTest
class SearchJavaPatternIntegrationTest {
    
    @Autowired
    private SearchJavaPatternTool tool;
    
    @MockBean
    private GroovyScriptGenerationService mockGenerator;
    
    @Test
    void testEndToEndWorkflow() {
        // Given: Mock Bedrock response
        when(mockGenerator.generateVisitorScript(...))
            .thenReturn(new GenerationResult(VALID_GROOVY_SCRIPT, 1));
        
        // When: Call tool
        String result = tool.searchJavaPattern(
            TEST_PROJECT_PATH,
            "singleton pattern",
            "ClassDeclaration",
            null
        );
        
        // Then: Verify results
        assertThat(result).contains("\"matches\":[");
        // Verify cache was populated
        // Verify script was executed
    }
}
```

#### 3.3 Performance Tests
**File**: `src/test/java/com/analyzer/refactoring/mcp/performance/PerformanceTest.java`

**Metrics to measure:**
- Cache hit performance (<100ms target)
- Cache miss performance (5-10s target)
- Script generation time
- Compilation time
- Execution time per file
- Memory usage

#### 3.4 Real Project Tests
**File**: `src/test/java/com/analyzer/refactoring/mcp/integration/RealProjectTest.java`

**Test on real projects:**
- Small project (~10 files)
- Medium project (~100 files)
- Verify matches are accurate
- Verify performance is acceptable

**Estimated effort**: 4-5 hours

## Phase 4: Advanced Features

### Goal
Add advanced capabilities for complex pattern matching scenarios.

### Tasks

#### 4.1 Multi-File Pattern Analysis
**Feature**: Support patterns that span multiple files

**Example**: Find all classes that implement an interface but don't override a specific method

**Implementation:**
- Enhance visitor to collect cross-file references
- Build dependency graph
- Execute analysis in multiple passes

#### 4.2 Pattern Template Library
**File**: `src/main/resources/visitor-templates/`

**Pre-built templates:**
- `singleton-pattern.groovy`
- `factory-pattern.groovy`
- `observer-pattern.groovy`
- `god-class-antipattern.groovy`
- `deep-nesting.groovy`

**Benefits:**
- Faster execution (skip Bedrock generation)
- Consistent results
- Lower costs

#### 4.3 Persistent Cache
**Feature**: Option to persist cache to disk

**Configuration:**
```properties
groovy.cache.persistent=true
groovy.cache.directory=.cache/groovy-visitors
groovy.cache.serialize-format=json
```

**Implementation:**
- Serialize compiled scripts to disk
- Load on startup
- Implement cache invalidation strategy

#### 4.4 Script Versioning
**Feature**: Track script versions and invalidate cache on changes

**Schema:**
```json
{
  "scriptId": "sha256-hash",
  "pattern": "singleton classes",
  "version": "1.0",
  "createdAt": "2025-11-13T10:00:00Z",
  "lastUsed": "2025-11-13T10:30:00Z",
  "useCount": 42,
  "averageExecutionMs": 45
}
```

#### 4.5 Parallel Execution
**Feature**: Execute visitor on multiple files in parallel

**Configuration:**
```properties
groovy.execution.parallel=true
groovy.execution.thread-pool-size=4
```

**Benefits:**
- Faster execution for large projects
- Better CPU utilization

**Estimated effort**: 6-8 hours

## Implementation Roadmap

### Sprint 1: OpenRewrite Integration (1 week)
- Days 1-2: Create OpenRewriteExecutionService
- Days 3-4: Update SearchJavaPatternTool
- Day 5: Enhance generation prompts
- Days 6-7: Unit tests and documentation

**Deliverable**: End-to-end workflow functioning with real file execution

### Sprint 2: Integration Testing (1 week)
- Days 1-2: Mock Bedrock and setup test infrastructure
- Days 3-4: End-to-end integration tests
- Day 5: Performance tests
- Days 6-7: Real project tests and bug fixes

**Deliverable**: Comprehensive test suite with >80% coverage

### Sprint 3: Advanced Features (2 weeks)
- Days 1-3: Multi-file pattern analysis
- Days 4-6: Pattern template library
- Days 7-9: Persistent cache
- Days 10-12: Script versioning
- Days 13-14: Parallel execution and optimization

**Deliverable**: Production-ready feature with advanced capabilities

## Success Metrics

### Phase 2: OpenRewrite Integration
- ✅ Visitor executes on real Java files
- ✅ Matches include accurate file/line/column info
- ✅ End-to-end workflow completes successfully
- ✅ Performance: <100ms cached, <10s uncached
- ✅ Unit test coverage >80%

### Phase 3: Integration Testing
- ✅ Integration test suite with >10 scenarios
- ✅ Performance benchmarks documented
- ✅ Successfully tested on real projects
- ✅ No regressions in existing functionality
- ✅ CI/CD integration

### Phase 4: Advanced Features
- ✅ Multi-file analysis working
- ✅ 5+ pattern templates available
- ✅ Persistent cache functional
- ✅ Script versioning tracking
- ✅ Parallel execution 2-4x faster
- ✅ Production deployment successful

## Risk Assessment

### High Risk
1. **OpenRewrite Learning Curve**: Team unfamiliar with OpenRewrite API
   - *Mitigation*: Dedicate time for API exploration, use documentation extensively

2. **Groovy-OpenRewrite Integration**: Complex interaction between generated Groovy and OpenRewrite
   - *Mitigation*: Start with simple examples, iterate gradually

### Medium Risk
1. **Performance**: Large projects may be slow
   - *Mitigation*: Implement parallel execution early, optimize hot paths

2. **Script Quality**: AI-generated scripts may have bugs
   - *Mitigation*: Enhance validation, improve prompts iteratively

### Low Risk
1. **Cache Invalidation**: Complex cache invalidation logic
   - *Mitigation*: Start with simple time-based eviction

## Dependencies

### External
- OpenRewrite 8.21.0 (already included)
- AWS Bedrock access (already configured)
- Groovy 4.0.18 (already installed)

### Internal
- Phase 1 infrastructure (complete)
- Test project with Java files
- Bedrock API quotas

## Resources Needed

### Development
- 1 Senior Java Developer (3-4 weeks)
- Access to test projects
- AWS Bedrock credits

### Documentation
- Update existing docs with Phase 2-4 features
- Create tutorial videos (optional)
- API examples for common patterns

## Getting Started with Phase 2

### Quick Start Commands

```bash
# 1. Create the new service
touch analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/OpenRewriteExecutionService.java

# 2. Create the test file
touch analyzer-refactoring-mcp/src/test/java/com/analyzer/refactoring/mcp/service/OpenRewriteExecutionServiceTest.java

# 3. Run existing tests to ensure nothing breaks
cd analyzer-refactoring-mcp
mvn test

# 4. Start implementing OpenRewriteExecutionService
code analyzer-refactoring-mcp/src/main/java/com/analyzer/refactoring/mcp/service/OpenRewriteExecutionService.java
```

### Key OpenRewrite APIs to Learn

1. **JavaParser**: Parse Java source files
2. **JavaSourceFile**: Represent parsed Java file
3. **JavaVisitor/JavaIsoVisitor**: Visit Java AST nodes
4. **Cursor**: Navigate AST during visitation
5. **ExecutionContext**: Share context during execution

### Example OpenRewrite Code

```java
// Parse a Java file
JavaParser parser = JavaParser.fromJavaVersion()
    .build();
    
List<SourceFile> sources = parser.parse(
    Paths.get(projectPath),
    null,
    new InMemoryExecutionContext()
);

// Execute visitor
for (SourceFile source : sources) {
    if (source instanceof JavaSourceFile javaSource) {
        // Create visitor instance from Groovy script
        JavaVisitor<?> visitor = createVisitorFromScript(script);
        
        // Execute
        JavaSourceFile result = (JavaSourceFile) visitor.visit(javaSource, ctx);
        
        // Extract matches
        List<Match> matches = extractMatches(visitor);
    }
}
```

## Conclusion

This plan provides a clear path from the current Phase 1 completion to a fully functional, production-ready AI-powered Java pattern search system. Each phase builds incrementally on the previous, with clear deliverables and success metrics.

**Next Step**: Create a new Cline task titled "Phase 2: OpenRewrite Integration for Groovy Visitor Execution" and use this document as the specification.
