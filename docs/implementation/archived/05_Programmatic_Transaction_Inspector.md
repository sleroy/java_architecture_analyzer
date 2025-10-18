# Task 5: I-0804 Programmatic Transaction Inspector

**ID:** Task 5  
**Priority:** P0 (Critical - Transaction Analysis)  
**Estimated Effort:** 6-8 hours  
**Prerequisites:** Tasks 1, 3 complete  
**Deliverables:** Programmatic transaction pattern detection  

## Overview

Implement inspector I-0804 to detect and analyze programmatic transaction management patterns in EJB applications. This inspector identifies UserTransaction usage, transaction boundaries, and programmatic transaction control patterns that need to be migrated to Spring's declarative transaction management.

## Technical Requirements

### Detection Capabilities

1. **UserTransaction Usage Detection**
   - Detect `javax.transaction.UserTransaction` imports and usage
   - Identify transaction begin/commit/rollback patterns
   - Track transaction context across method calls
   - Detect transaction timeout configurations

2. **Transaction Boundary Analysis**
   - Identify programmatic transaction boundaries
   - Detect nested transaction patterns
   - Analyze transaction propagation contexts
   - Map transaction scopes to business methods

3. **Transaction Control Pattern Recognition**
   - Detect try-catch-finally transaction patterns
   - Identify rollback-only scenarios
   - Recognize transaction status checking
   - Find transaction suspension/resumption

4. **Migration Complexity Assessment**
   - Classify transaction patterns by migration complexity
   - Identify Spring `@Transactional` conversion opportunities
   - Detect patterns requiring manual refactoring

### Implementation Specifications

#### Inspector Class Structure

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/ProgrammaticTransactionInspector.java
public class ProgrammaticTransactionInspector extends AbstractASMInspector 
        implements GraphAwareInspector {
    
    private static final String INSPECTOR_ID = "I-0804";
    private static final String INSPECTOR_NAME = "Programmatic Transaction Inspector";
    
    private static final String USER_TRANSACTION_CLASS = "javax/transaction/UserTransaction";
    private static final String TRANSACTION_MANAGER_CLASS = "javax/transaction/TransactionManager";
    private static final String TRANSACTION_STATUS_CLASS = "javax/transaction/Status";
    
    private final Set<String> TRANSACTION_METHODS = Set.of(
        "begin", "commit", "rollback", "setRollbackOnly", 
        "getRollbackOnly", "getStatus", "setTransactionTimeout"
    );
    
    private GraphRepository graphRepository;
    private Map<String, TransactionUsageMetadata> transactionUsageCache;
    
    public ProgrammaticTransactionInspector() {
        super(INSPECTOR_ID, INSPECTOR_NAME);
        this.transactionUsageCache = new ConcurrentHashMap<>();
    }
    
    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }
    
    @Override
    protected InspectorResult processClass(ProjectFile file, ClassNode classNode) {
        TransactionUsageAnalysis analysis = analyzeTransactionUsage(file, classNode);
        
        if (!analysis.hasTransactionUsage()) {
            return InspectorResult.empty();
        }
        
        return createInspectorResult(file, classNode, analysis);
    }
}
```

#### Transaction Usage Analysis

```java
private TransactionUsageAnalysis analyzeTransactionUsage(ProjectFile file, ClassNode classNode) {
    TransactionUsageAnalysis.Builder builder = TransactionUsageAnalysis.builder()
        .className(classNode.name)
        .projectFile(file);
    
    // Check for UserTransaction field declarations
    for (FieldNode field : classNode.fields) {
        if (isUserTransactionField(field)) {
            builder.addUserTransactionField(createFieldInfo(field));
        }
    }
    
    // Analyze methods for transaction usage
    for (MethodNode method : classNode.methods) {
        MethodTransactionAnalysis methodAnalysis = analyzeMethodTransactions(method);
        if (methodAnalysis.hasTransactionUsage()) {
            builder.addMethodAnalysis(methodAnalysis);
        }
    }
    
    return builder.build();
}

private boolean isUserTransactionField(FieldNode field) {
    return field.desc.contains(USER_TRANSACTION_CLASS) ||
           field.desc.contains(TRANSACTION_MANAGER_CLASS);
}

