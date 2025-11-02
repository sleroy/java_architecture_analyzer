# Task: Implement Antipattern Inspectors

**Priority:** MEDIUM  
**Estimated Effort:** 8-10 hours  
**Dependencies:** None  
**Goal:** Replace remaining grep commands in phase9-10-modernization.yaml and appendix-g-antipatterns.yaml

---

## Objective

Implement four inspectors to detect common code antipatterns, enabling replacement of grep commands with GRAPH_QUERY blocks.

## Current State

### Grep Commands to Replace:

**phase9-10-modernization.yaml (5 greps):**
```bash
grep -rn "extends" ${project_root}/semeru-springboot/src --include="*.java"
grep -rn 'private static.*getInstance' ${project_root}/semeru-springboot/src
grep -rn 'public static.*Utils' ${project_root}/semeru-springboot/src  
grep -rn 'throws.*Exception.*Exception' ${project_root}/semeru-springboot/src
```

**appendix-g-antipatterns.yaml (4 greps):**
```bash
grep -r 'private static.*getInstance'
grep -r 'throws.*Exception.*Exception'
grep -r 'public static.*Utils'
```

### Missing Inspectors:
1. **InheritanceDepthInspector** - Deep inheritance hierarchies
2. **SingletonPatternInspector** - Singleton pattern detection
3. **UtilityClassInspector** - Static utility class detection
4. **ExceptionAntipatternInspector** - Generic exception throws

---

## Inspector 1: SingletonPatternInspector

**Priority:** HIGHEST (simplest to implement)  
**Effort:** 2 hours

### Tags to Produce
```java
public static final String ANTIPATTERN_SINGLETON = "antipattern.singleton.detected";
public static final String SINGLETON_THREADSAFE = "antipattern.singleton.threadsafe";
public static final String SPRING_BEAN_CONVERSION = "spring.conversion.bean.candidate";
```

### Detection Criteria
- Private static instance variable
- Private constructor
- Public static getInstance() method
- Lazy vs eager initialization
- Thread safety (synchronized, volatile, etc.)

### Implementation Approach
Use JavaParser to detect:
1. Static field with class type
2. Private constructor
3. `getInstance()` method returning static field

---

## Inspector 2: UtilityClassInspector

**Priority:** HIGH  
**Effort:** 1.5 hours

### Tags to Produce
```java
public static final String ANTIPATTERN_UTILITY_CLASS = "antipattern.utilityClass";
public static final String UTILITY_ALL_STATIC = "antipattern.utility.allStatic";
public static final String SPRING_COMPONENT_CONVERSION = "spring.conversion.component.candidate";
```

### Detection Criteria
- Class name ends with "Utils", "Util", "Helper", "Helpers"
- All methods are static
- No instance variables
- Private constructor (or no constructor)
- Final class modifier

### Implementation Approach
JavaParser detection:
1. Check class naming pattern
2. Verify all methods are static
3. Check for private/no constructor
4. Count non-static members

---

## Inspector 3: ExceptionAntipatternInspector

**Priority:** HIGH  
**Effort:** 2 hours

### Tags to Produce
```java
public static final String ANTIPATTERN_EXCEPTION_GENERIC = "antipattern.exception.generic";
public static final String ANTIPATTERN_EXCEPTION_SWALLOWED = "antipattern.exception.swallowed";
public static final String ANTIPATTERN_EXCEPTION_MULTIPLE = "antipattern.exception.multipleGeneric";
public static final String REFACTORING_EXCEPTION_SPECIFIC = "refactoring.exception.specific";
```

### Detection Criteria
1. **Multiple Generic Exceptions:**
   - `throws Exception, Exception` pattern
   - Multiple RuntimeException throws
   
2. **Swallowed Exceptions:**
   - Empty catch blocks
   - Catch Exception without rethrowing
   
3. **Overly Generic:**
   - `throws Exception` instead of specific exception
   - `catch (Throwable t)` patterns

### Implementation Approach
JavaParser detection:
1. Analyze method throws clauses
2. Count Exception occurrences
3. Check catch block bodies
4. Identify specific vs generic exceptions

---

## Inspector 4: InheritanceDepthInspector

**Priority:** MEDIUM (most complex)  
**Effort:** 3 hours

