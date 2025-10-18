# Task 8: I-0706 EJB Create Method Usage Inspector

**ID:** Task 8  
**Priority:** P1 (High Priority Phase 1 Inspector)  
**Estimated Effort:** 6-8 hours  
**Prerequisites:** Tasks 1, 2, 3 complete  
**Deliverables:** Complete EJB create method pattern detection and analysis  

## Overview

Implement inspector I-0706 to detect and analyze EJB create method usage patterns in Entity Beans and Session Beans. This inspector is essential for understanding EJB lifecycle management, object creation patterns, and initialization workflows that need conversion to Spring factory methods, @PostConstruct patterns, or constructor injection during migration.

## Technical Requirements

### Detection Capabilities

1. **Entity Bean Create Method Analysis**
   - Detect ejbCreate methods in CMP and BMP entity beans
   - Analyze ejbPostCreate methods and their correspondence
   - Extract create method parameters and initialization logic
   - Map create methods to database insertion patterns

2. **Session Bean Create Method Analysis**
   - Detect ejbCreate methods in stateful session beans
   - Analyze create method parameters and state initialization
   - Identify create method overloads and variations
   - Extract initialization dependencies and patterns

3. **Home Interface Create Method Detection**
   - Analyze create methods in EJB Home interfaces
   - Map Home.create() to ejbCreate() method pairs
   - Detect create method signatures and return types
   - Identify remote vs local create method patterns

4. **Create Method Usage Pattern Analysis**
   - Detect client code calling Home.create() methods
   - Analyze create method invocation patterns
   - Identify factory pattern implementations
   - Map create method dependencies and JNDI lookups

5. **Migration Pattern Assessment**
   - Classify create methods by migration complexity
   - Identify Spring conversion requirements
   - Detect initialization anti-patterns
   - Recommend factory method or constructor injection patterns

### Implementation Specifications

#### Inspector Class Structure

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/EjbCreateMethodUsageInspector.java
public class EjbCreateMethodUsageInspector extends AbstractASMInspector 
        implements GraphAwareInspector {
    
    private static final String INSPECTOR_ID = "I-0706";
    private static final String INSPECTOR_NAME = "EJB Create Method Usage Inspector";
    
    private static final String EJB_CREATE_METHOD_PREFIX = "ejbCreate";
    private static final String EJB_POST_CREATE_METHOD_PREFIX = "ejbPostCreate";
    private static final String HOME_CREATE_METHOD_PREFIX = "create";
    
    private final Set<String> ENTITY_BEAN_INTERFACES = Set.of(
        "javax.ejb.EntityBean",
        "javax/ejb/EntityBean"
    );
    
    private final Set<String> SESSION_BEAN_INTERFACES = Set.of(
        "javax.ejb.SessionBean", 
        "javax/ejb/SessionBean"
    );
    
    private GraphRepository graphRepository;
    private Map<String, CreateMethodMetadata> createMethodCache;
    private Map<String, HomeInterfaceMetadata> homeInterfaceCache;
    
    public EjbCreateMethodUsageInspector() {
        super(INSPECTOR_ID, INSPECTOR_NAME);
        this.createMethodCache = new ConcurrentHashMap<>();
        this.homeInterfaceCache = new ConcurrentHashMap<>();
    }
    
    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }
    
    @Override
    protected InspectorResult processClass(ProjectFile file, ClassNode classNode) {
        InspectorResult.Builder resultBuilder = InspectorResult.builder().file(file);
        
        // Analyze EJB bean classes for ejbCreate methods
        if (isEjbBeanClass(classNode)) {
            CreateMethodMetadata beanMetadata = analyzeEjbBeanCreateMethods(file, classNode);
            if (beanMetadata.hasCreateMethods()) {
                addBeanCreateMethodResults(resultBuilder, beanMetadata);
                createBeanCreateMethodGraphNodes(file, classNode, beanMetadata);
            }
        }
        
        // Analyze Home interfaces for create methods
        if (isEjbHomeInterface(classNode)) {
            HomeInterfaceMetadata homeMetadata = analyzeHomeInterfaceCreateMethods(file, classNode);
            if (homeMetadata.hasCreateMethods()) {
                addHomeCreateMethodResults(resultBuilder, homeMetadata);
                createHomeInterfaceGraphNodes(file, classNode, homeMetadata);
            }
        }
        
        // Analyze client classes for create method usage
        CreateMethodUsageMetadata usageMetadata = analyzeCreateMethodUsage(file, classNode);
        if (usageMetadata.hasCreateMethodCalls()) {
            addCreateMethodUsageResults(resultBuilder, usageMetadata);
            createCreateMethodUsageGraphNodes(file, classNode, usageMetadata);
        }
        
        InspectorResult result = resultBuilder.build();
        return result.isEmpty() ? InspectorResult.empty() : result;
    }
}
```

#### EJB Bean Create Method Analysis

```java
private CreateMethodMetadata analyzeEjbBeanCreateMethods(ProjectFile file, ClassNode classNode) {
    CreateMethodMetadata.Builder builder = CreateMethodMetadata.builder()
        .className(classNode.name)
        .beanType(determineEjbBeanType(classNode))
        .projectFile(file);
    
    // Analyze all methods for create patterns
    for (MethodNode method : classNode.methods) {
        if (isEjbCreateMethod(method)) {
            EjbCreateMethodInfo createMethod = analyzeEjbCreateMethod(method, classNode);
            builder.addEjbCreateMethod(createMethod);
        } else if (isEjbPostCreateMethod(method)) {
            EjbPostCreateMethodInfo postCreateMethod = analyzeEjbPostCreateMethod(method, classNode);
            builder.addEjbPostCreateMethod(postCreateMethod);
        }
    }
    
    // Match ejbCreate with ejbPostCreate methods
    matchCreateAndPostCreateMethods(builder);
    
    return builder.build();
}

