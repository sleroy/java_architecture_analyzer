# Task 9: I-0709 Business Delegate Pattern Inspector

**ID:** Task 9  
**Priority:** P1 (High Priority Phase 1 Inspector)  
**Estimated Effort:** 5-7 hours  
**Prerequisites:** Tasks 1, 2, 3 complete  
**Deliverables:** Complete Business Delegate pattern detection and anti-pattern analysis  

## Overview

Implement inspector I-0709 to detect and analyze Business Delegate pattern usage in EJB applications. The Business Delegate pattern is a common EJB anti-pattern that hides EJB complexity behind a facade but introduces unnecessary layers and tight coupling. This inspector is essential for identifying these patterns and providing migration guidance to direct service injection or REST client patterns in Spring Boot applications.

## Technical Requirements

### Detection Capabilities

1. **Business Delegate Class Detection**
   - Identify classes implementing Business Delegate pattern
   - Detect delegate method signatures and EJB method mapping
   - Analyze delegate constructor patterns and initialization
   - Extract service interface abstraction patterns

2. **Service Locator Integration Analysis**
   - Detect Service Locator pattern usage within Business Delegates
   - Analyze JNDI lookup patterns and caching strategies
   - Identify EJB Home interface lookup and caching
   - Map Service Locator to Business Delegate relationships

3. **EJB Lookup Pattern Analysis**
   - Analyze hidden EJB lookup patterns within delegates
   - Detect Home interface usage and create method calls
   - Identify remote vs local EJB access patterns
   - Extract exception handling and retry logic

4. **Client Usage Pattern Detection**
   - Detect client code using Business Delegate pattern
   - Analyze delegate instantiation and lifecycle management
   - Identify Business Delegate factory patterns
   - Map client-to-delegate dependency relationships

5. **Anti-Pattern Assessment**
   - Classify Business Delegate complexity and layering depth
   - Identify unnecessary abstraction layers
   - Detect performance anti-patterns (excessive remote calls)
   - Assess migration complexity to direct service patterns

### Implementation Specifications

#### Inspector Class Structure

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/BusinessDelegatePatternJavaSourceInspector.java
public class BusinessDelegatePatternInspector extends AbstractASMInspector 
        implements GraphAwareInspector {
    
    private static final String INSPECTOR_ID = "I-0709";
    private static final String INSPECTOR_NAME = "Business Delegate Pattern Inspector";
    
    private static final Set<String> BUSINESS_DELEGATE_INDICATORS = Set.of(
        "BusinessDelegate", "Delegate", "ServiceDelegate", "EJBDelegate"
    );
    
    private static final Set<String> SERVICE_LOCATOR_INDICATORS = Set.of(
        "ServiceLocator", "ServiceFinder", "EJBServiceLocator", "Locator"
    );
    
    private static final Set<String> JNDI_LOOKUP_METHODS = Set.of(
        "lookup", "lookupLink", "rebind", "bind"
    );
    
    private final Set<String> EJB_HOME_METHODS = Set.of(
        "create", "findByPrimaryKey", "remove"
    );
    
    private GraphRepository graphRepository;
    private Map<String, BusinessDelegateMetadata> delegateCache;
    private Map<String, ServiceLocatorUsage> serviceLocatorCache;
    
    public BusinessDelegatePatternInspector() {
        super(INSPECTOR_ID, INSPECTOR_NAME);
        this.delegateCache = new ConcurrentHashMap<>();
        this.serviceLocatorCache = new ConcurrentHashMap<>();
    }
    
    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }
    
    @Override
    protected InspectorResult processClass(ProjectFile file, ClassNode classNode) {
        InspectorResult.Builder resultBuilder = InspectorResult.builder().file(file);
        
        // Analyze Business Delegate classes
        if (isBusinessDelegateClass(classNode)) {
            BusinessDelegateMetadata delegateMetadata = analyzeBusinessDelegate(file, classNode);
            addBusinessDelegateResults(resultBuilder, delegateMetadata);
            createBusinessDelegateGraphNodes(file, classNode, delegateMetadata);
        }
        
        // Analyze Service Locator usage
        ServiceLocatorUsage serviceLocatorUsage = analyzeServiceLocatorUsage(file, classNode);
        if (serviceLocatorUsage.hasServiceLocatorPattern()) {
            addServiceLocatorResults(resultBuilder, serviceLocatorUsage);
            createServiceLocatorGraphNodes(file, classNode, serviceLocatorUsage);
        }
        
        // Analyze client usage of Business Delegates
        BusinessDelegateClientUsage clientUsage = analyzeBusinessDelegateClientUsage(file, classNode);
        if (clientUsage.hasBusinessDelegateUsage()) {
            addClientUsageResults(resultBuilder, clientUsage);
            createClientUsageGraphNodes(file, classNode, clientUsage);
        }
        
        InspectorResult result = resultBuilder.build();
        return result.isEmpty() ? InspectorResult.empty() : result;
    }
}
```

#### Business Delegate Detection Logic

```java
private boolean isBusinessDelegateClass(ClassNode classNode) {
    String className = extractSimpleClassName(classNode.name);
    
    // Check class name patterns
    if (BUSINESS_DELEGATE_INDICATORS.stream().anyMatch(className::contains)) {
        return true;
    }
    
    // Check if class has delegate-like structure
    return hasBusinessDelegateStructure(classNode);
}

