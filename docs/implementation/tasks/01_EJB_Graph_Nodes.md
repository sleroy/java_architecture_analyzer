# Task 1: EJB Graph Node Implementation

**ID:** Task 1  
**Priority:** P0 (Critical - Foundation)  
**Estimated Effort:** 4-6 hours  
**Prerequisites:** None  
**Deliverables:** Complete EJB graph node type system  

## Overview

Implement specialized graph node types for EJB components to enable comprehensive migration analysis. These nodes will represent different EJB artifacts and their relationships in the codebase graph.

## Technical Requirements

### Core Node Types to Implement

1. **CMPEntityBeanNode** - Container Managed Persistence entity beans
2. **BMPEntityBeanNode** - Bean Managed Persistence entity beans  
3. **StatelessSessionBeanNode** - Stateless session beans
4. **StatefulSessionBeanNode** - Stateful session beans
5. **MessageDrivenBeanNode** - Message-driven beans
6. **EJBHomeInterfaceNode** - EJB home interfaces
7. **EJBRemoteInterfaceNode** - EJB remote interfaces
8. **EJBLocalInterfaceNode** - EJB local interfaces
9. **PrimaryKeyClassNode** - Entity bean primary key classes
10. **EJBDescriptorNode** - Deployment descriptor configurations

### Implementation Specifications

#### Base EJB Node Class

```java
// File: src/main/java/com/analyzer/core/graph/EJBComponentNode.java
public abstract class EJBComponentNode extends BaseGraphNode {
    private final String ejbName;
    private final String jndiName;
    private final Map<String, String> deploymentAttributes;
    private final Set<String> transactionAttributes;
    private final Set<String> securityRoles;
    
    protected EJBComponentNode(String nodeId, String nodeType, String ejbName) {
        super(nodeId, nodeType);
        this.ejbName = ejbName;
        this.deploymentAttributes = new HashMap<>();
        this.transactionAttributes = new HashSet<>();
        this.securityRoles = new HashSet<>();
    }
    
    // Getters and setters for EJB-specific properties
    public abstract String getComponentType();
    public abstract boolean requiresTransactionManagement();
    public abstract Set<String> getSupportedInterfaces();
}
```

#### Specific Node Implementations

**CMP Entity Bean Node:**
```java
// File: src/main/java/com/analyzer/core/graph/CMPEntityBeanNode.java
public class CMPEntityBeanNode extends EJBComponentNode {
    private final String abstractSchemaName;
    private final Map<String, String> cmpFields;
    private final Map<String, CMRRelationship> cmrRelationships;
    private final String primaryKeyClass;
    private final Map<String, String> finderMethods;
    
    public CMPEntityBeanNode(String nodeId, String ejbName) {
        super(nodeId, "CMP_ENTITY_BEAN", ejbName);
        this.cmpFields = new HashMap<>();
        this.cmrRelationships = new HashMap<>();
        this.finderMethods = new HashMap<>();
    }
    
    @Override
    public String getComponentType() { return "CMP Entity Bean"; }
    
    @Override
    public boolean requiresTransactionManagement() { return true; }
    
    @Override
    public Set<String> getSupportedInterfaces() {
        return Set.of("EntityObject", "EJBLocalObject");
    }
    
    // CMP-specific methods
    public void addCMPField(String fieldName, String columnMapping) {
        cmpFields.put(fieldName, columnMapping);
    }
    
    public void addCMRRelationship(String relationshipName, CMRRelationship relationship) {
        cmrRelationships.put(relationshipName, relationship);
    }
}
```

**Session Bean Nodes:**
```java
// File: src/main/java/com/analyzer/core/graph/StatefulSessionBeanNode.java
public class StatefulSessionBeanNode extends EJBComponentNode {
    private final Set<String> conversationalFields;
    private final Map<String, String> activationConfig;
    private final boolean requiresPassivation;
    
    public StatefulSessionBeanNode(String nodeId, String ejbName) {
        super(nodeId, "STATEFUL_SESSION_BEAN", ejbName);
        this.conversationalFields = new HashSet<>();
        this.activationConfig = new HashMap<>();
        this.requiresPassivation = false;
    }
    
    @Override
    public String getComponentType() { return "Stateful Session Bean"; }
    
    @Override
    public boolean requiresTransactionManagement() { return true; }
    
    public void addConversationalField(String fieldName) {
        conversationalFields.add(fieldName);
    }
}

// File: src/main/java/com/analyzer/core/graph/StatelessSessionBeanNode.java  
public class StatelessSessionBeanNode extends EJBComponentNode {
    private final Set<String> businessMethods;
    private final Map<String, String> poolingConfig;
    
    public StatelessSessionBeanNode(String nodeId, String ejbName) {
        super(nodeId, "STATELESS_SESSION_BEAN", ejbName);
        this.businessMethods = new HashSet<>();
        this.poolingConfig = new HashMap<>();
    }
    
    @Override
    public String getComponentType() { return "Stateless Session Bean"; }
    
    @Override
    public boolean requiresTransactionManagement() { return true; }
}
```

