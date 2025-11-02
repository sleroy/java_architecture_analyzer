# Week 4: YAML Migration Plan Design & Implementation

**Date:** 2025-01-30  
**Status:** Design Complete  
**Related Files:**
- `analyzer-core/src/main/resources/migration-plans/jboss-to-springboot-phase0-1.yaml`
- Week 1-3 implementations (core infrastructure, blocks, execution engine)

## Executive Summary

Successfully designed and implemented YAML-based migration plan structure for the first 5 tasks (TASK-000 through TASK-101) of the JBoss EJB 2 to Spring Boot migration. The YAML approach enables:

1. **Natural multi-line content handling** (bash scripts, Java code, XML, prompts)
2. **Human-readable task definitions** for 100+ migration tasks
3. **Inventory integration** via GraphQueryBlock for EJB component analysis
4. **Variable substitution** for reusable, parameterized plans
5. **Validation** via YAML parsers and JSON Schema

## YAML Structure Overview

### Top-Level Structure

```yaml
migration-plan:
  name: "Migration Plan Name"
  version: "1.0.0"
  description: |
    Multi-line description
  
  metadata:
    author: "Team Name"
    created: "2025-01-30"
    target-spring-boot-version: "2.7.18"
  
  variables:
    project_root: "${project.root}"
    spring_boot_version: "2.7.18"
    # ... other variables
    
  phases:
    - id: "phase-0"
      name: "Phase Name"
      description: "Phase description"
      
      tasks:
        - id: "task-000"
          name: "Task Name"
          description: "Task description"
          type: "ANALYSIS|SETUP|IMPLEMENTATION"
          depends-on: []
          
          blocks:
            - type: "COMMAND|GRAPH_QUERY|FILE_OPERATION|..."
              name: "block-name"
              # ... block-specific parameters
```

### Multi-Line Content Handling

YAML's `|` literal block scalar preserves newlines - perfect for:

**Bash Scripts:**
```yaml
command: |
  mkdir -p directory
  cd directory
  git init
  git commit -m "Initial commit"
```

**Java Code:**
```yaml
content: |
  package com.example;
  
  public class Main {
      public static void main(String[] args) {
          System.out.println("Hello");
      }
  }
```

**XML/POM Files:**
```yaml
content: |
  <?xml version="1.0"?>
  <project>
      <modelVersion>4.0.0</modelVersion>
      <groupId>com.example</groupId>
  </project>
```

**AI Prompts:**
```yaml
prompt: |
  Analyze this code and provide:
  1. Architecture overview
  2. Migration risks
  3. Recommendations
  
  Be specific and actionable.
```

## Reusable Block Patterns Identified

### Pattern 1: Git Branch Management

**Frequency:** Used in TASK-000, TASK-001, and likely in future tasks

```yaml
- type: "COMMAND"
  name: "create-branch"
  command: |
    git checkout -b ${branch_name}
  working-directory: "${project_root}"
  timeout-seconds: 30
```

**Parameterization:** `${branch_name}` variable makes it reusable

### Pattern 2: EJB Inventory Query

**Frequency:** Used 4 times in TASK-000 (all EJBs, stateless beans, CMP entities, MDBs)

```yaml
- type: "GRAPH_QUERY"
  name: "query-component-type"
  query-type: "BY_TAGS"
  tags:
    - "ejb.session.stateless"
  output-variable: "component_results"
```

**Reuse Strategy:**
- Common for all EJB component types
- Output variables feed into AI prompts and documentation
- Can be templated with tag list parameter

### Pattern 3: AI-Powered Analysis & Documentation

**Frequency:** Used in TASK-000, TASK-002, and will be heavily used throughout migration

```yaml
- type: "AI_PROMPT"
  name: "generate-analysis"
  prompt: |
    Based on analysis results:
    ${variable_with_context}
    
    Provide:
    1. Specific analysis point
    2. Another analysis point
  output-variable: "analysis_result"
  temperature: 0.3
  max-tokens: 2000
```

**Reuse Strategy:**
- Template-based prompts with variable substitution
- Consistent structure: context → analysis request → output
- Temperature tuning per use case (0.2-0.3 for technical, 0.7+ for creative)

### Pattern 4: Documentation File Creation