private boolean hasBusinessDelegateStructure(ClassNode classNode) {
    // Look for typical Business Delegate patterns:
    // 1. Private EJB reference fields
    // 2. Service Locator usage
    // 3. Methods that wrap EJB calls
    // 4. Exception translation patterns
    
    boolean hasEjbReferences = hasEjbReferenceFields(classNode);
    boolean hasServiceLocatorUsage = hasServiceLocatorPattern(classNode);
    boolean hasWrapperMethods = hasEjbWrapperMethods(classNode);
    
    return (hasEjbReferences && hasWrapperMethods) || 
           (hasServiceLocatorUsage && hasWrapperMethods);
}

private BusinessDelegateMetadata analyzeBusinessDelegate(ProjectFile file, ClassNode classNode) {
    BusinessDelegateMetadata.Builder builder = BusinessDelegateMetadata.builder()
        .className(classNode.name)
        .delegateType(determineDelegateType(classNode))
        .projectFile(file);
    
    // Analyze delegate methods
    for (MethodNode method : classNode.methods) {
        if (isDelegateMethod(method, classNode)) {
            BusinessDelegateMethod delegateMethod = analyzeDelegateMethod(method, classNode);
            builder.addDelegateMethod(delegateMethod);
        }
    }
    
    // Analyze EJB references
    List<EjbReference> ejbReferences = extractEjbReferences(classNode);
    builder.ejbReferences(ejbReferences);
    
    // Analyze Service Locator integration
    ServiceLocatorIntegration serviceLocatorIntegration = analyzeServiceLocatorIntegration(classNode);
    builder.serviceLocatorIntegration(serviceLocatorIntegration);
    
    // Analyze exception handling patterns
    ExceptionHandlingAnalysis exceptionAnalysis = analyzeExceptionHandling(classNode);
    builder.exceptionHandling(exceptionAnalysis);
    
    return builder.build();
}

private boolean isDelegateMethod(MethodNode method, ClassNode classNode) {
    // Skip constructors, getters, setters
    if (isConstructor(method) || isGetterSetter(method)) {
        return false;
    }
    
    // Check if method contains EJB calls
    if (method.instructions == null) {
        return false;
    }
    
    return containsEjbMethodCalls(method) || containsServiceLocatorCalls(method);
}

private BusinessDelegateMethod analyzeDelegateMethod(MethodNode method, ClassNode classNode) {
    BusinessDelegateMethod.Builder builder = BusinessDelegateMethod.builder()
        .methodName(method.name)
        .methodDescriptor(method.desc)
        .returnType(extractReturnType(method.desc))
        .parameters(extractMethodParameters(method.desc));
    
    // Analyze method body for EJB interactions
    if (method.instructions != null) {
        DelegateMethodAnalysis analysis = analyzeDelegateMethodBody(method, classNode);
        builder.ejbMethodCalls(analysis.getEjbMethodCalls())
               .serviceLocatorCalls(analysis.getServiceLocatorCalls())
               .exceptionHandling(analysis.getExceptionHandling())
               .parameterTransformation(analysis.getParameterTransformation())
               .returnValueTransformation(analysis.getReturnValueTransformation());
    }
    
    return builder.build();
}

