# Migration Path Fix - Action Plan

## Status: IN PROGRESS ⚠️

**Critical Issue:** AI agents were modifying source project instead of refactoring directory  
**Root Cause:** Source paths listed in prompts without explicit write target specification  
**Date Identified:** November 10, 2025

## Phases Fixed ✅

| Phase | File | AI Blocks Fixed | Status |
|-------|------|----------------|--------|
| Phase 2 | phase2-stateless-session-beans.yaml | 2 (batch + compile) | ✅ FIXED |
| Phase 3 | phase3-cmp-entity-beans.yaml | 2 (batch + compile) | ✅ FIXED |
| Phase 4 | phase4-bmp-entity-beans.yaml | 2 (batch + compile) | ✅ FIXED |
| Phase 5 | phase5-stateful-session-beans.yaml | 2 (batch + compile) | ✅ FIXED |
| Phase 6 | phase6-message-driven-beans.yaml | 2 (batch + compile) | ✅ FIXED |

## Phases Requiring Fixes ⚠️

| Phase | File | AI Blocks Found | Priority |
|-------|------|-----------------|----------|
| Phase 0 | phase0-assessment.yaml | 1 AI_ASSISTED | LOW (read-only) |
| Phase 1 | phase1-initialization.yaml | 1 AI_ASSISTED | ✅ OK (POM gen) |
| Phase 7 | phase7-ejb-interfaces-cleanup.yaml | 4 AI_ASSISTED_BATCH + 1 AI_ASSISTED | HIGH |
| Phase 8 | phase8-primary-key-classes.yaml | 1 AI_ASSISTED_BATCH + 1 AI_ASSISTED | HIGH |
| Phase 9 | phase9-jdbc-wrappers.yaml | 1 AI_ASSISTED_BATCH + 1 AI_ASSISTED | HIGH |
| Phase 10 | phase10-rest-apis.yaml | 1 AI_ASSISTED_BATCH + 1 AI_ASSISTED | HIGH |
| Phase 11 | phase11-soap-services.yaml | 1 AI_ASSISTED_BATCH + 1 AI_ASSISTED | HIGH |
| Phase 12 | phase12-antipatterns.yaml | 3 AI_ASSISTED_BATCH + 1 AI_ASSISTED | HIGH |

**Total Remaining:** 7 phase files, ~23 AI blocks to fix

## Fix Pattern Applied

### For AI_ASSISTED_BATCH blocks:

```yaml
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
```

### For AI_ASSISTED compile blocks:

```yaml
**CRITICAL - WORKING DIRECTORY:**
**You are working in the REFACTORING PROJECT at: ${refactoring_project_path}**
**Run all Maven commands in: ${refactoring_project_path}**
**DO NOT run commands in the source project!**

[... rest of tasks ...]

All files should be in ${refactoring_project_path}/src/main/java
Please compile the classes in ${refactoring_project_path} and fix any errors.
```

## Priority Action Items

### HIGH PRIORITY (Must fix before next migration)

1. **Phase 7 - EJB Interfaces Cleanup** (4 batch blocks)
   - Removes Home, Remote, Local interfaces
   - Critical: These write to source if not fixed
   
2. **Phase 8 - Primary Key Classes** (1 batch + 1 compile)
   - Migrates PK classes to @Embeddable
   - Critical: Modifies entity files
   
3. **Phase 9 - JDBC Wrappers** (1 batch + 1 compile)
   - Migrates JDBC DAOs to Spring repositories
   - Critical: Creates new repository files
   
4. **Phase 10 - REST APIs** (1 batch + 1 compile)
   - Migrates JAX-RS to Spring MVC
   - Critical: Controller creation
   
5. **Phase 11 - SOAP Services** (1 batch + 1 compile)
   - Migrates JAX-WS to Spring WS
   - Critical: Endpoint creation
   
6. **Phase 12 - Antipatterns** (3 batch blocks + 1 compile)
   - Modernizes singletons, utilities, exceptions
   - Critical: Cross-cutting refactoring

### MEDIUM PRIORITY

7. **Phase 0 - Assessment** (1 AI_ASSISTED)
   - Only generates documentation
   - Lower risk but should still clarify paths

### LOW PRIORITY

8. **Phase 1 - Initialization** (1 AI_ASSISTED)
   - ✅ Already safe - only edits POM in refactoring dir
   - Working directory correctly specified

## Recommended Approach

