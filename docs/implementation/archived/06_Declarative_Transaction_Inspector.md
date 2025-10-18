# Task 6: I-0805 Declarative Transaction Inspector

## Overview
**Priority**: High (Phase 1)  
**Inspector ID**: I-0805  
**Dependencies**: I-0401 (ejb-jar descriptor), I-0402 (assembly descriptor), I-0101/0201/0301 (bean identification)  
**Estimated Effort**: 3-4 days  
**Technology**: Java parsing, XML parsing  

## Purpose
Analyze declarative transaction attributes defined in EJB deployment descriptors (`ejb-jar.xml`) and vendor-specific descriptors. Map EJB transaction attributes to equivalent Spring `@Transactional` annotations with appropriate propagation settings and rollback rules.

## Technical Scope

### EJB Transaction Attributes to Detect
- **Required**: Start new transaction if none exists, join existing transaction
- **RequiresNew**: Always start new transaction, suspend current if exists  
- **Supports**: Join existing transaction if available, no transaction if none
- **NotSupported**: Execute without transaction, suspend current if exists
- **Mandatory**: Must have existing transaction, throw exception if none
- **Never**: Must not have transaction, throw exception if exists

### Spring @Transactional Mapping
```java
// EJB Required -> Spring default behavior
@Transactional(propagation = Propagation.REQUIRED)

// EJB RequiresNew -> Spring requires new
@Transactional(propagation = Propagation.REQUIRES_NEW)

// EJB Supports -> Spring supports
@Transactional(propagation = Propagation.SUPPORTS)

// EJB NotSupported -> Spring not supported
@Transactional(propagation = Propagation.NOT_SUPPORTED)

// EJB Mandatory -> Spring mandatory
@Transactional(propagation = Propagation.MANDATORY)

// EJB Never -> Spring never
@Transactional(propagation = Propagation.NEVER)
```

## Graph Integration

### Node Creation
- **TransactionConfigurationNode**: Represents declarative transaction configuration
  - `beanName`: EJB bean name
  - `methodName`: Method name or "*" for all methods
  - `methodParams`: Method parameter types for overloaded methods
  - `transactionAttribute`: EJB transaction attribute
  - `springPropagation`: Equivalent Spring propagation
  - `rollbackRules`: Rollback rules if specified
  - `readOnly`: Read-only optimization hint
  - `timeout`: Transaction timeout if specified

### Edge Creation
- **CONFIGURED_FOR**: TransactionConfigurationNode -> EJB bean nodes
- **APPLIES_TO**: TransactionConfigurationNode -> Method nodes

## Implementation Details

### Core Inspector Class
```java
package com.analyzer.rules.ejb2spring;

import com.analyzer.core.graph.GraphAwareInspector;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.inspectors.core.source.AbstractJavaParserInspector;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@RequiredTags({"EJB_DEPLOYMENT_DESCRIPTOR", "XML_FILE"})
@InspectorTags({"DECLARATIVE_TRANSACTION", "EJB_TRANSACTION_ATTRIBUTE", 
                "SPRING_TRANSACTIONAL_MAPPING", "TRANSACTION_CONFIGURATION"})
public class DeclarativeTransactionInspector extends AbstractJavaParserInspector 
    implements GraphAwareInspector {
    
    private GraphRepository graphRepository;
    
    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }
    
    @Override
    protected InspectorResult doInspection(ProjectFile file) {
        if (!isEjbJarXml(file)) {
            return InspectorResult.empty();
        }
        
        try {
            Document doc = parseXmlDocument(file);
            List<TransactionConfiguration> configs = 
                extractTransactionConfigurations(doc);
            
            return createResultWithConfigurations(configs, file);
            
        } catch (Exception e) {
            return InspectorResult.error("Failed to parse transaction config: " + 
                e.getMessage());
        }
    }
    
    private boolean isEjbJarXml(ProjectFile file) {
        return file.getPath().endsWith("ejb-jar.xml") ||
               file.getPath().contains("META-INF/ejb-jar.xml");
    }
}
```

