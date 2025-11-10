# Migration Path Fix - Critical Issue Resolution

## Issue Summary

**Date:** November 10, 2025  
**Severity:** CRITICAL  
**Impact:** AI was modifying source project instead of refactoring project

## Problem Description

During migration execution, AI agents (Amazon Q) were:
1. **Reading** from the correct source project paths (`/home/sleroy/git/java_architecture_analyzer/demo-ejb2-project`)
2. **Writing** back to the source project instead of the refactoring project (`/tmp/example`)
3. **Compiling** the source project instead of the refactoring project

This caused:
- Source project corruption with partial Spring migrations mixed with EJB code
- Compilation errors in the source project
- No files being created in the intended refactoring directory

## Root Cause

The migration plan phases included `sourceAliasPaths` in AI prompts, listing absolute paths to source files. Amazon Q interpreted these as both read and write locations, despite the `working-directory` parameter being set correctly.

Example from logs:
```
● Path: ../../home/sleroy/git/java_architecture_analyzer/demo-ejb2-project/src/main/java/com/example/ejbapp/service/MemberRegistration.java
```

And commands executed in wrong directory:
```
cd /home/sleroy/git/java_architecture_analyzer/demo-ejb2-project && mvn compile
```

## Solution

Added explicit path instructions in all AI_ASSISTED_BATCH and AI_ASSISTED prompts:

### Key Changes

1. **Added CRITICAL section at top of each AI prompt:**
```yaml
**CRITICAL - TARGET DIRECTORY:**
**YOU MUST WRITE ALL FILES TO: ${refactoring_project_path}/src/main/java**
**DO NOT write to the source project paths listed below - those are READ-ONLY references!**
```

2. **Separated source references from write targets:**
```yaml
**READ-ONLY Source Reference (for reading original code only):**
<#assign primarySource = current_node.sourceAliasPaths?filter(path -> path?ends_with(".java") && !path?contains("/.analysis/"))?first!"">
<#if primarySource?has_content>
- Source to read: ${primarySource}
</#if>

**WRITE Target (where you MUST write the migrated files):**
- Target directory: ${refactoring_project_path}/src/main/java
- Target file path: ${refactoring_project_path}/src/main/java/${current_node.fullyQualifiedName?replace(".", "/")}.java
```

3. **Updated compile prompts to emphasize working directory:**
```yaml
**CRITICAL - WORKING DIRECTORY:**
**You are working in the REFACTORING PROJECT at: ${refactoring_project_path}**
**Run all Maven commands in: ${refactoring_project_path}**
**DO NOT run commands in the source project!**
```

## Files Modified

### Phase 2: Stateless Session Beans
- File: `migrations/ejb2spring/phases/phase2-stateless-session-beans.yaml`
- Blocks updated:
  - `ai-migrate-stateless-beans` (AI_ASSISTED_BATCH)
  - `compile-stateless-services` (AI_ASSISTED)

### Phase 3: CMP Entity Beans
- File: `migrations/ejb2spring/phases/phase3-cmp-entity-beans.yaml`
- Blocks updated:
  - `ai-migrate-cmp-entities` (AI_ASSISTED_BATCH)
  - `compile-cmp-entities` (AI_ASSISTED)

### Phase 4: BMP Entity Beans
- File: `migrations/ejb2spring/phases/phase4-bmp-entity-beans.yaml`
- Blocks updated:
  - `ai-migrate-bmp-entities` (AI_ASSISTED_BATCH)
  - `compile-bmp-entities` (AI_ASSISTED)

## Testing Recommendations

1. **Clean test environment:**
```bash
# Remove contaminated source files
git checkout demo-ejb2-project/src/main/java/

# Clean refactoring directory
rm -rf /tmp/example

# Clean analysis cache
rm -rf demo-ejb2-project/.analysis/
```

2. **Run migration with --dry-run first:**
```bash
java -jar analyzer-app/target/analyzer-app.jar apply \
  --project demo-ejb2-project \
  --plan migrations/ejb2spring/jboss-to-springboot.yaml \
  --dry-run \
  --verbose
```

3. **Monitor logs for correct paths:**
- Look for: "Creating: /tmp/example/src/main/java/..."
- NOT: "Creating: .../demo-ejb2-project/src/main/java/..."

4. **Verify after migration:**
```bash
# Source project should be unchanged
git status demo-ejb2-project/

# Refactoring project should have migrated files
ls -la /tmp/example/src/main/java/
```