### Option A: Batch Fix All (Recommended)
Use search and replace across all remaining files:
1. Search for AI_ASSISTED_BATCH prompt sections
2. Apply standard fix pattern
3. Search for AI_ASSISTED compile sections  
4. Apply compile fix pattern
5. Test with full migration

### Option B: Incremental Fix
Fix phases in order 7→12, testing after each:
1. Fix phase 7 (interfaces)
2. Test migration up to phase 7
3. Fix phase 8 (primary keys)
4. Test migration up to phase 8
5. Continue...

### Option C: Create Fix Script
Create a script to automatically apply fixes:
- Pattern matching for AI block signatures
- Insert CRITICAL sections
- Validate YAML syntax
- Commit changes

## Testing Checklist

After all fixes applied:

- [ ] Clean source project: `git checkout demo-ejb2-project/src/`
- [ ] Clean refactoring: `rm -rf /tmp/example`
- [ ] Clean analysis: `rm -rf demo-ejb2-project/.analysis/`
- [ ] Run migration with --dry-run
- [ ] Verify logs show correct paths
- [ ] Check source unchanged: `git status demo-ejb2-project/`
- [ ] Check refactoring populated: `ls -la /tmp/example/src/`
- [ ] Run actual migration
- [ ] Verify compilation in refactoring dir only

## Success Criteria

✅ All AI_ASSISTED_BATCH blocks include:
- CRITICAL section at top
- READ-ONLY source reference
- Explicit WRITE target paths

✅ All AI_ASSISTED compile blocks include:
- CRITICAL working directory section
- Explicit ${refactoring_project_path} in commands
- Reminder about target location

✅ Testing confirms:
- Source project untouched
- All files in ${refactoring_project_path}
- No compilation of source project
- Migration can be re-run safely

## Next Steps

**Immediate:**
1. Complete fixes for phases 7-12 (23 blocks remaining)
2. Run validation script on all phase files
3. Test migration end-to-end
4. Update migration documentation

**Follow-up:**
1. Add automated validation in CI/CD
2. Create phase file templates with fixes built-in
3. Document pattern in migration guide
4. Consider adding runtime path validation

## Files Modified So Far

1. ✅ migrations/ejb2spring/phases/phase2-stateless-session-beans.yaml
2. ✅ migrations/ejb2spring/phases/phase3-cmp-entity-beans.yaml
3. ✅ migrations/ejb2spring/phases/phase4-bmp-entity-beans.yaml
4. ✅ migrations/ejb2spring/phases/phase5-stateful-session-beans.yaml
5. ✅ migrations/ejb2spring/phases/phase6-message-driven-beans.yaml
6. ✅ docs/implementation/migration-path-fix-summary.md
7. ✅ docs/implementation/MIGRATION_PATH_FIX_ACTION_PLAN.md (this file)

## Files Pending

1. ⏳ migrations/ejb2spring/phases/phase7-ejb-interfaces-cleanup.yaml (5 blocks)
2. ⏳ migrations/ejb2spring/phases/phase8-primary-key-classes.yaml (2 blocks)
3. ⏳ migrations/ejb2spring/phases/phase9-jdbc-wrappers.yaml (2 blocks)
4. ⏳ migrations/ejb2spring/phases/phase10-rest-apis.yaml (2 blocks)
5. ⏳ migrations/ejb2spring/phases/phase11-soap-services.yaml (2 blocks)
6. ⏳ migrations/ejb2spring/phases/phase12-antipatterns.yaml (4 blocks)
7. ⏳ migrations/ejb2spring/phases/phase0-assessment.yaml (1 block - low priority)

**Estimated Time to Complete:** 45-60 minutes (all remaining fixes)

## Risk Assessment

### Current State
- **5 phases fixed** = Phases 2-6 are safe to run
- **7 phases risky** = Phases 7-12 + phase 0 will modify source project
- **Migration incomplete** = Cannot safely run full migration yet

### Impact if Not Fixed
- ❌ Source project corruption continues
- ❌ Git history contaminated with partial migrations
- ❌ Migration must be manually reverted
- ❌ Time wasted debugging wrong directory issues
- ❌ Risk of data loss or project corruption

### Impact After Fixed
- ✅ Clean separation of source and target
- ✅ Idempotent migrations (can re-run safely)
- ✅ Source project preserved as reference
- ✅ Git-friendly migration process
- ✅ Confident deployment to production

## Decision Point

**Recommendation:** Complete all fixes (phases 7-12) before running any migrations, even in dry-run mode. The current state has fixed only 5 of 13 phases, leaving significant risk of source corruption if later phases execute.