private MethodTransactionAnalysis analyzeMethodTransactions(MethodNode method) {
    MethodTransactionAnalysis.Builder builder = MethodTransactionAnalysis.builder()
        .methodName(method.name)
        .methodDescriptor(method.desc);
    
    // Analyze method bytecode for transaction operations
    InsnList instructions = method.instructions;
    Iterator<AbstractInsnNode> iterator = instructions.iterator();
    
    TransactionControlFlow controlFlow = new TransactionControlFlow();
    
    while (iterator.hasNext()) {
        AbstractInsnNode instruction = iterator.next();
        
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode methodCall = (MethodInsnNode) instruction;
            
            if (isTransactionMethodCall(methodCall)) {
                TransactionOperation operation = analyzeTransactionOperation(methodCall);
                builder.addTransactionOperation(operation);
                controlFlow.recordOperation(operation);
            }
        } else if (instruction instanceof TryCatchBlockNode) {
            // Analyze exception handling around transactions
            TryCatchBlockNode tryCatch = (TryCatchBlockNode) instruction;
            ExceptionHandlingPattern pattern = analyzeExceptionHandling(tryCatch);
            builder.addExceptionPattern(pattern);
        }
    }
    
    // Analyze transaction control flow patterns
    TransactionPatternAnalysis patterns = analyzeTransactionPatterns(controlFlow);
    builder.setPatternAnalysis(patterns);
    
    return builder.build();
}

private boolean isTransactionMethodCall(MethodInsnNode methodCall) {
    return (methodCall.owner.equals(USER_TRANSACTION_CLASS) ||
            methodCall.owner.equals(TRANSACTION_MANAGER_CLASS)) &&
           TRANSACTION_METHODS.contains(methodCall.name);
}

private TransactionOperation analyzeTransactionOperation(MethodInsnNode methodCall) {
    TransactionOperationType type = determineOperationType(methodCall.name);
    
    return TransactionOperation.builder()
        .operationType(type)
        .methodName(methodCall.name)
        .owner(methodCall.owner)
        .descriptor(methodCall.desc)
        .lineNumber(getLineNumber(methodCall))
        .build();
}

private TransactionOperationType determineOperationType(String methodName) {
    switch (methodName) {
        case "begin":
            return TransactionOperationType.BEGIN;
        case "commit":
            return TransactionOperationType.COMMIT;
        case "rollback":
            return TransactionOperationType.ROLLBACK;
        case "setRollbackOnly":
            return TransactionOperationType.SET_ROLLBACK_ONLY;
        case "getRollbackOnly":
            return TransactionOperationType.GET_ROLLBACK_ONLY;
        case "getStatus":
            return TransactionOperationType.GET_STATUS;
        case "setTransactionTimeout":
            return TransactionOperationType.SET_TIMEOUT;
        default:
            return TransactionOperationType.OTHER;
    }
}
```

#### Transaction Pattern Analysis

```java
private TransactionPatternAnalysis analyzeTransactionPatterns(TransactionControlFlow controlFlow) {
    TransactionPatternAnalysis.Builder builder = TransactionPatternAnalysis.builder();
    
    // Detect common transaction patterns
    if (controlFlow.hasBeginCommitPattern()) {
        builder.addPattern(TransactionPattern.BEGIN_COMMIT);
    }
    
    if (controlFlow.hasTryCatchFinallyPattern()) {
        builder.addPattern(TransactionPattern.TRY_CATCH_FINALLY);
    }
    
    if (controlFlow.hasNestedTransactionPattern()) {
        builder.addPattern(TransactionPattern.NESTED_TRANSACTION);
        builder.setMigrationComplexity(MigrationComplexity.HIGH);
    }
    
    if (controlFlow.hasConditionalRollbackPattern()) {
        builder.addPattern(TransactionPattern.CONDITIONAL_ROLLBACK);
    }
    
    if (controlFlow.hasTransactionTimeoutPattern()) {
        builder.addPattern(TransactionPattern.TRANSACTION_TIMEOUT);
    }
    
    // Assess migration complexity
    MigrationComplexity complexity = assessMigrationComplexity(controlFlow);
    builder.setMigrationComplexity(complexity);
    
    // Generate Spring conversion recommendations
    List<SpringConversionRecommendation> recommendations = 
        generateSpringRecommendations(controlFlow);
    builder.setSpringRecommendations(recommendations);
    
    return builder.build();
}

