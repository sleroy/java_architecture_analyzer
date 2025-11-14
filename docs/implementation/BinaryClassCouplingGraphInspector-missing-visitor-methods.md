# BinaryClassCouplingGraphInspector - Unimplemented Visitor Methods Analysis

## Overview

Analysis of ASM ClassVisitor methods to identify which methods are not currently implemented in `BinaryClassCouplingGraphInspector` and whether they should be added for complete dependency tracking.

## Currently Implemented Methods

✅ `visit()` - Class header (superclass, interfaces, signature)
✅ `visitAnnotation()` - Class-level annotations
✅ `visitField()` - Field declarations
✅ `visitMethod()` - Method declarations
✅ `visitEnd()` - End of class
✅ Field `visitAnnotation()` - Field annotations
✅ Method `visitAnnotation()` - Method annotations
✅ Method `visitParameterAnnotation()` - Parameter annotations

## Unimplemented Methods

### High Priority - Should Implement

#### 1. `visitInnerClass(String name, String outerName, String innerName, int access)`
**Purpose**: Called for each inner class reference

**Why implement**: Inner classes create implicit dependencies that should be tracked in the graph.

**Example**:
```java
public class Outer {
    public class Inner { }  // Creates inner class relationship
}

public class Other {
    Outer.Inner field;  // References inner class
}
```

**Impact**: Missing inner class relationships in dependency graph.

#### 2. `visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible)`
**Purpose**: Type annotations on class declarations

**Why implement**: Java 8+ type annotations can appear on:
- Class extends/implements: `class MyClass extends @NonNull BaseClass`
- Type parameters: `class MyClass<@NonNull T>`

**Example**:
```java
class MyService extends @Validated BaseService { }
```

**Impact**: Missing some annotation dependencies (less common but valid).

#### 3. `visitOuterClass(String owner, String name, String descriptor)`
**Purpose**: Called for inner classes to identify their outer class

**Why implement**: Complete inner/outer class relationship tracking.

**Example**:
```java
public class Outer {
    class Inner { }  // Inner.class will have visitOuterClass("Outer", ...)
}
```

**Impact**: Missing outer class references for inner classes.

#### 4. `visitRecordComponent(String name, String descriptor, String signature)`
**Purpose**: Java 14+ record components

**Why implement**: Records are becoming common, components may have dependencies.

**Example**:
```java
record UserRecord(String name, Address address) { }
// address component creates dependency on Address
```

**Impact**: Missing dependencies in record declarations.

### Medium Priority - Consider Implementing

#### 5. `visitNestHost(String nestHost)` and `visitNestMember(String nestMember)`
**Purpose**: Java 11+ nest-based access control

**Why consider**: Tracks nest relationships for inner classes and lambdas.

**Example**: Automatically generated for inner classes and lambda expressions.

**Impact**: Mostly duplicates information from visitInnerClass/visitOuterClass.

#### 6. `visitPermittedSubclass(String permittedSubclass)`
**Purpose**: Java 17+ sealed classes

**Why consider**: Tracks which classes can extend sealed classes.

**Example**:
```java
sealed class Shape permits Circle, Square { }
```

**Impact**: Missing sealed class constraints (emerging feature).

### Low Priority - Probably Skip

#### 7. `visitSource(String source, String debug)`
**Purpose**: Source file name and debug info

**Why skip**: Not relevant for dependency analysis, only for debugging.

#### 8. `visitModule(String name, int access, String version)`
**Purpose**: Java 9+ module declarations

**Why skip**: Module-level, not class-level dependencies. Would need separate inspector.

#### 9. `visitAttribute(Attribute attribute)`
**Purpose**: Custom attributes

**Why skip**: Rare, framework-specific, not standard dependencies.

## Recommended Implementation Priority

### Phase 1 - Essential (Implement Now)
1. **visitInnerClass()** - Common, important for accurate dependency graphs
2. **visitOuterClass()** - Complements visitInnerClass()

### Phase 2 - Modern Java Support (Implement Soon)
3. **visitRecordComponent()** - Records are becoming standard
4. **visitTypeAnnotation()** - Complete annotation coverage

### Phase 3 - Future Features (Implement Later)
5. **visitPermittedSubclass()** - For sealed classes support
6. **visitNestHost/visitNestMember()** - If nest information is needed

### Skip
- visitSource()
- visitModule()
- visitAttribute()

## Implementation Notes

### visitInnerClass() Implementation Approach
```java
@Override
public void visitInnerClass(String name, String outerName, String innerName, int access) {
    if (outerName != null) {
        String outerClassName = Type.getObjectType(outerName).getClassName();
        String innerClassName = Type.getObjectType(name).getClassName();
        
        // If this class is the inner class, create edge to outer
        if (innerClassName.equals(sourceNode.getFullyQualifiedName())) {
            Map<String, Object> props = new HashMap<>();
            props.put("relationshipKind", "inner_class_of");
            createCouplingEdge(outerClassName, EDGE_USES, props);
        }
        
        // If this class references the inner class, create edge
        // (visitInnerClass is called for all inner classes referenced)
        Map<String, Object> props = new HashMap<>();
        props.put("relationshipKind", "references_inner_class");
        createCouplingEdge(innerClassName, EDGE_USES, props);
    }
    super.visitInnerClass(name, outerName, innerName, access);
}
```

### visitRecordComponent() Implementation Approach
```java
@Override
public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
    // Parse component type using TypeParser
    TypeInfo componentType = TypeParser.parseType(descriptor, signature);
    processTypeInfo(componentType, null, -1);
    
    return super.visitRecordComponent(name, descriptor, signature);
}
```

## Current Status

- ✅ Core dependencies covered (fields, methods, inheritance, annotations)
- ⚠️ Missing inner class relationships
- ⚠️ Missing record component dependencies
- ⚠️ Incomplete annotation coverage (missing type annotations)
- ❌ No sealed class support

## Recommendations

1. **Immediate**: Implement `visitInnerClass()` and `visitOuterClass()` for complete class relationship tracking
2. **Soon**: Add `visitRecordComponent()` for Java 14+ support
3. **Future**: Add `visitTypeAnnotation()` for complete annotation coverage
4. **Monitor**: Watch for sealed class adoption, implement `visitPermittedSubclass()` when needed

## Impact Assessment

Without inner class and record support:
- ~5-15% of dependencies may be missing in projects with extensive use of inner classes
- ~2-5% missing in projects using Java records
- Edge case: Type annotations rarely create new dependencies

With full implementation:
- Complete dependency graph for all Java language features
- Better support for modern Java (14+, 17+)
- More accurate architecture analysis

## Date

November 14, 2025