private boolean isEjbCreateMethod(MethodNode method) {
    return method.name.startsWith(EJB_CREATE_METHOD_PREFIX) && 
           !method.name.equals(EJB_CREATE_METHOD_PREFIX) &&
           isPublicMethod(method);
}

private boolean isEjbPostCreateMethod(MethodNode method) {
    return method.name.startsWith(EJB_POST_CREATE_METHOD_PREFIX) && 
           !method.name.equals(EJB_POST_CREATE_METHOD_PREFIX) &&
           isPublicMethod(method);
}

private EjbCreateMethodInfo analyzeEjbCreateMethod(MethodNode method, ClassNode classNode) {
    EjbCreateMethodInfo.Builder builder = EjbCreateMethodInfo.builder()
        .methodName(method.name)
        .methodDescriptor(method.desc)
        .createMethodSuffix(extractCreateMethodSuffix(method.name))
        .returnType(extractReturnType(method.desc))
        .parameters(extractMethodParameters(method.desc));
    
    // Analyze method body for initialization patterns
    if (method.instructions != null) {
        CreateMethodAnalysis analysis = analyzeCreateMethodBody(method, classNode);
        builder.initializationPatterns(analysis.getInitializationPatterns())
               .fieldAssignments(analysis.getFieldAssignments())
               .dependencyLookups(analysis.getDependencyLookups())
               .databaseOperations(analysis.getDatabaseOperations());
    }
    
    // Determine create method type
    EjbBeanType beanType = determineEjbBeanType(classNode);
    CreateMethodType createType = determineCreateMethodType(method, beanType);
    builder.createMethodType(createType);
    
    return builder.build();
}

private CreateMethodAnalysis analyzeCreateMethodBody(MethodNode method, ClassNode classNode) {
    CreateMethodAnalysis.Builder analysisBuilder = CreateMethodAnalysis.builder();
    
    InsnList instructions = method.instructions;
    for (AbstractInsnNode instruction : instructions) {
        if (instruction instanceof FieldInsnNode) {
            FieldInsnNode fieldInsn = (FieldInsnNode) instruction;
            if (fieldInsn.getOpcode() == Opcodes.PUTFIELD) {
                analysisBuilder.addFieldAssignment(fieldInsn.name, fieldInsn.desc);
            }
        } else if (instruction instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) instruction;
            
            // Detect JNDI lookups
            if (isJndiLookupCall(methodInsn)) {
                analysisBuilder.addDependencyLookup(extractJndiName(methodInsn));
            }
            
            // Detect database operations
            if (isDatabaseOperationCall(methodInsn)) {
                analysisBuilder.addDatabaseOperation(methodInsn.name, methodInsn.desc);
            }
            
            // Detect initialization patterns
            if (isInitializationCall(methodInsn)) {
                analysisBuilder.addInitializationPattern(methodInsn.name, methodInsn.owner);
            }
        }
    }
    
    return analysisBuilder.build();
}
```

#### Home Interface Create Method Analysis

```java
private HomeInterfaceMetadata analyzeHomeInterfaceCreateMethods(ProjectFile file, ClassNode classNode) {
    HomeInterfaceMetadata.Builder builder = HomeInterfaceMetadata.builder()
        .interfaceName(classNode.name)
        .isRemoteHome(isRemoteHomeInterface(classNode))
        .isLocalHome(isLocalHomeInterface(classNode))
        .projectFile(file);
    
    // Analyze create methods in Home interface
    for (MethodNode method : classNode.methods) {
        if (isHomeCreateMethod(method)) {
            HomeCreateMethodInfo createMethod = analyzeHomeCreateMethod(method, classNode);
            builder.addCreateMethod(createMethod);
        }
    }
    
    return builder.build();
}

