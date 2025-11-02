# Amazon Q Agent Block Types Design

## Overview

This document describes the design and implementation of two new block types for integrating Amazon Q as an autonomous agent in migration workflows.

## Background

**Problem:** Amazon Q performs autonomous code transformations but has limitations:
- Cannot process many files at once
- Requires files to be passed as context in the prompt
- Best used file-by-file for targeted transformations

**Solution:** Two complementary block types:
1. `AMAZON_Q_AGENT` - Single file/context transformation
2. `AMAZON_Q_AGENT_BATCH` - Iterate over multiple files, processing one at a time

## Block Type 1: AMAZON_Q_AGENT

### Purpose
Execute Amazon Q CLI for single-file or targeted transformations where you control the exact context.

### YAML Syntax

```yaml
- type: "AMAZON_Q_AGENT"
  name: "transform-core-service"
  description: "Transform critical service file"
  prompt: |
    Transform this EJB stateless bean to Spring @Service.
    Maintain all business logic and transaction semantics.
  context-files:
    - "src/main/java/com/example/CoreService.java"
    - "src/main/java/com/example/CoreServiceInterface.java"
  working-directory: "${project_root}"
  output-file: "target/CoreService.java"  # Optional - where Q writes result
  capture-output: true
  success-criteria: "exit-code-zero"  # or "output-contains: 'Success'"
  timeout-seconds: 300
```

### Parameters

| Parameter | Required | Type | Description |
|-----------|----------|------|-------------|
| `name` | Yes | String | Block identifier |
| `description` | No | String | Human-readable description |
| `prompt` | Yes | String | Instructions for Amazon Q (supports variable substitution) |
| `context-files` | Yes | List<String> | Files Q reads as context (supports variables) |
| `working-directory` | No | String | Where to execute Q CLI (default: project root) |
| `output-file` | No | String | Target file for Q's output (if applicable) |
| `capture-output` | No | Boolean | Whether to capture stdout/stderr (default: true) |
| `success-criteria` | No | String | How to determine success (default: "exit-code-zero") |
| `timeout-seconds` | No | Integer | Maximum execution time (default: 300) |

### Behavior

1. **Variable Substitution**: Replaces `${var}` in prompt and context-files
2. **Context Loading**: Reads specified files and includes in Q invocation
3. **CLI Invocation**: Executes Amazon Q CLI with prompt + context
4. **Output Capture**: Captures stdout/stderr for logging and validation
5. **Success Check**: Validates based on exit code or output pattern
6. **Error Handling**: Fails block if Q returns non-zero or timeout occurs

### Example CLI Invocation

```bash
aws amazonq transform \
  --prompt "Transform to Spring..." \
  --context-file "src/main/java/Service.java" \
  --output "target/Service.java" \
  --timeout 300
```

## Block Type 2: AMAZON_Q_AGENT_BATCH

### Purpose
Process multiple files with Amazon Q, one file at a time, using batch iteration pattern similar to `AI_PROMPT_BATCH`.

### YAML Syntax

```yaml
- type: "AMAZON_Q_AGENT_BATCH"
  name: "transform-all-stateless-beans"
  description: "Transform each stateless bean individually"
  items-variable: "stateless_beans"  # From prior GRAPH_QUERY
  prompt-template: |
    Transform this EJB stateless session bean to Spring @Service:
    
    File: ${item.path}
    Class: ${item.className}
    
    Requirements:
    - Change @Stateless to @Service
    - Add @Transactional for public methods
    - Use constructor injection
  context-files:
    - "${item.path}"  # Each iteration uses one file
  working-directory: "${project_root}"
  max-iterations: -1  # -1 = process all, or specify limit
  continue-on-error: false  # Stop on first failure
  timeout-seconds: 300  # Per file timeout
  output-variable: "transformation_results"  # Optional results array
```

### Parameters

| Parameter | Required | Type | Description |
|-----------|----------|------|-------------|
| `name` | Yes | String | Block identifier |
| `description` | No | String | Human-readable description |
| `items-variable` | Yes | String | Variable name containing list of items to process |
| `prompt-template` | Yes | String | Template with `${item.field}` placeholders |
| `context-files` | Yes | List<String> | File paths per iteration (supports `${item.field}`) |
| `working-directory` | No | String | Where to execute Q CLI |
| `max-iterations` | No | Integer | Max files to process (-1 = all, default: -1) |
| `continue-on-error` | No | Boolean | Whether to continue if one fails (default: false) |
| `timeout-seconds` | No | Integer | Timeout per file (default: 300) |
| `output-variable` | No | String | Variable to store results array |