private DelegateMethodAnalysis analyzeDelegateMethodBody(MethodNode method, ClassNode classNode) {
    DelegateMethodAnalysis.Builder analysisBuilder = DelegateMethodAnalysis.builder();
    
    InsnList instructions = method.instructions;
    for (AbstractInsnNode instruction : instructions) {
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) instruction;
            
            // Detect EJB method calls
            if (isEjbMethodCall(methodInsn)) {
                EjbMethodCall ejbCall = EjbMethodCall.builder()
                    .targetClass(methodInsn.owner)
                    .methodName(methodInsn.name)
                    .methodDescriptor(methodInsn.desc)
                    .callType(determineEjbCallType(methodInsn))
                    .build();
                analysisBuilder.addEjbMethodCall(ejbCall);
            }
            
            // Detect Service Locator calls
            if (isServiceLocatorCall(methodInsn)) {
                ServiceLocatorCall locatorCall = ServiceLocatorCall.builder()
                    .targetClass(methodInsn.owner)
                    .methodName(methodInsn.name)
                    .lookupType(determineLookupType(methodInsn))
                    .build();
                analysisBuilder.addServiceLocatorCall(locatorCall);
            }
            
            // Detect exception handling patterns
            if (isExceptionHandlingCall(methodInsn)) {
                ExceptionHandlingPattern pattern = analyzeExceptionPattern(methodInsn);
                analysisBuilder.addExceptionHandling(pattern);
            }
        }
        
        // Analyze parameter and return value transformations
        if (instruction instanceof TypeInsnNode) {
            TypeInsnNode typeInsn = (TypeInsnNode) instruction;
            if (isDataTransformation(typeInsn)) {
                analysisBuilder.addParameterTransformation(typeInsn.desc);
            }
        }
    }
    
    return analysisBuilder.build();
}
```

#### Service Locator Integration Analysis

```java
private ServiceLocatorUsage analyzeServiceLocatorUsage(ProjectFile file, ClassNode classNode) {
    ServiceLocatorUsage.Builder builder = ServiceLocatorUsage.builder()
        .className(classNode.name)
        .projectFile(file);
    
    // Check if this class IS a Service Locator
    if (isServiceLocatorClass(classNode)) {
        ServiceLocatorMetadata metadata = analyzeServiceLocatorClass(classNode);
        builder.isServiceLocator(true)
               .serviceLocatorMetadata(metadata);
    }
    
    // Check if this class USES a Service Locator
    ServiceLocatorUsagePattern usagePattern = analyzeServiceLocatorUsagePattern(classNode);
    if (usagePattern.hasUsage()) {
        builder.usagePattern(usagePattern);
    }
    
    return builder.build();
}

private boolean isServiceLocatorClass(ClassNode classNode) {
    String className = extractSimpleClassName(classNode.name);
    
    // Check class name patterns
    if (SERVICE_LOCATOR_INDICATORS.stream().anyMatch(className::contains)) {
        return true;
    }
    
    // Check for Service Locator structure
    return hasServiceLocatorStructure(classNode);
}

private boolean hasServiceLocatorStructure(ClassNode classNode) {
    boolean hasLookupMethods = false;
    boolean hasCachingFields = false;
    boolean hasJndiUsage = false;
    
    // Check for lookup methods
    for (MethodNode method : classNode.methods) {
        if (JNDI_LOOKUP_METHODS.stream().anyMatch(method.name::contains)) {
            hasLookupMethods = true;
            if (containsJndiCalls(method)) {
                hasJndiUsage = true;
            }
        }
    }
    
    // Check for caching fields (Map, Hashtable, etc.)
    for (FieldNode field : classNode.fields) {
        if (isCachingField(field)) {
            hasCachingFields = true;
        }
    }
    
    return hasLookupMethods && hasJndiUsage && hasCachingFields;
}

private ServiceLocatorMetadata analyzeServiceLocatorClass(ClassNode classNode) {
    ServiceLocatorMetadata.Builder builder = ServiceLocatorMetadata.builder()
        .className(classNode.name)
        .locatorType(determineServiceLocatorType(classNode));
    
    // Analyze lookup methods
    for (MethodNode method : classNode.methods) {
        if (isLookupMethod(method)) {
            ServiceLocatorLookupMethod lookupMethod = analyzeLookupMethod(method, classNode);
            builder.addLookupMethod(lookupMethod);
        }
    }
    
    // Analyze caching strategy
    CachingStrategy cachingStrategy = analyzeCachingStrategy(classNode);
    builder.cachingStrategy(cachingStrategy);
    
    // Analyze JNDI integration
    JndiIntegration jndiIntegration = analyzeJndiIntegration(classNode);
    builder.jndiIntegration(jndiIntegration);
    
    return builder.build();
}

private ServiceLocatorLookupMethod analyzeLookupMethod(MethodNode method, ClassNode classNode) {
    ServiceLocatorLookupMethod.Builder builder = ServiceLocatorLookupMethod.builder()
        .methodName(method.name)
        .methodDescriptor(method.desc)
        .returnType(extractReturnType(method.desc));
    
    if (method.instructions != null) {
        LookupMethodAnalysis analysis = analyzeLookupMethodBody(method);
        builder.jndiNames(analysis.getJndiNames())
               .cachingBehavior(analysis.getCachingBehavior())
               .exceptionHandling(analysis.getExceptionHandling())
               .performsNarrowCast(analysis.performsNarrowCast());
    }
    
    return builder.build();
}
```

#### Client Usage Pattern Detection

```java
private BusinessDelegateClientUsage analyzeBusinessDelegateClientUsage(ProjectFile file, ClassNode classNode) {
    BusinessDelegateClientUsage.Builder builder = BusinessDelegateClientUsage.builder()
        .className(classNode.name)
        .projectFile(file);
    
    // Analyze all methods for Business Delegate usage
    for (MethodNode method : classNode.methods) {
        if (method.instructions == null) continue;
        
        List<BusinessDelegateCall> delegateCalls = findBusinessDelegateCalls(method, classNode);
        if (!delegateCalls.isEmpty()) {
            BusinessDelegateCallContext callContext = BusinessDelegateCallContext.builder()
                .callerMethod(method.name)
                .callerClass(classNode.name)
                .delegateCalls(delegateCalls)
                .build();
            builder.addCallContext(callContext);
        }
    }
    
    // Analyze field references to Business Delegates
    List<BusinessDelegateReference> fieldReferences = analyzeBusinessDelegateFields(classNode);
    builder.fieldReferences(fieldReferences);
    
    return builder.build();
}