private boolean isHomeCreateMethod(MethodNode method) {
    return method.name.startsWith(HOME_CREATE_METHOD_PREFIX) &&
           (method.access & Opcodes.ACC_ABSTRACT) != 0 && // Abstract method in interface
           isPublicMethod(method);
}

private HomeCreateMethodInfo analyzeHomeCreateMethod(MethodNode method, ClassNode classNode) {
    HomeCreateMethodInfo.Builder builder = HomeCreateMethodInfo.builder()
        .methodName(method.name)
        .methodDescriptor(method.desc)
        .createMethodSuffix(extractCreateMethodSuffix(method.name))
        .returnType(extractReturnType(method.desc))
        .parameters(extractMethodParameters(method.desc))
        .isRemoteMethod(isRemoteHomeInterface(classNode))
        .isLocalMethod(isLocalHomeInterface(classNode));
    
    // Extract exception declarations
    if (method.exceptions != null) {
        List<String> exceptions = method.exceptions.stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        builder.declaredExceptions(exceptions);
    }
    
    return builder.build();
}

private boolean isRemoteHomeInterface(ClassNode classNode) {
    return implementsInterface(classNode, "javax/ejb/EJBHome");
}

private boolean isLocalHomeInterface(ClassNode classNode) {
    return implementsInterface(classNode, "javax/ejb/EJBLocalHome");
}
```

#### Create Method Usage Analysis

```java
private CreateMethodUsageMetadata analyzeCreateMethodUsage(ProjectFile file, ClassNode classNode) {
    CreateMethodUsageMetadata.Builder builder = CreateMethodUsageMetadata.builder()
        .className(classNode.name)
        .projectFile(file);
    
    // Analyze all methods for create method calls
    for (MethodNode method : classNode.methods) {
        if (method.instructions == null) continue;
        
        List<CreateMethodCall> createCalls = findCreateMethodCalls(method, classNode);
        if (!createCalls.isEmpty()) {
            CreateMethodCallContext callContext = CreateMethodCallContext.builder()
                .callerMethod(method.name)
                .callerClass(classNode.name)
                .createMethodCalls(createCalls)
                .build();
            builder.addCallContext(callContext);
        }
    }
    
    return builder.build();
}

private List<CreateMethodCall> findCreateMethodCalls(MethodNode method, ClassNode classNode) {
    List<CreateMethodCall> createCalls = new ArrayList<>();
    
    InsnList instructions = method.instructions;
    for (AbstractInsnNode instruction : instructions) {
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) instruction;
            
            if (isCreateMethodCall(methodInsn)) {
                CreateMethodCall createCall = analyzeCreateMethodCall(methodInsn, method);
                createCalls.add(createCall);
            }
        }
    }
    
    return createCalls;
}

private boolean isCreateMethodCall(MethodInsnNode methodInsn) {
    // Check if method name starts with "create"
    if (!methodInsn.name.startsWith("create")) {
        return false;
    }
    
    // Check if owner looks like an EJB Home interface
    return isLikelyEjbHomeInterface(methodInsn.owner);
}

private boolean isLikelyEjbHomeInterface(String className) {
    return className.endsWith("Home") || 
           className.endsWith("LocalHome") ||
           className.contains("Home");
}