private MigrationComplexity assessMigrationComplexity(TransactionControlFlow controlFlow) {
    int complexityScore = 0;
    
    // Base complexity for programmatic transactions
    complexityScore += 2;
    
    // Add complexity for different patterns
    if (controlFlow.hasNestedTransactionPattern()) {
        complexityScore += 4;
    }
    
    if (controlFlow.hasConditionalRollbackPattern()) {
        complexityScore += 2;
    }
    
    if (controlFlow.hasTransactionSuspensionPattern()) {
        complexityScore += 3;
    }
    
    if (controlFlow.getTransactionOperationCount() > 5) {
        complexityScore += 2;
    }
    
    // Classify complexity
    if (complexityScore <= 3) {
        return MigrationComplexity.LOW;
    } else if (complexityScore <= 7) {
        return MigrationComplexity.MEDIUM;
    } else {
        return MigrationComplexity.HIGH;
    }
}

private List<SpringConversionRecommendation> generateSpringRecommendations(
        TransactionControlFlow controlFlow) {
    
    List<SpringConversionRecommendation> recommendations = new ArrayList<>();
    
    if (controlFlow.hasSimpleBeginCommitPattern()) {
        recommendations.add(SpringConversionRecommendation.builder()
            .recommendationType(RecommendationType.REPLACE_WITH_ANNOTATION)
            .springAnnotation("@Transactional")
            .propagation("REQUIRED")
            .description("Replace simple begin/commit pattern with @Transactional")
            .confidence(ConfidenceLevel.HIGH)
            .build());
    }
    
    if (controlFlow.hasRollbackOnlyPattern()) {
        recommendations.add(SpringConversionRecommendation.builder()
            .recommendationType(RecommendationType.REPLACE_WITH_EXCEPTION)
            .description("Replace setRollbackOnly() with exception throwing")
            .confidence(ConfidenceLevel.MEDIUM)
            .build());
    }
    
    if (controlFlow.hasNestedTransactionPattern()) {
        recommendations.add(SpringConversionRecommendation.builder()
            .recommendationType(RecommendationType.MANUAL_REFACTORING)
            .description("Nested transactions require manual refactoring with REQUIRES_NEW propagation")
            .confidence(ConfidenceLevel.LOW)
            .build());
    }
    
    return recommendations;
}
```

#### Graph Integration

```java
private void createGraphNodes(ProjectFile file, TransactionUsageAnalysis analysis) {
    // Create a transaction boundary node for each method with programmatic transactions
    for (MethodTransactionAnalysis methodAnalysis : analysis.getMethodAnalyses()) {
        if (methodAnalysis.hasTransactionUsage()) {
            
            TransactionBoundaryNode boundaryNode = new TransactionBoundaryNode(
                generateNodeId(analysis.getClassName(), methodAnalysis.getMethodName()),
                analysis.getClassName() + "." + methodAnalysis.getMethodName()
            );
            
            boundaryNode.setTransactionType(TransactionType.PROGRAMMATIC);
            boundaryNode.setComplexity(methodAnalysis.getPatternAnalysis().getMigrationComplexity());
            
            // Add transaction operations
            for (TransactionOperation operation : methodAnalysis.getTransactionOperations()) {
                boundaryNode.addTransactionOperation(operation.getOperationType());
            }
            
            // Add detected patterns
            for (TransactionPattern pattern : methodAnalysis.getPatternAnalysis().getPatterns()) {
                boundaryNode.addTransactionPattern(pattern);
            }
            
            graphRepository.addNode(boundaryNode);
            
            // Create edges to related nodes
            createTransactionEdges(boundaryNode, analysis);
        }
    }
}

