# Token Optimization Complete Implementation Catalog

## Overview

This document provides the complete catalog of 16 tools/blocks designed to reduce AmazonQ token consumption by 85-95% during EJB to Spring migrations.

## Problem Statement

Current AI_ASSISTED_BATCH blocks send massive prompts (2000-5000 tokens) per class:
- Full source code context
- FreeMarker templates with conditional logic  
- Anti-pattern analysis results
- Migration instructions

**For 100+ EJB classes**: 250,000-500,000 tokens

**Solution**: Specialized tools/blocks that perform mechanical transformations, reducing AI prompts to compact metadata.

**Expected Result**: 15,000-30,000 tokens (85-95% reduction)

## Module Architecture

```
analyzer-inspectors/        Generic Java analysis
├── analysis/
│   ├── ClassMetadataExtractor
│   ├── DependencyGraphAnalyzer
│   └── FilePatternMatcher

analyzer-ejb2spring/        EJB-specific domain
├── openrewrite/           OpenRewrite recipes
├── analysis/              EJB-specific analyzers
└── blocks/                EJB-specific block types

analyzer-app/               Generic execution engine
└── executor/              Discovers blocks from all modules

analyzer-refactoring-mcp/   AI agent tooling (optional)
└── tool/                  MCP tools wrapping blocks
```

## Complete Catalog (16 Items)

### Legend
- **[OR]** = OpenRewrite Recipe (analyzer-ejb2spring/openrewrite)
- **[BT]** = Block Type (analyzer-ejb2spring/blocks)
- **[AN]** = Analysis Utility (analyzer-ejb2spring/analysis or analyzer-inspectors/analysis)
- **[MT]** = MCP Tool (analyzer-refactoring-mcp/tool) - Optional

---

## CATEGORY 1: High-Level Migration Pattern Tools

### 1. migrate_stateless_ejb_to_service ⭐ HIGH PRIORITY
**Token Savings**: 2000 → 100 tokens (95%)

**Components**:
- **[OR]** `StatelessToServiceRecipe` - Deterministic AST transformations
- **[BT]** `MigrateStatelessEjbBlock` - Orchestrates recipe
- **[MT]** `MigrateStatelessEjbTool` - AI agent wrapper

**Transformations**:
- Replace `@Stateless` → `@Service`
- Remove `@Local` and `@Remote`
- Convert `@EJB` fields → constructor injection
- Add `@Transactional` to transactional methods
- Update imports (remove javax.ejb.*, add org.springframework.*)

**Usage in YAML**:
```yaml
- type: "MIGRATE_STATELESS_EJB"
  source-nodes: "${stateless_beans}"
  target-directory: "${refactoring_project_path}/src/main/java"
  options:
    add-transactional: true
    constructor-injection: true
```

### 2. migrate_entity_bean_to_jpa
**Token Savings**: 2500 → 100 tokens (96%)

**Components**:
- **[OR]** `EntityBeanToJpaRecipe`
- **[BT]** `MigrateEntityBeanBlock`
- **[MT]** `MigrateEntityBeanTool`

**Transformations**:
- Convert EJB 2.x Entity beans → JPA entities
- Handle ejbCreate/ejbRemove lifecycle methods
- Convert finder methods to JPQL queries
- Add JPA annotations (@Entity, @Id, etc.)

### 3. convert_mdb_to_spring_jms
**Token Savings**: 2000 → 100 tokens (95%)

**Components**:
- **[OR]** `MdbToSpringJmsRecipe`
- **[BT]** `ConvertMdbBlock`
- **[MT]** `ConvertMdbTool`

**Transformations**:
- Replace `@MessageDriven` → `@Component` + `@JmsListener`
- Convert `onMessage()` → annotated method
- Update JMS configuration

### 4. remove_ejb_interface_type
**Token Savings**: 50 × 100 = 5000 → 200 tokens (96%)

**Components**:
- **[OR]** `RemoveEjbInterfaceRecipe`
- **[BT]** `RemoveEjbInterfaceBlock` (batch operation)
- **[MT]** `RemoveEjbInterfaceTool`

**Operations**:
- Batch delete Home/Remote/Local/LocalHome interfaces
- Remove references to deleted interfaces
- Update imports in dependent classes

