# Task 7: I-0905 Stateful Session State Inspector

## Overview
**Priority**: High (Phase 1)  
**Inspector ID**: I-0905  
**Dependencies**: I-0101 (session bean identification), I-0401 (ejb-jar descriptor), I-1100-1102 (state analysis)  
**Estimated Effort**: 4-5 days  
**Technology**: ASM bytecode analysis, Java parsing, state flow analysis  

## Purpose
Analyze stateful session beans to identify state management patterns, conversational state variables, and cross-method state dependencies. Provide recommendations for migrating stateful session beans to Spring scope patterns (session, conversation, or custom scoped beans) or refactoring to stateless designs.

## Technical Scope

### Stateful Session Bean Patterns to Detect
- **Instance variables**: Non-final fields that hold conversational state
- **State initialization**: ejbCreate/ejbActivate methods setting initial state
- **State persistence**: ejbPassivate/ejbRemove methods handling state cleanup
- **Cross-method dependencies**: Methods that read state written by other methods
- **Conversational workflows**: Related method calls maintaining state across invocations
- **State serialization**: Serializable fields for EJB passivation/activation

### Spring Migration Patterns
```java
// EJB Stateful Session Bean
@Stateful
public class ShoppingCartBean implements SessionBean {
    private List<Item> items = new ArrayList<>();  // Conversational state
    private Customer customer;                      // User context
    private BigDecimal total;                      // Calculated state
    
    public void addItem(Item item) { items.add(item); calculateTotal(); }
    public void setCustomer(Customer c) { this.customer = c; }
    public Order checkout() { return new Order(customer, items, total); }
}

// Spring Session-Scoped Bean
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ShoppingCartService {
    @Autowired private OrderService orderService;
    
    private List<Item> items = new ArrayList<>();
    private Customer customer;
    private BigDecimal total;
    
    public void addItem(Item item) { items.add(item); calculateTotal(); }
    public void setCustomer(Customer c) { this.customer = c; }
    public Order checkout() { return orderService.createOrder(customer, items, total); }
}
```

## Graph Integration

### Node Creation
- **StatefulSessionBeanNode**: Represents stateful session beans
  - `beanName`: EJB bean name from descriptor
  - `stateFields`: List of instance variables holding state
  - `initMethods`: Methods that initialize state
  - `stateMethods`: Methods that modify state
  - `accessMethods`: Methods that read state
  - `cleanupMethods`: Methods that clear state
  - `conversationalComplexity`: Assessment of state complexity
  - `migrationStrategy`: Recommended Spring migration approach

### Edge Creation
- **READS_STATE**: Methods that read instance variables
- **WRITES_STATE**: Methods that modify instance variables
- **STATE_DEPENDENCY**: Cross-method state flow relationships
- **INITIALIZATION**: ejbCreate/ejbActivate -> state fields
- **CLEANUP**: ejbRemove/ejbPassivate -> state fields

## Implementation Details

### Core Inspector Class

```java
package com.analyzer.rules.ejb2spring;

import com.analyzer.core.graph.GraphRepository;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;

@RequiredTags({"STATEFUL_SESSION_BEAN", "EJB_BEAN"})
@InspectorTags({"STATEFUL_SESSION_STATE", "CONVERSATIONAL_STATE",
        "CROSS_METHOD_DEPENDENCY", "SPRING_SCOPE_MIGRATION"})
public class StatefulSessionStateInspector extends AbstractASMInspector
        implements GraphAwareInspector {

    private GraphRepository graphRepository;

    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    protected InspectorResult doInspection(ProjectFile file) {
        if (!isStatefulSessionBean(file)) {
            return InspectorResult.empty();
        }

        try {
            StatefulBeanAnalysis analysis = analyzeStatefulBean(file);
            return createResultWithAnalysis(analysis, file);

        } catch (Exception e) {
            return InspectorResult.error("Failed to analyze stateful bean: " +
                    e.getMessage());
        }
    }

    private boolean isStatefulSessionBean(ProjectFile file) {
        return file.getTags().contains("STATEFUL_SESSION_BEAN");
    }
}
```

