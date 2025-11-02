# EJB to Spring Boot Migration Tool - Complete Implementation Plan

**Version:** 1.0.0  
**Date:** 2025-10-30  
**Status:** ✅ Ready for Implementation

---

## Table of Contents

- [1. Executive Summary](#1-executive-summary)
- [2. Architecture Overview](#2-architecture-overview)
- [3. Key Design Decisions](#3-key-design-decisions)
- [4. Component Architecture](#4-component-architecture)
- [5. Implementation Examples](#5-implementation-examples)
- [6. Implementation Roadmap](#6-implementation-roadmap)
- [7. Next Steps](#7-next-steps)

---

## 1. Executive Summary

This document provides the complete implementation plan for adding EJB-to-Spring Boot migration capabilities to the Java Architecture Analyzer tool.

### Key Features

- **Java Fluent API**: Type-safe migration plan definition with compile-time validation
- **Markdown Generation**: Auto-generate human-readable documentation from Java objects
- **Graph Database Integration**: Intelligent file filtering using existing inspector analysis
- **Batch + Iterative Operations**: OpenRewrite for batch refactoring, Amazon Q for complex AI-assisted tasks
- **5 Task Types**: AUTOMATED_REFACTORING, AUTOMATED_OPERATIONS, AI_ASSISTED, ANALYSIS, VALIDATION
- **Interactive CLI**: Step-by-step execution with progress tracking
- **H2 Database**: Track progress, resume on failure

### Success Criteria

- ✅ Execute first 5 tasks (TASK-000 through TASK-101)
- ✅ Support graph-based batch operations
- ✅ Generate Markdown documentation from Java
- ✅ Track progress with a state file in the project
- ✅ Integrate with existing inspectors

---

## 2. Architecture Overview

### 2.1 Component Structure

```
analyzer-ejb2spring/
├── src/main/java/com/analyzer/migration/
│   ├── plan/                    # Migration plan model
│   │   ├── MigrationPlan.java
│   │   ├── Phase.java
│   │   ├── Task.java
│   │   └── TaskType.java
│   ├── plans/                   # Concrete migration plans
│   │   └── JBossToSpringBootMigrationPlan.java
│   ├── blocks/                  # Execution blocks
│   │   ├── MigrationBlock.java
│   │   ├── BlockResult.java
│   │   ├── automated/
│   │   │   ├── CommandBlock.java
│   │   │   ├── FileOperationBlock.java
│   │   │   └── OpenRewriteBlock.java
│   │   ├── ai/
│   │   │   ├── AiPromptBlock.java
│   │   │   └── AiPromptBatchBlock.java
│   │   ├── analysis/
│   │   │   ├── GraphQueryBlock.java
│   │   │   └── InspectorBlock.java
│   │   └── validation/
│   │       └── InteractiveValidationBlock.java
│   ├── engine/                  # Execution engine
│   │   ├── MigrationEngine.java
│   │   ├── TaskExecutor.java
│   │   └── ProgressTracker.java
│   ├── export/                  # Documentation generation
│   │   └── MarkdownGenerator.java
│   └── context/
│       └── MigrationContext.java
├── src/main/resources/
│   └── recipes/ejb2-to-spring/
│       ├── 01-ejb-to-spring-service.yml
│       ├── 02-jaxrs-to-spring-rest.yml
│       └── 03-entity-imports-update.yml
└── pom.xml
```

### 2.2 Data Flow

```
1. Java Migration Plan (Source of Truth)
   ↓
2. MigrationEngine loads plan
   ↓
3. Query graph database for matching nodes
   ↓
4. Execute blocks (batch or iterative)
   ↓
5. Track progress in H2 database
   ↓
6. Generate Markdown documentation
```

---

## 3. Key Design Decisions

### 3.1 Java Fluent API (Not YAML)

**Decision**: Use Java Fluent API instead of YAML for migration plans

**Rationale**:
- ✅ Compile-time type safety
- ✅ IDE autocomplete and refactoring
- ✅ No schema validation needed
- ✅ Can still export to Markdown/YAML for documentation

**Example**:

```java
public class JBossToSpringBootMigrationPlan {
    public static MigrationPlan create() {
        return MigrationPlan.builder()
            .name("JBoss EJB 2 to Spring Boot Migration")
            .version("1.0.0")
            
            .phase(0, "PRE-MIGRATION ASSESSMENT")
                .task("TASK-000", "Project Baseline")
                    .type(TaskType.VALIDATION)
                    .commandBlock("000-1", "Create baseline branch")
                        .command("git checkout -b migration/springboot-baseline")
                        .build()
                    .build()
                .build()
            .build();
    }
}
```

### 3.2 Markdown Generation

**Decision**: Auto-generate Markdown documentation from Java objects

**Rationale**:
- Documentation always in sync with code
- Human-readable for stakeholders
- Can be version controlled
- Matches existing `MIGRATION_PLAN_JBOSS_TO_SPRINGBOOT.md` format

**Implementation**:

```java
// Generate Markdown
MigrationPlan plan = JBossToSpringBootMigrationPlan.create();
String markdown = plan.toMarkdown();
plan.toMarkdownFile(Path.of("MIGRATION_PLAN_JBOSS_TO_SPRINGBOOT.md"));
```

### 3.3 Graph Database Integration

**Decision**: Use graph database to query and filter files for batch operations

**Rationale**:
- Leverages existing inspector analysis
- Intelligent file filtering (e.g., "all session beans")
- Supports both batch and iterative operations

**Example**:

```java
// Query graph for session beans
.graphQueryBlock("301-1", "Find session beans")
    .nodeType("CLASS")
    .tagsAny("ejb.session.stateless")
    .outputVar("session_beans")
    .build()

// Apply OpenRewrite to ALL beans (BATCH)
.openRewriteBlock("301-2", "Transform beans")
    .recipeName("EjbToSpringService")
    .targetNodes("${session_beans}")  // Apply to filtered nodes
    .build()

// Or iterate for AI assistance (ITERATIVE)
.aiPromptBatchBlock("301-3", "Handle complex cases")
    .inputNodes("${failed_beans}")
    .promptTemplate("Convert ${current_node.class_name}...")
    .build()
```

### 3.4 Template Engine for Variable Substitution

**Decision**: Use FreeMarker template engine for all variable substitution

**Rationale**:
- ✅ Industry-standard, mature templating solution
- ✅ Powerful expression language (conditionals, loops, functions)
- ✅ Safe - no code execution risk
- ✅ Already in dependencies (see Maven section)
- ✅ Supports complex template logic beyond simple `${var}` replacement

**Features**:
- Variable substitution: `${project.path}`
- Conditionals: `<#if project.hasTests>...</#if>`
- Loops: `<#list nodes as node>...</#list>`
- Built-in functions: `${current_node.file_path?upper_case}`
- Custom functions: Read file, format date, etc.

**Example Template**:
```java
String promptTemplate = """
    Convert session bean to Spring service:
    
    **File:** ${current_node.file_path}
    **Class:** ${current_node.class_name}
    <#if current_node.hasRemoteInterface>
    **Remote Interface:** ${current_node.remoteInterface}
    </#if>
    
    <#list current_node.methods as method>
    - Method: ${method.name}
    </#list>
    """;

// In MigrationContext
public String substituteVariables(String template) {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
    Template tmpl = new Template("template", new StringReader(template), cfg);
    
    StringWriter out = new StringWriter();
    tmpl.process(contextVariables, out);
    return out.toString();
}
```

**Benefits for Complex Scenarios**:
- Read file contents in template: `${read_file(current_node.file_path)}`
- Conditional prompts based on node properties
- Loop over collections (methods, fields, dependencies)
- Format strings, dates, numbers
- Custom directives for domain-specific logic

### 3.5 Batch vs Iterative Operations

**Decision**: Support both batch (OpenRewrite) and iterative (AI) operations

**Batch Operations** (OpenRewrite):
- Apply recipe to ALL matching files at once
- Fast, automated transformations
- Good for simple, mechanical changes

**Iterative Operations** (AI-assisted):
- Generate one prompt per file
- User reviews and confirms each
- Good for complex transformations requiring human judgment

---

## 4. Component Architecture

### 4.1 Core Interfaces

```java
// MigrationBlock.java
public interface MigrationBlock {
    BlockResult execute(MigrationContext context) throws MigrationException;
    BlockType getType();
    boolean canExecute(MigrationContext context);
    String getId();
    String toMarkdownDescription();  // For documentation generation
}

// BlockResult.java
public class BlockResult {
    private final boolean success;
    private final String message;
    private final Map<String, Object> outputs;
    
    public static BlockResult success(String message) { ... }
    public static BlockResult failure(String message) { ... }
    public BlockResult withOutput(String key, Object value) { ... }
}
```

### 4.2 Graph Query Block

```java
public class GraphQueryBlock implements MigrationBlock {
    private String nodeType;
    private List<String> tagsAny;
    private List<String> tagsAll;
    private List<String> excludeTags;
    private String outputVar;
    
    @Override
    public BlockResult execute(MigrationContext context) {
        // Query H2 graph database
        GraphRepository repo = context.getGraphRepository();
        
        List<GraphNode> nodes = repo.query()
            .nodeType(nodeType)
            .hasAnyTag(tagsAny)
            .hasAllTags(tagsAll)
            .notHasTags(excludeTags)
            .execute();
        
        // Store in context
        context.setVariable(outputVar, nodes);
        
        return BlockResult.success(
            String.format("Found %d nodes", nodes.size())
        ).withOutput("node_count", nodes.size());
    }
    
    @Override
    public String toMarkdownDescription() {
        return String.format(
            "**Query graph database**\n" +
            "  - Node type: `%s`\n" +
            "  - Tags (any): `%s`\n" +
            "  - Output variable: `%s`",
            nodeType, String.join(", ", tagsAny), outputVar
        );
    }
}
```

### 4.3 OpenRewrite Block (Enhanced for Batch)

```java
public class OpenRewriteBlock implements MigrationBlock {
    private String recipeName;
    private Path recipeFile;
    private String targetNodesVar;  // Reference to graph query result
    
    @Override
    public BlockResult execute(MigrationContext context) {
        // Load recipe
        Recipe recipe = loadRecipe(recipeFile);
        
        // Get target files from graph query
        List<GraphNode> nodes = context.getVariable(targetNodesVar);
        List<Path> targetFiles = nodes.stream()
            .map(node -> Path.of(node.getProperty("file_path")))
            .collect(Collectors.toList());
        
        // Apply recipe to ALL files (BATCH OPERATION)
        List<Result> results = applyRecipeToFiles(recipe, targetFiles);
        
        // Write changes
        int changedFiles = writeResults(results);
        
        return BlockResult.success(
            String.format("Transformed %d files", changedFiles)
        );
    }
}
```

### 4.4 AI Prompt Batch Block (NEW)

```java
public class AiPromptBatchBlock implements MigrationBlock {
    private String inputNodesVar;
    private String promptTemplate;
    private boolean pauseBetween;
    private boolean skippable;
    
    @Override
    public BlockResult execute(MigrationContext context) {
        List<GraphNode> nodes = context.getVariable(inputNodesVar);
        
        int processed = 0;
        Scanner scanner = new Scanner(System.in);
        
        for (GraphNode node : nodes) {
            processed++;
            
            // Set current node in context for template variables
            context.setVariable("current_node", node);
            
            // Generate prompt with node-specific data
            String prompt = context.substituteVariables(promptTemplate);
            
            // Display prompt
            System.out.println(String.format(
                "\n=== AMAZON Q PROMPT [%d/%d] - %s ===", 
                processed, nodes.size(), node.getProperty("class_name")
            ));
            System.out.println(prompt);
            System.out.println("===================================");
            
            if (skippable) {
                System.out.print("\nExecute? [Y/n/s(kip all)]: ");
                String response = scanner.nextLine();
                if (response.equalsIgnoreCase("s")) break;
                if (response.equalsIgnoreCase("n")) continue;
            } else {
                System.out.print("\nPress Enter after executing...");
                scanner.nextLine();
            }
        }
        
        return BlockResult.success("Processed " + processed + " files");
    }
}
```

### 4.5 Markdown Generator

```java
public class MarkdownGenerator {
    private StringBuilder sb = new StringBuilder();
    
    public MarkdownGenerator h1(String text) {
        sb.append("# ").append(text).append("\n");
        return this;
    }
    
    public MarkdownGenerator h2(String text) {
        sb.append("## ").append(text).append("\n");
        return this;
    }
    
    public MarkdownGenerator codeBlock(String language, String code) {
        sb.append("```").append(language).append("\n");
        sb.append(code).append("\n");
        sb.append("```\n");
        return this;
    }
    
    @Override
    public String toString() {
        return sb.toString();
    }
}

// In MigrationPlan.java
public String toMarkdown() {
    MarkdownGenerator gen = new MarkdownGenerator();
    
    gen.h1(name);
    gen.h2(description);
    
    for (Phase phase : phases) {
        gen.h2("PHASE " + phase.getId() + ": " + phase.getName());
        
        for (Task task : phase.getTasks()) {
            gen.h4("TASK-" + task.getId() + ": " + task.getName());
            gen.bold("Purpose: ").append(task.getPurpose());
            
            for (MigrationBlock block : task.getBlocks()) {
                gen.append(block.toMarkdownDescription());
            }
        }
    }
    
    return gen.toString();
}
```

---

## 5. Implementation Examples

### 5.1 Complete Task Example (TASK-301)

```java
.task("TASK-301", "Migrate Session Beans to Spring Services")
    .type(TaskType.AUTOMATED_REFACTORING)
    .dependency("TASK-300")
    .purpose("Convert all EJB session beans to Spring services")
    
    // Step 1: Query graph for session beans
    .graphQueryBlock("301-1", "Find stateless session beans")
        .nodeType("CLASS")
        .tagsAny("ejb.session.stateless")
        .outputVar("session_beans")
        .build()
    
    // Step 2: Show user what will be migrated
    .validationBlock("301-2", "Review beans to migrate")
        .message("Found ${session_beans.count} session beans to migrate")
        .previewData("${session_beans}")
        .build()
    
    // Step 3: Apply OpenRewrite to ALL beans (BATCH)
    .openRewriteBlock("301-3", "Auto-transform session beans")
        .recipeName("com.byoskill.ejb2spring.EjbToSpringService")
        .recipeFile("recipes/ejb2-to-spring/01-ejb-to-spring-service.yml")
        .targetNodes("${session_beans}")
        .outputVar("auto_transformed")
        .build()
    
    // Step 4: Compile to verify
    .commandBlock("301-4", "Compile transformed code")
        .command("mvn compile")
        .workingDir("${project.path}")
        .build()
    
    // Step 5: Find beans that failed transformation
    .graphQueryBlock("301-5", "Find failed transformations")
        .nodeIdsExcluding("${session_beans}", "${auto_transformed}")
        .outputVar("failed_beans")
        .build()
    
    // Step 6: Handle failures with AI (ITERATIVE)
    .aiPromptBatchBlock("301-6", "Manually transform failed beans")
        .inputNodes("${failed_beans}")
        .skippable(true)
        .promptTemplate("""
            This session bean failed auto-transformation.
            
            **File:** ${current_node.file_path}
            **Class:** ${current_node.class_name}
            
            Current code:
            ```java
            ${read_file(current_node.file_path)}
            ```
            
            Please manually convert to Spring @Service.
            """)
        .build()
    
    .build()
```

### 5.2 Maven Dependencies

Add to `analyzer-ejb2spring/pom.xml`:

```xml
<dependencies>
    <!-- OpenRewrite -->
    <dependency>
        <groupId>org.openrewrite</groupId>
        <artifactId>rewrite-java</artifactId>
        <version>8.21.0</version>
    </dependency>
    <dependency>
        <groupId>org.openrewrite</groupId>
        <artifactId>rewrite-maven</artifactId>
        <version>8.21.0</version>
    </dependency>
    
    <!-- Template Engine (optional, for complex templates) -->
    <dependency>
        <groupId>org.freemarker</groupId>
        <artifactId>freemarker</artifactId>
        <version>2.3.32</version>
    </dependency>
</dependencies>
```

### 5.3 Database Schema Updates

Add to `analyzer-core/src/main/resources/db/schema.sql`:

```sql
-- Migration Progress Tracking
CREATE TABLE IF NOT EXISTS migration_progress (
    task_id VARCHAR(20) PRIMARY KEY,
    phase_id INT NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    validation_results TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Task Dependencies
CREATE TABLE IF NOT EXISTS task_dependencies (
    task_id VARCHAR(20) NOT NULL,
    requires_task_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (task_id, requires_task_id)
);

-- Block Execution History
CREATE TABLE IF NOT EXISTS block_execution_history (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(20) NOT NULL,
    block_id VARCHAR(50) NOT NULL,
    block_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    execution_time_ms BIGINT,
    output_data TEXT,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_migration_status ON migration_progress(status);
CREATE INDEX idx_migration_phase ON migration_progress(phase_id);
```

---

## 6. Implementation Roadmap

### Week 1: Core Infrastructure (Days 1-5)

**Day 1-2: Project Setup**
- [ ] Update `analyzer-ejb2spring/pom.xml` with OpenRewrite dependencies
- [ ] Create package structure (plan/, blocks/, engine/, export/)
- [ ] Update database schema with migration tables

**Day 3-4: Core Interfaces**
- [ ] Implement MigrationPlan, Phase, Task classes
- [ ] Implement MigrationBlock interface and BlockResult
- [ ] Implement MigrationContext for variable substitution

**Day 5: Markdown Generator**
- [ ] Implement MarkdownGenerator utility
- [ ] Add toMarkdownDescription() to all block types
- [ ] Test Markdown generation

### Week 2: Block Implementations (Days 6-10)

**Day 6-7: Automated Blocks**
- [ ] Implement CommandBlock
- [ ] Implement FileOperationBlock
- [ ] Write unit tests

**Day 8-9: Graph & AI Blocks**
- [ ] Implement GraphQueryBlock
- [ ] Implement OpenRewriteBlock (with batch support)
- [ ] Implement AiPromptBlock
- [ ] Implement AiPromptBatchBlock

**Day 10: Validation**
- [ ] Implement InteractiveValidationBlock
- [ ] Add validation helpers (file exists, command, etc.)

### Week 3: Execution Engine & CLI (Days 11-15)

**Day 11-12: Engine**
- [ ] Implement MigrationEngine
- [ ] Implement TaskExecutor
- [ ] Implement ProgressTracker
- [ ] Add H2 database persistence

**Day 13-14: CLI**
- [ ] Create Ejb2SpringCommand
- [ ] Implement status, execute, interactive subcommands
- [ ] Add to AnalyzerCLI

**Day 15: Integration**
- [ ] Connect to existing inspector system
- [ ] Test graph queries with real data
- [ ] Create sample OpenRewrite recipes

### Week 4: Testing & Documentation (Days 16-20)

**Day 16-17: Integration Testing**
- [ ] Create test EJB project
- [ ] Execute TASK-000 through TASK-002
- [ ] Execute TASK-100 and TASK-101
- [ ] Fix bugs

**Day 18: Java Migration Plan**
- [ ] Create JBossToSpringBootMigrationPlan.java
- [ ] Define first 5 tasks in Java
- [ ] Test plan execution

**Day 19: Documentation**
- [ ] Generate Markdown from Java plan
- [ ] Verify output matches expected format
- [ ] Write user documentation

**Day 20: Release**
- [ ] Final testing
- [ ] Code review
- [ ] Create release v1.0.0

---

## 7. Next Steps

### To Begin Implementation:

1. **Review this document** thoroughly
2. **Create feature branch**: `git checkout -b feature/ejb2spring-migration-tool`
3. **Start with Week 1, Day 1** tasks
4. **Follow roadmap sequentially**

### Quick Start Commands

Once implemented, users will execute:

```bash
# Generate Markdown documentation from Java plan
java-architecture-analyzer ejb2spring generate-docs

# Show migration status
java-architecture-analyzer ejb2spring status --project /path/to/ejb-project

# Execute specific task
java-architecture-analyzer ejb2spring execute \
  --project /path/to/ejb-project \
  --task TASK-000

# Interactive mode
java-architecture-analyzer ejb2spring interactive \
  --project /path/to/ejb-project
```

### Success Metrics

At completion, the tool should:

✅ Define migration plans in type-safe Java  
✅ Generate Markdown documentation automatically  
✅ Query graph database for intelligent filtering  
✅ Support batch operations (OpenRewrite)  
✅ Support iterative operations (AI-assisted)  
✅ Track progress in H2 database  
✅ Execute first 5 tasks successfully  

---

**Document Version:** 1.0.0  
**Last Updated:** 2025-10-30  
**Status:** ✅ Ready for Implementation