**Usage in YAML**:
```yaml
- type: "REMOVE_EJB_INTERFACE_TYPE"
  interface-type: "HOME"  # or REMOTE, LOCAL, LOCALHOME
  interfaces: "${home_interfaces}"
```

---

## CATEGORY 2: Annotation Transformation Tools

### 5. batch_replace_annotations ⭐ HIGH PRIORITY
**Token Savings**: N × 100 → 200 tokens (massive)

**Components**:
- **[OR]** `AnnotationReplacementRecipe` (parameterized)
- **[BT]** `BatchReplaceAnnotationsBlock`
- **[MT]** `BatchReplaceAnnotationsTool`

**Usage in YAML**:
```yaml
- type: "BATCH_REPLACE_ANNOTATIONS"
  files: "${all_ejb_classes}"
  mappings:
    "@Stateless": "@Service"
    "@EJB": "@Autowired"
    "@Resource": "@Autowired"
```

### 6. add_transactional_to_methods
**Token Savings**: 1500 → 150 tokens (90%)

**Components**:
- **[OR]** `AddTransactionalByPatternRecipe`
- **[BT]** `AddTransactionalBlock`
- **[MT]** `AddTransactionalTool`

**Configuration**:
```yaml
- type: "ADD_TRANSACTIONAL_TO_METHODS"
  classes: "${service_classes}"
  method-patterns:
    - "save*"
    - "update*"
    - "delete*"
    - "create*"
  read-only-patterns:
    - "find*"
    - "get*"
    - "list*"
```

### 7. migrate_security_annotations
**Token Savings**: 1000 → 100 tokens (90%)

**Components**:
- **[OR]** `EjbToSpringSecurityRecipe`
- **[BT]** `MigrateSecurityAnnotationsBlock`
- **[MT]** `MigrateSecurityAnnotationsTool`

**Transformations**:
- `@RolesAllowed("ADMIN")` → `@PreAuthorize("hasRole('ADMIN')")`
- `@PermitAll` → `@PreAuthorize("permitAll()")`
- `@DenyAll` → `@PreAuthorize("denyAll()")`

---

## CATEGORY 3: Dependency Injection Refactoring

### 8. convert_fields_to_constructor_injection
**Token Savings**: 1500 → 150 tokens (90%)

**Components**:
- **[OR]** `FieldToConstructorInjectionRecipe`
- **[BT]** `ConvertToConstructorInjectionBlock`
- **[MT]** `ConvertToConstructorInjectionTool`

**Transformations**:
- Convert `@EJB` annotated fields → constructor parameters
- Make fields `final`
- Generate constructor with all dependencies
- Remove field annotations

### 9. generate_spring_config_class
**Token Savings**: 2000 → 200 tokens (90%)

**Components**:
- **[AN]** `ConfigClassGenerator` (analyzer-ejb2spring/analysis)
- **[BT]** `GenerateConfigClassBlock`
- **[MT]** `GenerateConfigClassTool`

**Use Case**: Create `@Configuration` classes for factory methods

---

## CATEGORY 4: Compact Analysis Tools ⭐⭐ HIGHEST IMPACT

### 10. extract_class_metadata_compact ⭐⭐⭐ PRIORITY 1
**Token Savings**: 2000 → 200 tokens (90%)

**Components**:
- **[AN]** `ClassMetadataExtractor` (analyzer-inspectors/analysis) - Generic
- **[AN]** `EjbMetadataEnricher` (analyzer-ejb2spring/analysis) - EJB enrichment
- **[BT]** `ExtractEjbMetadataBlock` (analyzer-ejb2spring/blocks)
- **[MT]** `ExtractClassMetadataTool` (analyzer-refactoring-mcp)

**Output**: Compact JSON instead of full source
```json
{
  "fullyQualifiedName": "com.example.UserService",
  "annotations": ["Stateless", "Local"],
  "fields": [
    {"name": "userDao", "type": "UserDao", "annotations": ["EJB"], "isFinal": false}
  ],
  "methods": [
    {"name": "saveUser", "returnType": "void", "annotations": [], "isPublic": true}
  ],
  "imports": ["javax.ejb.Stateless", "javax.ejb.EJB"]
}
```

**Impact**: Every AI_ASSISTED_BATCH should use this instead of reading full source