### Stateful Bean Analysis Structure
```java
public class StatefulBeanAnalysis {
    private String beanClassName;
    private String beanName;
    private List<StateField> stateFields;
    private List<StateMethod> stateMethods;
    private Map<String, Set<String>> stateFlowGraph;  // method -> state fields accessed
    private ConversationalComplexity complexity;
    private SpringMigrationStrategy migrationStrategy;
    
    public static class StateField {
        private String fieldName;
        private String fieldType;
        private boolean isFinal;
        private boolean isSerializable;
        private boolean isCollection;
        private Set<String> readerMethods;
        private Set<String> writerMethods;
        private StateFieldRole role;  // USER_DATA, CALCULATED, CACHE, etc.
    }
    
    public static class StateMethod {
        private String methodName;
        private String methodSignature;
        private Set<String> readsFields;
        private Set<String> writesFields;
        private MethodRole role;  // INITIALIZER, MUTATOR, ACCESSOR, CLEANUP
        private boolean isEjbLifecycle;
    }
    
    public enum ConversationalComplexity {
        LOW,     // Simple state, few dependencies
        MEDIUM,  // Moderate state, some cross-method dependencies
        HIGH,    // Complex state, many dependencies, workflows
        CRITICAL // Very complex, requires manual refactoring
    }
    
    public enum SpringMigrationStrategy {
        SESSION_SCOPED,      // @Scope("session")
        REQUEST_SCOPED,      // @Scope("request") 
        CONVERSATION_SCOPED, // Custom conversation scope
        STATELESS_REFACTOR,  // Refactor to stateless + external state
        MANUAL_REVIEW        // Too complex for automated migration
    }
}
```

### ASM-Based State Analysis
```java
private StatefulBeanAnalysis analyzeStatefulBean(ProjectFile file) {
    StatefulBeanAnalysis analysis = new StatefulBeanAnalysis();
    
    // Use ASM to analyze bytecode
    ClassReader classReader = new ClassReader(file.getBinaryContent());
    
    StatefulBeanClassVisitor visitor = new StatefulBeanClassVisitor(analysis);
    classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
    
    // Perform state flow analysis
    analyzeStateFlows(analysis);
    
    // Assess complexity and migration strategy
    assessComplexityAndStrategy(analysis);
    
    return analysis;
}

private class StatefulBeanClassVisitor extends ClassVisitor {
    private StatefulBeanAnalysis analysis;
    private String currentClassName;
    
    public StatefulBeanClassVisitor(StatefulBeanAnalysis analysis) {
        super(ASM9);
        this.analysis = analysis;
    }
    
    @Override
    public void visit(int version, int access, String name, String signature,
                     String superName, String[] interfaces) {
        this.currentClassName = name.replace('/', '.');
        analysis.setBeanClassName(currentClassName);
    }
    
    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                  String signature, Object value) {
        
        // Skip static and final fields for state analysis
        if ((access & ACC_STATIC) == 0 && (access & ACC_FINAL) == 0) {
            StateField field = new StateField();
            field.setFieldName(name);
            field.setFieldType(Type.getType(descriptor).getClassName());
            field.setFinal((access & ACC_FINAL) != 0);
            field.setSerializable(isSerializableType(descriptor));
            field.setCollection(isCollectionType(descriptor));
            field.setRole(determineFieldRole(name, descriptor));
            
            analysis.addStateField(field);
        }
        
        return super.visitField(access, name, descriptor, signature, value);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                   String signature, String[] exceptions) {
        
        StateMethod method = new StateMethod();
        method.setMethodName(name);
        method.setMethodSignature(name + descriptor);
        method.setEjbLifecycle(isEjbLifecycleMethod(name));
        method.setRole(determineMethodRole(name));
        
        analysis.addStateMethod(method);
        
        return new StateMethodVisitor(method, analysis);
    }
}

private class StateMethodVisitor extends MethodVisitor {
    private StateMethod currentMethod;
    private StatefulBeanAnalysis analysis;
    
    public StateMethodVisitor(StateMethod method, StatefulBeanAnalysis analysis) {
        super(ASM9);
        this.currentMethod = method;
        this.analysis = analysis;
    }
    
    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        // Track field access patterns
        if (owner.equals(analysis.getBeanClassName().replace('.', '/'))) {
            switch (opcode) {
                case GETFIELD:
                    currentMethod.addReadField(name);
                    analysis.addFieldReader(name, currentMethod.getMethodName());
                    break;
                case PUTFIELD:
                    currentMethod.addWriteField(name);
                    analysis.addFieldWriter(name, currentMethod.getMethodName());
                    break;
            }
        }
        
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }
    
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, 
                               String descriptor, boolean isInterface) {
        // Track method calls that might affect state
        if (name.startsWith("set") || name.startsWith("add") || 
            name.startsWith("remove") || name.startsWith("clear")) {
            currentMethod.addStateMutation(name);
        }
        
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}
```

