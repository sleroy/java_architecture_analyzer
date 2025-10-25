# Phase 1 Critical EJB Inspector Specifications

> **Purpose:** Detailed implementation specifications for the 6 highest-priority EJB inspectors that must be implemented first to enable basic EJB2 to Spring Boot migration.

## I-0206: CMP Field Mapping Inspector

### Overview
**Purpose:** Extract field-to-column mappings from Container Managed Persistence deployment descriptors  
**Priority:** P0 (Critical)  
**Refactoring Support:** R-240 (CMP Field → JPA Field)  
**Technology:** Java parsing (XML + source analysis)  

### Implementation Specification

#### Class Structure
```java
@Component
public class CMPFieldMappingInspector extends AbstractJavaParserInspector 
    implements GraphAwareInspector {
    
    @Override
    public String getId() { return "I-0206"; }
    
    @Override
    public Set<String> getTags() {
        return Set.of(
            InspectorTags.EJB_CMP_FIELD_MAPPING,
            InspectorTags.EJB_ENTITY_BEAN,
            InspectorTags.PERSISTENCE_METADATA
        );
    }
}
```

#### Detection Logic
1. **CMP Descriptor Analysis**: Parse vendor-specific CMP mapping files
   - Oracle TopLink: `toplink-ejb-jar.xml`
   - WebLogic: `weblogic-cmp-rdbms-jar.xml`
   - JBoss: `jbosscmp-jdbc.xml`

2. **Field Mapping Extraction**:
   ```xml
   <!-- Example WebLogic CMP mapping -->
   <field-map>
       <cmp-field>customerName</cmp-field>
       <dbms-column>CUSTOMER_NAME</dbms-column>
       <dbms-column-type>VARCHAR2</dbms-column-type>
   </field-map>
   ```

3. **Source Code Correlation**: Match CMP fields in entity bean implementation
   ```java
   // Match these abstract getter/setter patterns
   public abstract String getCustomerName();
   public abstract void setCustomerName(String name);
   ```

#### Graph Node Creation
```java
// Create enhanced CMP entity node
CMPEntityBeanNode entityNode = new CMPEntityBeanNode();
entityNode.setCmpFields(extractedFieldMappings);

// Create field mapping relationships
for (CMPFieldDescriptor field : extractedFieldMappings) {
    CMPFieldMappingNode fieldNode = new CMPFieldMappingNode();
    fieldNode.setFieldName(field.getFieldName());
    fieldNode.setColumnName(field.getColumnName());
    fieldNode.setDataType(field.getDataType());
    
    graphRepository.createEdge(entityNode, fieldNode, EJBEdgeType.CMP_FIELD_MAPPING);
}
```

#### Output Structure
```json
{
  "inspector": "I-0206",
  "findings": [
    {
      "entityBean": "com.example.CustomerBean",
      "cmpFields": [
        {
          "fieldName": "customerName",
          "columnName": "CUSTOMER_NAME", 
          "dataType": "VARCHAR2",
          "nullable": false,
          "primaryKey": false
        }
      ]
    }
  ]
}
```

---

## I-0804: Programmatic Transaction Usage Inspector

### Overview
**Purpose:** Detect advanced `UserTransaction` patterns beyond basic API usage  
**Priority:** P0 (Critical)  
**Refactoring Support:** R-621 (Programmatic TX → Declarative)  
**Technology:** Binary analysis with ASM (preferred) + Java parsing  

### Implementation Specification

#### Class Structure
```java
@Component
public class ProgrammaticTransactionUsageInspector extends AbstractASMInspector 
    implements GraphAwareInspector {
    
    @Override
    public String getId() { return "I-0804"; }
    
    @Override
    public Set<String> getTags() {
        return Set.of(
            InspectorTags.EJB_PROGRAMMATIC_TRANSACTION,
            InspectorTags.TRANSACTION_BOUNDARY,
            InspectorTags.REFACTORING_TARGET
        );
    }
}
```

#### Detection Patterns