**Usage in YAML**:
```yaml
- type: "EXTRACT_EJB_METADATA"
  source-nodes: "${stateless_beans}"
  output-variable: "class_metadata"

- type: "AI_ASSISTED_BATCH"
  input-data: "${class_metadata}"  # ← Compact JSON, not source code
  prompt: |
    Class metadata: ${current_item}
    Handle special case: ...
```

### 11. analyze_class_antipatterns ⭐⭐ PRIORITY 2
**Token Savings**: Filters 80% of classes from AI prompts

**Components**:
- **[AN]** `EjbAntiPatternDetector` (analyzer-ejb2spring/analysis)
- **[BT]** `DetectEjbAntiPatternsBlock`
- **[MT]** `AnalyzeAntiPatternsTool`

**Detects**:
- Mutable state in stateless beans
- Factory patterns needing `@Configuration`
- Non-thread-safe patterns
- Improper resource management

**Output**:
```json
{
  "className": "com.example.UserService",
  "antiPatterns": [
    {"type": "MUTABLE_FIELD", "field": "cachedData", "severity": "HIGH"},
    {"type": "FACTORY_METHOD", "method": "createInstance", "severity": "MEDIUM"}
  ]
}
```

**Usage**: Pre-filter classes, AI only sees problematic ones
```yaml
- type: "DETECT_EJB_ANTIPATTERNS"
  source-nodes: "${all_classes}"
  output-variable: "problematic_classes"

- type: "AI_ASSISTED_BATCH"
  input-data: "${problematic_classes}"  # ← Only 20% of classes
```

### 12. get_dependency_graph
**Token Savings**: 1500 → 100 tokens (93%)

**Components**:
- **[AN]** `DependencyGraphAnalyzer` (analyzer-inspectors/analysis)
- **[BT]** `ExtractDependencyGraphBlock`
- **[MT]** `GetDependencyGraphTool`

**Output**: Class names only, not source
```json
{
  "className": "com.example.UserService",
  "dependsOn": ["UserDao", "EmailService"],
  "usedBy": ["UserController", "AdminService"]
}
```

---

## CATEGORY 5: Batch File Operations

### 13. batch_delete_interfaces
**Token Savings**: 50 × 50 = 2500 → 100 tokens (96%)

**Components**:
- **[AN]** `FilePatternMatcher` (analyzer-inspectors/analysis)
- **[BT]** `BatchDeleteBlock`
- **[MT]** `BatchDeleteTool`

**Usage**:
```yaml
- type: "BATCH_DELETE"
  pattern: "**/*Home.java"
  base-directory: "${refactoring_project_path}/src/main/java"
```

### 14. batch_move_classes
**Token Savings**: N × 100 → 150 tokens (massive)

**Components**:
- **[OR]** `MoveClassRecipe` (reuse Eclipse JDT)
- **[BT]** `BatchMoveClassesBlock`
- **[MT]** `BatchMoveClassesTool`

---

## CATEGORY 6: Smart Diff/Preview Tools

### 15. preview_migration_changes
**Token Savings**: 5000 → 500 tokens (90%)

**Components**:
- **[AN]** `MigrationPreviewGenerator` (analyzer-ejb2spring/analysis)
- **[BT]** `PreviewChangesBlock`
- **[MT]** `PreviewChangesTool`

**Purpose**: Show AI compact diff instead of full files

### 16. apply_transformation_template
**Token Savings**: N × 500 → 200 tokens (massive)

**Components**:
- **[AN]** `TransformationTemplateEngine` (analyzer-ejb2spring/analysis)
- **[BT]** `ApplyTemplateBlock`
- **[MT]** `ApplyTemplateTool`

---

## Implementation Phases

### Phase 1: Foundation & High-Impact (Weeks 1-2)
**Estimated Token Savings**: 85-90%

