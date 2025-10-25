# 🎯 CLINE TASK: Complete Phase 3 Inspector Migrations

**Status:** READY TO START  
**Progress:** 6/10 Complete (4 remaining)  
**Estimated Time:** 6.5-7.5 hours  
**Priority:** HIGH

---

## 📋 OBJECTIVE

Complete Phase 3 by migrating the remaining 4 ASM-based inspectors from file-centric to class-centric architecture. The migration pattern is proven and documented through 6 successful migrations.

---

## 🎉 WHAT'S BEEN ACCOMPLISHED

### ✅ Successfully Migrated (6/10)

1. **ClassMetricsInspectorV2** - Comprehensive class metrics (Phase 2 proof-of-concept)
2. **MethodCountInspectorV2** - Method counting
3. **BinaryClassFQNInspectorV2** - FQN extraction
4. **TypeInspectorASMInspectorV2** - Type detection
5. **BinaryJavaClassNodeInspectorV2** - Node validation
6. (Original ClassMetricsInspector was replaced by V2)

**All compile successfully with ZERO errors!** ✅

---

## 🔧 REMAINING WORK (4 Inspectors)

### Priority 1: EjbBinaryClassInspectorV2 🔴 START HERE

**File:** `src/main/java/com/analyzer/rules/ejb2spring/EjbBinaryClassInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/EjbBinaryClassInspectorV2.java`  
**Complexity:** HIGH (450+ lines - most complex)  
**Time:** 2-3 hours

**What it does:**
- Detects EJB 3.x annotations (@Stateless, @Stateful, @Entity, @MessageDriven)
- Detects EJB 2.x interfaces (SessionBean, EntityBean, MessageDrivenBean)
- Detects EJB standard interfaces (EJBHome, EJBObject, etc.)
- Sets 18 different EJB migration tags

**Migration notes:**
- Large visitor with multiple analysis methods
- Keep all detection logic intact
- Write results to JavaClassNode properties
- Maintain tag compatibility for dependencies

---

### Priority 2: StatefulSessionStateInspectorV2 🟡

**File:** `src/main/java/com/analyzer/rules/ejb2spring/StatefulSessionStateInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/StatefulSessionStateInspectorV2.java`  
**Complexity:** MEDIUM  
**Time:** 1 hour

**What it does:**
- Analyzes stateful session bean state patterns
- Detects passivation/activation callbacks
- Provides Spring @Scope recommendations

---

### Priority 3: EjbCreateMethodUsageInspectorV2 🟡

**File:** `src/main/java/com/analyzer/rules/ejb2spring/EjbCreateMethodUsageInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/EjbCreateMethodUsageInspectorV2.java`  
**Complexity:** MEDIUM  
**Time:** 1 hour

**What it does:**
- Detects EJB create method patterns
- Identifies factory pattern opportunities
- Spring migration recommendations

---

### Priority 4: JdbcDataAccessPatternInspectorV2 🔴

**File:** `src/main/java/com/analyzer/rules/ejb2spring/JdbcDataAccessPatternInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/JdbcDataAccessPatternInspectorV2.java`  
**Complexity:** HIGH  
**Time:** 1.5 hours

**What it does:**
- Detects JDBC data access patterns
- Analyzes Connection/Statement/ResultSet usage
- Resource leak detection
- Spring JDBC/JPA recommendations

---

### Priority 5: ProgrammaticTransactionUsageInspectorV2 🟡

**File:** `src/main/java/com/analyzer/rules/ejb2spring/ProgrammaticTransactionUsageInspector.java`  
**Target:** `src/main/java/com/analyzer/rules/ejb2spring/ProgrammaticTransactionUsageInspectorV2.java`  
**Complexity:** MEDIUM  
**Time:** 1 hour

**What it does:**
- Detects programmatic transaction management
- UserTransaction usage analysis
- @Transactional recommendations

---

## 📖 PROVEN MIGRATION PATTERN (9 Steps)

Follow this checklist for EACH inspector:

### Step 1: Read Original
```bash
# Understand the inspector's functionality
read_file src/main/java/com/analyzer/rules/[package]/[InspectorName].java
```

### Step 2: Create V2 File
```bash
# Create new V2 file in same package
write_to_file src/main/java/com/analyzer/rules/[package]/[InspectorName]V2.java
```

### Step 3: Update Class Declaration
```java
// OLD:
public class MyInspector extends AbstractASMInspector

// NEW:
public class MyInspectorV2 extends AbstractASMClassInspector
```

### Step 4: Update Constructor
```java
@Inject
public MyInspectorV2(ProjectFileRepository projectFileRepository,
                     ResourceResolver resourceResolver) {
    super(projectFileRepository, resourceResolver);
}
```

### Step 5: Update createClassVisitor
```java
@Override
protected ASMClassNodeVisitor createClassVisitor(
    JavaClassNode classNode,
    NodeDecorator<JavaClassNode> decorator) {
    return new MyVisitor(classNode, decorator);
}
```

### Step 6: Update Inner Visitor
```java
private static class MyVisitor extends ASMClassNodeVisitor {
    protected MyVisitor(JavaClassNode classNode,
                       NodeDecorator<JavaClassNode> decorator) {
        super(classNode, decorator);
    }
}
```

### Step 7: Update Property Writing
```java
// OLD:
setTag(SOME_TAG, value);
projectFile.setProperty(key, value);

// NEW:
setProperty("property.name", value);  // Metrics to JavaClassNode
enableTag(SOME_TAG);                  // Optional: for dependencies
```

