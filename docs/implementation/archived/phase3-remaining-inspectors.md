# Phase 3: Remaining Inspector Migrations

**Date:** 2025-10-20  
**Status:** READY TO START  
**Progress:** 6/10 Complete (4 remaining)

## Overview

This document tracks the remaining 4 ASM inspector migrations to complete Phase 3. The migration pattern is proven and documented. Each inspector follows the same 8-step checklist.

## Remaining Inspectors

### Batch 3: EJB Analysis (3 inspectors)

#### 1. EjbBinaryClassInspectorV2 🔴 HIGH PRIORITY

**Original:** `src/main/java/com/analyzer/rules/ejb2spring/EjbBinaryClassInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/EjbBinaryClassInspectorV2.java`  
**Complexity:** HIGH (450+ lines, most complex inspector)

**Current Functionality:**
- Detects EJB 3.x annotation-based beans (@Stateless, @Stateful, @Entity, @MessageDriven)
- Detects EJB 2.x interface-based beans (SessionBean, EntityBean, MessageDrivenBean)
- Detects EJB standard interfaces (EJBHome, EJBObject, EJBLocalHome, EJBLocalObject)
- Supports both javax.ejb and jakarta.ejb namespaces
- Sets 18 different EJB-related tags for migration planning

**Migration Strategy:**
- Follow proven pattern from ClassMetricsInspectorV2
- Maintain all detection logic (comprehensive EJB coverage)
- Write detection results to JavaClassNode properties
- Keep tag setting on ProjectFile for dependency resolution
- Consider consolidating analysis results into structured POJOs

**Key Challenge:**
- Large visitor class with multiple analysis methods
- Complex conditional logic for different EJB types
- Need to maintain backward compatibility with existing tags

**Estimated Effort:** 2-3 hours

---

#### 2. StatefulSessionStateInspectorV2 🟡 MEDIUM PRIORITY

**Original:** `src/main/java/com/analyzer/rules/ejb2spring/StatefulSessionStateInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/StatefulSessionStateInspectorV2.java`  
**Complexity:** MEDIUM

**Current Functionality:**
- Analyzes stateful session bean state management patterns
- Detects passivation/activation callbacks
- Identifies state field usage patterns
- Provides Spring @Scope migration recommendations

**Migration Strategy:**
- Standard pattern: extend AbstractASMClassInspector
- Write state analysis to JavaClassNode properties
- Maintain tag compatibility for dependency chains

**Estimated Effort:** 1 hour

---

#### 3. EjbCreateMethodUsageInspectorV2 🟡 MEDIUM PRIORITY

**Original:** `src/main/java/com/analyzer/rules/ejb2spring/EjbCreateMethodUsageInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/EjbCreateMethodUsageInspectorV2.java`  
**Complexity:** MEDIUM

**Current Functionality:**
- Detects EJB create method usage patterns
- Analyzes home interface create calls
- Identifies factory pattern opportunities for Spring migration

**Migration Strategy:**
- Standard pattern: extend AbstractASMClassInspector
- Write create method analysis to JavaClassNode properties
- Enable tags for factory pattern detection

**Estimated Effort:** 1 hour

---

### Batch 4: Pattern Detection (2 inspectors)

#### 4. JdbcDataAccessPatternInspectorV2 🔴 HIGH PRIORITY

**Original:** `src/main/java/com/analyzer/rules/ejb2spring/JdbcDataAccessPatternInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/JdbcDataAccessPatternInspectorV2.java`  
**Complexity:** HIGH

**Current Functionality:**
- Detects JDBC data access patterns
- Analyzes Connection, Statement, ResultSet usage
- Identifies resource leak risks
- Provides Spring JDBC/JPA migration recommendations

**Migration Strategy:**
- Follow proven pattern
- Write JDBC analysis results to JavaClassNode properties
- Consider creating JdbcAnalysisResult POJO for structured data
- Maintain tags for Spring migration recommendations

**Estimated Effort:** 1.5 hours

---

#### 5. ProgrammaticTransactionUsageInspectorV2 🟡 MEDIUM PRIORITY

**Original:** `src/main/java/com/analyzer/rules/ejb2spring/ProgrammaticTransactionUsageInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/ProgrammaticTransactionUsageInspectorV2.java`  
**Complexity:** MEDIUM

**Current Functionality:**
- Detects programmatic transaction management (UserTransaction usage)
- Analyzes transaction boundary patterns
- Provides @Transactional migration recommendations

