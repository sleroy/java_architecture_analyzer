# EJB to Spring Boot Migration Analysis Prompt

Use this prompt with any AI coding assistant to analyze EJB classes for migration. The assistant will generate a structured report identifying migration concerns, patterns, and recommendations.

---

## Prompt

```
Analyze the following EJB class for migration to Spring Boot. Provide a structured report identifying migration concerns, patterns, and recommendations in generic terms.

CLASS TO ANALYZE:
[PASTE CLASS SOURCE CODE]

ANALYSIS REQUIRED:

1. **EJB TYPE & BASIC CHARACTERISTICS**
   - Bean type: @Stateless, @Stateful, or @MessageDriven?
   - Lines of code: [COUNT]
   - Number of methods: [COUNT]
   - Number of field dependencies: [COUNT]
   - Interfaces implemented: [LIST]

2. **INHERITANCE ANALYSIS** ⚠️ CRITICAL FOR MIGRATION
   
   **Class Hierarchy**:
   - Extends: [Fully Qualified Name of parent class, or "java.lang.Object" if none]
   - Parent class is: EJB | Regular Java class | Abstract class | Framework class
   - Inheritance depth: [number of levels]
   
   **Parent Class Analysis** (if extends something other than Object):
   ```
   Parent Class: [Name]
   Location: [package/path if available]
   Type: @Stateless | Abstract Base | POJO | Unknown
   
   Inherited Fields:
   - [field name]: [type] [@annotations]
   - ...
   
   Inherited Methods:
   - [method name]([params]): [return type]
   - ...
   
   Protected/Public Members Used by Child:
   - [list members actually used in the analyzed class]
   ```
   
   **Inheritance Pattern Assessment**:
   - Pattern Type: 
     * BASE_DAO (common data access operations)
     * BASE_SERVICE (common business logic)
     * ABSTRACT_EJB (EJB lifecycle management)
     * UTILITY_BASE (shared helper methods)
     * OTHER (describe)
   
   - Shared Functionality:
     * Common CRUD operations? YES/NO
     * Transaction management? YES/NO
     * Security checks? YES/NO
     * Logging/auditing? YES/NO
     * Validation logic? YES/NO
     * Other: [describe]
   
   - Parent Dependencies:
     * Does parent have @EJB fields? YES/NO (list them)
     * Does parent have @Resource fields? YES/NO (list them)
     * Does parent have @PersistenceContext? YES/NO
     * Other injections? [list]
   
   **Refactoring Recommendation**:
   - Strategy: COMPOSITION | EXTRACTION | DELEGATION | MIXINS
   - Complexity: SIMPLE | MODERATE | COMPLEX
   - Reason: [explain why this strategy]
   
   **Composition Refactoring Plan**:
   ```
   RECOMMENDED APPROACH:
   
   1. Create dedicated service/component for shared functionality:
      - New class name: [suggested name, e.g., CommonDataAccessService]
      - Annotation: @Service or @Component
      - Methods to extract: [list inherited methods used]
   
   2. Inject into child class:
      - Field: private final [ServiceName] [serviceName];
      - Constructor injection: Add to constructor parameters
   
   3. Update method calls:
      - Replace: super.methodName() 
      - With: serviceName.methodName()
   
   4. Handle parent dependencies:
      [List how to migrate parent's @EJB/@Resource fields]
   ```
   
   **Migration Impact**:
   - Affects other classes: [YES/NO]
   - Other classes extending same parent: [list them if known]
   - Cascading migrations needed: [YES/NO]
   - Priority: HIGH (must migrate parent first) | MEDIUM | LOW
   
   **Anti-Pattern Flags**:
   - ⚠️ DEEP_INHERITANCE: Inheritance depth > 2
   - ⚠️ STATEFUL_PARENT: Parent has mutable state
   - ⚠️ MULTIPLE_RESPONSIBILITIES: Parent does too many things
   - ⚠️ TIGHT_COUPLING: Child heavily dependent on parent internals
   - ✅ CLEAN: Simple inheritance with clear separation
   
   **Overall Inheritance Assessment**: 
   - SIMPLE_REFACTOR (extract to injected service)
   - MODERATE_REFACTOR (multiple extracted services needed)
   - COMPLEX_REFACTOR (deep hierarchy, multiple concerns)
   - BLOCKING_ISSUE (must migrate parent first)

3. **STATE MANAGEMENT ANALYSIS**
   Examine all instance fields (including inherited):
   - Are there any non-final instance fields? (List them)
   - Are there any static mutable fields? (List them)
   - **Inherited state concerns**: Does parent have mutable fields? (List them)
   - Assessment: STATELESS_SAFE or HAS_MUTABLE_STATE
   - Concern Level: ✅ LOW | ⚠️ MEDIUM | 🚨 HIGH

4. **DEPENDENCY INJECTION PATTERNS**
   For each @EJB, @Resource, @PersistenceContext field (including inherited):
   ```
   Field Name: [name]
   Type: [fully qualified type]
   Injection Type: @EJB | @Resource | @PersistenceContext
   Usage: How many times referenced in code
   Inherited: YES/NO
   ```
   - Can all fields be made final? YES/NO
   - Are there setter injections? YES/NO
   - Constructor exists? YES/NO

5. **TRANSACTION BOUNDARIES**
   Identify methods that need transaction management:
   
   **Write Operations** (create/modify data):
   - Methods starting with: save, update, delete, create, persist, remove, insert, modify
   - Methods with explicit transaction attributes
   - List: [method names]
   
   **Read Operations** (read-only):
   - Methods starting with: find, get, list, search, query, retrieve, load, count
   - List: [method names]
   
   **Complex/Unclear**:
   - List methods that don't fit clear patterns: [method names]

6. **SECURITY ANNOTATIONS**
   - @RolesAllowed: [locations and roles]
   - @PermitAll: [locations]
   - @DenyAll: [locations]
   - @RunAs: [if present]
   - Security complexity: SIMPLE | MODERATE | COMPLEX

7. **INTERFACE ANALYSIS**
   - Remote interface name: [if exists]
   - Local interface name: [if exists]
   - Home interface name: [if exists]
   - Business methods count: [count]
   - Are all business methods in the bean? YES/NO

8. **ANTI-PATTERN DETECTION**
   
   **Inheritance Anti-Patterns**:
   - Extending EJB base class for shared functionality? YES/NO
   - Deep inheritance hierarchy (>2 levels)? YES/NO
   - Multiple responsibilities in parent? YES/NO
   - Tight coupling to parent implementation? YES/NO
   
   **Factory Pattern Indicators**:
   - Static factory methods present? YES/NO (list them)
   - getInstance() or similar methods? YES/NO
   
   **God Class Indicators**:
   - Lines of code > 500? YES/NO ([actual count])
   - Methods > 15? YES/NO ([actual count])
   - Dependencies > 10? YES/NO ([actual count])
   - Multiple responsibilities evident? YES/NO (describe)
   
   **Synchronization Issues**:
   - Synchronized methods/blocks? YES/NO (list them)
   - Volatile fields? YES/NO
   - Concurrent collections? YES/NO
   
   **Resource Management**:
   - Manual JDBC connections? YES/NO
   - Missing try-with-resources? YES/NO
   - Resource leaks possible? YES/NO
   
   **Overall Assessment**: CLEAN | MINOR_ISSUES | SIGNIFICANT_CONCERNS

9. **EXTERNAL DEPENDENCIES**
   Identify usage of:
   - JNDI lookups: [list InitialContext usage]
   - JMS: [MessageProducer, Queue, Topic usage]
   - EJB Timer Service: [TimerService usage]
   - JTA: [UserTransaction usage]
   - Other EE APIs: [list]

10. **MIGRATION COMPLEXITY ASSESSMENT**
    
    **Deterministic Migration** (Can be done mechanically):
    - ✅ Stateless with no anti-patterns
    - ✅ Simple dependency injection
    - ✅ Clear transaction patterns
    - ✅ Standard security annotations
    - ✅ No complex external dependencies
    - ✅ No problematic inheritance
    
    **Requires Human Review** (Has complexities):
    - ⚠️ Mutable state management
    - ⚠️ Factory patterns or singletons
    - ⚠️ Complex synchronization
    - ⚠️ Resource management issues
    - ⚠️ Heavy JNDI or JMS usage
    - ⚠️ Complex inheritance requiring refactoring
    
    **Classification**: DETERMINISTIC | NEEDS_REVIEW
    **Confidence**: HIGH | MEDIUM | LOW
    **Estimated Effort**: 0.5h | 2h | 4h | 8h+

11. **DEPENDENCY GRAPH POSITION**
    - This class is injected into: [list classes that depend on this]
    - This class injects: [list dependencies]
    - Inherits from: [parent class if any]
    - Shares parent with: [sibling classes if applicable]
    - Migration priority: HIGH (no dependencies) | MEDIUM (some deps) | LOW (many deps)
    - Suggested migration order: [number or phase]

12. **CONCRETE MIGRATION STEPS**
    Provide step-by-step transformation guide:
    
    ```
    STEP 0: Inheritance Refactoring (if applicable)
    - Analyze parent class: [parent class name]
    - Extract shared functionality to new service: [service name]
    - Create service as @Service component
    - Move inherited methods: [list methods]
    - Update child class to inject and use new service
    - Expected changes: [detailed transformation]
    
    STEP 1: Annotation Replacement
    - Replace @Stateless with @Service
    - Remove @Local, @Remote annotations
    - Replace @EJB with @Autowired
    - Replace @Resource with @Autowired (for Spring-managed resources)
    
    STEP 2: Constructor Injection
    - Convert field injections to constructor parameters
    - Make all dependency fields final
    - Add @RequiredArgsConstructor or explicit constructor
    
    STEP 3: Transaction Management
    - Add @Transactional to class or methods
    - Add @Transactional(readOnly=true) to read operations:
      [list specific methods]
    - Add @Transactional to write operations:
      [list specific methods]
    
    STEP 4: Security Migration
    - Replace @RolesAllowed with @PreAuthorize("hasAnyRole(...)"):
      [list specific conversions]
    - Replace @PermitAll with @PreAuthorize("permitAll()"):
      [list locations]
    
    STEP 5: Interface Cleanup
    - Remove EJB interfaces: [list files to delete]
    - Update references in dependent classes
    
    STEP 6: Special Concerns
    [List any manual interventions needed]
    ```

13. **OUTPUT: STRUCTURED REPORT**

```json
{
  "analysis": {
    "className": "CustomerOrderBean",
    "fullyQualifiedName": "com.example.ejb.CustomerOrderBean",
    "ejbType": "STATELESS",
    "metrics": {
      "linesOfCode": 245,
      "methodCount": 12,
      "dependencyCount": 4,
      "complexity": "MEDIUM"
    }
  },
  "inheritance": {
    "hasInheritance": true,
    "parentClass": "com.example.ejb.BaseDataAccessBean",
    "parentType": "ABSTRACT_EJB",
    "inheritanceDepth": 2,
    "inheritedFields": [
      {
        "name": "entityManager",
        "type": "javax.persistence.EntityManager",
        "annotation": "@PersistenceContext"
      }
    ],
    "inheritedMethods": [
      "findById(Long id)",
      "persist(Object entity)",
      "merge(Object entity)"
    ],
    "usedInheritedMembers": [
      "findById",
      "persist",
      "entityManager"
    ],
    "pattern": {
      "type": "BASE_DAO",
      "sharedFunctionality": ["CRUD_OPERATIONS", "TRANSACTION_MANAGEMENT"],
      "parentDependencies": ["EntityManager"]
    },
    "refactoring": {
      "strategy": "COMPOSITION",
      "complexity": "MODERATE",
      "newServiceName": "CommonDataAccessService",
      "methodsToExtract": ["findById", "persist", "merge"],
      "affectsOtherClasses": true,
      "siblingsUsingParent": ["ProductOrderBean", "SupplierOrderBean"],
      "migrationPriority": "HIGH",
      "reason": "Parent class used by 3 other EJBs, must migrate parent functionality first"
    },
    "antiPatterns": {
      "deepInheritance": false,
      "statefulParent": false,
      "multipleResponsibilities": false,
      "tightCoupling": true
    },
    "overallAssessment": "MODERATE_REFACTOR"
  },
  "stateManagement": {
    "hasStatefulConcerns": false,
    "mutableFields": [],
    "inheritedMutableFields": [],
    "assessment": "STATELESS_SAFE",
    "riskLevel": "LOW"
  },
  "dependencies": {
    "injections": [
      {
        "fieldName": "orderService",
        "type": "com.example.ejb.OrderService",
        "injectionType": "EJB",
        "usageCount": 5,
        "canBeFinal": true,
        "inherited": false
      }
    ],
    "constructorExists": false,
    "needsConstructorRefactoring": true
  },
  "transactions": {
    "writeOperations": ["createOrder", "updateOrder", "deleteOrder"],
    "readOperations": ["findOrderById", "listCustomerOrders"],
    "complexOperations": [],
    "requiresManualReview": false
  },
  "security": {
    "hasSecurityAnnotations": true,
    "annotations": [
      {
        "type": "RolesAllowed",
        "roles": ["admin", "manager"],
        "location": "class level"
      }
    ],
    "complexity": "SIMPLE"
  },
  "interfaces": {
    "remoteInterface": "CustomerOrderRemote.java",
    "localInterface": "CustomerOrderLocal.java",
    "homeInterface": null,
    "needsInterfaceCleanup": true
  },
  "antiPatterns": {
    "inheritance": {
      "extendsEjbBase": true,
      "deepHierarchy": false,
      "multipleResponsibilities": false,
      "tightCoupling": true
    },
    "factoryPattern": false,
    "godClass": false,
    "synchronizationIssues": false,
    "resourceLeaks": false,
    "overallAssessment": "MINOR_ISSUES"
  },
  "externalDependencies": {
    "usesJNDI": false,
    "usesJMS": false,
    "usesTimerService": false,
    "usesJTA": false,
    "other": []
  },
  "migrationStrategy": {
    "classification": "NEEDS_REVIEW",
    "confidence": "MEDIUM",
    "estimatedEffort": "2h",
    "blockingIssues": [
      "Must refactor inheritance from BaseDataAccessBean first",
      "Affects 3 sibling classes that also need migration"
    ],
    "mechanicalSteps": [
      "Extract parent's shared functionality to CommonDataAccessService",
      "Inject CommonDataAccessService into this class",
      "Replace super.method() calls with service.method() calls",
      "Replace @Stateless with @Service",
      "Convert field injection to constructor injection",
      "Add @Transactional annotations"
    ],
    "manualReviewRequired": [
      "Review tight coupling to parent's EntityManager",
      "Verify transaction boundaries after refactoring inheritance",
      "Coordinate migration with 3 sibling classes"
    ]
  },
  "dependencyGraph": {
    "injectedInto": ["OrderController", "ReportGenerator"],
    "injects": ["OrderService", "CustomerRepository"],
    "inheritsFrom": "BaseDataAccessBean",
    "sharesParentWith": ["ProductOrderBean", "SupplierOrderBean"],
    "migrationPriority": "HIGH",
    "suggestedOrder": 1,
    "prerequisiteMigrations": ["BaseDataAccessBean refactoring"]
  }
}
```
```

---

## Usage Instructions

1. **Copy the entire prompt** (everything between the triple backticks above)
2. **Replace `[PASTE CLASS SOURCE CODE]`** with the actual EJB class source code
3. **Paste into your AI assistant** (Claude, ChatGPT, etc.)
4. **Review the JSON output** and use it to plan your migration strategy

## Key Features

- **Tool-Agnostic**: Works with any AI coding assistant
- **Comprehensive**: Covers all migration concerns including inheritance patterns
- **Structured Output**: JSON format for easy parsing and automation
- **Risk Assessment**: Clearly separates deterministic vs. manual review cases
- **Actionable**: Provides concrete migration steps

## Common Inheritance Patterns Detected

1. **BASE_DAO**: Parent provides common CRUD operations → Extract to @Repository
2. **BASE_SERVICE**: Parent provides common business logic → Extract to @Service
3. **ABSTRACT_EJB**: Parent manages EJB lifecycle → Remove (Spring handles this)
4. **UTILITY_BASE**: Parent has helper methods → Extract to utility class or @Component
