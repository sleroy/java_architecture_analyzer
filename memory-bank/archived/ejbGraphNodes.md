# EJB Migration Graph Node Types

> **Purpose:** Define typed graph nodes for EJB2 to Spring Boot migration that extend the base `GraphNode` system to support refactoring pattern analysis.

## Core EJB Node Types

### Session Bean Nodes
```java
// Base session bean node
EJBSessionBeanNode extends BaseGraphNode {
    - beanName: String
    - sessionType: SessionType (STATEFUL, STATELESS)
    - transactionType: TransactionType (CONTAINER, BEAN)
    - remoteInterface: String (FQN)
    - localInterface: String (FQN) 
    - homeInterface: String (FQN)
    - implementationClass: String (FQN)
    - jndiName: String
}

// Specific stateful session bean
StatefulSessionBeanNode extends EJBSessionBeanNode {
    - conversationalState: List<FieldDescriptor>
    - passivationCallbacks: List<MethodDescriptor>
    - activationCallbacks: List<MethodDescriptor>
    - sessionTimeout: Integer
}

// Specific stateless session bean  
StatelessSessionBeanNode extends EJBSessionBeanNode {
    - poolSize: Integer
    - concurrentAccessPattern: AccessPattern
}
```

### Entity Bean Nodes
```java
// Base entity bean node
EJBEntityBeanNode extends BaseGraphNode {
    - beanName: String
    - persistenceType: PersistenceType (CMP, BMP)
    - primaryKeyClass: String (FQN)
    - tableName: String
    - dataSourceRef: String
    - transactionType: TransactionType
}

// Container Managed Persistence entity
CMPEntityBeanNode extends EJBEntityBeanNode {
    - cmpFields: List<CMPFieldDescriptor>
    - cmrRelationships: List<CMRRelationshipDescriptor>
    - ejbqlQueries: List<EJBQLQueryDescriptor>
    - finderMethods: List<FinderMethodDescriptor>
    - cascadeOperations: List<CascadeDescriptor>
}

// Bean Managed Persistence entity
BMPEntityBeanNode extends EJBEntityBeanNode {
    - jdbcPatterns: List<JDBCPatternDescriptor>
    - dataAccessMethods: List<MethodDescriptor>
    - sqlStatements: List<SQLStatementDescriptor>
}
```

### Message-Driven Bean Nodes
```java
// Message-driven bean node
EJBMessageDrivenBeanNode extends BaseGraphNode {
    - beanName: String
    - destinationType: DestinationType (QUEUE, TOPIC)
    - destinationName: String
    - messageSelector: String
    - acknowledgeMode: AcknowledgeMode
    - transactionType: TransactionType
    - maxPoolSize: Integer
    - activationConfig: Map<String, String>
}
```

## EJB Infrastructure Nodes

### Transaction Nodes
```java
// Transaction boundary node
TransactionBoundaryNode extends BaseGraphNode {
    - beanName: String
    - methodName: String
    - transactionAttribute: TransactionAttribute (REQUIRED, REQUIRES_NEW, etc.)
    - rollbackRules: List<String> (exception FQNs)
    - readOnly: Boolean
    - timeout: Integer
    - isolationLevel: IsolationLevel
}

// Transaction context node (for nested/distributed TX)
TransactionContextNode extends BaseGraphNode {
    - contextType: ContextType (NESTED, DISTRIBUTED, COMPENSATING)
    - participants: List<String> (bean FQNs)
    - coordinatorType: CoordinatorType (JTA, XA, SAGA)
}
```

### JNDI and Lookup Nodes  
```java
// JNDI lookup node
JNDILookupNode extends BaseGraphNode {
    - jndiName: String
    - targetBeanName: String
    - targetType: LookupType (EJB_HOME, EJB_LOCAL_HOME, DATASOURCE, JMS_DEST)
    - lookupPattern: LookupPattern (DIRECT, SERVICE_LOCATOR, DEPENDENCY_INJECTION)
    - callerClass: String (FQN)
    - callerMethod: String
}

// Service locator pattern node
ServiceLocatorNode extends BaseGraphNode {
    - locatorClass: String (FQN)
    - cachedLookups: List<String> (JNDI names)
    - lookupMethods: List<MethodDescriptor>
    - clientClasses: List<String> (FQNs of classes using this locator)
}
```

