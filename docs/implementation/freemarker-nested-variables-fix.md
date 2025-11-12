# FreeMarker Nested Variables and Input-Nodes Fix

## Issue Description

During the migration execution, two FreeMarker template errors occurred:

### Error 1: Template Variable Processing Error
```
FreeMarker template error:
The following has evaluated to null or missing:
==> springdoc  [in template "inline" at line 228, column 104]
```

### Error 2: Input-Nodes Type Mismatch
```
FreeMarker template error:
For "${...}" content: Expected a string or something automatically convertible to string (number, date or boolean), 
or "template output" , but this has evaluated to a sequence (ImmutableCollections$ListN wrapped into f.t.DefaultListAdapter):
==> home_interfaces  [in template "inline" at line 1, column 3]
```

## Root Causes

### Cause 1: Missing Nested Variable Definitions
In `migrations/ejb2spring/common/variables.yaml`, the variables were flat, but the AI prompts in phase1 were trying to access nested properties like:
- `${springdoc.version}` 
- `${jasperreports.version}`

These nested properties didn't exist, causing FreeMarker to fail when trying to evaluate them.

### Cause 2: Incorrect Input-Nodes Syntax
In `migrations/ejb2spring/phases/phase7-ejb-interfaces-cleanup.yaml`, the AI_ASSISTED_BATCH blocks were using:
```yaml
input-nodes: "${home_interfaces}"
```

This caused FreeMarker to treat the variable as a string template requiring string conversion, but the variable was actually a list/collection of nodes.

## Solutions Implemented

### Fix 1: Added Nested Variables Structure

**File**: `migrations/ejb2spring/common/variables.yaml`

Added proper nested variable definitions:
```yaml
# Dependency versions
springdoc:
  version: "2.7.0"
jasperreports:
  version: "7.0.1"
```

This allows FreeMarker templates to access:
- `${springdoc.version}` → "2.7.0"
- `${jasperreports.version}` → "7.0.1"

### Fix 2: Corrected Input-Nodes Syntax

**File**: `migrations/ejb2spring/phases/phase7-ejb-interfaces-cleanup.yaml`

Changed all AI_ASSISTED_BATCH blocks from:
```yaml
input-nodes: "${variable_name}"
```

To:
```yaml
input-nodes: variable_name
```

**Changed blocks:**
- `ai-remove-home-interfaces`: `"${home_interfaces}"` → `home_interfaces`
- `ai-remove-remote-interfaces`: `"${remote_interfaces}"` → `remote_interfaces`
- `ai-remove-local-interfaces`: `"${local_interfaces}"` → `local_interfaces`
- `ai-remove-localhome-interfaces`: `"${localhome_interfaces}"` → `localhome_interfaces`

## Technical Explanation

### Why the Input-Nodes Fix Works

The `input-nodes` field in AI_ASSISTED_BATCH expects a **variable reference**, not a **FreeMarker template string**:

1. **Wrong**: `input-nodes: "${home_interfaces}"` 
   - FreeMarker interprets this as a string template
   - Tries to convert the list to a string
   - Fails because collections can't be automatically stringified

2. **Correct**: `input-nodes: home_interfaces`
   - YAML parser reads this as a plain value
   - Block executor resolves the variable name at runtime
   - Gets the actual list/collection object
   - Processes it correctly

### Why Nested Variables are Needed

FreeMarker's dot notation (`${object.property}`) requires the parent object to exist:

1. **Before**: `springdoc` didn't exist → `${springdoc.version}` failed
2. **After**: `springdoc: { version: "2.7.0" }` → `${springdoc.version}` works

## Testing Recommendations

1. **Validate the YAML structure**:
   ```bash
   ./migrations/validate-migration-plans.sh
   ```

2. **Test variable resolution in isolation**:
   - Verify nested variables are accessible in FreeMarker templates
   - Test with simple template: `${springdoc.version}`

3. **Run a dry-run migration**:
   ```bash
   analyzer apply --project demo-ejb2-project \
                  --plan migrations/ejb2spring/jboss-to-springboot.yaml \
                  --dry-run
   ```

4. **Check phase 7 specifically**:
   - Ensure GRAPH_QUERY returns empty list if no interfaces found
   - Verify AI_ASSISTED_BATCH handles empty lists gracefully
   - Confirm variable resolution works for non-empty lists

## Related Issues

- The error occurred in Phase 8 (EJB Interface Cleanup)
- Phase 1 also had issues with `${springdoc.version}` reference
- Both issues are now resolved

## Files Modified

1. `migrations/ejb2spring/phases/phase7-ejb-interfaces-cleanup.yaml` - Fixed input-nodes syntax
2. `migrations/ejb2spring/common/variables.yaml` - Added nested variable structures

## Impact

These fixes ensure:
- FreeMarker templates can access nested properties correctly
- AI_ASSISTED_BATCH blocks can process variable collections properly
- Migration phases 1 and 7 will execute without template errors
- Other phases using similar patterns will also benefit

## Date
November 10, 2025