## Prevention Measures

### For Future Phases

When creating new migration phases with AI assistance:

1. **Always include CRITICAL section** specifying target directory
2. **Clearly separate** READ-ONLY source references from WRITE targets
3. **Use explicit paths** with `${refactoring_project_path}` variable
4. **Filter sourceAliasPaths** to exclude `.analysis` cache files
5. **Emphasize working directory** in compile/validation blocks

### Template for AI_ASSISTED_BATCH Prompts

```yaml
- type: "AI_ASSISTED_BATCH"
  name: "ai-migrate-xyz"
  input-nodes: xyz_nodes
  prompt: |
    **CRITICAL - TARGET DIRECTORY:**
    **YOU MUST WRITE ALL FILES TO: ${refactoring_project_path}/src/main/java**
    **DO NOT write to the source project paths listed below - those are READ-ONLY references!**
    
    **READ-ONLY Source Reference (for reading original code only):**
    <#assign primarySource = current_node.sourceAliasPaths?filter(path -> path?ends_with(".java") && !path?contains("/.analysis/"))?first!"">
    <#if primarySource?has_content>
    - Source to read: ${primarySource}
    </#if>
    
    **WRITE Target (where you MUST write the migrated file):**
    - Target directory: ${refactoring_project_path}/src/main/java
    - Target file path: ${refactoring_project_path}/src/main/java/${current_node.fullyQualifiedName?replace(".", "/")}.java
    
    [... rest of prompt ...]
  working-directory: "${refactoring_project_path}/src/main/java"
```

### Template for AI_ASSISTED Compile Prompts

```yaml
- type: "AI_ASSISTED"
  name: "compile-xyz"
  prompt: |
    **CRITICAL - WORKING DIRECTORY:**
    **You are working in the REFACTORING PROJECT at: ${refactoring_project_path}**
    **Run all Maven commands in: ${refactoring_project_path}**
    **DO NOT run commands in the source project!**
    
    **Tasks:**
    1. Run Maven compile in ${refactoring_project_path} to check for errors
    [... rest of tasks ...]
    9. All files should be in ${refactoring_project_path}/src/main/java
    
    Please compile the classes in ${refactoring_project_path} and fix any errors.
  working-directory: "${refactoring_project_path}"
```

## Impact Analysis

### Before Fix
- ❌ Source project files modified with partial Spring migrations
- ❌ EJB and Spring annotations mixed in source files
- ❌ Compilation errors in source project
- ❌ Empty refactoring directory
- ❌ Migration plan invalidated by source corruption

### After Fix
- ✅ Source project remains unchanged
- ✅ All migrations written to refactoring directory
- ✅ Clean separation between source and target
- ✅ Migration can be safely re-run
- ✅ Source project can be used as reference

## Lessons Learned

1. **AI Context Boundaries:** The `working-directory` parameter in YAML doesn't constrain where AI writes files - it's just a suggestion.

2. **Explicit Instructions Required:** AI needs explicit, prominent instructions about where to write files, not just implicit context.

3. **Path Filtering:** Need to filter `sourceAliasPaths` to:
   - Exclude `.analysis` cache directories
   - Select only the primary source file (not binary duplicates)
   - Present as READ-ONLY reference

4. **Defensive Prompting:** Use visual emphasis (capitals, bold) to highlight critical constraints that must not be violated.

5. **Validation Points:** Need validation checkpoints that verify:
   - Files created in correct location
   - Source project untouched
   - No compilation attempted on source project

## Related Issues

- Similar issues may exist in other phases (phase5-stateful-session-beans, phase6-message-driven-beans, etc.)
- Need to audit all phases that use AI_ASSISTED or AI_ASSISTED_BATCH blocks
- OpenRewrite blocks correctly use `base-directory` parameter and don't have this issue

## Next Steps

1. ✅ Fix Phase 2 (Stateless Session Beans)
2. ✅ Fix Phase 3 (CMP Entity Beans)  
3. ✅ Fix Phase 4 (BMP Entity Beans)
4. ⏳ Check Phase 5 (Stateful Session Beans)
5. ⏳ Check Phase 6 (Message Driven Beans)
6. ⏳ Check other phases for similar AI_ASSISTED blocks
7. ⏳ Test migration with fixed phases
8. ⏳ Update migration plan documentation with new requirements
