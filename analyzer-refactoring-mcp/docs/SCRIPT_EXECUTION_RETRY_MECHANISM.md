# Script Execution Retry Mechanism

## Overview

This document describes the automatic retry mechanism for handling Groovy visitor script execution failures in the SearchJavaPatternTool.

## Problem Statement

When AI-generated Groovy visitor scripts fail during execution (e.g., due to incorrect OpenRewrite API usage), the system would previously just return an error to the user. This required manual intervention to fix the script.

Common errors include:
- `MissingPropertyException`: Accessing non-existent properties (e.g., `coordinates` on `Space` objects)
- `NoSuchMethodException`: Calling methods with incorrect signatures
- Incorrect OpenRewrite API usage patterns

## Solution

Implemented a self-healing retry mechanism that:

1. **Catches execution failures** - When a generated script fails during execution
2. **Extracts error context** - Captures the exception, stack trace, and problematic script
3. **Regenerates with feedback** - Passes error details to Bedrock AI to learn from mistakes
4. **Retries execution** - Tests the corrected script automatically
5. **Limits attempts** - Maximum of 3 execution attempts to avoid infinite loops

## Architecture

### Flow Diagram

```
Initial Request
      ↓
┌─────────────────┐
│ Get/Generate    │
│ Initial Script  │
└────────┬────────┘
         ↓
┌────────────────────────────────┐
│ EXECUTION RETRY LOOP           │
│ (max 3 attempts)               │
│                                │
│  ┌──────────────────────┐     │
│  │ Execute Script       │     │
│  └─────────┬────────────┘     │
│            │                   │
│     ┌──────▼──────┐           │
│     │  Success?   │           │
│     └──────┬──────┘           │
│            │                   │
│     Yes ◄──┴──► No            │
│      │            │            │
│      │     ┌──────▼──────────┐│
│      │     │ Out of retries? ││
│      │     └──────┬──────────┘│
│      │            │            │
│      │     Yes ◄──┴──► No     │
│      │      │          │       │
│      │      │   ┌──────▼──────┐
│      │      │   │ Invalidate  │
│      │      │   │ Cache       │
│      │      │   └──────┬──────┘
│      │      │          │       │
│      │      │   ┌──────▼──────┐
│      │      │   │ Regenerate  │
│      │      │   │ with Error  │
│      │      │   └──────┬──────┘
│      │      │          │       │
│      │      │   ┌──────▼──────┐
│      │      │   │ Wait & Loop │
│      │      │   └─────────────┘
│      │      │                  │
└──────┼──────┼──────────────────┘
       │      │
   Return  Return
   Success  Error
```

### Components Modified

#### 1. SearchJavaPatternTool

**New Constants:**
```java
private static final int MAX_EXECUTION_RETRIES = 3;
```

**Modified Method:**
- `searchJavaPattern()` - Now includes retry loop for execution failures

**New Helper Method:**
```java
private String formatExecutionError(ExecutionException e)
```
Formats error details for AI feedback, including:
- Full exception message
- Stack trace (first 5 lines)
- Contextual hints based on error type

**Enhanced Logic:**
```java
// Retry loop for execution failures
while (executionAttempts < MAX_EXECUTION_RETRIES) {
    try {
        result = executeVisitorScript(...);
        break; // Success!
    } catch (ExecutionException e) {
        if (executionAttempts < MAX_EXECUTION_RETRIES) {
            // Invalidate cache
            scriptCache.invalidate(...);
            
            // Regenerate with error feedback
            currentScript = generateAndCacheScript(
                ..., 
                previousScript, 
                formattedError);
            
            // Wait and retry
        } else {
            throw e; // Out of retries
        }
    }
}
```

#### 2. GroovyScriptGenerationService

**New Method:**
```java
public GenerationResult generateVisitorScriptWithErrorFeedback(
    String projectPath,
    String patternDescription,
    String nodeType,
    List<String> filePaths,
    String previousScript,    // Script that failed
    String previousError)     // Error message
```

**Enhanced Prompt:**
When regenerating, the prompt now includes:
```
## Previous Attempt Failed
The previous script had this error:
```
[error details]
```

Previous script that failed:
```groovy
[failed script]
```

Please fix the error and generate a corrected script.
```

#### 3. VisitorScriptCache

**New Method:**
```java
public void invalidate(
    String projectPath,
    String patternDescription,
    String nodeType,
    List<String> filePaths)
```