**Frequency:** Used 3 times in TASK-000, TASK-001, TASK-002

```yaml
- type: "FILE_OPERATION"
  name: "create-document"
  operation: "CREATE"
  path: "${project_root}/docs/${document_name}.md"
  content: |
    # Document Title
    
    ${variable_content}
    
    ## Sections...
```

**Reuse Strategy:**
- Markdown templates with variable substitution
- Consistent documentation structure
- Can use AI-generated content via variables

### Pattern 5: Maven Build Verification

**Frequency:** Used in TASK-100, likely in all POM-related tasks

```yaml
- type: "COMMAND"
  name: "maven-verify"
  command: |
    mvn clean compile
  working-directory: "${project_root}/${module_dir}"
  timeout-seconds: 300
```

**Reuse Strategy:**
- Standard verification after POM changes
- Adjustable timeout based on project size
- Can be chained with other Maven goals

### Pattern 6: Interactive Validation Checkpoints

**Frequency:** Used in every task (TASK-000, TASK-001, TASK-002, TASK-100, TASK-101)

```yaml
- type: "INTERACTIVE_VALIDATION"
  name: "verify-step"
  validation-type: "MANUAL_CONFIRM"
  message: |
    Step completed:
    
    - Item 1 completed
    - Item 2 completed
    
    Please verify and proceed.
  required: true
  timeout-seconds: 1800
```

**Reuse Strategy:**
- Every task ends with validation
- Consistent message structure
- Adjustable timeout for complexity

### Pattern 7: Directory Structure Creation

**Frequency:** Used in TASK-001, likely in module creation tasks

```yaml
- type: "COMMAND"
  name: "create-directories"
  command: |
    mkdir -p ${base_path}/src/main/java
    mkdir -p ${base_path}/src/main/resources
    mkdir -p ${base_path}/src/test/java
    mkdir -p ${base_path}/src/test/resources
  working-directory: "${project_root}"
  timeout-seconds: 30
```

**Reuse Strategy:**
- Standard Maven/Spring Boot structure
- Parameterized base path
- Can be extended for additional directories

## Inventory Integration Strategy

### GraphQueryBlock Usage

The YAML plan heavily leverages `GraphQueryBlock` to query the H2 database containing analyzed EJB components:

**Query Types Supported:**
1. `BY_TYPE` - Query nodes by type (e.g., "JavaClass")
2. `BY_TAGS` - Query nodes by tags (e.g., "ejb.session.stateless")
3. `BY_TYPE_AND_TAGS` - Combined query
4. `ALL` - Return all nodes

### Example: EJB Component Discovery

```yaml
# Query all stateless session beans
- type: "GRAPH_QUERY"
  name: "query-stateless-beans"
  query-type: "BY_TAGS"
  tags:
    - "ejb.session.stateless"
  output-variable: "stateless_beans"
```

**Output Variables Created:**
- `stateless_beans` - Full list of GraphNodeEntity objects
- `stateless_beans_ids` - List of node IDs
- `stateless_beans_summary` - Summary map with count, query type, tags

### Using Query Results in AI Prompts

```yaml
- type: "AI_PROMPT"
  name: "generate-migration-plan"
  prompt: |
    Analyze these stateless session beans:
    
    Count: ${stateless_beans_summary.count}
    Components: ${stateless_beans_ids}
    
    Generate migration plan for each component.
  output-variable: "migration_plan"
```

### Using Query Results in Batch Processing

```yaml
- type: "AI_PROMPT_BATCH"
  name: "convert-each-bean"
  items-variable: "stateless_beans"
  prompt-template: |
    Convert this EJB bean to Spring:
    
    Class: ${current_item.className}
    Package: ${current_item.packageName}
    
    Generate:
    - @Service annotation
    - Constructor injection
    - @Transactional where needed
```

### Integration Benefits

1. **Automated Discovery:** No manual inventory required
2. **Data-Driven Decisions:** Migration strategy based on actual codebase
3. **Accurate Estimates:** Use component counts for effort estimation
4. **Intelligent Prioritization:** Identify complex components early
5. **Batch Processing:** AI can process each component individually

### Tag Strategy