### State Flow Analysis
```java
private void analyzeStateFlows(StatefulBeanAnalysis analysis) {
    Map<String, Set<String>> stateFlowGraph = new HashMap<>();
    
    for (StateMethod method : analysis.getStateMethods()) {
        Set<String> affectedFields = new HashSet<>();
        affectedFields.addAll(method.getReadsFields());
        affectedFields.addAll(method.getWritesFields());
        
        stateFlowGraph.put(method.getMethodName(), affectedFields);
    }
    
    analysis.setStateFlowGraph(stateFlowGraph);
    
    // Identify cross-method dependencies
    identifyCrossMethodDependencies(analysis);
    
    // Detect conversational workflows
    detectConversationalWorkflows(analysis);
}

private void identifyCrossMethodDependencies(StatefulBeanAnalysis analysis) {
    for (StateField field : analysis.getStateFields()) {
        if (field.getWriterMethods().size() > 0 && field.getReaderMethods().size() > 0) {
            // Field is written by some methods and read by others
            for (String writer : field.getWriterMethods()) {
                for (String reader : field.getReaderMethods()) {
                    if (!writer.equals(reader)) {
                        analysis.addCrossMethodDependency(writer, reader, field.getFieldName());
                    }
                }
            }
        }
    }
}

private void detectConversationalWorkflows(StatefulBeanAnalysis analysis) {
    // Identify common EJB stateful patterns
    
    // Shopping cart pattern: add -> calculate -> checkout
    if (hasMethodPattern(analysis, "add.*", "calculate.*", "checkout|submit|finish")) {
        analysis.addWorkflowPattern("SHOPPING_CART");
    }
    
    // Wizard pattern: next -> previous -> finish
    if (hasMethodPattern(analysis, "next|forward", "previous|back", "finish|complete")) {
        analysis.addWorkflowPattern("WIZARD");
    }
    
    // Builder pattern: set* -> build/create
    if (hasMethodPattern(analysis, "set.*", "build|create|construct")) {
        analysis.addWorkflowPattern("BUILDER");
    }
    
    // Multi-step transaction: begin -> add/modify -> commit/rollback
    if (hasMethodPattern(analysis, "begin|start", "add|modify|update", "commit|rollback|finish")) {
        analysis.addWorkflowPattern("MULTI_STEP_TRANSACTION");
    }
}
```

### Complexity and Migration Strategy Assessment
```java
private void assessComplexityAndStrategy(StatefulBeanAnalysis analysis) {
    ConversationalComplexity complexity = assessComplexity(analysis);
    analysis.setComplexity(complexity);
    
    SpringMigrationStrategy strategy = determineMigrationStrategy(analysis, complexity);
    analysis.setMigrationStrategy(strategy);
}

private ConversationalComplexity assessComplexity(StatefulBeanAnalysis analysis) {
    int stateFieldCount = analysis.getStateFields().size();
    int crossMethodDeps = analysis.getCrossMethodDependencies().size();
    int workflowPatterns = analysis.getWorkflowPatterns().size();
    
    // Complexity scoring
    int complexityScore = 0;
    complexityScore += stateFieldCount * 2;  // Each state field adds complexity
    complexityScore += crossMethodDeps * 3;  // Cross-method deps are more complex
    complexityScore += workflowPatterns * 5; // Workflows add significant complexity
    
    // Special cases that increase complexity
    if (hasSerializableState(analysis)) complexityScore += 5;
    if (hasCollectionState(analysis)) complexityScore += 3;
    if (hasCalculatedState(analysis)) complexityScore += 4;
    if (hasEjbLifecycleMethods(analysis)) complexityScore += 3;
    
    if (complexityScore <= 10) return ConversationalComplexity.LOW;
    if (complexityScore <= 25) return ConversationalComplexity.MEDIUM;
    if (complexityScore <= 40) return ConversationalComplexity.HIGH;
    return ConversationalComplexity.CRITICAL;
}

private SpringMigrationStrategy determineMigrationStrategy(
    StatefulBeanAnalysis analysis, ConversationalComplexity complexity) {
    
    // Critical complexity always needs manual review
    if (complexity == ConversationalComplexity.CRITICAL) {
        return SpringMigrationStrategy.MANUAL_REVIEW;
    }
    
    // Analyze usage patterns to determine best Spring scope
    if (hasWebSessionPatterns(analysis)) {
        return SpringMigrationStrategy.SESSION_SCOPED;
    }
    
    if (hasRequestScopedPatterns(analysis)) {
        return SpringMigrationStrategy.REQUEST_SCOPED;
    }
    
    if (hasConversationalPatterns(analysis)) {
        return SpringMigrationStrategy.CONVERSATION_SCOPED;
    }
    
    // Simple stateful beans might be better as stateless
    if (complexity == ConversationalComplexity.LOW && canBeStateless(analysis)) {
        return SpringMigrationStrategy.STATELESS_REFACTOR;
    }
    
    // Default to session scope for web applications
    return SpringMigrationStrategy.SESSION_SCOPED;
}
```

