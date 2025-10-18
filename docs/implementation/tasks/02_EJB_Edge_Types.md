# Task 2: EJB Edge Types and Relationships

**ID:** Task 2  
**Priority:** P0 (Critical - Foundation)  
**Estimated Effort:** 2-3 hours  
**Prerequisites:** Task 1 (EJB Graph Nodes) complete  
**Deliverables:** EJB relationship modeling system  

## Overview

Define and implement specialized graph edge types to represent relationships between EJB components. These edges will capture the complex interactions and dependencies that exist in EJB applications, enabling comprehensive migration analysis.

## Technical Requirements

### Core Edge Types to Implement

1. **EJB_IMPLEMENTS** - Bean implements Home/Remote/Local interface
2. **EJB_USES_HOME** - Component uses Home interface for lookups  
3. **EJB_CALLS_BUSINESS** - Cross-EJB business method invocation
4. **CMR_RELATIONSHIP** - Container managed relationship between entities
5. **EJB_TRANSACTION_BOUNDARY** - Transaction propagation between components
6. **EJB_SECURITY_DEPENDENCY** - Security role propagation
7. **JNDI_LOOKUP_DEPENDENCY** - JNDI lookup dependencies
8. **EJB_CREATE_DEPENDENCY** - Home interface create method usage
9. **EJB_FINDER_DEPENDENCY** - Entity finder method usage
10. **MESSAGE_DESTINATION_BINDING** - MDB to JMS destination binding

### Implementation Specifications

#### Base EJB Edge Class

```java
// File: src/main/java/com/analyzer/core/graph/EJBRelationshipEdge.java
public abstract class EJBRelationshipEdge extends GraphEdge {
    private final String relationshipType;
    private final Map<String, String> relationshipAttributes;
    private final String sourceContext;
    private final String targetContext;
    private final int strength; // Relationship strength (1-10)
    
    protected EJBRelationshipEdge(String edgeId, String edgeType, 
            String sourceNodeId, String targetNodeId, String relationshipType) {
        super(edgeId, edgeType, sourceNodeId, targetNodeId);
        this.relationshipType = relationshipType;
        this.relationshipAttributes = new HashMap<>();
        this.strength = calculateRelationshipStrength();
    }
    
    public abstract String getRelationshipDescription();
    public abstract boolean isMigrationCritical();
    public abstract Set<String> getRequiredTransformations();
    protected abstract int calculateRelationshipStrength();
}
```

#### Specific Edge Implementations

**EJB Interface Implementation Edge:**
```java
// File: src/main/java/com/analyzer/core/graph/EJBImplementsEdge.java
public class EJBImplementsEdge extends EJBRelationshipEdge {
    private final String interfaceType; // "HOME", "REMOTE", "LOCAL"
    private final Set<String> implementedMethods;
    private final boolean isRequired;
    
    public EJBImplementsEdge(String edgeId, String sourceNodeId, String targetNodeId, 
            String interfaceType) {
        super(edgeId, "EJB_IMPLEMENTS", sourceNodeId, targetNodeId, "IMPLEMENTS");
        this.interfaceType = interfaceType;
        this.implementedMethods = new HashSet<>();
        this.isRequired = true;
    }
    
    @Override
    public String getRelationshipDescription() {
        return String.format("EJB Bean implements %s interface", interfaceType);
    }
    
    @Override
    public boolean isMigrationCritical() {
        return true; // Interface implementations must be refactored
    }
    
    @Override
    public Set<String> getRequiredTransformations() {
        return Set.of("REMOVE_EJB_INTERFACE", "CREATE_SPRING_SERVICE");
    }
    
    @Override
    protected int calculateRelationshipStrength() {
        return 9; // Very strong - structural dependency
    }
}
```