**EJB Session Beans:**
- `ejb.session` (parent tag)
- `ejb.session.stateless`
- `ejb.session.stateful`

**EJB Entities:**
- `ejb.entity` (parent tag)
- `ejb.entity.cmp` (Container Managed Persistence)
- `ejb.entity.bmp` (Bean Managed Persistence)

**Message-Driven Beans:**
- `ejb.mdb`

**JAX-RS Resources:**
- `jaxrs.resource`
- `jaxrs.path`

**Usage in Queries:**
```yaml
# Query all EJB components
tags: ["ejb.session", "ejb.entity", "ejb.mdb"]

# Query only stateless beans
tags: ["ejb.session.stateless"]

# Query JAX-RS resources
tags: ["jaxrs.resource"]
```

## YAML Loader Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     YAML Loader Architecture                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────┐
│  YAML File      │
│  *.yaml         │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  YamlMigrationPlanLoader            │
│  - Jackson YAML ObjectMapper        │
│  - Reads & validates YAML           │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  MigrationPlanDTO (Data Transfer)   │
│  - Mirrors YAML structure           │
│  - Jackson annotations              │
│  - Validation annotations           │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  MigrationPlanConverter             │
│  - DTO → Domain Model               │
│  - Creates MigrationBlock instances │
│  - Injects dependencies             │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  MigrationPlan (Domain Model)       │
│  - Week 1 classes                   │
│  - Ready for execution              │
└─────────────────────────────────────┘
```

### Key Classes to Implement

#### 1. YamlMigrationPlanLoader

```java
package com.analyzer.migration.loader;

import com.analyzer.migration.plan.MigrationPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Loads migration plans from YAML files.
 */
public class YamlMigrationPlanLoader {
    private static final Logger logger = LoggerFactory.getLogger(YamlMigrationPlanLoader.class);
    
    private final ObjectMapper yamlMapper;
    private final MigrationPlanConverter converter;
    
    public YamlMigrationPlanLoader(MigrationPlanConverter converter) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.converter = converter;
    }
    
    /**
     * Load migration plan from file path.
     */
    public MigrationPlan loadFromFile(Path yamlFile) throws IOException {
        logger.info("Loading migration plan from: {}", yamlFile);
        MigrationPlanDTO dto = yamlMapper.readValue(yamlFile.toFile(), MigrationPlanDTO.class);
        return converter.convert(dto);
    }
    
    /**
     * Load migration plan from classpath resource.
     */
    public MigrationPlan loadFromResource(String resourcePath) throws IOException {
        logger.info("Loading migration plan from classpath: {}", resourcePath);
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            MigrationPlanDTO dto = yamlMapper.readValue(is, MigrationPlanDTO.class);
            return converter.convert(dto);
        }
    }
}
```

#### 2. MigrationPlanDTO

```java
package com.analyzer.migration.loader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for migration plan YAML.
 * Mirrors the YAML structure.
 */
public class MigrationPlanDTO {
    
    @JsonProperty("migration-plan")
    @NotNull
    @Valid
    private PlanRootDTO plan;
    
    public PlanRootDTO getPlan() {
        return plan;
    }
    
    public void setPlan(PlanRootDTO plan) {
        this.plan = plan;
    }
    
    public static class PlanRootDTO {
        @NotEmpty
        private String name;
        
        @NotEmpty
        private String version;
        
        private String description;
        
        @Valid
        private MetadataDTO metadata;
        
        private Map<String, String> variables;
        
        @NotEmpty
        @Valid
        private List<PhaseDTO> phases;
        
        // Getters and setters...
    }
    
    public static class MetadataDTO {
        private String author;
        private String created;
        
        @JsonProperty("target-spring-boot-version")
        private String targetSpringBootVersion;
        
        @JsonProperty("source-jboss-version")
        private String sourceJbossVersion;
        
        // Getters and setters...
    }
    
    public static class PhaseDTO {
        @NotEmpty
        private String id;
        
        @NotEmpty
        private String name;
        
        private String description;
        
        @NotEmpty
        @Valid
        private List<TaskDTO> tasks;
        
        // Getters and setters...
    }
    
    public static class TaskDTO {
        @NotEmpty
        private String id;
        
        @NotEmpty
        private String name;
        