**Migration Strategy:**
- Standard pattern: extend AbstractASMClassInspector
- Write transaction analysis to JavaClassNode properties
- Enable tags for Spring @Transactional recommendations

**Estimated Effort:** 1 hour

---

## Migration Checklist (Apply to Each Inspector)

### Step 1: Preparation
- [ ] Read original inspector to understand functionality
- [ ] Identify all metrics/properties being set
- [ ] Note any complex logic or special cases

### Step 2: Create V2 File
- [ ] Create `[InspectorName]V2.java` in same package
- [ ] Copy original file as starting point

### Step 3: Update Class Declaration
```java
// Change extends clause
public class [Name]V2 extends AbstractASMClassInspector
```

### Step 4: Update Constructor
```java
@Inject
public [Name]V2(ProjectFileRepository projectFileRepository,
                ResourceResolver resourceResolver) {
    super(projectFileRepository, resourceResolver);
}
```

### Step 5: Update createClassVisitor Method
```java
@Override
protected ASMClassNodeVisitor createClassVisitor(
    JavaClassNode classNode,
    NodeDecorator<JavaClassNode> decorator) {
    return new [Name]Visitor(classNode, decorator);
}
```

### Step 6: Update Inner Visitor Class
```java
private static class [Name]Visitor extends ASMClassNodeVisitor {
    protected [Name]Visitor(JavaClassNode classNode,
                           NodeDecorator<JavaClassNode> decorator) {
        super(classNode, decorator);
    }
    
    // Update all method signatures as needed
}
```

### Step 7: Update Property/Tag Writing
```java
// OLD:
setTag(SOME_TAG, value);
projectFile.setProperty(SOME_PROP, value);

// NEW:
setProperty("property.name", value);  // For metrics/data
enableTag(SOME_TAG);                  // For dependency resolution (optional)
```

### Step 8: Update JavaDoc
- [ ] Add Phase 3 migration note
- [ ] Document key differences from original
- [ ] Explain architectural benefits

### Step 9: Verification
- [ ] Compile with `mvn compile -DskipTests`
- [ ] Verify no errors for new V2 file
- [ ] Check that property names follow conventions

---

## Success Criteria

### Per Inspector
- ✅ V2 file compiles successfully
- ✅ Extends AbstractASMClassInspector
- ✅ Constructor injects ProjectFileRepository + ResourceResolver
- ✅ createClassVisitor returns ASMClassNodeVisitor
- ✅ Properties written to JavaClassNode (not ProjectFile)
- ✅ Tags enabled only if needed for dependencies
- ✅ JavaDoc updated with migration notes

### Overall Phase 3
- ✅ All 10 inspectors have V2 versions
- ✅ All V2 versions compile without errors
- ✅ Migration patterns documented
- ✅ Phase 3 completion summary updated
- ✅ Memory bank updated with achievements

---

## Reference Files

**Essential Reading:**
1. `docs/implementation/tasks/phase3-progress-summary.md` - Migration progress
2. `src/main/java/com/analyzer/rules/metrics/ClassMetricsInspectorV2.java` - Proven pattern
3. `src/main/java/com/analyzer/inspectors/core/binary/AbstractASMClassInspector.java` - Base class
4. `docs/implementation/tasks/phase2-completion-summary.md` - Infrastructure details

**Completed Examples:**
- MethodCountInspectorV2 - Simple counting
- BinaryClassFQNInspectorV2 - FQN extraction
- TypeInspectorASMInspectorV2 - Type detection
- BinaryJavaClassNodeInspectorV2 - Node validation

---

## Estimated Total Time

- **EjbBinaryClassInspectorV2:** 2-3 hours (complex)
- **StatefulSessionStateInspectorV2:** 1 hour
- **EjbCreateMethodUsageInspectorV2:** 1 hour
- **JdbcDataAccessPatternInspectorV2:** 1.5 hours
- **ProgrammaticTransactionUsageInspectorV2:** 1 hour

**Total:** 6.5-7.5 hours for remaining migrations

---

## Notes

1. **Start with EjbBinaryClassInspectorV2** - Most complex, but follows same pattern
2. **One at a time** - Compile and verify after each migration
3. **Consistency matters** - Follow established patterns exactly
4. **Document as you go** - Update progress summary after each completion
5. **Keep original files** - Don't delete old inspectors (backward compatibility)

---

**Created:** 2025-10-20  
**Last Updated:** 2025-10-20  
**Status:** Ready for next development session
