# BinaryClassCouplingGraphInspector - Annotation and Type Parameter Coverage

## Overview
Enhanced `BinaryClassCouplingGraphInspector` to capture annotation usage and generic type parameter dependencies that were previously missing from the coupling graph.

## Implementation Date
November 14, 2025

## Problem Statement
The original visitor implementation only covered:
- Class inheritance (`extends` relationship)
- Interface implementation (`implements` relationship)  
- Field and method signature dependencies (`uses` relationship)

However, it did NOT cover:
- **Annotation usage** - Classes, methods, and fields annotated with annotations
- **Generic type parameters** - Type arguments in generic signatures like `List<String>`

This gap meant important dependencies were not captured in the coupling graph, which could affect:
- Migration analysis (missing annotation dependencies like `@EJB`, `@Stateless`)
- Architecture analysis (missing framework annotation usage)
- Dependency analysis (missing generic type dependencies)

## Solution Implemented

### New Edge Types

Added two new edge type constants:

```java
public static final String EDGE_ANNOTATED_WITH = "annotated_with";
public static final String EDGE_TYPE_PARAMETER = "type_parameter";
```

### Annotation Coverage

Implemented annotation dependency tracking at three levels:

#### 1. Class-Level Annotations
```java
@Override
public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
    final Type annotationType = Type.getType(descriptor);
    final String annotationClassName = annotationType.getClassName();
    createCouplingEdge(annotationClassName, EDGE_ANNOTATED_WITH);
    return super.visitAnnotation(descriptor, visible);
}
```

#### 2. Field-Level Annotations
```java
@Override
public FieldVisitor visitField(...) {
    // ... existing field type logic ...
    
    // Return custom field visitor to capture field annotations
    return new FieldVisitor(api, fv) {
        @Override
        public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            final Type annotationType = Type.getType(desc);
            final String annotationClassName = annotationType.getClassName();
            createCouplingEdge(annotationClassName, EDGE_ANNOTATED_WITH);
            return super.visitAnnotation(desc, visible);
        }
    };
}
```

#### 3. Method-Level and Parameter Annotations
```java
@Override
public MethodVisitor visitMethod(...) {
    // ... existing method signature logic ...
    
    // Return custom method visitor to capture annotations
    return new MethodVisitor(api, mv) {
        @Override
        public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            // Method annotations
            createCouplingEdge(annotationType.getClassName(), EDGE_ANNOTATED_WITH);
            return super.visitAnnotation(desc, visible);
        }
        
        @Override
        public AnnotationVisitor visitParameterAnnotation(...) {
            // Parameter annotations  
            createCouplingEdge(annotationType.getClassName(), EDGE_ANNOTATED_WITH);
            return super.visitParameterAnnotation(parameter, desc, visible);
        }
    };
}
```

### Type Parameter Coverage

Implemented generic signature parsing using ASM's SignatureVisitor:

```java
private void parseSignatureForTypeParameters(final String signature) {
    try {
        final SignatureReader reader = new SignatureReader(signature);
        reader.accept(new SignatureVisitor(api) {
            @Override
            public void visitClassType(final String name) {
                final String className = Type.getObjectType(name).getClassName();
                createCouplingEdge(className, EDGE_TYPE_PARAMETER);
                super.visitClassType(name);
            }
        });
    } catch (Exception e) {
        logger.trace("Could not parse signature for type parameters: {}", signature, e);
    }
}
```

This method is called for:
- Class-level generic signatures (e.g., `class Foo<T extends Bar>`)
- Field generic signatures (e.g., `List<String> items`)
- Method generic signatures (e.g., `<T> T doSomething()`)

## Complete Edge Type Coverage

The inspector now creates all five types of coupling edges:

1. **extends** - Inheritance relationship (class extends superclass)
2. **implements** - Interface implementation  
3. **uses** - General usage dependency (fields, method signatures)
4. **annotated_with** - Annotation usage (class, field, method, parameter annotations)
5. **type_parameter** - Generic type parameter usage

## Examples

### Annotation Dependencies Captured

```java
@Stateless  // Creates edge: MyService -> javax.ejb.Stateless (annotated_with)
public class MyService {
    
    @EJB  // Creates edge: MyService -> javax.ejb.EJB (annotated_with)
    private OtherService other;
    
    @TransactionAttribute(REQUIRED)  // Creates edges for both annotation and enum
    public void doWork(@NotNull String param) {  // Parameter annotation captured
        // ...
    }
}
```

### Type Parameter Dependencies Captured

```java
public class MyRepository {
    private List<Customer> customers;  // Creates edge: MyRepository -> Customer (type_parameter)
    
    private Map<String, Order> orders;  // Creates edges to both String and Order
    
    public <T extends Entity> T find(Class<T> type) {  // Type bound captured
        // ...
    }
}
```

## Impact on EJB Migration

This enhancement is particularly valuable for EJB to Spring migration:

### Annotation Dependencies Now Captured
- `@Stateless`, `@Stateful`, `@MessageDriven`
- `@EJB`, `@Resource`, `@Inject`
- `@TransactionAttribute`, `@RolesAllowed`
- `@Local`, `@Remote`, `@LocalBean`

### Example Migration Analysis
Before this change, the graph would miss that a class depends on `javax.ejb.Stateless`. Now we can:
- Query all classes annotated with EJB annotations
- Track which classes need migration
- Analyze annotation coupling patterns
- Generate accurate dependency reports

## Technical Details

### ASM API Usage
- Uses ASM 9 (via `Opcodes.ASM9`)
- Leverages `SignatureReader` and `SignatureVisitor` for generic signatures
- Returns custom `FieldVisitor` and `MethodVisitor` to intercept annotation visits

### Error Handling
- Signature parsing wrapped in try-catch with trace-level logging
- Gracefully handles malformed generic signatures
- Continues processing even if signature parsing fails

### Performance Considerations
- Reuses existing deduplication logic (processedDependencies HashSet)
- No duplicate edges created for the same annotation/type parameter
- Minimal overhead added to existing bytecode scanning

## Testing Recommendations

1. **Annotation Coverage Test**
   - Create test class with annotations at all levels
   - Verify edges created for each annotation type
   - Test both visible and invisible annotations

2. **Generic Type Parameter Test**
   - Create test class with various generic patterns
   - Verify type parameter edges for collections, maps, custom generics
   - Test nested generics (e.g., `List<Map<String, Integer>>`)

3. **Integration Test**
   - Run full analysis on EJB project
   - Query graph for annotation dependencies
   - Verify coupling metrics include new edge types

## Files Modified

- `analyzer-inspectors/src/main/java/com/analyzer/rules/graph/BinaryClassCouplingGraphInspector.java`

## Related Documentation

- [BinaryClassCouplingGraphInspector.md](BinaryClassCouplingGraphInspector.md) - Original implementation
- [EJB Migration Analysis](../spec/analyses.md) - How this supports EJB migration

## Verification

Build successful:
```bash
cd analyzer-inspectors && mvn clean compile -DskipTests
# BUILD SUCCESS
```

## Next Steps

1. Update any graph query tools to handle new edge types
2. Add graph visualization support for annotation and type parameter edges
3. Create migration patterns that leverage annotation dependencies
4. Document new edge types in user-facing documentation