### Graph Node Integration
```java
private InspectorResult createResultWithAnalysis(StatefulBeanAnalysis analysis, ProjectFile file) {
    InspectorResult.Builder builder = InspectorResult.builder();
    
    // Create stateful session bean node
    StatefulSessionBeanNode beanNode = createStatefulBeanNode(analysis);
    graphRepository.addNode(beanNode);
    
    // Create edges for state relationships
    createStateRelationshipEdges(analysis, beanNode);
    
    // Add result entries
    builder.addEntry(createMainResultEntry(analysis, file));
    
    // Add entries for each state field
    for (StateField field : analysis.getStateFields()) {
        builder.addEntry(createStateFieldEntry(field, analysis, file));
    }
    
    return builder.build();
}

private StatefulSessionBeanNode createStatefulBeanNode(StatefulBeanAnalysis analysis) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("beanName", analysis.getBeanName());
    properties.put("className", analysis.getBeanClassName());
    properties.put("stateFieldCount", analysis.getStateFields().size());
    properties.put("stateMethodCount", analysis.getStateMethods().size());
    properties.put("crossMethodDependencies", analysis.getCrossMethodDependencies().size());
    properties.put("workflowPatterns", analysis.getWorkflowPatterns());
    properties.put("conversationalComplexity", analysis.getComplexity().name());
    properties.put("migrationStrategy", analysis.getMigrationStrategy().name());
    properties.put("migrationComplexity", assessMigrationComplexity(analysis));
    
    return new StatefulSessionBeanNode(
        generateNodeId("STATEFUL_BEAN", analysis.getBeanName()),
        properties
    );
}

private void createStateRelationshipEdges(StatefulBeanAnalysis analysis, 
                                        StatefulSessionBeanNode beanNode) {
    // Create edges for cross-method state dependencies
    for (Map.Entry<String, Map<String, Set<String>>> dependency : 
         analysis.getCrossMethodDependencies().entrySet()) {
        
        String sourceMethod = dependency.getKey();
        for (Map.Entry<String, Set<String>> target : dependency.getValue().entrySet()) {
            String targetMethod = target.getKey();
            Set<String> sharedFields = target.getValue();
            
            Map<String, Object> edgeProps = new HashMap<>();
            edgeProps.put("sharedFields", sharedFields);
            edgeProps.put("dependencyType", "STATE_FLOW");
            
            GraphEdge stateEdge = new GraphEdge(
                generateNodeId("METHOD", analysis.getBeanName(), sourceMethod),
                generateNodeId("METHOD", analysis.getBeanName(), targetMethod),
                "STATE_DEPENDENCY",
                edgeProps
            );
            
            graphRepository.addEdge(stateEdge);
        }
    }
}
```

## Expected Results

### Detected Patterns
- **Conversational state fields**: Instance variables maintaining state across method calls
- **State initialization patterns**: ejbCreate/ejbActivate methods setting up state
- **State cleanup patterns**: ejbRemove/ejbPassivate methods handling cleanup
- **Cross-method dependencies**: Methods reading state written by other methods
- **Workflow patterns**: Shopping cart, wizard, builder, multi-step transaction patterns
- **State serialization requirements**: Passivation/activation compatible fields