**Container Managed Relationship Edge:**
```java
// File: src/main/java/com/analyzer/core/graph/CMRRelationshipEdge.java
public class CMRRelationshipEdge extends EJBRelationshipEdge {
    private final String relationshipName;
    private final String cardinality; // "ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_MANY"
    private final String mappedBy;
    private final boolean cascadeDelete;
    private final String foreignKeyColumn;
    
    public CMRRelationshipEdge(String edgeId, String sourceNodeId, String targetNodeId,
            String relationshipName, String cardinality) {
        super(edgeId, "CMR_RELATIONSHIP", sourceNodeId, targetNodeId, "CMR");
        this.relationshipName = relationshipName;
        this.cardinality = cardinality;
        this.cascadeDelete = false;
    }
    
    @Override
    public String getRelationshipDescription() {
        return String.format("CMR %s relationship: %s", cardinality, relationshipName);
    }
    
    @Override
    public boolean isMigrationCritical() {
        return true; // CMR must be converted to JPA associations
    }
    
    @Override
    public Set<String> getRequiredTransformations() {
        return Set.of("CONVERT_TO_JPA_ASSOCIATION", "ADD_JPA_ANNOTATIONS");
    }
    
    @Override
    protected int calculateRelationshipStrength() {
        return 8; // Strong - data relationship
    }
}
```

**Business Method Call Edge:**
```java
// File: src/main/java/com/analyzer/core/graph/EJBBusinessCallEdge.java
public class EJBBusinessCallEdge extends EJBRelationshipEdge {
    private final String methodName;
    private final String callType; // "LOCAL", "REMOTE", "HOME"
    private final String transactionContext;
    private final boolean isInSameTransaction;
    private final int callFrequency; // Estimated calls per execution
    
    public EJBBusinessCallEdge(String edgeId, String sourceNodeId, String targetNodeId,
            String methodName, String callType) {
        super(edgeId, "EJB_CALLS_BUSINESS", sourceNodeId, targetNodeId, "BUSINESS_CALL");
        this.methodName = methodName;
        this.callType = callType;
        this.callFrequency = 1;
    }
    
    @Override
    public String getRelationshipDescription() {
        return String.format("%s call to %s", callType, methodName);
    }
    
    @Override
    public boolean isMigrationCritical() {
        return "REMOTE".equals(callType); // Remote calls need special handling
    }
    
    @Override
    public Set<String> getRequiredTransformations() {
        if ("REMOTE".equals(callType)) {
            return Set.of("CONVERT_TO_REST_CALL", "ADD_SERVICE_CLIENT");
        }
        return Set.of("CONVERT_TO_DEPENDENCY_INJECTION");
    }
    
    @Override
    protected int calculateRelationshipStrength() {
        return "REMOTE".equals(callType) ? 6 : 4; // Remote calls are stronger dependencies
    }
}
```

**JNDI Lookup Dependency Edge:**
```java
// File: src/main/java/com/analyzer/core/graph/JNDILookupEdge.java
public class JNDILookupEdge extends EJBRelationshipEdge {
    private final String jndiName;
    private final String lookupContext; // Method or field where lookup occurs
    private final boolean isStaticLookup;
    private final String lookupPattern; // Pattern used for lookup
    
    public JNDILookupEdge(String edgeId, String sourceNodeId, String targetNodeId,
            String jndiName) {
        super(edgeId, "JNDI_LOOKUP_DEPENDENCY", sourceNodeId, targetNodeId, "JNDI_LOOKUP");
        this.jndiName = jndiName;
        this.isStaticLookup = false;
    }
    
    @Override
    public String getRelationshipDescription() {
        return String.format("JNDI lookup: %s", jndiName);
    }
    
    @Override
    public boolean isMigrationCritical() {
        return true; // All JNDI lookups must be replaced with DI
    }
    
    @Override
    public Set<String> getRequiredTransformations() {
        return Set.of("REPLACE_WITH_DEPENDENCY_INJECTION", "REMOVE_JNDI_LOOKUP");
    }
    
    @Override
    protected int calculateRelationshipStrength() {
        return 7; // Strong - runtime dependency
    }
}
```

### Edge Property Specifications

#### Common Properties
- **edgeId**: Unique identifier for the relationship
- **relationshipType**: Type of EJB relationship
- **relationshipAttributes**: Additional metadata
- **sourceContext**: Context where relationship originates
- **targetContext**: Context where relationship targets
- **strength**: Numeric strength indicator (1-10)