### Data Source and Resource Nodes
```java
// Data source node
DataSourceNode extends BaseGraphNode {
    - resourceName: String
    - jndiName: String
    - dataSourceType: DataSourceType (JTA, NON_JTA)
    - driverClass: String
    - connectionUrl: String
    - poolingConfig: PoolingConfigDescriptor
    - transactionIsolation: IsolationLevel
}

// JMS destination node
JMSDestinationNode extends BaseGraphNode {
    - destinationName: String
    - jndiName: String
    - destinationType: DestinationType (QUEUE, TOPIC)
    - connectionFactory: String
    - durability: Boolean (for topics)
}
```

## Client and Integration Nodes

### EJB Client Pattern Nodes
```java
// Business delegate node
BusinessDelegateNode extends BaseGraphNode {
    - delegateClass: String (FQN)
    - targetEjbHome: String (JNDI name)
    - serviceLocatorRef: String (FQN)
    - clientMethods: List<MethodDescriptor>
    - exceptionHandling: ExceptionHandlingPattern
}

// EJB reference node (for web tier)
EJBReferenceNode extends BaseGraphNode {
    - refName: String
    - refType: ReferenceType (REMOTE, LOCAL)
    - homeInterface: String (FQN)
    - remoteInterface: String (FQN)
    - ejbLink: String (bean name)
    - jndiName: String
}
```

### Relationship and Dependency Nodes
```java
// CMR relationship node
CMRRelationshipNode extends BaseGraphNode {
    - relationshipName: String
    - sourceBean: String (FQN)
    - targetBean: String (FQN)
    - cardinality: Cardinality (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY)
    - cascadeDelete: Boolean
    - bidirectional: Boolean
    - foreignKeyColumns: List<String>
}

// Call dependency node (method calls between EJBs)
EJBCallDependencyNode extends BaseGraphNode {
    - sourceBean: String (FQN)
    - sourceMethod: String
    - targetBean: String (FQN)
    - targetMethod: String
    - callType: CallType (BUSINESS_METHOD, CREATE, REMOVE, FINDER)
    - transactionPropagation: TransactionAttribute
}
```

## Vendor-Specific Nodes

### Application Server Configuration
```java
// Vendor configuration node (base)
VendorConfigurationNode extends BaseGraphNode {
    - vendorType: VendorType (WEBLOGIC, WEBSPHERE, JBOSS, GLASSFISH)
    - configFile: String (path)
    - bindings: Map<String, String> (bean name -> JNDI name)
    - vendorSpecificSettings: Map<String, Object>
}

// Clustering configuration node
ClusteringConfigurationNode extends BaseGraphNode {
    - clusterName: String
    - loadBalancingPolicy: LoadBalancingPolicy
    - failoverConfig: FailoverConfigDescriptor
    - replicationConfig: ReplicationConfigDescriptor
    - affectedBeans: List<String> (bean FQNs)
}
```

## Migration Analysis Nodes

### Service Boundary Analysis
```java
// Microservice boundary candidate node
ServiceBoundaryNode extends BaseGraphNode {
    - boundaryName: String
    - includedBeans: List<String> (bean FQNs)
    - businessCapability: String
    - dataOwnership: List<String> (entity FQNs)
    - incomingDependencies: List<DependencyDescriptor>
    - outgoingDependencies: List<DependencyDescriptor>
    - cohesionScore: Double
    - couplingScore: Double
}

// Configuration externalization node
ConfigurationExternalizationNode extends BaseGraphNode {
    - configKey: String
    - currentValue: String
    - valueSource: ValueSource (HARDCODED, ENV_ENTRY, PROPERTIES_FILE)
    - usageLocations: List<UsageLocation>
    - migrationTarget: MigrationTarget (APPLICATION_YML, CONFIG_PROPERTIES)
}
```