Removes failed scripts from memory cache (keeps disk storage for analysis).

## Error Context Features

### 1. Comprehensive Error Formatting

The `formatExecutionError()` method provides:

```java
EXECUTION ERROR: [main error message]

Caused by: [exception class]: [message]
  at [stack trace line 1]
  at [stack trace line 2]
  ...

HINT: [specific guidance based on error type]
```

### 2. Contextual Hints

Based on error patterns, provides specific guidance:

| Error Pattern | Hint Provided |
|--------------|---------------|
| `coordinates` | Use `Markers` instead of `coordinates`. Use `node.getMarkers()` or `getCursor().firstEnclosingOrThrow(SourceFile.class)` |
| `nosuchproperty` | Check OpenRewrite API documentation for correct property names and methods |
| `nosuchmethod` | Verify method signature matches OpenRewrite API |

## Metrics Tracking

Execution retries are tracked in `CallMetrics`:

```java
.cacheHit(cacheHit && executionAttempts == 1)  // Only true if no regeneration
.generationAttempts(totalGenerationAttempts)    // Cumulative across retries
```

## Example Scenario

### Scenario: MissingPropertyException for `coordinates`

**Attempt 1:**
```
Script generated with incorrect API usage:
  node.getPrefix().getCoordinates().getLine()
  
Execution: FAIL
Error: MissingPropertyException: No such property: coordinates for class: Space
```

**Attempt 2:**
```
Cache invalidated
Regenerating with error feedback:
  Previous error: "No such property: coordinates..."
  Hint: "Use Markers instead of coordinates..."
  
New script generated with correct API:
  def markers = node.getMarkers()
  // Extract location differently
  
Execution: SUCCESS
Result: Pattern matches returned
```

## Configuration

No additional configuration required. Uses existing settings:

```yaml
groovy:
  bedrock:
    max-retries: 3  # Used for generation retries within each attempt
  cache:
    enabled: true   # Cache must be enabled for invalidation to work
```

## Benefits

1. **Self-Healing**: Scripts automatically fix themselves on execution failures
2. **Learning**: AI sees real execution errors and generates corrections
3. **Token Efficient**: Only regenerates on actual failures, not preemptively
4. **User Experience**: Reduces manual intervention for common API mistakes
5. **Transparent**: Full logging shows retry process
6. **Analytics**: Metrics track success/failure rates and retry counts

## Limitations

1. **Maximum 3 Attempts**: After 3 execution failures, gives up and returns error
2. **Generation Retries**: Each regeneration can take multiple Bedrock API calls (up to `max-retries` setting)
3. **No Template Retry**: If a pre-built template fails, switches to generation mode
4. **Memory Only**: Cache invalidation only affects memory cache, not disk storage

## Future Enhancements

Potential improvements:

1. **Adaptive Retry Count**: Adjust max retries based on error complexity
2. **Error Pattern Learning**: Build knowledge base of common fixes
3. **Template Correction**: Allow template scripts to be corrected
4. **Parallel Attempts**: Generate multiple corrections simultaneously
5. **User Notification**: Real-time progress updates during retry cycles

## Testing

To test the retry mechanism:

```java
// Create a pattern that will fail initially
searchJavaPattern(
    projectPath,
    "Spring @Service annotated classes",  // Will likely fail with coordinates error
    "ClassDeclaration",
    null
);

// Monitor logs for:
// - Initial execution failure
// - Cache invalidation
// - Regeneration with error feedback
// - Successful retry
```

## Monitoring

Key log messages to watch:

```
- "Executing visitor script (attempt X/3)"
- "Execution failed on attempt X/3: [error]"
- "Regenerating script with error feedback..."
- "Invalidated cache for pattern: ..."
- "Execution successful on attempt X"
- "Pattern search completed: N matches found after X execution attempt(s)"
```

## Related Documentation

- [GROOVY_VISITOR_GENERATION.md](GROOVY_VISITOR_GENERATION.md) - Script generation process
- [SCRIPT_PERSISTENCE_AND_ANALYTICS.md](SCRIPT_PERSISTENCE_AND_ANALYTICS.md) - Caching and analytics
- [SEARCH_JAVA_PATTERN_TOOL.md](SEARCH_JAVA_PATTERN_TOOL.md) - Main tool documentation
