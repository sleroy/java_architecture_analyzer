# Migration Plan Schema Validation

## Overview

This document describes the JSON Schema validation system for migration plan YAML files to prevent common errors and ensure consistency.

## Schema File

Location: `migrations/migration-plan-schema.json`

The schema validates:
- Required properties per block type
- Valid enum values for operations and query types
- Proper property naming (e.g., `items-variable` not `batch-variable`)
- Type-specific requirements (e.g., CREATE_MULTIPLE needs `files` and `base-path`)

## Validation Tools

### Option 1: VSCode YAML Extension (Real-time Validation)

1. Install the **YAML** extension by Red Hat
2. Add schema reference to your YAML file header:

```yaml
# yaml-language-server: $schema=../migration-plan-schema.json

migration-plan:
  name: "My Migration Plan"
  ...
```

3. VSCode will now show:
   - Red squiggly lines for errors
   - Auto-complete suggestions
   - Hover documentation

### Option 2: Command-Line Validation with yamllint

Install yamllint:
```bash
pip install yamllint
```

Validate YAML files:
```bash
yamllint migrations/ejb2spring/**/*.yaml
```

### Option 3: JSON Schema Validator

Install ajv-cli:
```bash
npm install -g ajv-cli
```

Validate against schema (convert YAML to JSON first):
```bash
# Convert YAML to JSON and validate
python -c "import yaml, json, sys; print(json.dumps(yaml.safe_load(open('migrations/ejb2spring/jboss-to-springboot.yaml'))))" | \
  ajv validate -s migrations/migration-plan-schema.json -d -
```

## Common Validation Errors

### 1. Wrong Property Name in AI_PROMPT_BATCH

**Error:**
```
Required property 'items-variable' missing in block: my-block
```

**Fix:**
```yaml
# WRONG:
- type: "AI_PROMPT_BATCH"
  batch-variable: "my_items"  # ❌

# CORRECT:
- type: "AI_PROMPT_BATCH"
  items-variable: "my_items"  # ✅
```

### 2. Missing CREATE_MULTIPLE Properties

**Error:**
```
No enum constant FileOperationBlock.FileOperation.CREATE_MULTIPLE
```

**Fix:**
```yaml
# WRONG:
- type: "FILE_OPERATION"
  operation: "CREATE_MULTIPLE"
  path: "some/path"  # ❌ Wrong properties

# CORRECT:
- type: "FILE_OPERATION"
  operation: "CREATE_MULTIPLE"
  files: "${generated_files}"      # ✅ Variable name
  base-path: "${output_directory}" # ✅ Base directory
```

### 3. Missing prompt-template in AI_PROMPT_BATCH

**Error:**
```
Required property 'prompt-template' missing
```

**Fix:**
```yaml
# WRONG:
- type: "AI_PROMPT_BATCH"
  items-variable: "items"
  prompt: |  # ❌ Should be 'prompt-template'

# CORRECT:
- type: "AI_PROMPT_BATCH"
  items-variable: "items"
  prompt-template: |  # ✅ Correct property name
```

## Block-Specific Requirements

### AI_PROMPT_BATCH
**Required:**
- `items-variable`: Variable name containing list of items
- `prompt-template`: Template for generating prompts

**Optional:**
- `max-prompts`: Limit number of prompts (-1 for unlimited)
- `parallel`: Process items in parallel (boolean)
- `temperature`: AI temperature (0-2)
- `max-tokens`: Maximum tokens per prompt
- `output-variable`: Variable to store results

### FILE_OPERATION (CREATE_MULTIPLE)
**Required:**
- `files`: Variable name containing Map<String, String> of files
- `base-path`: Base directory path for all files

**Optional:**
- None (inherits `createDirectories` behavior from CREATE)

### GRAPH_QUERY
**Required:**
- `query-type`: One of BY_TYPE, BY_TAGS, BY_PACKAGE, CUSTOM

**Optional:**
- `node-type`: Filter by node type
- `tags`: Filter by tags (string or array)
- `output-variable`: Variable to store results

## Automated Validation

### Pre-commit Hook

Create `.git/hooks/pre-commit`:
```bash
#!/bin/bash
echo "Validating migration YAML files..."
yamllint migrations/**/*.yaml
if [ $? -ne 0 ]; then
  echo "YAML validation failed. Commit aborted."
  exit 1
fi
```

### Maven Plugin

Add to `pom.xml` (future enhancement):
```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>validate-yaml</id>
      <phase>validate</phase>
      <goals>
        <goal>exec</goal>
      </goals>
      <configuration>
        <executable>yamllint</executable>
        <arguments>
          <argument>migrations</argument>
        </arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Schema Maintenance

When adding new block types or properties:

1. Update `migration-plan-schema.json`
2. Add enum value for new block type
3. Define required/optional properties
4. Test with sample YAML
5. Update this documentation

## Testing Schema Validation

Test the schema catches errors:

```bash
# Create test YAML with intentional error
echo '
migration-plan:
  phases:
    - id: test
      name: Test
      tasks:
        - id: t1
          blocks:
            - type: AI_PROMPT_BATCH
              name: test
              batch-variable: items  # WRONG property name
              prompt-template: test
' > /tmp/test-plan.yaml

# Validate (should fail)
yamllint /tmp/test-plan.yaml
```

## Benefits

✅ Catch errors before runtime
✅ IDE autocomplete and validation
✅ Consistent property naming
✅ Self-documenting API
✅ Faster debugging
✅ CI/CD integration

## Next Steps

1. Add schema references to all phase YAML files
2. Set up pre-commit hooks
3. Add Maven validation phase
4. Document in main README