## Performance and Caching Nodes

### Caching Pattern Nodes
```java
// EJB caching pattern node
EJBCachingPatternNode extends BaseGraphNode {
    - beanName: String (FQN)
    - cachingStrategy: CachingStrategy (METHOD_LEVEL, INSTANCE_LEVEL, QUERY_LEVEL)
    - cacheSize: Integer
    - evictionPolicy: EvictionPolicy
    - timeToLive: Integer
    - cacheKeys: List<String>
}

// Performance anti-pattern node
PerformanceAntiPatternNode extends BaseGraphNode {
    - patternType: AntiPatternType (CHATTY_INTERFACE, N_PLUS_ONE, EAGER_LOADING)
    - severity: Severity (LOW, MEDIUM, HIGH, CRITICAL)
    - affectedComponents: List<String> (component FQNs)
    - suggestedRefactoring: String (refactoring rule ID)
}
```

## Node Relationships and Edges

### Standard Edge Types
```java
// EJB-specific edge types extending GraphEdge
enum EJBEdgeType {
    IMPLEMENTS_INTERFACE,    // Bean -> Interface
    EXTENDS_HOME,           // Interface -> EJBHome/EJBLocalHome  
    JNDI_LOOKUP,           // Client -> Bean (via JNDI)
    CMR_RELATIONSHIP,      // Entity -> Entity (CMR)
    TRANSACTION_BOUNDARY,  // Method -> Transaction
    DATA_ACCESS,           // Bean -> DataSource
    MESSAGE_CONSUMPTION,   // MDB -> JMS Destination
    SERVICE_LOCATION,      // Client -> ServiceLocator
    BUSINESS_DELEGATION,   // Client -> BusinessDelegate
    VENDOR_BINDING,        // Bean -> VendorConfiguration
    MIGRATION_TARGET       // Current Component -> Target Component
}
```

## Node Creation Patterns

### Inspector to Node Mapping
```
I-0101 (identify_session_bean_impl) -> EJBSessionBeanNode
I-0201 (identify_entity_bean_impl) -> EJBEntityBeanNode  
I-0206 (identify_cmp_field_mapping) -> CMPEntityBeanNode (enhanced)
I-0301 (identify_mdb_impl) -> EJBMessageDrivenBeanNode
I-0403 (identify_method_transaction_attribute) -> TransactionBoundaryNode
I-0701 (identify_jndi_lookup) -> JNDILookupNode
I-0702 (identify_service_locator_pattern) -> ServiceLocatorNode
I-0703 (identify_datasource_ref) -> DataSourceNode
```

### Node Enhancement Process
1. **Basic Detection**: Create base node with core properties
2. **Relationship Analysis**: Add edges to related nodes
3. **Pattern Recognition**: Enhance with pattern-specific properties
4. **Migration Readiness**: Add refactoring rule recommendations
5. **Dependency Analysis**: Build comprehensive dependency graph

## Usage in Refactoring Rules

### Graph Query Examples
```java
// Find all stateful session beans with conversational state
List<StatefulSessionBeanNode> statefulBeans = 
    graphRepository.findNodesByType(StatefulSessionBeanNode.class)
        .filter(bean -> !bean.getConversationalState().isEmpty());

// Find CMR relationships that need JPA conversion  
List<CMRRelationshipNode> cmrRelations =
    graphRepository.findNodesByType(CMRRelationshipNode.class)
        .filter(rel -> rel.getCardinality() == MANY_TO_MANY);

// Find service boundary candidates
List<ServiceBoundaryNode> boundaries = 
    graphRepository.findNodesByType(ServiceBoundaryNode.class)
        .filter(boundary -> boundary.getCohesionScore() > 0.7);
```

This graph model provides the foundation for sophisticated EJB migration analysis and enables the refactoring rules to make informed decisions based on comprehensive architectural understanding.