private List<BusinessDelegateCall> findBusinessDelegateCalls(MethodNode method, ClassNode classNode) {
    List<BusinessDelegateCall> delegateCalls = new ArrayList<>();
    
    InsnList instructions = method.instructions;
    for (AbstractInsnNode instruction : instructions) {
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) instruction;
            
            if (isBusinessDelegateCall(methodInsn)) {
                BusinessDelegateCall delegateCall = analyzeBusinessDelegateCall(methodInsn, method);
                delegateCalls.add(delegateCall);
            }
        }
    }
    
    return delegateCalls;
}

private boolean isBusinessDelegateCall(MethodInsnNode methodInsn) {
    String targetClass = methodInsn.owner;
    
    // Check if target class looks like a Business Delegate
    return isLikelyBusinessDelegateClass(targetClass);
}

private boolean isLikelyBusinessDelegateClass(String className) {
    String simpleName = extractSimpleClassName(className);
    return BUSINESS_DELEGATE_INDICATORS.stream()
        .anyMatch(indicator -> simpleName.contains(indicator));
}

private BusinessDelegateCall analyzeBusinessDelegateCall(MethodInsnNode methodInsn, MethodNode callerMethod) {
    BusinessDelegateCall.Builder builder = BusinessDelegateCall.builder()
        .targetClass(methodInsn.owner)
        .methodName(methodInsn.name)
        .methodDescriptor(methodInsn.desc)
        .callerMethod(callerMethod.name)
        .parameters(extractMethodParameters(methodInsn.desc));
    
    // Analyze call context
    BusinessDelegateCallContext context = analyzeBusinessDelegateCallContext(methodInsn, callerMethod);
    builder.callContext(context);
    
    return builder.build();
}

private BusinessDelegateCallContext analyzeBusinessDelegateCallContext(MethodInsnNode delegateCall, MethodNode callerMethod) {
    BusinessDelegateCallContext.Builder builder = BusinessDelegateCallContext.builder();
    
    // Look for delegate instantiation pattern before call
    AbstractInsnNode current = delegateCall.getPrevious();
    int lookback = 0;
    while (current != null && lookback < 15) {
        if (current instanceof MethodInsnNode) {
            MethodInsnNode prevCall = (MethodInsnNode) current;
            if (prevCall.name.equals("<init>") && 
                prevCall.owner.equals(delegateCall.owner)) {
                builder.hasLocalInstantiation(true);
                break;
            }
        } else if (current instanceof FieldInsnNode) {
            FieldInsnNode fieldInsn = (FieldInsnNode) current;
            if (fieldInsn.getOpcode() == Opcodes.GETFIELD &&
                isBusinessDelegateField(fieldInsn)) {
                builder.usesFieldReference(true)
                       .fieldName(fieldInsn.name);
                break;
            }
        }
        current = current.getPrevious();
        lookback++;
    }
    
    // Look for exception handling around delegate call
    boolean hasExceptionHandling = hasExceptionHandlingAround(delegateCall, callerMethod);
    builder.hasExceptionHandling(hasExceptionHandling);
    
    return builder.build();
}
```

#### Graph Integration

```java
private void createBusinessDelegateGraphNodes(ProjectFile file, ClassNode classNode,
        BusinessDelegateMetadata metadata) {
    
    // Create Business Delegate node
    BusinessDelegateNode delegateNode = BusinessDelegateNode.builder()
        .nodeId(classNode.name)
        .className(classNode.name)
        .delegateType(metadata.getDelegateType())
        .delegateMethodCount(metadata.getDelegateMethods().size())
        .ejbReferenceCount(metadata.getEjbReferences().size())
        .hasServiceLocatorIntegration(metadata.getServiceLocatorIntegration().isPresent())
        .build();
    
    // Add delegate methods to node
    for (BusinessDelegateMethod delegateMethod : metadata.getDelegateMethods()) {
        BusinessDelegateMethodDescriptor descriptor = BusinessDelegateMethodDescriptor.builder()
            .methodName(delegateMethod.getMethodName())
            .methodSignature(delegateMethod.getMethodDescriptor())
            .ejbCallCount(delegateMethod.getEjbMethodCalls().size())
            .hasExceptionTranslation(delegateMethod.getExceptionHandling().hasTranslation())
            .hasParameterTransformation(delegateMethod.hasParameterTransformation())
            .build();
        
        delegateNode.addDelegateMethod(delegateMethod.getMethodName(), descriptor);
    }
    
    graphRepository.addNode(delegateNode);
    
    // Create edges to referenced EJBs
    createBusinessDelegateToEjbEdges(classNode.name, metadata.getEjbReferences());
}