        private String description;
        
        @NotEmpty
        private String type;
        
        @JsonProperty("depends-on")
        private List<String> dependsOn;
        
        @NotEmpty
        @Valid
        private List<BlockDTO> blocks;
        
        // Getters and setters...
    }
    
    public static class BlockDTO {
        @NotEmpty
        private String type;
        
        @NotEmpty
        private String name;
        
        private String description;
        
        // Dynamic properties based on block type
        private Map<String, Object> properties;
        
        // Getters and setters...
    }
}
```

#### 3. MigrationPlanConverter

```java
package com.analyzer.migration.loader;

import com.analyzer.migration.blocks.*;
import com.analyzer.migration.plan.*;
import com.analyzer.migration.loader.dto.*;
import com.analyzer.core.db.H2GraphStorageRepository;

/**
 * Converts DTOs to domain model objects.
 */
public class MigrationPlanConverter {
    
    private final H2GraphStorageRepository repository;
    
    public MigrationPlanConverter(H2GraphStorageRepository repository) {
        this.repository = repository;
    }
    
    public MigrationPlan convert(MigrationPlanDTO dto) {
        MigrationPlan.Builder planBuilder = MigrationPlan.builder()
                .name(dto.getPlan().getName())
                .description(dto.getPlan().getDescription());
        
        // Convert phases
        for (PhaseDTO phaseDto : dto.getPlan().getPhases()) {
            Phase phase = convertPhase(phaseDto);
            planBuilder.addPhase(phase);
        }
        
        return planBuilder.build();
    }
    
    private Phase convertPhase(PhaseDTO dto) {
        Phase.Builder phaseBuilder = Phase.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription());
        
        // Convert tasks
        for (TaskDTO taskDto : dto.getTasks()) {
            Task task = convertTask(taskDto);
            phaseBuilder.addTask(task);
        }
        
        return phaseBuilder.build();
    }
    
    private Task convertTask(TaskDTO dto) {
        Task.Builder taskBuilder = Task.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .type(TaskType.valueOf(dto.getType()));
        
        // Add dependencies
        if (dto.getDependsOn() != null) {
            dto.getDependsOn().forEach(taskBuilder::dependsOn);
        }
        
        // Convert blocks
        for (BlockDTO blockDto : dto.getBlocks()) {
            MigrationBlock block = convertBlock(blockDto);
            taskBuilder.addBlock(block);
        }
        
        return taskBuilder.build();
    }
    
    private MigrationBlock convertBlock(BlockDTO dto) {
        BlockType blockType = BlockType.valueOf(dto.getType());
        
        switch (blockType) {
            case COMMAND:
                return convertCommandBlock(dto);
            case GRAPH_QUERY:
                return convertGraphQueryBlock(dto);
            case FILE_OPERATION:
                return convertFileOperationBlock(dto);
            case AI_PROMPT:
                return convertAiPromptBlock(dto);
            case INTERACTIVE_VALIDATION:
                return convertInteractiveValidationBlock(dto);
            default:
                throw new IllegalArgumentException("Unknown block type: " + blockType);
        }
    }
    
    // Convert specific block types...
    private CommandBlock convertCommandBlock(BlockDTO dto) {
        return CommandBlock.builder()
                .name(dto.getName())
                .command((String) dto.getProperties().get("command"))
                .workingDirectory((String) dto.getProperties().get("working-directory"))
                .timeoutSeconds(((Number) dto.getProperties().get("timeout-seconds")).longValue())
                .build();
    }
    
    // ... other block converters
}
```

### Jackson YAML Integration

**Dependencies (add to pom.xml):**
```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
    <version>2.15.2</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>
<dependency>
    <groupId>javax.validation</groupId>
    <artifactId>validation-api</artifactId>
    <version>2.0.1.Final</version>
</dependency>
```

### Usage Example

```java
// Create loader with dependencies
H2GraphStorageRepository repository = ...;
MigrationPlanConverter converter = new MigrationPlanConverter(repository);
YamlMigrationPlanLoader loader = new YamlMigrationPlanLoader(converter);

// Load plan from classpath
MigrationPlan plan = loader.loadFromResource(
    "/migration-plans/jboss-to-springboot-phase0-1.yaml"
);

