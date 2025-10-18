# Task 13: Type Usage Inspector Implementation

## Overview
Implement a comprehensive inspector that analyzes and counts the different types of type usage within a Java class, including field types, parameter types, return types, local variable types, and imported types. This inspector provides detailed metrics about type dependencies and complexity.

## Inspector Details

### Inspector Name
`TypeUsageInspector`

### Location
`src/main/java/com/analyzer/rules/metrics/TypeUsageInspector.java`

### Base Class
Extends `AbstractClassLoaderBasedInspector` (with ASM and JavaParser integration)

### Analysis Approaches
1. **ClassLoader Analysis** (Primary): Runtime reflection for complete type information
2. **ASM Analysis** (Detailed): Bytecode inspection for method signatures and field types
3. **JavaParser Analysis** (Source): Source code parsing for imports and local variables

### Dependencies
- **Required Tags**: `InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME` (provided by `TypeInspectorASMInspector`)
- **Optional Tags**: `"source_file"` for enhanced JavaParser analysis
- **Graph Dependencies**: Uses `JavaClassNode` for comprehensive metric attachment

## Technical Implementation

### Core Algorithm
1. **Type Collection**: Gather all type references from multiple sources
2. **Categorization**: Classify types by usage context (field, parameter, return, etc.)
3. **Deduplication**: Remove duplicate type references within categories
4. **Complexity Scoring**: Calculate type usage complexity metrics
5. **Metric Storage**: Attach detailed metrics to `JavaClassNode`

### Key Methods

```java
@Override
protected void analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile, ResultDecorator projectFileDecorator) {
    TypeUsageMetrics metrics = analyzeTypeUsage(loadedClass, projectFile);
    attachTypeMetricsToClassNode(projectFile, metrics);
}

private TypeUsageMetrics analyzeTypeUsage(Class<?> loadedClass, ProjectFile projectFile) {
    TypeUsageMetrics metrics = new TypeUsageMetrics();
    
    // Analyze different type usage contexts
    analyzeFieldTypes(loadedClass, metrics);
    analyzeMethodTypes(loadedClass, metrics);
    analyzeConstructorTypes(loadedClass, metrics);
    analyzeAnnotationTypes(loadedClass, metrics);
    
    // Enhance with source code analysis if available
    if (projectFile.hasTag("source_file")) {
        enhanceWithSourceAnalysis(projectFile, metrics);
    }
    
    return metrics;
}

private void analyzeFieldTypes(Class<?> clazz, TypeUsageMetrics metrics) {
    for (Field field : clazz.getDeclaredFields()) {
        Class<?> fieldType = field.getType();
        metrics.addFieldType(fieldType);
        
        // Handle generic types
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            analyzeParameterizedType((ParameterizedType) genericType, metrics);
        }
    }
}

private void analyzeMethodTypes(Class<?> clazz, TypeUsageMetrics metrics) {
    for (Method method : clazz.getDeclaredMethods()) {
        // Return type
        Class<?> returnType = method.getReturnType();
        if (!returnType.equals(void.class)) {
            metrics.addReturnType(returnType);
        }
        
        // Parameter types
        for (Class<?> paramType : method.getParameterTypes()) {
            metrics.addParameterType(paramType);
        }
        
        // Exception types
        for (Class<?> exceptionType : method.getExceptionTypes()) {
            metrics.addExceptionType(exceptionType);
        }
        
        // Generic method analysis
        analyzeGenericMethodTypes(method, metrics);
    }
}
```

### Type Usage Metrics Class

```java
public static class TypeUsageMetrics {
    private final Set<Class<?>> fieldTypes = new LinkedHashSet<>();
    private final Set<Class<?>> parameterTypes = new LinkedHashSet<>();
    private final Set<Class<?>> returnTypes = new LinkedHashSet<>();
    private final Set<Class<?>> exceptionTypes = new LinkedHashSet<>();
    private final Set<Class<?>> annotationTypes = new LinkedHashSet<>();
    private final Set<Class<?>> localVariableTypes = new LinkedHashSet<>();
    private final Set<String> importedTypes = new LinkedHashSet<>();
    private final Set<Class<?>> genericTypeArguments = new LinkedHashSet<>();
    
    // Computed metrics
    private int totalUniqueTypes;
    private int primitiveTypeCount;
    private int referenceTypeCount;
    private int collectionTypeCount;
    private int frameworkTypeCount;
    private double typeComplexityScore;
    
    // ... getter/setter methods and metric calculation logic
}
```

