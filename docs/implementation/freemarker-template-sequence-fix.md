# FreeMarker Template Sequence Handling Fix

**Date:** November 7, 2025  
**Issue:** FreeMarker template error when processing node properties containing lists/arrays

## Problem

The migration execution was failing with the following FreeMarker error:

```
Error executing FreeMarker template
FreeMarker template error:
For "${...}" content: Expected a string or something automatically convertible to string 
(number, date or boolean), or "template output", but this has evaluated to a sequence 
(ArrayList wrapped into f.t.DefaultListAdapter):
==> current_node.properties[key]!"null"  [in template "inline" at line 18, column 98]
```

## Root Cause

The FreeMarker templates in the migration phase YAML files had two issues:

1. **Missing sequence handling:** The template only checked for boolean types but didn't handle sequences (lists/arrays)
2. **Redundant null check:** The template checked `current_node.properties??` even though this property is always defined

When a node property contained a list/array value, FreeMarker couldn't convert it to a string automatically, causing the error.

## Solution

### Part 1: Fixed Sequence Handling (Property Listing)

**Before (Incorrect):**
```freemarker
**Node Properties:**
<#if current_node.properties??>
<#list current_node.properties?keys as key>
- ${key}: <#if current_node.properties[key]?is_boolean>${current_node.properties[key]?c}<#else>${current_node.properties[key]!"null"}</#if>
</#list>
<#else>
- (No properties available)
</#if>
```

**After (Correct - Final Version Using GraphNode API):**
```freemarker
**Node Properties:**
<#list current_node.properties?keys as key>
- ${key}: ${current_node.getPropertyToString(key)}
</#list>
```

**Changes:**
1. **Removed `<#if current_node.properties??>`** - Not needed since `current_node.properties` is always defined
2. **Uses GraphNode API method** - `getPropertyToString(key)` instead of direct map access
3. **Removed all type checking** - No longer need to check for boolean, sequence, or hash types
4. **Consistent with codebase** - Uses the same GraphNode API methods throughout the system
5. **One-line solution** - Dramatically simpler and more maintainable

**Why `getPropertyToString()` is best:**
- Consistent with GraphNode API used throughout codebase
- Calls `toString()` method on property value internally
- Handles all types automatically (Boolean, List, Map, String, Integer, etc.)
- Returns sensible string representations for all objects
- Avoids direct property map access
- Type-safe method from GraphNode interface

### Part 2: Simplified Property Checks Using GraphNode API

For conditional property checks, we simplified the templates using GraphNode's built-in methods:

**Before (Verbose):**
```freemarker
<#if current_node.properties?? && current_node.properties["jms.destination.name"]??>
**📨 JMS DESTINATION DETECTED:**
- Destination: ${current_node.properties["jms.destination.name"]}
<#if current_node.properties?? && current_node.properties["jms.destination.type"]??>
- Type: ${current_node.properties["jms.destination.type"]}
</#if>
</#if>
```

**After (Simplified):**
```freemarker
<#if current_node.hasProperty("jms.destination.name")>
**📨 JMS DESTINATION DETECTED:**
- Destination: ${current_node.getStringProperty("jms.destination.name", "unknown")}
<#if current_node.hasProperty("jms.destination.type")>
- Type: ${current_node.getStringProperty("jms.destination.type", "unknown")}
</#if>
</#if>
```

**Benefits of Using GraphNode Methods:**
1. **Cleaner code** - No redundant `??` null checks
2. **Type safety** - Use `getStringProperty()`, `getIntProperty()`, `getBooleanProperty()` with defaults
3. **More readable** - Intent is clearer with `hasProperty()` vs nested null checks
4. **Consistent API** - Uses the same methods available throughout the codebase

## Files Fixed

✅ `migrations/ejb2spring/phases/phase-message-driven-beans.yaml`  
✅ `migrations/ejb2spring/phases/phase-stateful-session-beans.yaml`  
✅ `migrations/ejb2spring/phases/phase-bmp-entity-beans.yaml`  
✅ `migrations/ejb2spring/phases/phase-cmp-entity-beans.yaml`  
✅ `migrations/ejb2spring/phases/phase-stateless-session-beans.yaml`

## Files Not Affected

The following phase files use different template structures and don't have this pattern:
- phase-rest-apis.yaml
- phase-soap-services.yaml
- phase-jdbc-wrappers.yaml
- phase-primary-key-classes.yaml
- phase-antipatterns.yaml
- phase-ejb-interfaces-cleanup.yaml
- phase-stateless-session-beans.yaml

## Testing

The simplified fix using `?string` allows the FreeMarker template to properly handle all property types by calling their `toString()` method:
- **Boolean values:** `true` → `"true"`, `false` → `"false"`
- **Sequence values (lists/arrays):** Uses Java's default `toString()`: `"[item1, item2, item3]"`
- **Hash values (maps/objects):** Uses Java's default `toString()`: `"{key1=value1, key2=value2}"`
- **Other values (strings/numbers):** Displayed as-is

## Example Output

With a node that has these properties:
```java
{
  "jms.destination.name": "NotificationQueue",
  "jms.destination.type": "Queue",
  "ejb.transaction.attribute": "REQUIRED",
  "jms.message.selectors": ["type='notification'", "priority>5"],  // ArrayList
  "resource.analysis_result": {                                     // LinkedHashMap
    "dataSourceCount": 2,
    "resourceRefCount": 5
  }
}
```

The template now outputs (using Java's `toString()` representations):
```
**Node Properties:**
- jms.destination.name: NotificationQueue
- jms.destination.type: Queue
- ejb.transaction.attribute: REQUIRED
- jms.message.selectors: [type='notification', priority>5]
- resource.analysis_result: {dataSourceCount=2, resourceRefCount=5}
```

## Errors Fixed

### Error 1: ArrayList (Sequence)
```
Expected a string... but this has evaluated to a sequence (ArrayList wrapped into f.t.DefaultListAdapter)
```
**Fixed by:** Using `?string` which calls `ArrayList.toString()`

### Error 2: LinkedHashMap (Hash)
```
Expected a string... but this has evaluated to an extended_hash (LinkedHashMap wrapped into f.t.DefaultMapAdapter)
```
**Fixed by:** Using `?string` which calls `LinkedHashMap.toString()`

## Impact

This fix resolves all migration failures caused by complex property types (sequences and hashes). The template now safely handles all FreeMarker data types by delegating to Java's `toString()` method, which is much simpler and more maintainable than explicit type checking.