// Execute plan
MigrationContext context = new MigrationContext(projectRoot, repository);
MigrationEngine engine = new MigrationEngine(context);
ExecutionResult result = engine.executePlan(plan);
```

## JSON Schema for Validation

### Schema Purpose

- Validate YAML structure before loading
- Provide IDE autocomplete/validation
- Document the YAML format
- Enable third-party tool integration

### Schema Structure

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "https://github.com/yourorg/migration-plan-schema.json",
  "title": "Migration Plan",
  "description": "Schema for JBoss to Spring Boot migration plans",
  "type": "object",
  "required": ["migration-plan"],
  "properties": {
    "migration-plan": {
      "type": "object",
      "required": ["name", "version", "phases"],
      "properties": {
        "name": {
          "type": "string",
          "description": "Name of the migration plan"
        },
        "version": {
          "type": "string",
          "pattern": "^\\d+\\.\\d+\\.\\d+$",
          "description": "Semantic version"
        },
        "description": {
          "type": "string"
        },
        "metadata": {
          "$ref": "#/definitions/metadata"
        },
        "variables": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          }
        },
        "phases": {
          "type": "array",
          "items": {
            "$ref": "#/definitions/phase"
          },
          "minItems": 1
        }
      }
    }
  },
  "definitions": {
    "metadata": {
      "type": "object",
      "properties": {
        "author": {"type": "string"},
        "created": {"type": "string", "format": "date"},
        "target-spring-boot-version": {"type": "string"},
        "source-jboss-version": {"type": "string"}
      }
    },
    "phase": {
      "type": "object",
      "required": ["id", "name", "tasks"],
      "properties": {
        "id": {"type": "string", "pattern": "^phase-\\d+$"},
        "name": {"type": "string"},
        "description": {"type": "string"},
        "tasks": {
          "type": "array",
          "items": {"$ref": "#/definitions/task"},
          "minItems": 1
        }
      }
    },
    "task": {
      "type": "object",
      "required": ["id", "name", "type", "blocks"],
      "properties": {
        "id": {"type": "string", "pattern": "^task-\\d+$"},
        "name": {"type": "string"},
        "description": {"type": "string"},
        "type": {
          "type": "string",
          "enum": ["ANALYSIS", "SETUP", "IMPLEMENTATION", "VALIDATION"]
        },
        "depends-on": {
          "type": "array",
          "items": {"type": "string"}
        },
        "blocks": {
          "type": "array",
          "items": {"$ref": "#/definitions/block"},
          "minItems": 1
        }
      }
    },
    "block": {
      "type": "object",
      "required": ["type", "name"],
      "properties": {
        "type": {
          "type": "string",
          "enum": [
            "COMMAND",
            "FILE_OPERATION",
            "OPENREWRITE",
            "GRAPH_QUERY",
            "AI_PROMPT",
            "AI_PROMPT_BATCH",
            "INTERACTIVE_VALIDATION"
          ]
        },
        "name": {"type": "string"},
        "description": {"type": "string"}
      },
      "oneOf": [
        {"$ref": "#/definitions/commandBlock"},
        {"$ref": "#/definitions/graphQueryBlock"},
        {"$ref": "#/definitions/fileOperationBlock"},
        {"$ref": "#/definitions/aiPromptBlock"},
        {"$ref": "#/definitions/interactiveValidationBlock"}
      ]
    },
    "commandBlock": {
      "properties": {
        "type": {"const": "COMMAND"},
        "command": {"type": "string"},
        "working-directory": {"type": "string"},
        "timeout-seconds": {"type": "integer", "minimum": 1},
        "capture-output": {"type": "boolean", "default": true}
      },
      "required": ["command"]
    },
    "graphQueryBlock": {
      "properties": {
        "type": {"const": "GRAPH_QUERY"},
        "query-type": {
          "type": "string",
          "enum": ["BY_TYPE", "BY_TAGS", "BY_TYPE_AND_TAGS", "ALL"]
        },
        "tags": {
          "type": "array",
          "items": {"type": "string"}
        },
        "node-type": {"type": "string"},
        "output-variable": {"type": "string"}
      },
      "required": ["query-type", "output-variable"]
    },
    "fileOperationBlock": {
      "properties": {
        "type": {"const": "FILE_OPERATION"},
        "operation": {
          "type": "string",
          "enum": ["CREATE", "CREATE_DIR", "COPY", "MOVE", "DELETE"]
        },
        "path": {"type": "string"},
        "content": {"type": "string"}
      },
      "required": ["operation", "path"]
    },
    "aiPromptBlock": {
      "properties": {
        "type": {"const": "AI_PROMPT"},
        "prompt": {"type": "string"},
        "output-variable": {"type": "string"},
        "temperature": {"type": "number", "minimum": 0, "maximum": 2},
        "max-tokens": {"type": "integer", "minimum": 1}
      },
      "required": ["prompt", "output-variable"]
    },
    "interactiveValidationBlock": {
      "properties": {
        "type": {"const": "INTERACTIVE_VALIDATION"},
        "validation-type": {
          "type": "string",
          "enum": ["MANUAL_CONFIRM", "REVIEW", "APPROVAL"]
        },
        "message": {"type": "string"},
        "required": {"type": "boolean"},
        "timeout-seconds": {"type": "integer", "minimum": 1}
      },
      "required": ["validation-type", "message"]
    }
  }
}
```