1. **UserTransaction Usage Patterns**:
   ```java
   // Pattern 1: Basic transaction demarcation
   UserTransaction tx = context.getUserTransaction();
   tx.begin();
   // business logic
   tx.commit();
   
   // Pattern 2: Exception handling with rollback
   try {
       tx.begin();
       // business logic
       tx.commit();
   } catch (Exception e) {
       tx.rollback();
       throw e;
   }
   
   // Pattern 3: Nested transaction attempts
   if (tx.getStatus() == Status.STATUS_NO_TRANSACTION) {
       tx.begin();
   }
   ```

2. **ASM Visitor Implementation**:
   ```java
   @Override
   public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
       // Detect UserTransaction method calls
       if ("javax/transaction/UserTransaction".equals(owner)) {
           switch (name) {
               case "begin":
                   recordTransactionBoundary(TransactionBoundaryType.BEGIN);
                   break;
               case "commit":
                   recordTransactionBoundary(TransactionBoundaryType.COMMIT);
                   break;
               case "rollback":
                   recordTransactionBoundary(TransactionBoundaryType.ROLLBACK);
                   break;
               case "getStatus":
                   recordTransactionBoundary(TransactionBoundaryType.STATUS_CHECK);
                   break;
           }
       }
   }
   ```

#### Graph Node Creation
```java
// Create transaction boundary nodes for each method
TransactionBoundaryNode txNode = new TransactionBoundaryNode();
txNode.setBeanName(getCurrentClassName());
txNode.setMethodName(getCurrentMethodName());
txNode.setTransactionType(TransactionType.PROGRAMMATIC);
txNode.setTransactionPattern(detectedPattern); // SIMPLE, EXCEPTION_HANDLING, NESTED

graphRepository.addNode(txNode);
```

---

## I-0805: Container Managed Transaction Attributes Inspector

### Overview
**Purpose:** Map method-level transaction attributes from deployment descriptors  
**Priority:** P0 (Critical)  
**Refactoring Support:** R-622 (Method TX Attributes → Spring TX)  
**Technology:** Java parsing (XML analysis)  

### Implementation Specification

#### Detection Logic
1. **Parse Assembly Descriptor**:
   ```xml
   <assembly-descriptor>
     <container-transaction>
       <method>
         <ejb-name>OrderBean</ejb-name>
         <method-name>createOrder</method-name>
       </method>
       <trans-attribute>Required</trans-attribute>
     </container-transaction>
   </assembly-descriptor>
   ```

2. **Map to Spring Propagation**:
   ```java
   private SpringPropagation mapEJBToSpring(String ejbAttribute) {
       switch (ejbAttribute) {
           case "Required": return SpringPropagation.REQUIRED;
           case "RequiresNew": return SpringPropagation.REQUIRES_NEW;
           case "Supports": return SpringPropagation.SUPPORTS;
           case "NotSupported": return SpringPropagation.NOT_SUPPORTED;
           case "Never": return SpringPropagation.NEVER;
           case "Mandatory": return SpringPropagation.MANDATORY;
           default: return SpringPropagation.REQUIRED;
       }
   }
   ```

---

## I-0905: Stateful Bean Conversational State Inspector

### Overview
**Purpose:** Analyze stateful session bean state management patterns  
**Priority:** P1 (Critical)  
**Refactoring Support:** R-850 (Stateful State → External Store)  
**Technology:** Binary analysis with ASM + Java parsing for state flow  

### Implementation Specification

#### State Analysis Patterns
1. **Field Access Pattern Analysis**:
   ```java
   // Detect non-final instance fields in stateful beans
   @Override
   public void visitField(int access, String name, String descriptor, String signature, Object value) {
       if (!Modifier.isFinal(access) && !Modifier.isStatic(access)) {
           conversationalFields.add(new FieldDescriptor(name, descriptor));
       }
   }
   ```

2. **Cross-Method State Flow**:
   ```java
   // Track field writes and reads across method boundaries
   private void analyzeStateFlow() {
       Map<String, Set<String>> fieldWriters = new HashMap<>();
       Map<String, Set<String>> fieldReaders = new HashMap<>();
       
       // ASM analysis to track PUTFIELD/GETFIELD instructions
       // Build state flow graph between methods
   }
   ```