### Multi-Approach Integration

```java
// ASM enhancement for bytecode-level analysis
private void enhanceWithASMAnalysis(ProjectFile projectFile, TypeUsageMetrics metrics) {
    // Use ASM ClassReader to extract detailed type information from bytecode
    // Analyze method signatures, field descriptors, and local variable tables
}

// JavaParser enhancement for source code analysis
private void enhanceWithSourceAnalysis(ProjectFile projectFile, TypeUsageMetrics metrics) {
    // Parse Java source to extract:
    // - Import statements
    // - Local variable declarations
    // - Type annotations
    // - Generic type bounds
}
```

### Graph Integration
- **Target**: Attach comprehensive metrics to `JavaClassNode`
- **Property Keys**: 
  - `"types.total_unique"` (Integer)
  - `"types.field_count"` (Integer)
  - `"types.parameter_count"` (Integer)
  - `"types.return_count"` (Integer)
  - `"types.exception_count"` (Integer)
  - `"types.primitive_count"` (Integer)
  - `"types.reference_count"` (Integer)
  - `"types.collection_count"` (Integer)
  - `"types.framework_count"` (Integer)
  - `"types.complexity_score"` (Double)
  - `"types.imported_count"` (Integer)
- **Access Pattern**: `classNode.setProperty(key, value)`

## Expected Behaviors

### Normal Cases
- **Simple Class**: `class A { int x; String s; }` → field_count = 2, primitive_count = 1, reference_count = 1
- **Method-Heavy Class**: Multiple methods with various parameter/return types
- **Generic Class**: `class B<T> { List<T> items; Map<String, T> map; }` → complex generic analysis
- **Framework Integration**: Spring controllers, JPA entities with framework-specific types

### Complex Cases
- **Nested Generics**: `Map<String, List<Set<CustomType>>>` → deep generic analysis
- **Wildcard Types**: `List<? extends Number>` → bounded wildcard handling
- **Anonymous Classes**: Inner classes with local variable capture
- **Lambda Expressions**: Functional interface type analysis
- **Annotation Processing**: Custom annotations with complex attribute types

### Edge Cases
- **Primitive Arrays**: `int[]`, `String[][]` → array type classification
- **Inner Classes**: Nested and static inner class type references
- **Enum Types**: Enum constants and methods
- **Interface Types**: Functional and marker interface usage
- **Reflection Usage**: `Class<?>`, `Method`, `Field` type references

### Error Handling
- **Class Loading Failures**: Graceful degradation to ASM/JavaParser only
- **Generic Type Resolution**: Handle type erasure and raw types
- **Security Restrictions**: Handle reflection access limitations
- **Circular Dependencies**: Detect and handle circular type references
- **Missing Types**: Handle references to non-available types

## Testing Strategy

### Test File Location
`src/test/java/com/analyzer/rules/metrics/TypeUsageInspectorTest.java`

### Test Coverage
1. **Basic Type Usage**: Classes with various field, parameter, and return types
2. **Generic Type Analysis**: Complex generic type hierarchies and wildcards
3. **Framework Integration**: Spring, JPA, and enterprise framework type usage
4. **Array Types**: Multi-dimensional arrays and primitive arrays
5. **Inner Classes**: Nested class type references
6. **Lambda and Method References**: Functional programming type usage
7. **Annotation Types**: Custom annotation usage and processing
8. **Multi-Approach Validation**: ClassLoader vs ASM vs JavaParser consistency
9. **Performance Testing**: Large classes with extensive type usage
10. **Error Scenarios**: Non-loadable types, security restrictions
11. **Graph Integration**: Verify comprehensive metrics attachment

### Test Data Structure
```
test-classes/
├── type-usage/
│   ├── SimpleTypes.java              // basic field/method types
│   ├── GenericTypes.java             // complex generic usage
│   ├── CollectionTypes.java          // List, Set, Map variations
│   ├── FrameworkTypes.java           // Spring, JPA annotations
│   ├── ArrayTypes.java               // multi-dimensional arrays
│   ├── InnerClassTypes.java          // nested class references
│   ├── FunctionalTypes.java          // lambdas, method references
│   ├── AnnotationTypes.java          // custom annotation usage
│   ├── ComplexHierarchy.java         // deep inheritance with types
│   └── PerformanceTest.java          // large class with many types
```