private void createTransactionEdges(TransactionBoundaryNode boundaryNode, 
        TransactionUsageAnalysis analysis) {
    
    // Create edge to the class containing the transaction
    String classNodeId = analysis.getClassName();
    
    TransactionBoundaryEdge edge = new TransactionBoundaryEdge(
        generateEdgeId(classNodeId, boundaryNode.getNodeId()),
        classNodeId,
        boundaryNode.getNodeId(),
        "HAS_PROGRAMMATIC_TRANSACTION"
    );
    
    edge.setTransactionType(TransactionType.PROGRAMMATIC);
    edge.setMigrationRequired(true);
    
    graphRepository.addEdge(edge);
}
```

#### Inspector Result Creation

```java
private InspectorResult createInspectorResult(ProjectFile file, ClassNode classNode,
        TransactionUsageAnalysis analysis) {
    
    InspectorResult.Builder builder = InspectorResult.builder()
        .file(file)
        .addTag(InspectorTags.EJB_PROGRAMMATIC_TX)
        .addTag(InspectorTags.EJB_USER_TRANSACTION)
        .addTag(InspectorTags.EJB_MIGRATION_HIGH_PRIORITY);
    
    // Add complexity tags
    MigrationComplexity overallComplexity = analysis.getOverallComplexity();
    switch (overallComplexity) {
        case LOW:
            builder.addTag(InspectorTags.EJB_MIGRATION_SIMPLE);
            break;
        case MEDIUM:
            builder.addTag(InspectorTags.EJB_MIGRATION_MEDIUM_PRIORITY);
            break;
        case HIGH:
            builder.addTag(InspectorTags.EJB_MIGRATION_COMPLEX);
            builder.addTag(InspectorTags.EJB_MIGRATION_MANUAL);
            break;
    }
    
    // Add pattern-specific tags
    Set<TransactionPattern> allPatterns = analysis.getAllPatterns();
    if (allPatterns.contains(TransactionPattern.NESTED_TRANSACTION)) {
        builder.addTag(InspectorTags.EJB_ANTI_PATTERN_TRANSACTION);
    }
    
    // Add properties for detailed analysis
    builder.addProperty("className", analysis.getClassName())
           .addProperty("userTransactionFieldCount", 
               String.valueOf(analysis.getUserTransactionFields().size()))
           .addProperty("transactionalMethodCount", 
               String.valueOf(analysis.getMethodAnalyses().size()))
           .addProperty("overallComplexity", overallComplexity.toString())
           .addProperty("detectedPatterns", formatPatterns(allPatterns))
           .addProperty("springRecommendations", formatRecommendations(analysis));
    
    // Create graph nodes
    createGraphNodes(file, analysis);
    
    return builder.build();
}

private String formatPatterns(Set<TransactionPattern> patterns) {
    return patterns.stream()
        .map(TransactionPattern::toString)
        .collect(Collectors.joining(", "));
}

private String formatRecommendations(TransactionUsageAnalysis analysis) {
    return analysis.getMethodAnalyses().stream()
        .flatMap(method -> method.getPatternAnalysis().getSpringRecommendations().stream())
        .map(SpringConversionRecommendation::getDescription)
        .collect(Collectors.joining("; "));
}
```

### Data Model Classes

#### TransactionUsageAnalysis

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/TransactionUsageAnalysis.java
public class TransactionUsageAnalysis {
    private final String className;
    private final ProjectFile projectFile;
    private final List<UserTransactionFieldInfo> userTransactionFields;
    private final List<MethodTransactionAnalysis> methodAnalyses;
    private final MigrationComplexity overallComplexity;
    
    public boolean hasTransactionUsage() {
        return !userTransactionFields.isEmpty() || !methodAnalyses.isEmpty();
    }
    
    public Set<TransactionPattern> getAllPatterns() {
        return methodAnalyses.stream()
            .flatMap(method -> method.getPatternAnalysis().getPatterns().stream())
            .collect(Collectors.toSet());
    }
    
    // Builder pattern implementation
    public static Builder builder() {
        return new Builder();
    }
}
```

#### TransactionOperation

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/TransactionOperation.java
public class TransactionOperation {
    private final TransactionOperationType operationType;
    private final String methodName;
    private final String owner;
    private final String descriptor;
    private final int lineNumber;
    