private CreateMethodCall analyzeCreateMethodCall(MethodInsnNode methodInsn, MethodNode callerMethod) {
    CreateMethodCall.Builder builder = CreateMethodCall.builder()
        .targetClass(methodInsn.owner)
        .methodName(methodInsn.name)
        .methodDescriptor(methodInsn.desc)
        .callerMethod(callerMethod.name)
        .parameters(extractMethodParameters(methodInsn.desc));
    
    // Analyze context around the call
    CreateCallContext context = analyzeCreateCallContext(methodInsn, callerMethod);
    builder.callContext(context);
    
    return builder.build();
}

private CreateCallContext analyzeCreateCallContext(MethodInsnNode createCall, MethodNode callerMethod) {
    CreateCallContext.Builder builder = CreateCallContext.builder();
    
    // Look for JNDI lookup pattern before create call
    AbstractInsnNode current = createCall.getPrevious();
    int lookback = 0;
    while (current != null && lookback < 10) {
        if (current instanceof MethodInsnNode) {
            MethodInsnNode prevCall = (MethodInsnNode) current;
            if (isJndiLookupCall(prevCall)) {
                builder.hasJndiLookup(true)
                       .jndiName(extractJndiName(prevCall));
                break;
            }
        }
        current = current.getPrevious();
        lookback++;
    }
    
    // Look for exception handling around create call
    boolean hasExceptionHandling = hasExceptionHandlingAround(createCall, callerMethod);
    builder.hasExceptionHandling(hasExceptionHandling);
    
    return builder.build();
}
```

#### Graph Integration

```java
private void createBeanCreateMethodGraphNodes(ProjectFile file, ClassNode classNode, 
        CreateMethodMetadata metadata) {
    
    // Find or create the EJB bean node
    String nodeId = classNode.name;
    Optional<GraphNode> existingNode = graphRepository.findNodeById(nodeId);
    
    EjbBeanGraphNode beanNode;
    if (existingNode.isPresent() && existingNode.get() instanceof EjbBeanGraphNode) {
        beanNode = (EjbBeanGraphNode) existingNode.get();
    } else {
        beanNode = createEjbBeanGraphNode(classNode, metadata.getBeanType());
        graphRepository.addNode(beanNode);
    }
    
    // Add create method information to the node
    for (EjbCreateMethodInfo createMethod : metadata.getEjbCreateMethods()) {
        EjbCreateMethodDescriptor descriptor = EjbCreateMethodDescriptor.builder()
            .methodName(createMethod.getMethodName())
            .methodSignature(createMethod.getMethodDescriptor())
            .createMethodType(createMethod.getCreateMethodType())
            .parameterCount(createMethod.getParameters().size())
            .hasPostCreateMethod(metadata.hasMatchingPostCreateMethod(createMethod))
            .initializationComplexity(calculateInitializationComplexity(createMethod))
            .build();
        
        beanNode.addCreateMethod(createMethod.getMethodName(), descriptor);
    }
}

private void createHomeInterfaceGraphNodes(ProjectFile file, ClassNode classNode,
        HomeInterfaceMetadata metadata) {
    
    // Create Home interface node
    EjbHomeInterfaceNode homeNode = EjbHomeInterfaceNode.builder()
        .nodeId(classNode.name)
        .interfaceName(classNode.name)
        .isRemoteHome(metadata.isRemoteHome())
        .isLocalHome(metadata.isLocalHome())
        .build();
    
    // Add create methods to home node
    for (HomeCreateMethodInfo createMethod : metadata.getCreateMethods()) {
        HomeCreateMethodDescriptor descriptor = HomeCreateMethodDescriptor.builder()
            .methodName(createMethod.getMethodName())
            .methodSignature(createMethod.getMethodDescriptor())
            .returnType(createMethod.getReturnType())
            .parameterCount(createMethod.getParameters().size())
            .isRemoteMethod(createMethod.isRemoteMethod())
            .throwsRemoteException(createMethod.getDeclaredExceptions().contains("java.rmi.RemoteException"))
            .build();
        
        homeNode.addCreateMethod(createMethod.getMethodName(), descriptor);
    }
    
    graphRepository.addNode(homeNode);
    
    // Create edge from Home interface to Bean class
    String beanClassName = inferBeanClassFromHome(classNode.name);
    if (beanClassName != null) {
        EjbHomeToEjbBeanEdge edge = new EjbHomeToEjbBeanEdge(
            generateEdgeId(classNode.name, beanClassName),
            classNode.name,
            beanClassName,
            "manages"
        );
        graphRepository.addEdge(edge);
    }
}