### Node Property Specifications

#### Common Properties
- **nodeId**: Unique identifier (typically FQN of implementation class)
- **ejbName**: EJB logical name from deployment descriptor
- **jndiName**: JNDI binding name for lookups
- **transactionAttributes**: Per-method transaction requirements
- **securityRoles**: Security roles and method permissions
- **deploymentAttributes**: Vendor-specific deployment settings

#### Entity Bean Specific Properties
- **abstractSchemaName**: EJB-QL schema name
- **cmpFields**: Map of field name to database column mapping
- **cmrRelationships**: Container managed relationships
- **primaryKeyClass**: Primary key class reference
- **finderMethods**: Custom finder method definitions

#### Session Bean Specific Properties
- **conversationalFields**: Fields maintaining conversational state (stateful only)
- **businessMethods**: Exposed business method signatures
- **poolingConfig**: Container pooling configuration

#### Message-Driven Bean Properties
- **destinationType**: Queue or Topic
- **destinationName**: JMS destination name
- **acknowledgeMode**: JMS acknowledgment mode
- **messageSelector**: JMS message selector

## Implementation Tasks

### Step 1: Create Base Classes (1-2 hours)
1. Create `EJBComponentNode` abstract base class
2. Define common EJB properties and methods
3. Implement serialization support for graph persistence

### Step 2: Implement Entity Bean Nodes (1-2 hours)
1. Create `CMPEntityBeanNode` with CMP-specific properties
2. Create `BMPEntityBeanNode` with BMP-specific properties
3. Implement CMR relationship modeling classes

### Step 3: Implement Session Bean Nodes (1 hour)
1. Create `StatefulSessionBeanNode` with conversational state tracking
2. Create `StatelessSessionBeanNode` with business method tracking
3. Add transaction and security attribute support

### Step 4: Implement Interface and Support Nodes (1 hour)
1. Create interface nodes (Home, Remote, Local)
2. Create `PrimaryKeyClassNode` for entity key classes
3. Create `EJBDescriptorNode` for deployment descriptors

### Step 5: Integration and Testing (30-60 minutes)
1. Update `GraphRepository` to support new node types
2. Add node factory methods to `GraphNode` interface
3. Create unit tests for each node type

## File Structure

```
src/main/java/com/analyzer/core/graph/
├── EJBComponentNode.java           # Base EJB node class  
├── CMPEntityBeanNode.java          # CMP entity bean node
├── BMPEntityBeanNode.java          # BMP entity bean node
├── StatefulSessionBeanNode.java    # Stateful session bean node
├── StatelessSessionBeanNode.java   # Stateless session bean node  
├── MessageDrivenBeanNode.java      # MDB node
├── EJBHomeInterfaceNode.java       # Home interface node
├── EJBRemoteInterfaceNode.java     # Remote interface node
├── EJBLocalInterfaceNode.java      # Local interface node
├── PrimaryKeyClassNode.java        # Primary key class node
├── EJBDescriptorNode.java          # Deployment descriptor node
└── CMRRelationship.java            # CMR relationship model class
```

## Testing Requirements

### Unit Tests
- Test node creation and property management
- Test serialization/deserialization
- Test node comparison and equality

### Integration Tests  
- Test node integration with existing graph system
- Test node creation from inspector results
- Test graph traversal with EJB nodes

## Success Criteria

- [ ] All 10 EJB node types implemented and tested
- [ ] Node properties accurately model EJB deployment descriptors
- [ ] Seamless integration with existing graph infrastructure
- [ ] 100% unit test coverage for new node classes
- [ ] Performance benchmarks showing <10ms node creation time

## Implementation Prompt

Use this specification to implement the EJB graph node system. Focus on:
1. Clean inheritance hierarchy with proper abstraction
2. Complete property modeling for migration analysis
3. Efficient memory usage for large enterprise applications
4. Extensibility for future EJB patterns and vendor extensions

The nodes should capture all information needed for Spring Boot migration analysis and refactoring rule application.
