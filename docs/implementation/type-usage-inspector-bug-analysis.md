# TypeUsageInspector Bug Analysis

## Issue
The `METRIC_TYPES_FIELD_COUNT` (and similar metrics for parameters and return types) are reporting low values because generic type arguments are not being counted in their respective categories.

## Root Cause

### Current Behavior
When analyzing a field like:
```java
private List<String> items;
private Map<Integer, Customer> customers;
```

The current code:
1. Adds `List` and `Map` to `fieldTypes` via `addFieldType()`
2. Analyzes generic parameters (`String`, `Integer`, `Customer`) via `analyzeParameterizedTypeSafe()` 
3. These generic arguments are added ONLY to `genericTypes`, NOT to `fieldTypes`

**Result:**
- `types.field_count` = 2 (only List and Map)
- `types.generic_count` = 5 (List, Map, String, Integer, Customer)
- Generic arguments (String, Integer, Customer) are missing from field count

### Same Issue in Other Methods
The bug affects:
- **Field types**: Generic arguments not counted as field types
- **Return types**: Generic arguments not counted as return types
- **Parameter types**: Generic arguments not counted as parameter types

### Code Analysis

In `analyzeFieldTypes()` (line 143):
```java
private void analyzeFieldTypes(Class<?> clazz, TypeUsageMetrics metrics) {
    for (Field field : clazz.getDeclaredFields()) {
        Class<?> fieldType = field.getType();
        metrics.addFieldType(fieldType);  // ✓ Adds raw type to fieldTypes
        
        Type genericType = field.getGenericType();
        Set<Type> visitedTypes = new HashSet<>();
        if (genericType instanceof ParameterizedType) {
            // ✗ Generic arguments added to genericTypes, NOT fieldTypes
            analyzeParameterizedTypeSafe((ParameterizedType) genericType, metrics, visitedTypes, 0);
        }
    }
}
```

In `analyzeParameterizedTypeSafe()` (line 298):
```java
private void analyzeParameterizedTypeSafe(ParameterizedType paramType, TypeUsageMetrics metrics,
        Set<Type> visitedTypes, int depth) {
    // ...
    Type rawType = paramType.getRawType();
    if (rawType instanceof Class<?>) {
        metrics.addGenericType((Class<?>) rawType);  // ✗ Only adds to genericTypes
    }
    
    for (Type typeArg : paramType.getActualTypeArguments()) {
        analyzeGenericTypeArgumentSafe(typeArg, metrics, visitedTypes, depth + 1);
        // ✗ Type arguments also only added to genericTypes
    }
}
```

## Solution

We need to track the **context** (field/parameter/return) when analyzing generic types and add them to the appropriate sets.

### Approach 1: Add Context Enum (Recommended)
Pass a context parameter through the generic type analysis chain:

```java
enum TypeContext {
    FIELD, PARAMETER, RETURN, OTHER
}

private void analyzeFieldTypes(Class<?> clazz, TypeUsageMetrics metrics) {
    for (Field field : clazz.getDeclaredFields()) {
        Class<?> fieldType = field.getType();
        metrics.addFieldType(fieldType);
        
        Type genericType = field.getGenericType();
        Set<Type> visitedTypes = new HashSet<>();
        if (genericType instanceof ParameterizedType) {
            analyzeParameterizedTypeSafe((ParameterizedType) genericType, metrics, 
                visitedTypes, 0, TypeContext.FIELD);  // Pass context
        }
    }
}

private void analyzeParameterizedTypeSafe(ParameterizedType paramType, TypeUsageMetrics metrics,
        Set<Type> visitedTypes, int depth, TypeContext context) {
    // ...
    Type rawType = paramType.getRawType();
    if (rawType instanceof Class<?>) {
        Class<?> rawClass = (Class<?>) rawType;
        metrics.addGenericType(rawClass);
        // Also add to context-specific set
        switch (context) {
            case FIELD: metrics.addFieldType(rawClass); break;
            case PARAMETER: metrics.addParameterType(rawClass); break;
            case RETURN: metrics.addReturnType(rawClass); break;
        }
    }
    
    for (Type typeArg : paramType.getActualTypeArguments()) {
        analyzeGenericTypeArgumentSafe(typeArg, metrics, visitedTypes, depth + 1, context);
    }
}
```

### Approach 2: Separate Methods for Each Context
Create separate methods for analyzing generic types in different contexts:
- `analyzeFieldGenericType()`
- `analyzeParameterGenericType()`
- `analyzeReturnGenericType()`

This is more verbose but clearer.

## Impact

### Before Fix
```
Field: List<String> items
types.field_count = 1  (only List)
types.generic_count = 2 (List, String)
```

### After Fix
```
Field: List<String> items
types.field_count = 2  (List AND String)
types.generic_count = 2 (List, String)
```

## Test Cases Needed

1. Simple generic field: `private List<String> items;`
   - Should count both List and String as field types

2. Nested generics: `private Map<String, List<Integer>> data;`
   - Should count Map, String, List, Integer as field types

3. Generic method parameters: `void process(List<Customer> customers)`
   - Should count List and Customer as parameter types

4. Generic return types: `Map<String, Integer> getData()`
   - Should count Map, String, Integer as return types

5. Wildcard types: `List<? extends Number> numbers`
   - Should count List and Number (from bound) as field types