#### Interface Implementation Properties
- **interfaceType**: HOME, REMOTE, LOCAL
- **implementedMethods**: Set of method signatures
- **isRequired**: Whether implementation is mandatory

#### CMR Relationship Properties
- **relationshipName**: EJB-QL relationship name
- **cardinality**: Relationship cardinality
- **mappedBy**: Relationship mapping attribute
- **cascadeDelete**: Whether cascading delete is enabled
- **foreignKeyColumn**: Database foreign key column

#### Business Call Properties
- **methodName**: Called method name
- **callType**: LOCAL, REMOTE, HOME
- **transactionContext**: Transaction propagation info
- **callFrequency**: Estimated call frequency

## Implementation Tasks

### Step 1: Create Base Edge Classes (1 hour)
1. Create `EJBRelationshipEdge` abstract base class
2. Define common relationship properties and methods
3. Implement relationship strength calculation framework

### Step 2: Implement Core Relationship Edges (1 hour)
1. Create `EJBImplementsEdge` for interface implementations
2. Create `CMRRelationshipEdge` for entity relationships
3. Create `EJBBusinessCallEdge` for business method calls

### Step 3: Implement Lookup and Dependency Edges (30-45 minutes)
1. Create `JNDILookupEdge` for JNDI dependencies
2. Create `EJBCreateDependencyEdge` for create method usage
3. Create `MessageDestinationBindingEdge` for MDB bindings

### Step 4: Integration and Testing (30-45 minutes)
1. Update `GraphRepository` to support new edge types
2. Add edge factory methods to `GraphEdge` interface
3. Create unit tests for each edge type

## File Structure

```
src/main/java/com/analyzer/core/graph/
├── EJBRelationshipEdge.java         # Base EJB edge class
├── EJBImplementsEdge.java           # Interface implementation edge
├── CMRRelationshipEdge.java         # CMR relationship edge
├── EJBBusinessCallEdge.java         # Business method call edge
├── JNDILookupEdge.java             # JNDI lookup dependency edge
├── EJBCreateDependencyEdge.java    # Create method dependency edge
├── EJBFinderDependencyEdge.java    # Finder method dependency edge
├── TransactionBoundaryEdge.java    # Transaction boundary edge
├── SecurityDependencyEdge.java     # Security dependency edge
└── MessageDestinationBindingEdge.java # MDB destination binding edge
```

## Testing Requirements

### Unit Tests
- Test edge creation and property management
- Test relationship strength calculations
- Test transformation requirement determination

### Integration Tests
- Test edge integration with EJB nodes
- Test graph traversal with EJB edges
- Test migration analysis using edge types

## Success Criteria

- [ ] All 10 EJB edge types implemented and tested
- [ ] Edge properties capture complete relationship semantics
- [ ] Relationship strength calculations are accurate
- [ ] Integration with existing graph infrastructure is seamless
- [ ] 100% unit test coverage for new edge classes

## Implementation Prompt

Use this specification to implement the EJB relationship edge system. Focus on:

1. **Complete Relationship Modeling**: Capture all aspects of EJB relationships needed for migration analysis
2. **Migration-Aware Design**: Each edge type should indicate required transformations
3. **Performance Optimization**: Efficient edge creation and traversal for large applications
4. **Extensibility**: Support for additional EJB patterns and vendor-specific relationships

The edges should provide sufficient information for Spring Boot migration refactoring rules to determine appropriate transformations and generate migration recommendations.

## Integration with Inspectors

These edge types will be created by various EJB inspectors:

- **I-0206 (CMP Field Mapping)**: Creates `CMRRelationshipEdge`
- **I-0706 (EJB Create Method Usage)**: Creates `EJBCreateDependencyEdge`
- **I-0701 (JNDI Lookup)**: Creates `JNDILookupEdge`
- **Session Bean Inspectors**: Create `EJBImplementsEdge` and `EJBBusinessCallEdge`
- **Entity Bean Inspectors**: Create `CMRRelationshipEdge`

The edge system provides the foundation for comprehensive EJB dependency analysis and migration planning.