private void createServiceLocatorGraphNodes(ProjectFile file, ClassNode classNode,
        ServiceLocatorUsage serviceLocatorUsage) {
    
    if (serviceLocatorUsage.isServiceLocator()) {
        // Create Service Locator node
        ServiceLocatorNode locatorNode = ServiceLocatorNode.builder()
            .nodeId(classNode.name)
            .className(classNode.name)
            .locatorType(serviceLocatorUsage.getServiceLocatorMetadata().getLocatorType())
            .lookupMethodCount(serviceLocatorUsage.getServiceLocatorMetadata().getLookupMethods().size())
            .cachingStrategy(serviceLocatorUsage.getServiceLocatorMetadata().getCachingStrategy())
            .build();
        
        graphRepository.addNode(locatorNode);
    }
    
    if (serviceLocatorUsage.getUsagePattern() != null) {
        // Create edges to Service Locators used by this class
        createServiceLocatorUsageEdges(classNode.name, serviceLocatorUsage.getUsagePattern());
    }
}

private void createClientUsageGraphNodes(ProjectFile file, ClassNode classNode,
        BusinessDelegateClientUsage clientUsage) {
    
    // Create Client node
    BusinessDelegateClientNode clientNode = BusinessDelegateClientNode.builder()
        .nodeId(classNode.name)
        .className(classNode.name)
        .clientType(determineClientType(classNode))
        .businessDelegateCallCount(getTotalDelegateCalls(clientUsage))
        .usesFieldReferences(clientUsage.getFieldReferences().size() > 0)
        .build();
    
    // Add delegate usage information
    for (BusinessDelegateCallContext callContext : clientUsage.getCallContexts()) {
        for (BusinessDelegateCall call : callContext.getDelegateCalls()) {
            BusinessDelegateUsageDescriptor descriptor = BusinessDelegateUsageDescriptor.builder()
                .targetDelegate(call.getTargetClass())
                .delegateMethodName(call.getMethodName())
                .callerMethod(call.getCallerMethod())
                .hasLocalInstantiation(call.getCallContext().hasLocalInstantiation())
                .usesFieldReference(call.getCallContext().usesFieldReference())
                .hasExceptionHandling(call.getCallContext().hasExceptionHandling())
                .build();
            
            clientNode.addDelegateUsage(call.getTargetClass(), descriptor);
        }
    }
    
    graphRepository.addNode(clientNode);
    
    // Create edges from client to Business Delegates
    createClientToBusinessDelegateEdges(classNode.name, clientUsage);
}

private void createBusinessDelegateToEjbEdges(String delegateClassName, List<EjbReference> ejbReferences) {
    for (EjbReference ejbRef : ejbReferences) {
        BusinessDelegateToEjbEdge edge = new BusinessDelegateToEjbEdge(
            generateEdgeId(delegateClassName, ejbRef.getEjbClassName()),
            delegateClassName,
            ejbRef.getEjbClassName(),
            "delegates-to"
        );
        
        edge.setEjbType(ejbRef.getEjbType());
        edge.setAccessType(ejbRef.getAccessType()); // remote/local
        edge.setCallFrequency(ejbRef.getCallFrequency());
        
        graphRepository.addEdge(edge);
    }
}
```

#### Inspector Result Creation

```java
private void addBusinessDelegateResults(InspectorResult.Builder builder,
        BusinessDelegateMetadata metadata) {
    
    builder.addTag(InspectorTags.EJB_BUSINESS_DELEGATE)
           .addTag(InspectorTags.EJB_ANTI_PATTERN)
           .addTag(InspectorTags.EJB_MIGRATION_HIGH_PRIORITY);
    
    // Add complexity tags based on delegate structure
    int totalDelegateMethods = metadata.getDelegateMethods().size();
    int totalEjbReferences = metadata.getEjbReferences().size();
    
    if (totalDelegateMethods > 10 || totalEjbReferences > 5) {
        builder.addTag(InspectorTags.EJB_MIGRATION_COMPLEX);
    } else if (totalDelegateMethods > 5 || totalEjbReferences > 2) {
        builder.addTag(InspectorTags.EJB_MIGRATION_MEDIUM);
    } else {
        builder.addTag(InspectorTags.EJB_MIGRATION_SIMPLE);
    }
    
    // Add specific Business Delegate tags
    if (metadata.getServiceLocatorIntegration().isPresent()) {
        builder.addTag(InspectorTags.EJB_SERVICE_LOCATOR);
    }
    
    boolean hasExceptionTranslation = metadata.getDelegateMethods().stream()
        .anyMatch(method -> method.getExceptionHandling().hasTranslation());
    if (hasExceptionTranslation) {
        builder.addTag(InspectorTags.EJB_EXCEPTION_TRANSLATION);
    }
    
    // Add properties
    builder.addProperty("delegateType", metadata.getDelegateType().toString())
           .addProperty("delegateMethodCount", String.valueOf(totalDelegateMethods))
           .addProperty("ejbReferenceCount", String.valueOf(totalEjbReferences))
           .addProperty("hasServiceLocatorIntegration", 
                       String.valueOf(metadata.getServiceLocatorIntegration().isPresent()))
           .addProperty("migrationComplexity", assessBusinessDelegateMigrationComplexity(metadata));
}