private void createCreateMethodUsageGraphNodes(ProjectFile file, ClassNode classNode,
        CreateMethodUsageMetadata usageMetadata) {
    
    // Create client class node
    EjbClientNode clientNode = EjbClientNode.builder()
        .nodeId(classNode.name)
        .className(classNode.name)
        .clientType(determineClientType(classNode))
        .build();
    
    // Add create method usage information
    for (CreateMethodCallContext callContext : usageMetadata.getCallContexts()) {
        for (CreateMethodCall call : callContext.getCreateMethodCalls()) {
            CreateMethodUsageDescriptor descriptor = CreateMethodUsageDescriptor.builder()
                .targetHomeInterface(call.getTargetClass())
                .createMethodName(call.getMethodName())
                .callerMethod(call.getCallerMethod())
                .hasJndiLookup(call.getCallContext().hasJndiLookup())
                .jndiName(call.getCallContext().getJndiName())
                .hasExceptionHandling(call.getCallContext().hasExceptionHandling())
                .build();
            
            clientNode.addCreateMethodUsage(call.getTargetClass(), descriptor);
        }
    }
    
    graphRepository.addNode(clientNode);
    
    // Create edges from client to Home interfaces
    createClientToHomeEdges(classNode.name, usageMetadata);
}

private void createClientToHomeEdges(String clientClassName, CreateMethodUsageMetadata usageMetadata) {
    Set<String> referencedHomes = usageMetadata.getCallContexts().stream()
        .flatMap(context -> context.getCreateMethodCalls().stream())
        .map(CreateMethodCall::getTargetClass)
        .collect(Collectors.toSet());
    
    for (String homeInterface : referencedHomes) {
        EjbClientToHomeEdge edge = new EjbClientToHomeEdge(
            generateEdgeId(clientClassName, homeInterface),
            clientClassName,
            homeInterface,
            "uses"
        );
        graphRepository.addEdge(edge);
    }
}
```

#### Inspector Result Creation

```java
private void addBeanCreateMethodResults(InspectorResult.Builder builder, 
        CreateMethodMetadata metadata) {
    
    builder.addTag(InspectorTags.EJB_CREATE_METHOD)
           .addTag(metadata.getBeanType() == EjbBeanType.ENTITY_BEAN ? 
                   InspectorTags.EJB_ENTITY_BEAN : InspectorTags.EJB_SESSION_BEAN);
    
    // Add complexity tags
    int totalCreateMethods = metadata.getEjbCreateMethods().size();
    if (totalCreateMethods > 3) {
        builder.addTag(InspectorTags.EJB_MIGRATION_COMPLEX);
    } else if (totalCreateMethods > 1) {
        builder.addTag(InspectorTags.EJB_MIGRATION_MEDIUM);
    } else {
        builder.addTag(InspectorTags.EJB_MIGRATION_SIMPLE);
    }
    
    // Add create method specific tags
    for (EjbCreateMethodInfo createMethod : metadata.getEjbCreateMethods()) {
        if (createMethod.getCreateMethodType() == CreateMethodType.PARAMETERIZED) {
            builder.addTag(InspectorTags.EJB_PARAMETERIZED_CREATE);
        }
        if (!createMethod.getInitializationPatterns().isEmpty()) {
            builder.addTag(InspectorTags.EJB_COMPLEX_INITIALIZATION);
        }
        if (!createMethod.getDependencyLookups().isEmpty()) {
            builder.addTag(InspectorTags.EJB_DEPENDENCY_INJECTION_CANDIDATE);
        }
    }
    
    // Add properties
    builder.addProperty("ejbCreateMethodCount", String.valueOf(totalCreateMethods))
           .addProperty("ejbPostCreateMethodCount", String.valueOf(metadata.getEjbPostCreateMethods().size()))
           .addProperty("beanType", metadata.getBeanType().toString())
           .addProperty("migrationComplexity", assessCreateMethodMigrationComplexity(metadata));
}