1. **ClassMetadataExtractor** (#10) - analyzer-inspectors
2. **ExtractEjbMetadataBlock** (#10) - analyzer-ejb2spring
3. **EjbAntiPatternDetector** (#11) - analyzer-ejb2spring
4. **DetectEjbAntiPatternsBlock** (#11) - analyzer-ejb2spring
5. **BatchReplaceAnnotationsBlock** (#5) - analyzer-ejb2spring

**Why First**: These eliminate source code from prompts (90% token reduction per class)

### Phase 2: Core Migration Patterns (Weeks 3-4)
**Estimated Additional Savings**: 80-90% for covered cases

6. **StatelessToServiceRecipe** + Block (#1)
7. **FieldToConstructorInjectionRecipe** + Block (#8)
8. **AddTransactionalByPatternRecipe** + Block (#6)
9. **MigrateSecurityAnnotationsBlock** (#7)

**Why Second**: Handle most common EJB patterns deterministically (0 tokens)

### Phase 3: Specialized Migrations (Weeks 5-6)

10. **EntityBeanToJpaRecipe** + Block (#2)
11. **MdbToSpringJmsRecipe** + Block (#3)
12. **RemoveEjbInterfaceRecipe** + Block (#4)
13. **GenerateConfigClassBlock** (#9)

### Phase 4: Batch Operations (Week 7)

14. **BatchDeleteBlock** (#13)
15. **BatchMoveClassesBlock** (#14)
16. **DependencyGraphBlock** (#12)

### Phase 5: Developer Experience (Week 8)

17. **PreviewChangesBlock** (#15)
18. **ApplyTemplateBlock** (#16)

### Phase 6: MCP Tools (Week 9 - Optional)

19. Create MCP tool wrappers for all blocks (if AI agent support needed)

---

## Expected Token Reduction

### Before Optimization
```yaml
# Current approach
- type: "AI_ASSISTED_BATCH"
  input-nodes: stateless_beans  # 100 classes
  prompt: |
    [2500 tokens of context per class]
    
Total: 100 × 2500 = 250,000 tokens
Cost: ~$5-10 per run
```

### After Optimization
```yaml
# Optimized approach
- type: "EXTRACT_EJB_METADATA"
  output-variable: "metadata"
  
- type: "DETECT_EJB_ANTIPATTERNS"
  output-variable: "issues"
  
- type: "BATCH_REPLACE_ANNOTATIONS"
  # Handles 80% of cases, 0 tokens
  
- type: "AI_ASSISTED_BATCH"
  input-data: "${issues}"  # Only 20 classes with issues
  prompt: |
    Metadata: ${current_item}  # 200 tokens
    
Total: 20 × 200 = 4,000 tokens
Cost: ~$0.10 per run
```

**Token Reduction**: 250,000 → 4,000 (98% reduction)
**Cost Reduction**: $5-10 → $0.10 (98% reduction)

---

## Integration with Existing Migration Plans

### Current Pattern (Token-Heavy)
```yaml
- type: "AI_ASSISTED_BATCH"
  input-nodes: "${stateless_beans}"
  prompt: |
    READ FILE: ${current_node.sourceAliasPaths}
    [Full 2000-token source code]
    [500 tokens of instructions]
    Migrate to Spring...
```

### Optimized Pattern (Token-Efficient)
```yaml
- type: "EXTRACT_EJB_METADATA"
  source-nodes: "${stateless_beans}"
  output-variable: "metadata"

- type: "DETECT_EJB_ANTIPATTERNS"
  input-data: "${metadata}"
  output-variable: "issues"

- type: "BATCH_REPLACE_ANNOTATIONS"
  input-data: "${metadata}"
  mappings: {"@Stateless": "@Service"}
  # ← Handles standard cases, 0 tokens

- type: "AI_ASSISTED_BATCH"
  input-data: "${issues}"  # ← Only special cases
  prompt: |
    Metadata: ${current_item}  # 200 tokens
    Anti-patterns: ${current_item.antiPatterns}
    How to handle: ...
```

---

## Success Metrics

### Token Consumption
- **Target**: 85-95% reduction
- **Measurement**: Track tokens per class before/after

### Migration Coverage
- **Target**: 80% deterministic (OpenRewrite)
- **Target**: 20% AI-assisted (complex cases)

### Development Efficiency
- **Target**: Reduce AI costs by 90%+
- **Target**: 10x faster migrations (deterministic vs AI)

### Code Quality
- **Target**: 100% reproducible results
- **Target**: Git-reviewable changes

---

## Next Steps

1. **Audit** existing analyzer-ejb2spring code
2. **Implement** Phase 1 high-impact items
3. **Test** with demo-ejb2-project
4. **Measure** token reduction
5. **Iterate** based on results

---

## References

- [OpenRewrite Documentation](https://docs.openrewrite.org/)
- [Eclipse JDT AST](https://www.eclipse.org/jdt/)
- Migration Plans: `migrations/ejb2spring/`
- Implementation Docs: `docs/implementation/`