### Transaction Configuration Data Structure
```java
public class TransactionConfiguration {
    private String beanName;
    private String methodName;  // "*" for all methods
    private List<String> methodParams;  // for overloaded methods
    private EJBTransactionAttribute ejbAttribute;
    private SpringPropagation springPropagation;
    private List<String> rollbackRules;
    private boolean readOnly;
    private int timeout;
    private String sourceLocation;  // descriptor file location
    
    // Getters, setters, and mapping logic
    
    public SpringPropagation mapToSpring() {
        switch (ejbAttribute) {
            case REQUIRED: return SpringPropagation.REQUIRED;
            case REQUIRES_NEW: return SpringPropagation.REQUIRES_NEW;
            case SUPPORTS: return SpringPropagation.SUPPORTS;
            case NOT_SUPPORTED: return SpringPropagation.NOT_SUPPORTED;
            case MANDATORY: return SpringPropagation.MANDATORY;
            case NEVER: return SpringPropagation.NEVER;
            default: return SpringPropagation.REQUIRED;
        }
    }
}
```

### XML Parsing Logic
```java
private List<TransactionConfiguration> extractTransactionConfigurations(Document doc) {
    List<TransactionConfiguration> configs = new ArrayList<>();
    
    // Parse container-transaction elements
    NodeList containerTransactions = doc.getElementsByTagName("container-transaction");
    
    for (int i = 0; i < containerTransactions.getLength(); i++) {
        Element containerTx = (Element) containerTransactions.item(i);
        configs.addAll(parseContainerTransaction(containerTx));
    }
    
    return configs;
}

private List<TransactionConfiguration> parseContainerTransaction(Element containerTx) {
    List<TransactionConfiguration> configs = new ArrayList<>();
    
    // Get transaction attribute
    String transAttr = getElementText(containerTx, "trans-attribute");
    EJBTransactionAttribute ejbAttr = EJBTransactionAttribute.valueOf(transAttr);
    
    // Get method elements
    NodeList methods = containerTx.getElementsByTagName("method");
    
    for (int i = 0; i < methods.getLength(); i++) {
        Element method = (Element) methods.item(i);
        
        String beanName = getElementText(method, "ejb-name");
        String methodName = getElementText(method, "method-name");
        
        // Handle method parameters for overloaded methods
        List<String> methodParams = parseMethodParams(method);
        
        TransactionConfiguration config = new TransactionConfiguration();
        config.setBeanName(beanName);
        config.setMethodName(methodName);
        config.setMethodParams(methodParams);
        config.setEjbAttribute(ejbAttr);
        config.setSpringPropagation(mapToSpringPropagation(ejbAttr));
        
        // Check for read-only hints (vendor-specific)
        config.setReadOnly(isReadOnlyMethod(methodName));
        
        configs.add(config);
    }
    
    return configs;
}

private List<String> parseMethodParams(Element method) {
    List<String> params = new ArrayList<>();
    NodeList paramElements = method.getElementsByTagName("method-param");
    
    for (int i = 0; i < paramElements.getLength(); i++) {
        params.add(paramElements.item(i).getTextContent().trim());
    }
    
    return params;
}

private boolean isReadOnlyMethod(String methodName) {
    // Heuristic for read-only methods
    return methodName.startsWith("find") || 
           methodName.startsWith("get") || 
           methodName.startsWith("is") || 
           methodName.startsWith("has") ||
           methodName.startsWith("count") ||
           methodName.startsWith("list") ||
           methodName.startsWith("search");
}
```

### Graph Node Integration
```java
private InspectorResult createResultWithConfigurations(
    List<TransactionConfiguration> configs, ProjectFile file) {
    
    InspectorResult.Builder builder = InspectorResult.builder();
    
    for (TransactionConfiguration config : configs) {
        // Create transaction configuration node
        TransactionConfigurationNode txNode = createTransactionNode(config);
        graphRepository.addNode(txNode);
        
        // Find related EJB bean node
        Optional<GraphNode> beanNode = findEjbBeanNode(config.getBeanName());
        if (beanNode.isPresent()) {
            // Create CONFIGURED_FOR edge
            GraphEdge configEdge = new GraphEdge(
                txNode.getId(), beanNode.get().getId(),
                "CONFIGURED_FOR", createEdgeProperties(config)
            );
            graphRepository.addEdge(configEdge);
        }
        
        // Add result entry
        builder.addEntry(createResultEntry(config, file));
    }
    
    return builder.build();
}

private TransactionConfigurationNode createTransactionNode(TransactionConfiguration config) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("beanName", config.getBeanName());
    properties.put("methodName", config.getMethodName());
    properties.put("methodParams", config.getMethodParams());
    properties.put("ejbTransactionAttribute", config.getEjbAttribute().name());
    properties.put("springPropagation", config.getSpringPropagation().name());
    properties.put("readOnly", config.isReadOnly());
    properties.put("timeout", config.getTimeout());
    properties.put("sourceLocation", config.getSourceLocation());
    
    return new TransactionConfigurationNode(
        generateNodeId("TX_CONFIG", config.getBeanName(), config.getMethodName()),
        properties
    );
}
```