### Tags to Produce
```java
public static final String ANTIPATTERN_INHERITANCE_DEEP = "antipattern.inheritance.deep";
public static final String ANTIPATTERN_GOD_CLASS = "antipattern.godClass";
public static final String REFACTORING_COMPOSITION_CANDIDATE = "refactoring.composition.candidate";
```

### Detection Criteria
- Inheritance depth > 3 levels
- God class: > 500 LOC, > 20 methods, > 10 fields
- Multiple inheritance chains
- Complex class hierarchies

### Implementation Approach
Requires graph traversal:
1. Build inheritance hierarchy from class repository
2. Calculate depth for each class
3. Detect god classes by counting members
4. Recommend composition refactoring

### Complexity Note
This inspector is more complex because it requires:
- Graph traversal (ClassNodeRepository)
- Cross-file analysis
- Hierarchy reconstruction

---

## Implementation Order

### Week 1: Easy Wins (4 hours)
1. **SingletonPatternInspector** (2 hours)
2. **UtilityClassInspector** (1.5 hours)
3. Update phase9-10-modernization.yaml (30 min)

### Week 2: Complex Patterns (4 hours)
4. **ExceptionAntipatternInspector** (2 hours)
5. **InheritanceDepthInspector** (3 hours) - Requires graph traversal

### Week 3: Integration & Testing (2 hours)
6. Update appendix-g-antipatterns.yaml
7. Comprehensive testing
8. Documentation

---

## Testing Strategy

### Sample Code for Each Inspector

**Singleton:**
```java
public class DatabaseConnection {
    private static DatabaseConnection instance;
    
    private DatabaseConnection() {}
    
    public static DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }
}
```

**Utility Class:**
```java
public final class StringUtils {
    private StringUtils() {}
    
    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
    
    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
```

**Exception Antipattern:**
```java
public void processData() throws Exception, Exception {  // Multiple generic
    try {
        // code
    } catch (Exception e) {
        // Empty - swallowed
    }
}
```

**Deep Inheritance:**
```java
class A {}
class B extends A {}
class C extends B {}
class D extends C {}  // Depth = 4 (antipattern)
```

---

## Integration with YAML

### Example GRAPH_QUERY Blocks

```yaml
- type: "GRAPH_QUERY"
  name: "query-singleton-patterns"
  description: "Query for Singleton pattern antipatterns (tagged by SingletonPatternInspector)"
  query-type: "BY_TAGS"
  tags:
    - "antipattern.singleton.detected"
  output-variable: "singleton_classes"

- type: "GRAPH_QUERY"
  name: "query-utility-classes"
  description: "Query for static utility class antipatterns (tagged by UtilityClassInspector)"
  query-type: "BY_TAGS"
  tags:
    - "antipattern.utilityClass"
  output-variable: "utility_classes"

- type: "GRAPH_QUERY"
  name: "query-exception-antipatterns"
  description: "Query for exception handling antipatterns (tagged by ExceptionAntipatternInspector)"
  query-type: "BY_TAGS"
  tags:
    - "antipattern.exception.generic"
  output-variable: "exception_antipatterns"

- type: "GRAPH_QUERY"
  name: "query-deep-inheritance"
  description: "Query for deep inheritance hierarchies (tagged by InheritanceDepthInspector)"
  query-type: "BY_TAGS"
  tags:
    - "antipattern.inheritance.deep"
  output-variable: "deep_hierarchies"
```

---

## Success Criteria

- [ ] All 4 antipattern inspectors implemented
- [ ] Tags added to EjbMigrationTags.java
- [ ] Inspectors registered in factory
- [ ] Unit tests pass for each inspector
- [ ] phase9-10-modernization.yaml updated (5 greps replaced)
- [ ] appendix-g-antipatterns.yaml updated (4 greps replaced)
- [ ] Maven compilation succeeds
- [ ] GRAPH_QUERY blocks return expected results

---

## Next Steps After Completion

1. All 26 grep commands will be replaced
2. 100% coverage with GRAPH_QUERY blocks
3. No remaining string pattern matching
4. Complete migration to inspector-based analysis
5. Final validation and testing

---

## Timeline Summary

| Inspector | Effort | Cumulative |
|-----------|--------|------------|
| Singleton | 2h | 2h |
| Utility | 1.5h | 3.5h |
| Exception | 2h | 5.5h |
| Inheritance | 3h | 8.5h |
| Integration | 1.5h | 10h |

**Total: 10 hours over 2-3 days**