    // Builder pattern implementation
}

public enum TransactionOperationType {
    BEGIN, COMMIT, ROLLBACK, SET_ROLLBACK_ONLY, 
    GET_ROLLBACK_ONLY, GET_STATUS, SET_TIMEOUT, OTHER
}
```

#### TransactionPattern

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/TransactionPattern.java
public enum TransactionPattern {
    BEGIN_COMMIT("Simple begin/commit pattern"),
    TRY_CATCH_FINALLY("Try-catch-finally transaction pattern"),
    NESTED_TRANSACTION("Nested transaction pattern"),
    CONDITIONAL_ROLLBACK("Conditional rollback pattern"),
    TRANSACTION_TIMEOUT("Transaction timeout configuration"),
    TRANSACTION_SUSPENSION("Transaction suspension/resumption");
    
    private final String description;
    
    TransactionPattern(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

## Implementation Tasks

### Step 1: Create Base Inspector Structure (1-2 hours)
1. Create `ProgrammaticTransactionInspector` class extending `AbstractASMInspector`
2. Implement basic UserTransaction detection logic
3. Set up graph integration interface

### Step 2: Implement Transaction Operation Detection (2-3 hours)
1. Create transaction method call detection
2. Implement transaction operation analysis
3. Add transaction control flow tracking

### Step 3: Implement Pattern Recognition (2-3 hours)
1. Create transaction pattern detection logic
2. Implement complexity assessment algorithms
3. Add Spring conversion recommendation generation

### Step 4: Create Data Model Classes (1 hour)
1. Implement transaction analysis model classes
2. Create transaction pattern enumerations
3. Add validation and utility methods

### Step 5: Integration and Testing (1 hour)
1. Create comprehensive unit tests
2. Test with sample transaction patterns
3. Validate graph node creation

## File Structure

```
src/main/java/com/analyzer/rules/ejb2spring/
├── ProgrammaticTransactionInspector.java      # Main inspector implementation
├── model/
│   ├── TransactionUsageAnalysis.java          # Transaction usage metadata
│   ├── MethodTransactionAnalysis.java         # Method-level analysis
│   ├── TransactionOperation.java              # Transaction operation info
│   ├── TransactionPattern.java                # Transaction pattern enum
│   ├── SpringConversionRecommendation.java    # Spring migration recommendations
│   └── TransactionControlFlow.java            # Control flow analysis
└── util/
    └── TransactionPatternMatcher.java          # Pattern matching utilities

src/test/java/com/analyzer/rules/ejb2spring/
├── ProgrammaticTransactionInspectorTest.java  # Unit tests
├── TransactionPatternTest.java                # Pattern detection tests
└── fixtures/
    ├── SimpleTransactionBean.java             # Simple transaction patterns
    ├── ComplexTransactionBean.java            # Complex transaction patterns
    └── NestedTransactionBean.java             # Nested transaction patterns
```

## Testing Requirements

### Unit Tests
- Test UserTransaction field detection
- Test transaction operation recognition
- Test pattern matching accuracy
- Test complexity assessment logic
- Test Spring recommendation generation

### Integration Tests
- Test with real EJB transaction code
- Test pattern detection accuracy
- Test graph integration
- Test migration recommendation quality

## Success Criteria

- [ ] Accurate detection of programmatic transactions (≥95% accuracy)
- [ ] Complete transaction pattern recognition
- [ ] Accurate complexity assessment
- [ ] Useful Spring conversion recommendations
- [ ] Comprehensive graph node creation
- [ ] 100% unit test coverage
- [ ] Performance: <200ms for typical transaction analysis

## Implementation Prompt

Use this specification to implement the Programmatic Transaction Inspector. Focus on:

1. **Complete Transaction Analysis**: Detect all programmatic transaction patterns and boundaries
2. **Accurate Pattern Recognition**: Identify common transaction control patterns
3. **Migration-Ready Recommendations**: Provide specific Spring conversion guidance
4. **Performance Optimization**: Efficient bytecode analysis for transaction detection
5. **Complexity Assessment**: Accurate migration complexity evaluation

This inspector is critical for transaction boundary analysis and Spring `@Transactional` migration planning.