### Result Analysis and Recommendations
```java
private InspectorResult.Entry createResultEntry(TransactionConfiguration config, ProjectFile file) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("beanName", config.getBeanName());
    metadata.put("methodName", config.getMethodName());
    metadata.put("ejbTransactionAttribute", config.getEjbAttribute().name());
    metadata.put("springPropagation", config.getSpringPropagation().name());
    metadata.put("migrationComplexity", assessMigrationComplexity(config));
    metadata.put("springAnnotation", generateSpringAnnotation(config));
    metadata.put("recommendations", generateRecommendations(config));
    
    return new InspectorResult.Entry(
        file.getPath(),
        config.getBeanName() + "." + config.getMethodName(),
        "Declarative transaction configuration found",
        metadata
    );
}

private String assessMigrationComplexity(TransactionConfiguration config) {
    // Simple direct mappings
    if (config.getEjbAttribute() == EJBTransactionAttribute.REQUIRED ||
        config.getEjbAttribute() == EJBTransactionAttribute.REQUIRES_NEW) {
        return "LOW";
    }
    
    // More complex scenarios requiring careful consideration
    if (config.getEjbAttribute() == EJBTransactionAttribute.NOT_SUPPORTED ||
        config.getEjbAttribute() == EJBTransactionAttribute.NEVER) {
        return "MEDIUM";
    }
    
    // Edge cases that need manual review
    return "HIGH";
}

private String generateSpringAnnotation(TransactionConfiguration config) {
    StringBuilder annotation = new StringBuilder("@Transactional(");
    
    // Add propagation if not default
    if (config.getSpringPropagation() != SpringPropagation.REQUIRED) {
        annotation.append("propagation = Propagation.")
                  .append(config.getSpringPropagation().name());
    }
    
    // Add read-only hint
    if (config.isReadOnly()) {
        if (annotation.length() > "@Transactional(".length()) {
            annotation.append(", ");
        }
        annotation.append("readOnly = true");
    }
    
    // Add timeout if specified
    if (config.getTimeout() > 0) {
        if (annotation.length() > "@Transactional(".length()) {
            annotation.append(", ");
        }
        annotation.append("timeout = ").append(config.getTimeout());
    }
    
    annotation.append(")");
    return annotation.toString();
}

private List<String> generateRecommendations(TransactionConfiguration config) {
    List<String> recommendations = new ArrayList<>();
    
    switch (config.getEjbAttribute()) {
        case REQUIRED:
            recommendations.add("Direct mapping to Spring @Transactional default behavior");
            break;
            
        case REQUIRES_NEW:
            recommendations.add("Use @Transactional(propagation = Propagation.REQUIRES_NEW)");
            recommendations.add("Consider performance impact of frequent transaction suspension");
            break;
            
        case NOT_SUPPORTED:
            recommendations.add("Use @Transactional(propagation = Propagation.NOT_SUPPORTED)");
            recommendations.add("Review if non-transactional behavior is still appropriate");
            break;
            
        case NEVER:
            recommendations.add("Use @Transactional(propagation = Propagation.NEVER)");
            recommendations.add("Consider if this restriction is necessary in Spring context");
            break;
            
        case SUPPORTS:
            recommendations.add("Use @Transactional(propagation = Propagation.SUPPORTS)");
            recommendations.add("Consider making transaction behavior more explicit");
            break;
            
        case MANDATORY:
            recommendations.add("Use @Transactional(propagation = Propagation.MANDATORY)");
            recommendations.add("Ensure calling code properly manages transactions");
            break;
    }
    
    if (config.isReadOnly()) {
        recommendations.add("Add readOnly = true for performance optimization");
    }
    
    return recommendations;
}
```

## Expected Results

### Detected Patterns
- **Per-method transaction attributes**: Individual method transaction requirements
- **Bean-level defaults**: Default transaction behavior for entire beans
- **Method overloading**: Multiple methods with same name but different parameters
- **Wildcard configurations**: Methods using "*" pattern matching
- **Vendor-specific extensions**: Additional transaction settings from vendor descriptors

