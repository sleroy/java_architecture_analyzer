# AI Provider Selection Feature

## Overview

The analyzer now supports multiple AI backend providers for AI-assisted migration tasks. Users can choose between Amazon Q and Google Gemini CLI tools via a command-line parameter.

## Implementation Summary

### Architecture

**Package:** `com.analyzer.ai`

**Components:**
- `AiBackend` - Interface for AI backend implementations
- `AiBackendType` - Enum defining supported providers (AMAZON_Q, GEMINI)
- `AiBackendFactory` - Factory for creating backend instances
- `AmazonQBackend` - Amazon Q CLI implementation
- `GeminiBackend` - Google Gemini CLI implementation

**Integration:**
- `MigrationContext` - Stores and provides AI backend to blocks
- `ApplyMigrationCommand` - Adds `--ai-provider` CLI parameter
- `AiAssistedBlock` - Uses backend abstraction for execution
- `AiAssistedBatchBlock` - Delegates to AiAssistedBlock (automatic support)

### Key Features

1. **Pluggable Architecture** - Easy to add new providers in the future
2. **CLI-Based** - Both backends use ProcessBuilder for CLI execution
3. **Backward Compatible** - Defaults to Amazon Q if no provider specified
4. **Availability Check** - Validates CLI tool is installed before execution
5. **Consistent Interface** - Both providers use identical API

## Usage

### Command-Line Parameter

Add the `--ai-provider` parameter to any command that uses AI-assisted blocks:

```bash
# Use Amazon Q (default)
analyzer apply --project /path/to/project --plan migration.yaml

# Explicitly specify Amazon Q
analyzer apply --project /path/to/project --plan migration.yaml --ai-provider amazonq

# Use Google Gemini
analyzer apply --project /path/to/project --plan migration.yaml --ai-provider gemini
```

### Supported Provider Names

**Amazon Q:**
- `amazonq`
- `amazon-q`
- `q`
- `amazon_q`

**Gemini:**
- `gemini`
- `google`

### Prerequisites

#### Amazon Q
- Install Amazon Q CLI: `npm install -g @aws/amazon-q-developer-cli`
- Configure AWS credentials (SSO or IAM)
- Verify installation: `q --version`

#### Gemini
- Install Gemini CLI: `npm install -g @google/generative-ai-cli`
- Configure API key or authentication
- Verify installation: `gemini --version`

### CLI Commands Used

**Amazon Q:**
```bash
q chat --no-interactive --trust-all-tools
```

**Gemini:**
```bash
gemini --yolo
```

Both commands:
- Accept prompts via stdin
- Return responses via stdout
- Support timeout configuration
- Work in specified working directories

## Examples

### Example 1: Basic Usage with Gemini

```bash
analyzer apply \
  --project /home/user/my-ejb-project \
  --plan migrations/ejb2spring/jboss-to-springboot.yaml \
  --ai-provider gemini
```

### Example 2: Using Amazon Q with Variables

```bash
analyzer apply \
  --project /home/user/my-ejb-project \
  --plan migrations/ejb2spring/jboss-to-springboot.yaml \
  --ai-provider amazonq \
  -Dtarget_package=com.example.spring \
  -Dspring_boot_version=3.2.0
```

### Example 3: Dry Run with Gemini

```bash
analyzer apply \
  --project /home/user/my-ejb-project \
  --plan migrations/ejb2spring/jboss-to-springboot.yaml \
  --ai-provider gemini \
  --dry-run
```

## Error Handling

### Provider Not Available

If the specified provider's CLI is not installed:

```
[ERROR] AI provider 'gemini' is not available on this system
[ERROR] Please ensure the gemini --yolo CLI is installed and accessible
```

**Solution:** Install the required CLI tool and verify it's in your PATH.

### Invalid Provider Name

If an unknown provider is specified:

```
[ERROR] Invalid AI provider: myai
[ERROR] Supported providers: amazonq, gemini
```

**Solution:** Use one of the supported provider names.

### Execution Timeout

If the AI backend times out (default: 300 seconds):

```
[ERROR] Failed to execute AI backend for 'task-name': Gemini CLI timed out after 300 seconds
```

**Solution:** Increase timeout in YAML plan or simplify the prompt.

## YAML Configuration

No changes required to existing YAML migration plans. The AI provider is selected at runtime via CLI parameter:

```yaml
# Existing AI_ASSISTED block works with any provider
- type: AI_ASSISTED
  name: generate-spring-config
  prompt: |
    Generate a Spring Boot configuration class for the following EJB: ${current_node_id}
  working-directory: ${project_root}
```

## Technical Details

### Backend Interface

```java
public interface AiBackend {
    String executePrompt(String prompt, Path workingDirectory, int timeoutSeconds) 
        throws IOException, InterruptedException;
    
    boolean isAvailable();
    
    AiBackendType getType();
    
    String getCliCommand();
}
```

### Factory Usage

```java
// Create from string
AiBackend backend = AiBackendFactory.createFromString("gemini");

// Create from enum
AiBackend backend = AiBackendFactory.create(AiBackendType.GEMINI);

// Get default (Amazon Q)
AiBackend backend = AiBackendFactory.createDefault();
```

### Context Integration

```java
// Set in command
AiBackend backend = initializeAiBackend();
context.setAiBackend(backend);

// Use in blocks
AiBackend backend = context.getAiBackend();
String response = backend.executePrompt(prompt, workingDir, timeout);
```

## Testing

### Manual Testing

1. Install both CLI tools
2. Run migration with Amazon Q:
   ```bash
   analyzer apply --project test-project --plan test.yaml --ai-provider amazonq
   ```
3. Run same migration with Gemini:
   ```bash
   analyzer apply --project test-project --plan test.yaml --ai-provider gemini
   ```
4. Compare results

### Availability Testing

```bash
# Test Amazon Q availability
q --version

# Test Gemini availability  
gemini --version
```

## Future Enhancements

Potential future improvements:

1. **Additional Providers**
   - Claude CLI
   - GPT-4 CLI
   - Local LLMs (Ollama, etc.)

2. **Provider Configuration**
   - Model selection (e.g., `--ai-model gemini-1.5-pro`)
   - API key configuration
   - Custom CLI parameters

3. **Performance Optimization**
   - Parallel execution for batch operations
   - Response caching
   - Streaming output

4. **Enhanced Error Handling**
   - Retry logic
   - Fallback providers
   - Graceful degradation

## Related Files

- `analyzer-core/src/main/java/com/analyzer/ai/`
  - `AiBackend.java`
  - `AiBackendType.java`
  - `AiBackendFactory.java`
  - `AmazonQBackend.java`
  - `GeminiBackend.java`

- `analyzer-core/src/main/java/com/analyzer/migration/context/MigrationContext.java`
- `analyzer-app/src/main/java/com/analyzer/cli/ApplyMigrationCommand.java`
- `analyzer-core/src/main/java/com/analyzer/migration/blocks/ai/AiAssistedBlock.java`
- `analyzer-core/src/main/java/com/analyzer/migration/blocks/ai/AiAssistedBatchBlock.java`

## Changelog

### Version 1.0.0-SNAPSHOT (2025-11-06)

- Added AI backend abstraction layer
- Implemented Amazon Q backend
- Implemented Gemini backend
- Added `--ai-provider` CLI parameter to `ApplyMigrationCommand`
- Updated `MigrationContext` with AI backend support
- Refactored `AiAssistedBlock` to use backend abstraction
- Updated `AiAssistedBatchBlock` documentation
- Maintained backward compatibility (defaults to Amazon Q)
