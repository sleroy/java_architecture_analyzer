# Phase: Stateless Session Beans - Variable Documentation

This document provides markdown-formatted lists of the variables used in the `phase-stateless-session-beans.yaml` migration phase.

## Variables Overview

### 1. stateless_beans (List of Graph Nodes)

**Variable Name:** `stateless_beans`

**Type:** List of GraphNode objects

**Source:** GRAPH_QUERY block (query-stateless-beans)

**Query:** Nodes tagged with `EJB_STATELESS_SESSION_BEAN`

**Description:** Contains all stateless session bean classes identified in the codebase.

**Structure:**
- Each entry is a JavaClassNode object with properties:
  - `id` - Unique node identifier (same as fullyQualifiedName)
  - `simpleName` - Simple class name without package
  - `fullyQualifiedName` - Complete class name including package
  - `packageName` - Package name
  - `sourceFilePath` - File system path to the source file
  - `classType` - Type of class (class, interface, enum, etc.)
  - `sourceType` - How discovered (source or binary)
  - Additional metrics and properties

**Usage in Phase:**
- Input for `AI_ASSISTED_BATCH` block iteration
- Referenced as `${stateless_beans}` in AI prompts (outputs node IDs)
- Used to process each bean individually during migration

**Example Values:**
```markdown
- Node ID: com.example.service.UserServiceBean
  - simpleName: UserServiceBean
  - fullyQualifiedName: com.example.service.UserServiceBean
  - packageName: com.example.service
  - sourceFilePath: /src/main/java/com/example/service/UserServiceBean.java
  - classType: class
  - sourceType: source

- Node ID: com.example.order.OrderProcessorBean
  - simpleName: OrderProcessorBean
  - fullyQualifiedName: com.example.order.OrderProcessorBean
  - packageName: com.example.order
  - sourceFilePath: /src/main/java/com/example/order/OrderProcessorBean.java
  - classType: class
  - sourceType: source

- Node ID: com.example.payment.PaymentServiceBean
  - simpleName: PaymentServiceBean
  - fullyQualifiedName: com.example.payment.PaymentServiceBean
  - packageName: com.example.payment
  - sourceFilePath: /src/main/java/com/example/payment/PaymentServiceBean.java
  - classType: class
  - sourceType: binary
```

---

### 2. stateless_beans_names (List of Strings)

**Variable Name:** `stateless_beans_names`

**Type:** List of String values

**Source:** Automatically generated alias from `stateless_beans`

**Description:** Contains the class names (as strings) extracted from the stateless_beans graph nodes. This is a convenience variable for displaying class names in prompts and messages.

**Generation:** 
- Automatically derived from `stateless_beans` graph nodes
- Extracts the `className` property from each node
- May include fully qualified names or simple class names depending on implementation

**Usage in Phase:**
- Referenced as `${stateless_beans_names}` in AI_ASSISTED block
- Used for compilation and validation prompts
- Provides human-readable list of migrated classes

**Example Values:**
```markdown
- UserServiceBean
- OrderProcessorBean
- PaymentServiceBean
- InventoryManagerBean
- NotificationServiceBean
- ReportGeneratorBean
- AuthenticationServiceBean
- DataValidationBean
```

---

## Variable Usage in Migration Blocks

### Block 1: GRAPH_QUERY
```yaml
output-variable: "stateless_beans"
```
**Creates:** List of GraphNode objects representing all stateless session beans

### Block 3: AI_ASSISTED_BATCH
```yaml
input-nodes: stateless_beans
```
**Uses:** Iterates over each GraphNode in `stateless_beans`

**Available in prompt:**
- `${current_node_id}` - Current node being processed (ID)
- `${current_node.simpleName}` - Simple class name (e.g., "UserServiceBean")
- `${current_node.fullyQualifiedName}` - Full class name (e.g., "com.example.service.UserServiceBean")
- `${current_node.packageName}` - Package name (e.g., "com.example.service")
- `${current_node.sourceFilePath}` - Source file path
- `${current_node.classType}` - Type (class, interface, enum, etc.)
- `${current_node.sourceType}` - Discovery method (source or binary)

### Block 4: AI_ASSISTED
```yaml
prompt: |
  - Total beans processed: ${node_count}
  - Class IDs are ${stateless_beans}
  - Classes are ${stateless_beans_names}
```
**Uses:** 
- `${node_count}` - Total number of beans found
- `${stateless_beans}` - List of node IDs (for reference)
- `${stateless_beans_names}` - List of class names (for display)

---

## Related Context Variables

Additional variables available during phase execution:

### Batch Processing Variables
- `${current_node_id}` - ID of the node currently being processed
- `${current_node}` - Full GraphNode object with all properties
- `${node_count}` - Total number of nodes in the batch
- `${batch_index}` - Current position in batch (0-based)

### Summary Variables
- `${success_count}` - Number of successfully migrated beans
- `${failure_count}` - Number of beans that failed migration
- `${skipped_count}` - Number of beans skipped

### Project Variables
- `${project_root}` - Root directory of the project
- `${artifact_id}` - Maven artifact ID
- `${group_id}` - Maven group ID

---

## Migration Flow

1. **GRAPH_QUERY** → Creates `stateless_beans` list
2. **OPENREWRITE** → Applies automated refactorings to all beans
3. **AI_ASSISTED_BATCH** → Processes each bean in `stateless_beans` individually
4. **AI_ASSISTED** → Compiles and validates using `stateless_beans_names` for display
5. **INTERACTIVE_VALIDATION** → Manual review using summary variables

---

## Notes

- The `stateless_beans` variable persists throughout the entire phase execution
- The `_names` suffix indicates an automatically generated string list alias
- JavaClassNode objects contain rich metadata beyond just the class name
- Variables are resolved using FreeMarker template syntax (`${variable_name}`)
- Node properties are accessed using dot notation (`${node.property}`)
- Property names match JavaClassNode getter methods (e.g., `getSimpleName()` → `${node.simpleName}`)

## JavaClassNode Property Reference

Based on `analyzer-core/src/main/java/com/analyzer/api/graph/JavaClassNode.java`:

### Core Properties (via getters)
- `id` - Node identifier (same as fullyQualifiedName)
- `simpleName` - Class name without package (from `getSimpleName()`)
- `fullyQualifiedName` - Complete class name with package (from `getFullyQualifiedName()`)
- `packageName` - Package containing the class (from `getPackageName()`)
- `sourceFilePath` - Path to source file (from `getSourceFilePath()`)
- `classType` - Type: class, interface, enum, annotation, record (from `getClassType()`)
- `sourceType` - Discovery method: "source" or "binary" (from `getSourceType()`)
- `projectFileId` - ID of the ProjectFile containing this class (from `getProjectFileId()`)

### Metrics (via getters)
- `methodCount` - Number of methods (from `getMethodCount()`)
- `fieldCount` - Number of fields (from `getFieldCount()`)
- `cyclomaticComplexity` - Cyclomatic complexity metric (from `getCyclomaticComplexity()`)
- `weightedMethods` - Weighted methods metric (from `getWeightedMethods()`)
- `afferentCoupling` - Afferent coupling metric (from `getAfferentCoupling()`)
- `efferentCoupling` - Efferent coupling metric (from `getEfferentCoupling()`)

### Additional Properties
- Properties can be accessed via `getProperty(String key)` or through the properties map
- Tags are available via `getTags()` method
- Custom properties set by inspectors are accessible through the property system
