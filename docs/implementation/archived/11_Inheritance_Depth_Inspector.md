# Task 11: Inheritance Depth Inspector Implementation

## Overview
Implement a ClassLoader-based inspector that computes the inheritance depth (number of classes extended) for Java classes. This inspector uses runtime class loading to analyze the complete inheritance hierarchy.

## Inspector Details

### Inspector Name
`InheritanceDepthInspector`

### Location
`src/main/java/com/analyzer/rules/metrics/InheritanceDepthInspector.java`

### Base Class
Extends `AbstractClassLoaderBasedInspector`

### Dependencies
- **Required Tags**: `InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME` (provided by `TypeInspectorASMInspector`)
- **Graph Dependencies**: Uses `JavaClassNode` for metric attachment (not `ProjectFile`)

## Technical Implementation

### Core Algorithm
1. **Class Loading**: Use `JARClassLoaderService` to load the target class at runtime
2. **Hierarchy Traversal**: Walk up the inheritance chain using `Class.getSuperclass()`
3. **Depth Calculation**: Count the number of classes from target class to `Object` (exclusive)
4. **Metric Storage**: Attach depth value to `JavaClassNode` using property system

### Key Methods

```java
@Override
protected void analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile, ResultDecorator projectFileDecorator) {
    int inheritanceDepth = calculateInheritanceDepth(loadedClass);
    attachMetricToClassNode(projectFile, "inheritance.depth", inheritanceDepth);
}

private int calculateInheritanceDepth(Class<?> clazz) {
    int depth = 0;
    Class<?> current = clazz.getSuperclass();
    while (current != null && !current.equals(Object.class)) {
        depth++;
        current = current.getSuperclass();
    }
    return depth;
}
```

### Graph Integration
- **Target**: Attach metrics to `JavaClassNode` (not `ProjectFile`)
- **Property Key**: `"inheritance.depth"`
- **Property Type**: Integer
- **Access Pattern**: `classNode.setProperty("inheritance.depth", depth)`

## Expected Behaviors

### Normal Cases
- **Simple Class**: `class A {}` → depth = 0 (extends Object implicitly)
- **Single Inheritance**: `class B extends A {}` → depth = 1
- **Multiple Levels**: `class C extends B extends A {}` → depth = 2
- **Framework Classes**: Spring controllers, JPA entities with deep hierarchies

### Edge Cases
- **Object Class**: Should return depth = 0
- **Interface Types**: Skip interfaces, only count class inheritance
- **Abstract Classes**: Count abstract classes in hierarchy
- **Generic Classes**: Handle parameterized types correctly

### Error Handling
- **Class Loading Failures**: Use `safeLoadClass()` and skip if class cannot be loaded
- **SecurityManager Restrictions**: Handle reflection security exceptions
- **Circular Dependencies**: Should not occur in valid Java, but handle gracefully

## Testing Strategy

### Test File Location
`src/test/java/com/analyzer/rules/metrics/InheritanceDepthInspectorTest.java`

### Test Coverage
1. **Basic Inheritance Chains**: Test classes with 0, 1, 2, 3+ levels of inheritance
2. **Abstract Class Hierarchies**: Ensure abstract classes are counted
3. **Interface Implementations**: Verify interfaces are ignored for depth calculation
4. **Edge Cases**: Object class, inner classes, anonymous classes
5. **Error Scenarios**: Non-loadable classes, security restrictions
6. **Graph Integration**: Verify metrics are attached to correct `JavaClassNode`

### Test Data Structure
```
test-classes/
├── inheritance/
│   ├── SimpleClass.java          // depth = 0
│   ├── SingleInheritance.java    // depth = 1  
│   ├── MultipleInheritance.java  // depth = 2
│   ├── AbstractHierarchy.java    // depth varies
│   └── InterfaceImpl.java        // depth ignores interfaces
```

## Performance Considerations

### Optimization Strategies
- **Class Loading Cache**: Leverage `JARClassLoaderService` caching
- **Reflection Optimization**: Minimize reflective calls per class
- **Early Termination**: Stop at `Object.class` boundary
- **Memory Management**: Release class references after analysis

### Scalability
- **Large Codebases**: Efficiently handle 1000+ classes
- **Deep Hierarchies**: Handle enterprise frameworks with 10+ levels
- **Concurrent Processing**: Thread-safe implementation for parallel execution

## Integration Points

### Phase 2.4 Classification
This inspector is part of **Phase 2.4: ClassLoader-Based Metrics** alongside:
- Interface Number Inspector (Task 12)
- Type Usage Inspector (Task 13)

### Data Flow
1. **Input**: Java class files with FQN tags
2. **Processing**: Runtime class loading and reflection analysis
3. **Output**: Inheritance depth metrics attached to `JavaClassNode`
4. **Consumer**: Architecture analysis reports, complexity metrics

### Relationship to Existing Inspectors
- **Prerequisite**: `TypeInspectorASMInspector` for FQN identification
- **Parallel**: Other ClassLoader-based metric inspectors
- **Consumer**: Future architecture quality assessments

## Success Criteria

1. **Functional**: Correctly calculates inheritance depth for all test cases
2. **Performance**: Processes 1000+ classes in under 10 seconds
3. **Integration**: Seamlessly integrates with existing inspector pipeline
4. **Quality**: 90%+ code coverage with comprehensive error handling
5. **Documentation**: Clear JavaDoc with usage examples

## Implementation Priority
**Priority**: Medium (after P2-04 JBoss EJB Configuration debugging)
**Estimated Effort**: 1-2 days (design + implementation + testing)
**Blocking Dependencies**: None (AbstractClassLoaderBasedInspector exists)