## Performance Considerations

### Optimization Strategies
- **Type Caching**: Cache type analysis results to avoid redundant reflection
- **Reflection Optimization**: Batch reflection calls and minimize overhead
- **Memory Management**: Efficient collection usage for large type sets
- **Lazy Evaluation**: Defer expensive type analysis until needed
- **Generic Type Optimization**: Cache parameterized type resolution

### Scalability
- **Large Codebases**: Handle classes with 100+ fields and methods efficiently
- **Complex Generics**: Process deeply nested generic type hierarchies
- **Framework Integration**: Handle enterprise applications with extensive framework usage
- **Concurrent Processing**: Thread-safe implementation for parallel analysis

## Integration Points

### Phase 2.4 Classification
This inspector is part of **Phase 2.4: ClassLoader-Based Metrics** alongside:
- Inheritance Depth Inspector (Task 11)
- Interface Number Inspector (Task 12)

### Data Flow
1. **Input**: Java class files with FQN tags
2. **Processing**: Multi-approach type usage analysis (ClassLoader + ASM + JavaParser)
3. **Output**: Comprehensive type usage metrics attached to `JavaClassNode`
4. **Consumer**: Architecture analysis, dependency mapping, complexity assessment

### Relationship to Existing Inspectors
- **Prerequisite**: `TypeInspectorASMInspector` for FQN identification
- **Enhancement**: `JavaImportGraphInspector` for import relationship analysis
- **Parallel**: Other ClassLoader-based metric inspectors
- **Consumer**: Architecture quality assessments, dependency analysis tools

### Multi-Approach Benefits
- **Completeness**: ClassLoader provides runtime type information
- **Detail**: ASM provides bytecode-level type descriptors
- **Context**: JavaParser provides source-level type context and imports
- **Validation**: Cross-validation across multiple analysis approaches

## Success Criteria

1. **Functional**: Accurately analyzes all categories of type usage including complex generics
2. **Multi-Approach**: Successfully integrates ClassLoader, ASM, and JavaParser analysis
3. **Performance**: Processes 1000+ classes with extensive type usage in under 30 seconds
4. **Integration**: Seamlessly integrates with existing inspector pipeline
5. **Quality**: 95%+ code coverage with comprehensive error handling
6. **Accuracy**: Consistent results across different analysis approaches
7. **Scalability**: Handles enterprise-scale applications with complex type hierarchies

## Implementation Priority
**Priority**: Medium (after P2-04 JBoss EJB Configuration debugging)
**Estimated Effort**: 3-4 days (most complex of the three metric inspectors)
**Blocking Dependencies**: None (AbstractClassLoaderBasedInspector exists)

## Additional Considerations

### Framework-Specific Analysis
- **Spring Framework**: Detect Spring-specific type patterns (@Autowired, @Component types)
- **JPA/Hibernate**: Analyze entity relationship types and mapping annotations
- **EJB**: Handle enterprise bean type dependencies
- **Microservices**: REST endpoint parameter/return type analysis
- **Testing Frameworks**: Mock and test-specific type usage

### Type Complexity Scoring
- **Primitive Types**: Low complexity score (1 point)
- **Standard Library**: Medium complexity score (2 points)
- **Third-Party Framework**: Higher complexity score (3 points)
- **Custom Application Types**: Variable complexity based on usage context
- **Generic Type Parameters**: Additional complexity multipliers
- **Nested Generics**: Exponential complexity scoring for deep nesting

### Future Enhancements
- **Dependency Graph Generation**: Build type dependency graphs for visualization
- **Architectural Metrics**: Calculate coupling and cohesion metrics based on type usage
- **Framework Recommendations**: Suggest framework upgrades based on type usage patterns
- **Type Usage Patterns**: Identify common type usage anti-patterns and code smells
- **Migration Analysis**: Support for framework migration impact analysis

### Integration with Existing Graph System
- **Type Relationships**: Create edges between classes based on type usage
- **Dependency Mapping**: Map type dependencies across modules and packages
- **Circular Dependency Detection**: Identify problematic type dependency cycles
- **Impact Analysis**: Assess impact of type changes across the codebase