private void addHomeCreateMethodResults(InspectorResult.Builder builder,
        HomeInterfaceMetadata metadata) {
    
    builder.addTag(InspectorTags.EJB_HOME_INTERFACE)
           .addTag(InspectorTags.EJB_CREATE_METHOD);
    
    if (metadata.isRemoteHome()) {
        builder.addTag(InspectorTags.EJB_REMOTE_INTERFACE);
    }
    if (metadata.isLocalHome()) {
        builder.addTag(InspectorTags.EJB_LOCAL_INTERFACE);
    }
    
    // Add properties
    builder.addProperty("homeCreateMethodCount", String.valueOf(metadata.getCreateMethods().size()))
           .addProperty("isRemoteHome", String.valueOf(metadata.isRemoteHome()))
           .addProperty("isLocalHome", String.valueOf(metadata.isLocalHome()));
}

private void addCreateMethodUsageResults(InspectorResult.Builder builder,
        CreateMethodUsageMetadata usageMetadata) {
    
    builder.addTag(InspectorTags.EJB_CLIENT_CODE)
           .addTag(InspectorTags.EJB_CREATE_METHOD_USAGE);
    
    int totalCreateCalls = usageMetadata.getCallContexts().stream()
        .mapToInt(context -> context.getCreateMethodCalls().size())
        .sum();
    
    boolean hasJndiLookups = usageMetadata.getCallContexts().stream()
        .flatMap(context -> context.getCreateMethodCalls().stream())
        .anyMatch(call -> call.getCallContext().hasJndiLookup());
    
    if (hasJndiLookups) {
        builder.addTag(InspectorTags.EJB_JNDI_LOOKUP);
        builder.addTag(InspectorTags.EJB_DEPENDENCY_INJECTION_CANDIDATE);
    }
    
    // Add properties
    builder.addProperty("createMethodCallCount", String.valueOf(totalCreateCalls))
           .addProperty("referencedHomeInterfaceCount", 
                       String.valueOf(countReferencedHomeInterfaces(usageMetadata)))
           .addProperty("hasJndiLookups", String.valueOf(hasJndiLookups));
}

private String assessCreateMethodMigrationComplexity(CreateMethodMetadata metadata) {
    int complexityScore = 0;
    
    // Base complexity for having create methods
    complexityScore += 2;
    
    // Add complexity for each create method
    complexityScore += metadata.getEjbCreateMethods().size();
    
    // Add complexity for initialization patterns
    for (EjbCreateMethodInfo createMethod : metadata.getEjbCreateMethods()) {
        complexityScore += createMethod.getInitializationPatterns().size();
        complexityScore += createMethod.getDependencyLookups().size();
        if (!createMethod.getDatabaseOperations().isEmpty()) {
            complexityScore += 2;
        }
    }
    
    // Unmatched ejbPostCreate methods add complexity
    int unmatchedPostCreateMethods = metadata.getEjbPostCreateMethods().size() - 
                                   metadata.getMatchedPostCreateMethodCount();
    complexityScore += unmatchedPostCreateMethods * 2;
    
    // Classify complexity
    if (complexityScore <= 5) {
        return "LOW";
    } else if (complexityScore <= 12) {
        return "MEDIUM";
    } else {
        return "HIGH";
    }
}
```

### Data Model Classes

#### CreateMethodMetadata

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/CreateMethodMetadata.java
public class CreateMethodMetadata {
    private final String className;
    private final EjbBeanType beanType;
    private final ProjectFile projectFile;
    private final List<EjbCreateMethodInfo> ejbCreateMethods;
    private final List<EjbPostCreateMethodInfo> ejbPostCreateMethods;
    private final Map<String, String> createToPostCreateMappings;
    
    public boolean hasCreateMethods() {
        return !ejbCreateMethods.isEmpty();
    }
    
    public boolean hasMatchingPostCreateMethod(EjbCreateMethodInfo createMethod) {
        return createToPostCreateMappings.containsKey(createMethod.getMethodName());
    }
    
    public int getMatchedPostCreateMethodCount() {
        return createToPostCreateMappings.size();
    }
    
    // Builder pattern implementation
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        // Builder implementation with method to match create/postCreate methods
        public Builder matchCreateAndPostCreateMethods() {
            // Logic to match ejbCreate methods with ejbPostCreate methods
            return this;
        }
    }
}

public enum EjbBeanType {
    ENTITY_BEAN, STATEFUL_SESSION_BEAN, STATELESS_SESSION_BEAN, MESSAGE_DRIVEN_BEAN
}
```