### Generated Metadata
- **EJB transaction attribute**: Original EJB setting
- **Spring propagation mapping**: Equivalent Spring setting
- **Read-only optimization**: Performance hint for query methods
- **Migration complexity**: Assessment of conversion difficulty
- **Spring annotation**: Generated @Transactional annotation text
- **Recommendations**: Specific migration guidance

### Graph Relationships
- **CONFIGURED_FOR**: Links transaction config to EJB beans
- **APPLIES_TO**: Links transaction config to specific methods
- **CONFLICTS_WITH**: Identifies conflicting transaction settings
- **OVERRIDES**: Method-level settings overriding bean-level defaults

## Integration Points

### Dependency Resolution
- **I-0401**: Requires ejb-jar.xml parsing capability
- **I-0402**: Needs assembly descriptor analysis
- **Bean Inspectors**: Links to I-0101/0201/0301 for bean identification
- **Method Analysis**: Coordinates with method signature inspectors

### Refactoring Rule Support
- **R-110**: Provides data for adding @Transactional annotations
- **R-620**: Supports conversion of programmatic to declarative transactions
- **R-130**: Integrates with security annotation mapping

### Spring Boot Configuration
- **Transaction Manager**: Identifies need for specific transaction managers
- **JTA Support**: Flags cases requiring XA transaction support
- **Database Integration**: Links to datasource configuration requirements

## Testing Strategy

### Unit Test Cases
```java
@Test
public void testBasicTransactionAttributeMapping() {
    ProjectFile ejbJarFile = createEjbJarWithTransactionConfig(
        "TestBean", "businessMethod", "Required"
    );
    
    InspectorResult result = inspector.inspect(ejbJarFile);
    
    assertThat(result.getEntries()).hasSize(1);
    InspectorResult.Entry entry = result.getEntries().get(0);
    assertThat(entry.getMetadata().get("springPropagation")).isEqualTo("REQUIRED");
    assertThat(entry.getMetadata().get("springAnnotation"))
        .isEqualTo("@Transactional()");
}

@Test
public void testComplexTransactionConfiguration() {
    ProjectFile ejbJarFile = createEjbJarWithMethodOverloads();
    
    InspectorResult result = inspector.inspect(ejbJarFile);
    
    // Verify multiple method configurations
    assertThat(result.getEntries()).hasSize(3);
    
    // Verify read-only detection
    InspectorResult.Entry findMethod = findEntryByMethod(result, "findCustomer");
    assertThat(findMethod.getMetadata().get("readOnly")).isEqualTo(true);
}

@Test
public void testGraphNodeCreation() {
    ProjectFile ejbJarFile = createEjbJarWithTransactionConfig(
        "OrderBean", "*", "RequiresNew"
    );
    
    inspector.inspect(ejbJarFile);
    
    // Verify transaction configuration node creation
    List<GraphNode> txNodes = graphRepository.getNodesByType("TransactionConfigurationNode");
    assertThat(txNodes).hasSize(1);
    
    TransactionConfigurationNode txNode = (TransactionConfigurationNode) txNodes.get(0);
    assertThat(txNode.getBeanName()).isEqualTo("OrderBean");
    assertThat(txNode.getMethodName()).isEqualTo("*");
    assertThat(txNode.getSpringPropagation()).isEqualTo("REQUIRES_NEW");
}
```

### Integration Test Scenarios
- **Multi-bean configurations**: Complex ejb-jar.xml with multiple beans
- **Method overloading**: Same method name with different parameter types
- **Vendor descriptor integration**: WebLogic/JBoss specific transaction settings
- **Inheritance scenarios**: Transaction attributes on parent/child bean methods

## Success Criteria
- [ ] Successfully parse all standard EJB transaction attributes
- [ ] Correctly map EJB attributes to Spring propagation settings
- [ ] Generate accurate @Transactional annotation templates
- [ ] Create proper graph nodes and relationships
- [ ] Provide actionable migration recommendations
- [ ] Handle edge cases (method overloading, wildcards, conflicts)
- [ ] Pass comprehensive unit and integration tests
- [ ] Support vendor-specific transaction extensions

## Risk Mitigation
- **XML parsing failures**: Implement robust error handling and validation
- **Complex method signatures**: Handle overloaded methods with parameter matching
- **Vendor differences**: Account for application server specific variations
- **Performance concerns**: Optimize for large deployment descriptors
- **Edge case handling**: Comprehensive testing of unusual configurations