### Behavior

1. **Item Iteration**: Loops through list from `items-variable`
2. **Template Expansion**: For each item, expands `${item.field}` in prompt and context-files
3. **Per-File Q Invocation**: Calls Amazon Q for each file individually
4. **Progress Tracking**: Reports N/M progress
5. **Error Handling**: 
   - If `continue-on-error=false`: Stop on first failure
   - If `continue-on-error=true`: Log error, continue to next file
6. **Result Aggregation**: Collects all results in output variable

### Integration with GRAPH_QUERY

**Step 1: Query files**
```yaml
- type: "GRAPH_QUERY"
  name: "query-stateless-beans"
  query-type: "BY_TAGS"
  tags: ["ejb.session.stateless"]
  output-variable: "stateless_beans"
```

**Step 2: Transform each**
```yaml
- type: "AMAZON_Q_AGENT_BATCH"
  name: "transform-beans"
  items-variable: "stateless_beans"
  prompt-template: "Transform ${item.path}..."
  context-files: ["${item.path}"]
```

## Implementation Classes

### 1. AmazonQAgentBlock.java

```java
package com.analyzer.migration.blocks.ai;

public class AmazonQAgentBlock extends BaseMigrationBlock {
    private final String prompt;
    private final List<String> contextFiles;
    private final String workingDirectory;
    private final String outputFile;
    private final boolean captureOutput;
    private final String successCriteria;
    private final int timeoutSeconds;
    
    @Override
    public BlockResult execute(MigrationContext context) {
        // 1. Substitute variables in prompt and context files
        // 2. Build Amazon Q CLI command
        // 3. Execute with timeout
        // 4. Capture output
        // 5. Validate success
        // 6. Return result
    }
    
    public static class Builder {
        // Builder pattern for construction
    }
}
```

### 2. AmazonQAgentBatchBlock.java

```java
package com.analyzer.migration.blocks.ai;

public class AmazonQAgentBatchBlock extends BaseMigrationBlock {
    private final String itemsVariableName;
    private final String promptTemplate;
    private final List<String> contextFileTemplates;
    private final String workingDirectory;
    private final int maxIterations;
    private final boolean continueOnError;
    private final int timeoutSeconds;
    private final String outputVariable;
    
    @Override
    public BlockResult execute(MigrationContext context) {
        // 1. Get items from context variable
        // 2. For each item:
        //    a. Expand template with item fields
        //    b. Execute Amazon Q agent
        //    c. Collect result
        //    d. Check error handling
        // 3. Aggregate results
        // 4. Store in output variable if specified
        // 5. Return combined result
    }
    
    public static class Builder {
        // Builder pattern for construction
    }
}
```

## MigrationPlanConverter Updates

Add conversion methods:

```java
private AmazonQAgentBlock convertAmazonQAgentBlock(BlockDTO dto) {
    // Extract parameters and build block
}

private AmazonQAgentBatchBlock convertAmazonQAgentBatchBlock(BlockDTO dto) {
    // Extract parameters and build block
}
```

Update switch statement in `convertBlock()`:

```java
switch (dto.getType().toUpperCase()) {
    // ... existing cases ...
    case "AMAZON_Q_AGENT":
        return convertAmazonQAgentBlock(dto);
    case "AMAZON_Q_AGENT_BATCH":
        return convertAmazonQAgentBatchBlock(dto);
    // ...
}
```

## Usage Examples in Migration Plan

### Example 1: Transform Single Critical File

```yaml
- id: "task-050"
  name: "Transform Core Business Service"
  description: "Convert critical EJB service to Spring"
  blocks:
    - type: "AMAZON_Q_AGENT"
      name: "transform-core-service"
      prompt: |
        Transform this core business service from EJB to Spring Boot.
        This is a critical file - preserve all business logic exactly.
        Add comprehensive logging for all transformations made.
      context-files:
        - "src/main/java/com/example/core/BusinessService.java"
        - "src/main/java/com/example/core/BusinessServiceInterface.java"
      working-directory: "${project_root}"
      timeout-seconds: 600
```