#### EjbCreateMethodInfo

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/EjbCreateMethodInfo.java
public class EjbCreateMethodInfo {
    private final String methodName;
    private final String methodDescriptor;
    private final String createMethodSuffix;
    private final String returnType;
    private final List<MethodParameter> parameters;
    private final CreateMethodType createMethodType;
    private final List<String> initializationPatterns;
    private final List<String> fieldAssignments;
    private final List<String> dependencyLookups;
    private final List<DatabaseOperation> databaseOperations;
    
    // Builder pattern implementation
}

public enum CreateMethodType {
    DEFAULT_CREATE,     // ejbCreate() with no parameters
    PARAMETERIZED,      // ejbCreate(...) with parameters
    NAMED_CREATE        // ejbCreateBySomething(...)
}
```

#### HomeInterfaceMetadata

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/HomeInterfaceMetadata.java
public class HomeInterfaceMetadata {
    private final String interfaceName;
    private final boolean isRemoteHome;
    private final boolean isLocalHome;
    private final ProjectFile projectFile;
    private final List<HomeCreateMethodInfo> createMethods;
    
    public boolean hasCreateMethods() {
        return !createMethods.isEmpty();
    }
    
    // Builder pattern implementation
}
```

#### CreateMethodUsageMetadata

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/CreateMethodUsageMetadata.java
public class CreateMethodUsageMetadata {
    private final String className;
    private final ProjectFile projectFile;
    private final List<CreateMethodCallContext> callContexts;
    
    public boolean hasCreateMethodCalls() {
        return !callContexts.isEmpty();
    }
    
    // Builder pattern implementation
}

public class CreateMethodCallContext {
    private final String callerMethod;
    private final String callerClass;
    private final List<CreateMethodCall> createMethodCalls;
    
    // Builder pattern implementation
}

public class CreateMethodCall {
    private final String targetClass;
    private final String methodName;
    private final String methodDescriptor;
    private final String callerMethod;
    private final List<MethodParameter> parameters;
    private final CreateCallContext callContext;
    
    // Builder pattern implementation
}
```

## Implementation Tasks

### Step 1: Create Base Inspector Structure (1-2 hours)
1. Create `EjbCreateMethodUsageInspector` class extending `AbstractASMInspector`
2. Implement EJB bean and Home interface detection logic
3. Set up graph integration interface

### Step 2: Implement EJB Bean Create Method Analysis (2-3 hours)
1. Create ejbCreate and ejbPostCreate method detection logic
2. Implement create method body analysis for initialization patterns
3. Add create/postCreate method matching logic

### Step 3: Implement Home Interface Create Method Analysis (1-2 hours)
1. Create Home interface create method detection
2. Implement remote vs local Home interface differentiation
3. Add create method signature analysis

### Step 4: Implement Create Method Usage Analysis (1-2 hours)
1. Create client code create method call detection
2. Implement JNDI lookup pattern detection around create calls
3. Add create method call context analysis

### Step 5: Create Data Model Classes (1-2 hours)
1. Implement `CreateMethodMetadata` and related classes
2. Create builder patterns for complex objects
3. Add validation and utility methods

### Step 6: Integration and Testing (1-2 hours)
1. Create comprehensive unit tests
2. Test with sample EJB beans and Home interfaces
3. Validate graph node creation and edge relationships

## File Structure

```
src/main/java/com/analyzer/rules/ejb2spring/
├── EjbCreateMethodUsageInspector.java         # Main inspector implementation
├── model/
│   ├── CreateMethodMetadata.java              # Create method metadata
│   ├── EjbCreateMethodInfo.java               # EJB create method information
│   ├── EjbPostCreateMethodInfo.java           # EJB post-create method information
│   ├── HomeInterfaceMetadata.java             # Home interface metadata
│   ├── HomeCreateMethodInfo.java              # Home create method information
│   ├── CreateMethodUsageMetadata.java         # Create method usage metadata
│   ├── CreateMethodCallContext.java           # Create method call context
│   ├── CreateMethodCall.java                  # Individual create method call
│   ├── CreateCallContext.java                 # Context around create calls
│   └── CreateMethodAnalysis.java              # Create method body analysis
└── util/
    ├── CreateMethodMatcher.java               # Create/PostCreate method matching
    └── CreateMethodPatternAnalyzer.java       # Create method pattern analysis