private void addServiceLocatorResults(InspectorResult.Builder builder,
        ServiceLocatorUsage serviceLocatorUsage) {
    
    builder.addTag(InspectorTags.EJB_SERVICE_LOCATOR)
           .addTag(InspectorTags.EJB_ANTI_PATTERN);
    
    if (serviceLocatorUsage.isServiceLocator()) {
        builder.addTag(InspectorTags.EJB_SERVICE_LOCATOR_IMPLEMENTATION);
        
        ServiceLocatorMetadata metadata = serviceLocatorUsage.getServiceLocatorMetadata();
        builder.addProperty("serviceLocatorType", metadata.getLocatorType().toString())
               .addProperty("lookupMethodCount", String.valueOf(metadata.getLookupMethods().size()))
               .addProperty("cachingStrategy", metadata.getCachingStrategy().toString());
    }
    
    if (serviceLocatorUsage.getUsagePattern() != null) {
        builder.addTag(InspectorTags.EJB_SERVICE_LOCATOR_USAGE);
        builder.addProperty("serviceLocatorCalls", 
                          String.valueOf(serviceLocatorUsage.getUsagePattern().getCallCount()));
    }
}

private void addClientUsageResults(InspectorResult.Builder builder,
        BusinessDelegateClientUsage clientUsage) {
    
    builder.addTag(InspectorTags.EJB_CLIENT_CODE)
           .addTag(InspectorTags.EJB_BUSINESS_DELEGATE_USAGE);
    
    int totalDelegateCalls = getTotalDelegateCalls(clientUsage);
    int uniqueDelegates = getUniqueDelegateCount(clientUsage);
    
    if (totalDelegateCalls > 10 || uniqueDelegates > 5) {
        builder.addTag(InspectorTags.EJB_MIGRATION_COMPLEX);
    }
    
    // Add properties
    builder.addProperty("businessDelegateCallCount", String.valueOf(totalDelegateCalls))
           .addProperty("uniqueBusinessDelegateCount", String.valueOf(uniqueDelegates))
           .addProperty("usesFieldReferences", String.valueOf(clientUsage.getFieldReferences().size() > 0));
}