### Generated Metadata
- **State field analysis**: Field types, serialization compatibility, access patterns
- **Method role classification**: Initializers, mutators, accessors, cleanup methods
- **Complexity assessment**: LOW/MEDIUM/HIGH/CRITICAL complexity rating
- **Migration strategy**: Recommended Spring scope or refactoring approach
- **Spring configuration**: Generated @Scope annotations and configuration
- **Refactoring recommendations**: Specific guidance for state management migration

### Graph Relationships
- **READS_STATE**: Method -> State field reading relationships
- **WRITES_STATE**: Method -> State field writing relationships
- **STATE_DEPENDENCY**: Cross-method state flow dependencies
- **INITIALIZATION**: Lifecycle method -> State field initialization
- **CLEANUP**: Lifecycle method -> State field cleanup

## Integration Points

### Dependency Resolution
- **I-0101**: Requires session bean identification
- **I-0401**: Needs deployment descriptor parsing for bean configuration
- **I-1100-1102**: Leverages existing state analysis inspectors
- **Method inspectors**: Coordinates with method signature analysis

### Refactoring Rule Support
- **R-800**: Provides data for externalizing conversational state
- **R-810**: Supports making services stateless and thread-safe
- **R-100**: Integrates with session bean to service conversion
- **R-400**: Links to web tier dependency injection patterns

### Spring Boot Configuration
- **Scope configuration**: Generates @Scope annotations and proxy settings
- **Session management**: Identifies need for session management configuration
- **State externalization**: Recommends Redis/database state storage solutions

## Testing Strategy

### Unit Test Cases
```java
@Test
public void testSimpleStatefulBeanAnalysis() {
    ProjectFile beanFile = createStatefulBeanWithSimpleState();
    
    InspectorResult result = inspector.inspect(beanFile);
    
    assertThat(result.getEntries()).isNotEmpty();
    InspectorResult.Entry mainEntry = result.getEntries().get(0);
    assertThat(mainEntry.getMetadata().get("conversationalComplexity")).isEqualTo("LOW");
    assertThat(mainEntry.getMetadata().get("migrationStrategy")).isEqualTo("SESSION_SCOPED");
}

@Test
public void testComplexStatefulBeanAnalysis() {
    ProjectFile beanFile = createComplexStatefulBeanWithWorkflows();
    
    InspectorResult result = inspector.inspect(beanFile);
    
    // Verify complexity assessment
    InspectorResult.Entry mainEntry = findMainEntry(result);
    assertThat(mainEntry.getMetadata().get("conversationalComplexity")).isEqualTo("HIGH");
    assertThat(mainEntry.getMetadata().get("workflowPatterns")).isEqualTo(Arrays.asList("SHOPPING_CART", "WIZARD"));
}

@Test
public void testCrossMethodDependencyDetection() {
    ProjectFile beanFile = createBeanWithCrossMethodDependencies();
    
    inspector.inspect(beanFile);
    
    // Verify state dependency edges in graph
    List<GraphEdge> stateEdges = graphRepository.getEdgesByType("STATE_DEPENDENCY");
    assertThat(stateEdges).hasSize(2);
    
    GraphEdge dependency = stateEdges.get(0);
    assertThat(dependency.getProperties().get("sharedFields")).isEqualTo(Arrays.asList("cartItems"));
}
```

### Integration Test Scenarios
- **Multi-workflow stateful beans**: Complex beans with multiple conversational patterns
- **Inheritance hierarchies**: Stateful beans extending other stateful beans
- **State serialization**: Beans with complex serializable state objects
- **EJB lifecycle integration**: Beans using full ejbCreate/ejbRemove lifecycle

## Success Criteria
- [ ] Successfully identify all stateful session bean state fields
- [ ] Accurately detect cross-method state dependencies
- [ ] Correctly assess conversational complexity levels
- [ ] Generate appropriate Spring migration strategies
- [ ] Create comprehensive graph relationships for state flows
- [ ] Provide actionable refactoring recommendations
- [ ] Handle complex state patterns (collections, calculated fields)
- [ ] Pass comprehensive unit and integration tests

## Risk Mitigation
- **Complex state analysis**: Use multiple analysis techniques (ASM + static analysis)
- **Performance concerns**: Optimize for large stateful beans with many methods
- **Edge case handling**: Comprehensive testing of unusual state patterns
- **Migration strategy accuracy**: Validate recommendations against real-world scenarios
- **Graph relationship explosion**: Limit edge creation for very complex beans