### Step 8: Update JavaDoc
```java
/**
 * Class-centric [Name] inspector - Phase 3 migration.
 * 
 * Key Differences from [OriginalName]:
 * - Extends AbstractASMClassInspector (class-centric)
 * - Receives JavaClassNode directly
 * - Writes metrics to JavaClassNode properties
 * 
 * @since Phase 3 - Systematic Inspector Migration
 */
```

### Step 9: Verify Compilation
```bash
mvn compile -DskipTests
# Should compile with NO errors for V2 file
```

---

## 🎯 SUCCESS CRITERIA

### Per Inspector
- [ ] V2 file exists in same package as original
- [ ] Extends `AbstractASMClassInspector`
- [ ] Constructor takes `ProjectFileRepository + ResourceResolver`
- [ ] `createClassVisitor` returns `ASMClassNodeVisitor`
- [ ] Properties written to JavaClassNode (not ProjectFile)
- [ ] Compiles with zero errors
- [ ] JavaDoc documents Phase 3 migration

### Overall Phase 3
- [ ] All 10 inspectors have V2 versions
- [ ] All V2 versions compile successfully
- [ ] Update `docs/implementation/tasks/phase3-progress-summary.md`
- [ ] Update memory bank with completion

---

## 📚 REFERENCE FILES (Read These First!)

### Essential Documentation
1. **`docs/implementation/tasks/phase3-remaining-inspectors.md`**
   - Detailed specs for each remaining inspector
   - Complete migration checklist
   - Reference examples

2. **`docs/implementation/tasks/phase3-progress-summary.md`**
   - What's been completed
   - Benefits achieved
   - Key learnings

### Working Examples (Study These!)
1. **`src/main/java/com/analyzer/rules/metrics/ClassMetricsInspectorV2.java`**
   - Most comprehensive example
   - Complex visitor with multiple metrics
   - Best pattern to follow

2. **`src/main/java/com/analyzer/rules/metrics/MethodCountInspectorV2.java`**
   - Simple, clean example
   - Good starting reference

3. **`src/main/java/com/analyzer/rules/std/TypeInspectorASMInspectorV2.java`**
   - Type detection patterns
   - Tag + property usage

### Infrastructure
4. **`src/main/java/com/analyzer/inspectors/core/binary/AbstractASMClassInspector.java`**
   - Base class you'll extend
   - Understand createClassVisitor contract

---

## 🚀 EXECUTION PLAN

### Session 1: EJB Inspector (2-3 hours)
```
1. Read EjbBinaryClassInspector thoroughly
2. Create EjbBinaryClassInspectorV2
3. Migrate class structure (steps 1-6)
4. Migrate visitor logic carefully
5. Update property writing (step 7)
6. Add JavaDoc (step 8)
7. Compile and verify (step 9)
8. Update progress doc
```

### Session 2: State & Create (2 hours)
```
1. Migrate StatefulSessionStateInspectorV2 (1 hour)
2. Migrate EjbCreateMethodUsageInspectorV2 (1 hour)
3. Compile both, update progress
```

### Session 3: JDBC & Transaction (2.5 hours)
```
1. Migrate JdbcDataAccessPatternInspectorV2 (1.5 hours)
2. Migrate ProgrammaticTransactionUsageInspectorV2 (1 hour)
3. Final compilation check
4. Update all documentation
5. Memory bank update
6. Phase 3 COMPLETE! 🎉
```

---

## ⚠️ IMPORTANT REMINDERS

1. **Baby Steps™** - One inspector at a time, compile after each
2. **Pattern Consistency** - Follow proven examples exactly
3. **Don't Delete Originals** - Keep old inspectors for backward compatibility
4. **Property Names** - Use `JavaClassNode.PROP_*` constants when available
5. **Tags vs Properties** - Tags for dependencies, Properties for metrics
6. **Compilation Check** - Must compile after each migration
7. **Documentation** - Update progress doc after each completion

---

## 🎓 KEY LEARNINGS FROM FIRST 6 MIGRATIONS

1. **The pattern works** - 6/6 compiled successfully
2. **ASMClassNodeVisitor is powerful** - Cleaner than old approach
3. **NodeDecorator provides type safety** - Catches errors at compile time
4. **Infrastructure handles node creation** - Inspectors just analyze
5. **Documentation matters** - Future maintainers will thank you

---

## 📞 NEED HELP?

**If you get stuck:**
1. Review ClassMetricsInspectorV2 (best complete example)
2. Check phase3-remaining-inspectors.md (detailed specs)
3. Verify you followed all 9 steps
4. Check compilation errors carefully
5. Ensure you're using ASMClassNodeVisitor (not ASMClassVisitor)

---

## 🎯 START HERE

**Command to begin:**
```bash
# Read the detailed specification
read_file docs/implementation/tasks/phase3-remaining-inspectors.md

# Then read the first target inspector
read_file src/main/java/com/analyzer/rules/ejb2spring/EjbBinaryClassInspector.java

# Study the proven pattern
read_file src/main/java/com/analyzer/rules/metrics/ClassMetricsInspectorV2.java
```

**Then:** Follow the 9-step migration pattern for EjbBinaryClassInspectorV2!

---

**Created:** 2025-10-20  
**For:** Cline AI Assistant  
**Status:** Ready to execute  
**Expected Completion:** 3 sessions (6.5-7.5 hours total)

**LET'S COMPLETE PHASE 3! 🚀**