private String assessBusinessDelegateMigrationComplexity(BusinessDelegateMetadata metadata) {
    int complexityScore = 0;
    
    // Base complexity for Business Delegate anti-pattern
    complexityScore += 5;
    
    // Add complexity for delegate methods
    complexityScore += metadata.getDelegateMethods().size();
    
    // Add complexity for EJB references
    complexityScore += metadata.getEjbReferences().size() * 2;
    
    // Add complexity for Service Locator integration
    if (metadata.getServiceLocatorIntegration().isPresent()) {
        complexityScore += 3;
    }
    
    // Add complexity for exception translation
    long exceptionTranslationCount = metadata.getDelegateMethods().stream()
        .mapToLong(method -> method.getExceptionHandling().hasTranslation() ? 1 : 0)
        .sum();
    complexityScore += (int) exceptionTranslationCount * 2;
    
    // Add complexity for parameter/return transformations
    long transformationCount = metadata.getDelegateMethods().stream()
        .mapToLong(method -> (method.hasParameterTransformation() ? 1 : 0) + 
                            (method.hasReturnValueTransformation() ? 1 : 0))
        .sum();
    complexityScore += (int) transformationCount;
    
    // Classify complexity
    if (complexityScore <= 8) {
        return "LOW";
    } else if (complexityScore <= 15) {
        return "MEDIUM";
    } else {
        return "HIGH";
    }
}
```

### Data Model Classes

#### BusinessDelegateMetadata

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/BusinessDelegateMetadata.java
public class BusinessDelegateMetadata {
    private final String className;
    private final BusinessDelegateType delegateType;
    private final ProjectFile projectFile;
    private final List<BusinessDelegateMethod> delegateMethods;
    private final List<EjbReference> ejbReferences;
    private final Optional<ServiceLocatorIntegration> serviceLocatorIntegration;
    private final ExceptionHandlingAnalysis exceptionHandling;
    
    private BusinessDelegateMetadata(Builder builder) {
        this.className = builder.className;
        this.delegateType = builder.delegateType;
        this.projectFile = builder.projectFile;
        this.delegateMethods = Collections.unmodifiableList(builder.delegateMethods);
        this.ejbReferences = Collections.unmodifiableList(builder.ejbReferences);
        this.serviceLocatorIntegration = builder.serviceLocatorIntegration;
        this.exceptionHandling = builder.exceptionHandling;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getClassName() { return className; }
    public BusinessDelegateType getDelegateType() { return delegateType; }
    public ProjectFile getProjectFile() { return projectFile; }
    public List<BusinessDelegateMethod> getDelegateMethods() { return delegateMethods; }
    public List<EjbReference> getEjbReferences() { return ejbReferences; }
    public Optional<ServiceLocatorIntegration> getServiceLocatorIntegration() { return serviceLocatorIntegration; }
    public ExceptionHandlingAnalysis getExceptionHandling() { return exceptionHandling; }
    
    public static class Builder {
        private String className;
        private BusinessDelegateType delegateType;
        private ProjectFile projectFile;
        private List<BusinessDelegateMethod> delegateMethods = new ArrayList<>();
        private List<EjbReference> ejbReferences = new ArrayList<>();
        private Optional<ServiceLocatorIntegration> serviceLocatorIntegration = Optional.empty();
        private ExceptionHandlingAnalysis exceptionHandling;
        
        public Builder className(String className) {
            this.className = className;
            return this;
        }
        
        public Builder delegateType(BusinessDelegateType delegateType) {
            this.delegateType = delegateType;
            return this;
        }
        
        public Builder projectFile(ProjectFile projectFile) {
            this.projectFile = projectFile;
            return this;
        }
        
        public Builder addDelegateMethod(BusinessDelegateMethod method) {
            this.delegateMethods.add(method);
            return this;
        }
        
        public Builder ejbReferences(List<EjbReference> ejbReferences) {
            this.ejbReferences = new ArrayList<>(ejbReferences);
            return this;
        }
        
        public Builder serviceLocatorIntegration(ServiceLocatorIntegration integration) {
            this.serviceLocatorIntegration = Optional.ofNullable(integration);
            return this;
        }
        
        public Builder exceptionHandling(ExceptionHandlingAnalysis exceptionHandling) {
            this.exceptionHandling = exceptionHandling;
            return this;
        }
        
        public BusinessDelegateMetadata build() {
            return new BusinessDelegateMetadata(this);
        }
    }
}

public enum BusinessDelegateType {
    STATELESS_SESSION_DELEGATE,
    STATEFUL_SESSION_DELEGATE,
    ENTITY_BEAN_DELEGATE,
    MESSAGE_DRIVEN_DELEGATE,
    COMPOSITE_DELEGATE
}
```

#### BusinessDelegateMethod

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/BusinessDelegateMethod.java
public class BusinessDelegateMethod {
    private final String methodName;
    private final String methodDescriptor;
    private final String returnType;
    private final List<String> parameters;
    private final List<EjbMethodCall> ejbMethodCalls;
    private final List<ServiceLocatorCall> serviceLocatorCalls;
    private final ExceptionHandlingPattern exceptionHandling;
    private final boolean hasParameterTransformation;
    private final boolean hasReturnValueTransformation;
    
