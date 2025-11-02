# GRAPH_QUERY Tag Audit - All Phase Files

**Date:** 2025-11-02
**Purpose:** Verify all GRAPH_QUERY blocks use correct inspector-generated tags

## ✅ Files with GRAPH_QUERY Blocks

### Phase 0: Assessment (phase0-assessment.yaml)
- ✅ query-all-ejb-components
- ✅ query-stateless-beans  
- ✅ query-cmp-entities
- ✅ query-message-driven-beans

### Phase 2: JDBC Migration (phase2-jdbc-migration.yaml)
- ✅ query-jdbc-data-access-classes (ALREADY FIXED)

### Phase 2B: Entity Beans (phase2b-entity-beans.yaml)
- ❓ query-cmp-entity-beans (needs verification)
- ❓ query-bmp-entity-beans (needs verification)

### Phase 3: Session Beans (phase3-session-beans.yaml)
- ❓ query-stateless-session-beans (needs verification)
- ❓ query-stateful-session-beans (needs verification)

### Phase 3B-3C: EJB Cleanup (phase3b-3c-ejb-cleanup.yaml)
- ❓ query-message-driven-beans (needs verification)

## 📋 Correct Tag Names from EjbMigrationTags.java

### Session Beans
- `ejb.sessionBean` (general)
- `ejb.stateless.sessionBean` (stateless)
- `ejb.stateful.sessionBean` (stateful)

### Entity Beans
- `ejb.entityBean` (general)
- `ejb.cmp.entityBean` (CMP)
- `ejb.bmp.entityBean` (BMP)

### Message-Driven Beans
- `ejb.messageDrivenBean`

### Data Access
- `dataAccess.layer` (JDBC wrappers)

### Spring Conversion Tags
- `spring.conversion.service`
- `spring.conversion.component`
- `jpa.conversion.entity`

## 🔍 Verification Needed

Need to verify each file uses the exact tag strings from EjbMigrationTags.java.
All tag queries must match the constants defined in the inspector code.

## 📝 Action Items

1. Check phase0-assessment.yaml tags
2. Check phase2b-entity-beans.yaml tags  
3. Check phase3-session-beans.yaml tags
4. Check phase3b-3c-ejb-cleanup.yaml tags
5. Update any incorrect tag names to match EjbMigrationTags constants
