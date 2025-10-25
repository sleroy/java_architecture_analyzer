Perfect clarification! This creates a __crystal clear separation of concerns__:

## Refined NodeDecorator Design

### Clear Separation: Properties vs Tags

```java
/**
 * Generic decorator for GraphNode that provides:
 * - Property operations (data/metrics with aggregation)
 * - Tag operations (boolean flags)
 * - Error handling
 */
public class NodeDecorator<T extends GraphNode> {
    private final T node;
    
    public NodeDecorator(T node) {
        this.node = node;
    }
    
    public T getNode() {
        return node;
    }
    
    // ========== PROPERTY OPERATIONS (Data/Metrics) ==========
    
    /**
     * Set a property value directly
     */
    public void setProperty(String propertyName, Object value) {
        node.setProperty(propertyName, value);
    }
    
    /**
     * Set property to maximum of current and new value
     */
    public void setMaxProperty(String propertyName, int value) {
        Integer current = getIntProperty(node, propertyName, 0);
        if (value > current) {
            node.setProperty(propertyName, value);
        }
    }
    
    public void setMaxProperty(String propertyName, double value) {
        Double current = getDoubleProperty(node, propertyName, 0.0);
        if (value > current) {
            node.setProperty(propertyName, value);
        }
    }
    
    /**
     * Set property to higher complexity level
     */
    public void setMaxComplexityProperty(String propertyName, String complexity) {
        String current = getStringProperty(node, propertyName, "NONE");
        String max = computeMaxComplexity(current, complexity);
        node.setProperty(propertyName, max);
    }
    
    /**
     * Boolean OR for property (any inspector sets true = stays true)
     */
    public void orProperty(String propertyName, boolean value) {
        boolean current = getBooleanProperty(node, propertyName, false);
        node.setProperty(propertyName, current || value);
    }
    
    /**
     * Boolean AND for property (all must be true)
     */
    public void andProperty(String propertyName, boolean value) {
        boolean current = getBooleanProperty(node, propertyName, true);
        node.setProperty(propertyName, current && value);
    }
    
    // ========== TAG OPERATIONS (Boolean Flags) ==========
    
    /**
     * Enable a tag (set boolean flag to true)
     */
    public void enableTag(String tagName) {
        node.addTag(tagName);
    }
    
    /**
     * Disable a tag (remove boolean flag)
     */
    public void disableTag(String tagName) {
        node.removeTag(tagName);  // Assumes GraphNode has removeTag()
    }
    
    /**
     * Check if tag is enabled
     */
    public boolean hasTag(String tagName) {
        return node.hasTag(tagName);
    }
    
    // ========== ERROR HANDLING ==========
    
    public void error(String errorMessage) {
        node.setProperty("ERROR", errorMessage);
    }
    
    public void error(Throwable throwable) {
        node.setProperty("ERROR", throwable.getMessage());
    }
    
    // Helper methods...
}
```

## Usage Examples

### Example 1: Metrics Inspector (Properties)

```java
public class MethodCountInspector implements Inspector<JavaClassNode> {
    @Override
    public void inspect(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
        int methodCount = analyzeMethodCount(classNode);
        
        // Set property data
        decorator.setProperty(JavaClassNode.PROP_METHOD_COUNT, methodCount);
        decorator.setProperty(JavaClassNode.PROP_PUBLIC_METHODS, publicCount);
        
        // Aggregation if needed
        decorator.setMaxProperty("maxMethodsInClass", methodCount);
        
        // Enable tag to mark analysis complete
        decorator.enableTag("METRICS_METHOD_COUNT_ANALYZED");
    }
}
```

### Example 2: EJB Detection Inspector (Tags + Properties)

```java
public class EjbBinaryClassInspector implements Inspector<JavaClassNode> {
    @Override
    public void inspect(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
        if (hasStatelessAnnotation(classNode)) {
            // Enable detection tags
            decorator.enableTag("EJB_STATELESS");
            decorator.enableTag("EJB_DETECTED");
            
            // Set property metadata
            decorator.setProperty("ejbType", "STATELESS");
            decorator.setProperty("ejbVersion", "3.x");
            decorator.setMaxComplexityProperty("migrationComplexity", "MEDIUM");
        }
    }
}
```

### Example 3: Complexity Aggregation (Properties)

```java
public class ComplexityInspector implements Inspector<JavaClassNode> {
    @Override
    public void inspect(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
        int complexity = calculateComplexity(classNode);
        
        // Set this class's complexity
        decorator.setProperty("cyclomaticComplexity", complexity);
        
        // Track package-level max (aggregation)
        decorator.setMaxProperty("packageMaxComplexity", complexity);
        
        // Set complexity level
        String level = complexity > 50 ? "HIGH" : "MEDIUM";
        decorator.setMaxComplexityProperty("complexityLevel", level);
        
        // Tag for high complexity
        if (complexity > 50) {
            decorator.enableTag("HIGH_COMPLEXITY");
        }
    }
}
```

## Updated Inspector Interface

```java
public interface Inspector<T extends GraphNode> {
    /**
     * Inspects the node and decorates it with properties and tags
     * 
     * @param node The node to inspect
     * @param decorator Helper for setting properties (with aggregation) and tags
     */
    void inspect(T node, NodeDecorator<T> decorator);
    
    String getName();
    RequiredTags getDependencies();
    boolean canProcess(T node);
    boolean supports(T node);
}
```

## Key Design Decisions

### 1. __Properties__ (via decorator methods)

- Use for __data and metrics__
- Support aggregation (setMax, or, and, setMaxComplexity)
- Examples: methodCount, complexity, ejbType

### 2. __Tags__ (via enableTag/disableTag)

- Use for __boolean flags__
- Simple presence/absence semantics
- Examples: "EJB_DETECTED", "METRICS_ANALYZED", "HIGH_COMPLEXITY"

### 3. __Clear Mental Model__

```javascript
Property: "methodCount" = 42          ← Data/metric
Tag: "METRICS_ANALYZED"               ← Boolean flag
```

## Next Steps for Architecture

1. __Add `removeTag()` to GraphNode interface__ (needed for disableTag)
2. __Create NodeDecorator__ class with this design
3. __Update Inspector interface__ signature
4. __Migrate ProjectFileDecorator__ → NodeDecorator pattern
5. __Update all inspectors__ to use new API

Does this separation between properties and tags match your vision?