src/test/java/com/analyzer/rules/ejb2spring/
├── EjbCreateMethodUsageInspectorTest.java     # Unit tests
├── CreateMethodMetadataTest.java              # Metadata tests
└── fixtures/
    ├── SampleEntityBean.java                  # Test CMP entity bean with ejbCreate
    ├── SampleStatefulSessionBean.java         # Test stateful session bean
    ├── SampleEntityHome.java                  # Test entity Home interface
    ├── SampleSessionHome.java                 # Test session Home interface
    └── SampleEjbClient.java                   # Test client using create methods
```

## Testing Requirements

### Unit Tests
- Test EJB bean ejbCreate method detection
- Test ejbPostCreate method detection and matching
- Test Home interface create method detection
- Test client create method call detection
- Test JNDI lookup pattern detection
- Test graph node creation and relationships

### Integration Tests
- Test with real EJB applications containing create methods
- Test create method complexity assessment
- Test migration pattern recommendations
- Test graph integration with other EJB inspectors

## Success Criteria

- [ ] Accurate detection of ejbCreate methods in Entity and Session beans (≥95% accuracy)
- [ ] Complete ejbPostCreate method detection and matching with ejbCreate methods
- [ ] Accurate Home interface create method detection
- [ ] Complete client create method call detection and context analysis
- [ ] Proper JNDI lookup pattern detection around create calls
- [ ] Comprehensive graph node creation with create method metadata
- [ ] Migration complexity assessment functionality
- [ ] 100% unit test coverage
- [ ] Performance: <300ms for typical EJB create method analysis

## Migration Recommendations

This inspector provides the following migration guidance:

### Entity Bean Create Methods
- **ejbCreate() → @PostConstruct + JPA @Entity**
  ```java
  // EJB 2.x
  public void ejbCreate(String name, String email) {
      this.name = name;
      this.email = email;
  }
  
  // Spring Boot + JPA
  @Entity
  public class Customer {
      @PostConstruct
      public void initialize() {
          // Initialization logic if needed
      }
      
      // Constructor injection preferred
      public Customer(String name, String email) {
          this.name = name;
          this.email = email;
      }
  }
  ```

### Session Bean Create Methods
- **ejbCreate() → Constructor injection or @PostConstruct**
  ```java
  // EJB 2.x
  public void ejbCreate(UserService userService) {
      this.userService = userService;
  }
  
  // Spring Boot
  @Service
  public class BusinessService {
      private final UserService userService;
      
      public BusinessService(UserService userService) {
          this.userService = userService;
      }
  }
  ```

### Home Interface Create Methods
- **Home.create() → Factory methods or direct instantiation**
  ```java
  // EJB 2.x
  CustomerHome home = (CustomerHome) context.lookup("CustomerHome");
  Customer customer = home.create("John", "john@example.com");
  
  // Spring Boot
  @Service
  public class CustomerFactory {
      @Autowired
      private CustomerRepository customerRepository;
      
      public Customer createCustomer(String name, String email) {
          Customer customer = new Customer(name, email);
          return customerRepository.save(customer);
      }
  }
  ```

### Client Code Migration
- **JNDI lookup + create() → Dependency injection**
  ```java
  // EJB 2.x
  InitialContext ctx = new InitialContext();
  CustomerHome home = (CustomerHome) ctx.lookup("java:comp/env/ejb/CustomerHome");
  Customer customer = home.create("John", "john@example.com");
  
  // Spring Boot
  @Service
  public class BusinessLogic {
      @Autowired
      private CustomerFactory customerFactory;
      
      public void processCustomer() {
          Customer customer = customerFactory.createCustomer("John", "john@example.com");
      }
  }
  ```

## Implementation Prompt

Use this specification to implement the EJB Create Method Usage Inspector. Focus on:

1. **Complete Create Method Analysis**: Detect all ejbCreate, ejbPostCreate, and Home.create patterns
2. **Accurate Pattern Recognition**: Properly identify create method signatures and initialization logic
3. **Client Usage Detection**: Find all create method calls and their JNDI lookup contexts
4. **Migration-Ready Output**: Provide detailed information needed for Spring Boot conversion
5. **Performance Optimization**: Efficient analysis for large EJB applications with many create methods

This inspector is essential for understanding EJB object lifecycle patterns and provides the foundation for converting EJB creation patterns to Spring dependency injection and factory patterns.