### Validation Tools

**Command-line validation:**
```bash
# Using yq (YAML processor)
yq validate migration-plan.yaml

# Using ajv-cli (JSON Schema validator)
ajv validate -s migration-plan-schema.json -d migration-plan.yaml

# Using Python
python3 -c "import yaml, jsonschema; ..."
```

**IDE Integration:**
Add schema reference to YAML file:
```yaml
# yaml-language-server: $schema=migration-plan-schema.json

migration-plan:
  name: "My Plan"
  # ... rest of plan
```

## Implementation Plan (Week 4+)

### Phase 1: YAML Loader Implementation (Week 4)

**Tasks:**
1. ✅ Design YAML structure (completed)
2. ✅ Create example YAML for first 5 tasks (completed)
3. Add Jackson YAML dependencies to `analyzer-core/pom.xml`
4. Implement `MigrationPlanDTO` and nested DTOs
5. Implement `YamlMigrationPlanLoader`
6. Implement `MigrationPlanConverter`
7. Create unit tests for YAML loading
8. Test with example YAML file

**Deliverables:**
- `YamlMigrationPlanLoader.java`
- `MigrationPlanDTO.java` (with nested classes)
- `MigrationPlanConverter.java`
- Unit tests
- Integration test loading example YAML

### Phase 2: JSON Schema & Validation (Week 4-5)

**Tasks:**
1. Create JSON Schema file
2. Add schema validation to loader
3. Create schema documentation
4. Set up IDE integration guide
5. Add schema validation to CI/CD

**Deliverables:**
- `migration-plan-schema.json`
- Schema validation in loader
- Documentation for schema usage

### Phase 3: Block Converter Extensions (Week 5)

**Tasks:**
1. Implement converters for all 7 block types
2. Handle variable substitution in converters
3. Add validation for block-specific requirements
4. Create factory pattern for block creation
5. Test each block type conversion

**Deliverables:**
- Complete `MigrationPlanConverter` implementation
- Block factory classes
- Comprehensive unit tests

### Phase 4: Variable Resolution (Week 5)

**Tasks:**
1. Implement variable resolution system
2. Support system properties (${project.root})
3. Support plan-level variables
4. Support task-level variables
5. Support block output variables
6. Test variable scoping and precedence

**Deliverables:**
- `VariableResolver` class
- Variable scoping implementation
- Documentation on variable usage

### Phase 5: Advanced Features (Week 6)

**Tasks:**
1. Implement plan validation (circular dependencies, etc.)
2. Add plan visualization (Graphviz/Mermaid output)
3. Create plan templating system
4. Implement plan composition (include other plans)
5. Add dry-run mode

**Deliverables:**
- Plan validator
- Visualization generator
- Template system
- Plan composition feature

### Phase 6: Complete Migration Plans (Week 7-8)

**Tasks:**
1. Create YAML for Phase 2 tasks (TASK-200-299: Data Access Layer)
2. Create YAML for Phase 3 tasks (TASK-300-399: Business Logic