    private BusinessDelegateMethod(Builder builder) {
        this.methodName = builder.methodName;
        this.methodDescriptor = builder.methodDescriptor;
        this.returnType = builder.returnType;
        this.parameters = Collections.unmodifiableList(builder.parameters);
        this.ejbMethodCalls = Collections.unmodifiableList(builder.ejbMethodCalls);
        this.serviceLocatorCalls = Collections.unmodifiableList(builder.serviceLocatorCalls);
        this.exceptionHandling = builder.exceptionHandling;
        this.hasParameterTransformation = builder.hasParameterTransformation;
        this.hasReturnValueTransformation = builder.hasReturnValueTransformation;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getMethodName() { return methodName; }
    public String getMethodDescriptor() { return methodDescriptor; }
    public String getReturnType() { return returnType; }
    public List<String> getParameters() { return parameters; }
    public List<EjbMethodCall> getEjbMethodCalls() { return ejbMethodCalls; }
    public List<ServiceLocatorCall> getServiceLocatorCalls() { return serviceLocatorCalls; }
    public ExceptionHandlingPattern getExceptionHandling() { return exceptionHandling; }
    public boolean hasParameterTransformation() { return hasParameterTransformation; }
    public boolean hasReturnValueTransformation() { return hasReturnValueTransformation; }
    
    public static class Builder {
        private String methodName;
        private String methodDescriptor;
        private String returnType;
        private List<String> parameters = new ArrayList<>();
        private List<EjbMethodCall> ejbMethodCalls = new ArrayList<>();
        private List<ServiceLocatorCall> serviceLocatorCalls = new ArrayList<>();
        private ExceptionHandlingPattern exceptionHandling;
        private boolean hasParameterTransformation;
        private boolean hasReturnValueTransformation;
        
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }
        
        public Builder methodDescriptor(String methodDescriptor) {
            this.methodDescriptor = methodDescriptor;
            return this;
        }
        
        public Builder returnType(String returnType) {
            this.returnType = returnType;
            return this;
        }
        
        public Builder parameters(List<String> parameters) {
            this.parameters = new ArrayList<>(parameters);
            return this;
        }
        
        public Builder ejbMethodCalls(List<EjbMethodCall> ejbMethodCalls) {
            this.ejbMethodCalls = new ArrayList<>(ejbMethodCalls);
            return this;
        }
        
        public Builder serviceLocatorCalls(List<ServiceLocatorCall> serviceLocatorCalls) {
            this.serviceLocatorCalls = new ArrayList<>(serviceLocatorCalls);
            return this;
        }
        
        public Builder exceptionHandling(ExceptionHandlingPattern exceptionHandling) {
            this.exceptionHandling = exceptionHandling;
            return this;
        }
        
        public Builder parameterTransformation(boolean hasParameterTransformation) {
            this.hasParameterTransformation = hasParameterTransformation;
            return this;
        }
        
        public Builder returnValueTransformation(boolean hasReturnValueTransformation) {
            this.hasReturnValueTransformation = hasReturnValueTransformation;
            return this;
        }
        
        public BusinessDelegateMethod build() {
            return new BusinessDelegateMethod(this);
        }
    }
}
```

### Testing Strategy

#### Unit Testing Requirements

```java
// File: src/test/java/com/analyzer/rules/ejb2spring/BusinessDelegatePatternInspectorTest.java
@ExtendWith(MockitoExtension.class)
class BusinessDelegatePatternInspectorTest {
    
    @Mock
    private GraphRepository graphRepository;
    
    private BusinessDelegatePatternInspector inspector;
    
    @BeforeEach
    void setUp() {
        inspector = new BusinessDelegatePatternInspector();
        inspector.setGraphRepository(graphRepository);
    }
    
    @Test
    void shouldDetectSimpleBusinessDelegate() {
        // Test detection of basic Business Delegate pattern
    }
    
    @Test
    void shouldAnalyzeServiceLocatorIntegration() {
        // Test Service Locator integration analysis
    }
    
    @Test
    void shouldDetectClientUsagePatterns() {
        // Test client code detection that uses Business Delegates
    }
    
    @Test
    void shouldCreateCorrectGraphNodes() {
        // Test graph node creation and relationship mapping
    }
    
    @Test
    void shouldAssessComplexityCorrectly() {
        // Test migration complexity assessment
    }
}
```

### Migration Recommendations

**Spring Boot Conversion Patterns:**

1. **Direct Service Injection**: Replace Business Delegate with direct @Service injection
2. **REST Client Templates**: Convert remote EJB calls to REST client calls
3. **Configuration Externalization**: Replace JNDI lookups with @ConfigurationProperties
4. **Exception Translation**: Convert EJB exceptions to Spring exceptions using @ControllerAdvice

### Implementation Notes

- **Performance**: Use ASM method visitor pattern for efficient bytecode analysis
- **Memory**: Cache delegate metadata to avoid reanalysis
- **Accuracy**: Cross-reference with Service Locator detection for complete analysis
- **Integration**: Coordinate with EJB component inspectors for complete dependency mapping

### Risk Mitigation

- **False Positives**: Verify delegate patterns through method analysis, not just naming
- **Complex Hierarchies**: Handle inheritance and composition patterns in delegates
- **Runtime Dependencies**: Account for dynamic proxy and reflection-based delegates
- **Legacy Code**: Handle mixed EJB versions and vendor-specific patterns

---

**Completion Status**: Task 9 Complete ✅  
**Phase 1 Status**: 9/9 Tasks Complete ✅  
**Next Phase**: Begin Phase 2 Implementation Task Definitions