### Example 2: Batch Transform All Stateless Beans

```yaml
- id: "task-051"
  name: "Transform All Stateless Session Beans"
  description: "Convert all EJB stateless beans to Spring services"
  blocks:
    # Step 1: Query beans to transform
    - type: "GRAPH_QUERY"
      name: "find-stateless-beans"
      query-type: "BY_TAGS"
      tags: ["ejb.session.stateless"]
      output-variable: "stateless_beans"
    
    # Step 2: Transform each bean
    - type: "AMAZON_Q_AGENT_BATCH"
      name: "batch-transform-beans"
      items-variable: "stateless_beans"
      prompt-template: |
        Transform this EJB stateless session bean to Spring @Service:
        
        File: ${item.path}
        Class: ${item.className}
        
        Transformation Requirements:
        1. Replace @Stateless with @Service
        2. Add @Transactional to public methods that modify state
        3. Convert field injection to constructor injection
        4. Replace @EJB with @Autowired or constructor parameter
        5. Update logging to use SLF4J
        
        Preserve all business logic exactly as is.
      context-files:
        - "${item.path}"
      max-iterations: -1
      continue-on-error: false
      timeout-seconds: 300
      output-variable: "transformation_results"
    
    # Step 3: Verify compilation
    - type: "COMMAND"
      name: "verify-compilation"
      command: "mvn clean compile"
      working-directory: "${project_root}"
```

## Success Criteria Options

### exit-code-zero (default)
Q CLI must return exit code 0

### output-contains
```yaml
success-criteria: "output-contains: 'Transformation complete'"
```

### file-exists
```yaml
success-criteria: "file-exists: ${output_file}"
```

### all-of (multiple criteria)
```yaml
success-criteria: "all-of: [exit-code-zero, output-contains: 'Success']"
```

## Error Handling

### Single File (AMAZON_Q_AGENT)
- Q CLI failure → Block fails immediately
- Timeout → Block fails with timeout error
- Invalid context files → Block fails with validation error

### Batch (AMAZON_Q_AGENT_BATCH)
- Individual file failure:
  - If `continue-on-error=false` → Stop immediately, fail entire block
  - If `continue-on-error=true` → Log error, continue to next file
- All files failed → Block fails even with `continue-on-error=true`
- Partial success → Block succeeds if at least one file succeeds (with `continue-on-error=true`)

## Implementation Priority

1. ✅ Design document (this file)
2. Create `AmazonQAgentBlock.java`
3. Create `AmazonQAgentBatchBlock.java`
4. Update `MigrationPlanConverter.java`
5. Add example tasks to migration plan YAML
6. Write unit tests
7. Write integration tests with mock Q CLI
8. Document CLI integration requirements

## CLI Integration Requirements

The implementation will need:

1. **Amazon Q CLI installed and configured**
   ```bash
   aws configure
   # Q CLI must be available
   ```

2. **CLI Command Format**
   ```bash
   aws amazonq transform \
     --prompt "..." \
     --context-file "path/to/file.java" \
     [--output "path/to/output.java"] \
     [--timeout 300]
   ```

3. **Environment Variables**
   - `AWS_PROFILE` - AWS profile to use
   - `AWS_REGION` - AWS region for Q service
   - `Q_CLI_PATH` - Optional custom path to Q CLI

## Testing Strategy

### Unit Tests
- Prompt template expansion
- Context file resolution
- Success criteria evaluation
- Error handling paths

### Integration Tests  
- Mock Q CLI responses
- Batch iteration logic
- Variable substitution
- Timeout handling

### Manual Tests
- Real Q CLI invocation (with test files)
- Batch processing of multiple files
- Error scenarios (network, timeout, invalid files)

## Future Enhancements

1. **Parallel Batch Processing**: Process multiple files concurrently (with Q rate limits)
2. **Retry Logic**: Automatic retry on transient Q failures
3. **Result Validation**: Compile/test transformed code automatically
4. **Cost Tracking**: Track Q API usage and costs per transformation
5. **Diff Generation**: Generate before/after diffs for human review