#### Graph Node Creation
```java
StatefulSessionBeanNode statefulNode = new StatefulSessionBeanNode();
statefulNode.setConversationalState(detectedFields);
statefulNode.setStateFlowPatterns(crossMethodFlows);
statefulNode.setPassivationCallbacks(passivationMethods);
statefulNode.setActivationCallbacks(activationMethods);
```

---

## I-0706: EJB Create Method Usage Inspector

### Overview
**Purpose:** Find `home.create()` patterns and parameters for client migration  
**Priority:** P1 (Critical)  
**Refactoring Support:** R-700 (EJB Create → Constructor/Factory)  
**Technology:** Binary analysis with ASM + Java parsing  

### Implementation Specification

#### Detection Patterns
1. **Home Interface Create Calls**:
   ```java
   // Pattern detection in client code
   CustomerHome home = (CustomerHome) context.lookup("CustomerHome");
   Customer customer = home.create(name, address);
   
   // ASM detection
   @Override
   public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
       if (name.startsWith("create") && isEJBHomeInterface(owner)) {
           recordCreateMethodUsage(owner, name, desc);
       }
   }
   ```

2. **Parameter Analysis**:
   ```java
   private void analyzeCreateParameters(String methodDescriptor) {
       Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
       for (Type argType : argumentTypes) {
           createParameters.add(new ParameterDescriptor(argType));
       }
   }
   ```

---

## I-0709: Business Delegate Pattern Inspector

### Overview
**Purpose:** Identify client-side EJB access patterns using business delegates  
**Priority:** P2 (Important)  
**Refactoring Support:** R-703 (Business Delegate → Direct Injection)  
**Technology:** Java parsing + Graph analysis  

### Implementation Specification

#### Pattern Recognition
1. **Delegate Class Structure**:
   ```java
   // Typical business delegate pattern
   public class OrderDelegate {
       private OrderHome orderHome;
       
       public OrderDelegate() {
           ServiceLocator locator = ServiceLocator.getInstance();
           orderHome = locator.getOrderHome();
       }
       
       public void createOrder(OrderData data) {
           try {
               Order order = orderHome.create();
               order.processOrder(data);
           } catch (RemoteException e) {
               throw new BusinessException(e);
           }
       }
   }
   ```

2. **Detection Criteria**:
   - Class name contains "Delegate" or "Facade"
   - Contains EJB home interface references
   - Wraps remote exceptions in business exceptions
   - Used by web tier or client components

## Implementation Dependencies

### Required Infrastructure Updates

#### InspectorTags Extensions
```java
public static final String EJB_CMP_FIELD_MAPPING = "ejb.cmp.fieldMapping";
public static final String EJB_PROGRAMMATIC_TRANSACTION = "ejb.transaction.programmatic";
public static final String EJB_STATEFUL_STATE = "ejb.stateful.conversationalState";
public static final String EJB_CREATE_METHOD_USAGE = "ejb.client.createMethod";
public static final String EJB_BUSINESS_DELEGATE = "ejb.client.businessDelegate";
public static final String TRANSACTION_BOUNDARY = "transaction.boundary";
```

#### Graph Node Enhancements
```java
// Add EJB-specific node types to GraphRepository
graphRepository.registerNodeType(CMPEntityBeanNode.class);
graphRepository.registerNodeType(TransactionBoundaryNode.class);
graphRepository.registerNodeType(StatefulSessionBeanNode.class);
graphRepository.registerNodeType(BusinessDelegateNode.class);
```

### Testing Strategy

#### Unit Test Coverage
- **Mock EJB Deployments**: Create test EJB applications
- **Descriptor Parsing**: Test XML parsing with various vendor formats
- **ASM Analysis**: Test bytecode pattern detection
- **Graph Integration**: Verify node creation and relationships

#### Integration Test Scenarios  
- **Real EJB Applications**: Test against actual EJB 2.x codebases
- **Multi-vendor Support**: Test WebLogic, JBoss, WebSphere descriptors
- **Performance**: Large codebase analysis benchmarks
- **End-to-end**: Inspector → Graph → Refactoring rule validation

This specification provides the foundation for implementing the most critical EJB migration inspectors that will enable the refactoring rules to successfully transform EJB applications to Spring Boot.
