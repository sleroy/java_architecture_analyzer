# Phase 3: BinaryJavaClassNodeCollector Migration - TODO List

**Status:** In Progress  
**Date Started:** October 21, 2025  
**Objective:** Create BinaryJavaClassNodeCollector to separate node CREATION from node ANALYSIS

## Context Summary

### What We Have ✅
- ✅ Phase 1 Complete: Core collector abstractions created
  - `Collector<S, T>` interface
  - `CollectionContext` helper class  
  - `ClassNodeCollector` specific interface
  - `AbstractBinaryClassCollector` base class (has ALL the logic!)
  
- ✅ Phase 2 Complete: InspectorRegistry updated
  - Added `Map<String, Collector<?, ?>> collectors` storage
  - Added collector registration/retrieval methods
  - Updated PicoContainer integration to auto-discover collectors
  
- ✅ Old Inspector Already Migrated:
  - `BinaryJavaClassNodeInspectorV2` now ANALYZES nodes (validates, adds properties)
  - No longer CREATES nodes (Phase 3 class-centric migration complete)

### What We Need 🎯
- Create `BinaryJavaClassNodeCollector` to CREATE JavaClassNode objects
- Extends `AbstractBinaryClassCollector` (which has all the logic)
- Only needs to implement `getName()` method
- Will be auto-discovered by PicoContainer (scans `com.analyzer` packages)

---

## TODO Checklist

### Step 1: Create BinaryJavaClassNodeCollector ⭐ PRIMARY TASK

- [ ] **Step 1.1**: Create new file `src/main/java/com/analyzer/core/collector/BinaryJavaClassNodeCollector.java`
  - Package: `com.analyzer.core.collector` (will be auto-discovered)
  - Class: Extends `AbstractBinaryClassCollector`
  - Implements: Only `getName()` method required
  
- [ ] **Step 1.2**: Add proper imports
  - `import com.analyzer.resource.ResourceResolver;`
  - `import javax.inject.Inject;`
  
- [ ] **Step 1.3**: Add @Component annotation (if using Spring) or rely on PicoContainer discovery
  - PicoContainer scans `com.analyzer.inspectors` and `com.analyzer.rules` packages
  - Might need to add `com.analyzer.core.collector` to scan list
  
- [ ] **Step 1.4**: Add constructor with ResourceResolver injection
  ```java
  @Inject
  public BinaryJavaClassNodeCollector(ResourceResolver resourceResolver) {
      super(resourceResolver);
  }
  ```
  
- [ ] **Step 1.5**: Implement getName() method
  ```java
  @Override
  public String getName() {
      return "BinaryJavaClassNodeCollector";
  }
  ```
  
- [ ] **Step 1.6**: Add comprehensive JavaDoc
  - Explain purpose: Creates JavaClassNode objects from .class files
  - Note this is Phase 2 (node collection) not Phase 4 (node analysis)
  - Reference migration from old inspector pattern

### Step 2: Verify PicoContainer Auto-Discovery

- [ ] **Step 2.1**: Check if `com.analyzer.core.collector` is in scan packages
  - Review `InspectorContainerConfig.java` scanPackages list
  - Default: `"com.analyzer.inspectors", "com.analyzer.rules", "com.rules"`
  - May need to add `"com.analyzer.core.collector"` to scan list
  
- [ ] **Step 2.2**: Update InspectorContainerConfig if needed
  - Add `"com.analyzer.core.collector"` to DEFAULT_SCAN_PACKAGES
  - Or move collector to `com.analyzer.rules.graph` package (already scanned)
  
- [ ] **Step 2.3**: Verify Collector interface check in isInspectorClass()
  - Current method only checks `Inspector.class.isAssignableFrom(clazz)`
  - Need to also check for `Collector.class.isAssignableFrom(clazz)`
  - Update method to discover both Inspectors AND Collectors

### Step 3: Update InspectorContainerConfig for Collector Discovery

- [ ] **Step 3.1**: Add isCollectorClass() method
  ```java
  private boolean isCollectorClass(Class<?> clazz) {
      if (!Collector.class.isAssignableFrom(clazz)) return false;
      if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) return false;
      return hasInjectableConstructor(clazz);
  }
  ```
  
- [ ] **Step 3.2**: Update registerInspectorClass() to handle collectors too
  - Or create separate registerCollectorClass() method
  - Register collectors in PicoContainer just like inspectors
  
- [ ] **Step 3.3**: Update isKnownDependencyType() to include Collector types
  ```java
  return type == ResourceResolver.class ||
         type == JARClassLoaderService.class ||
         type == ClassNodeRepository.class ||
         Inspector.class.isAssignableFrom(type) ||
         Collector.class.isAssignableFrom(type);  // ADD THIS
  ```

### Step 4: Test Compilation

- [ ] **Step 4.1**: Run Maven compile
  ```bash
  mvn clean compile -DskipTests
  ```
  
- [ ] **Step 4.2**: Verify zero NEW compilation errors
  - Pre-existing errors: ~50 (documented in progress.md)
  - Acceptable: Same number of errors as before
  - Unacceptable: ANY new errors related to collector code
  
- [ ] **Step 4.3**: Review compiler output for collector class
  - Verify `BinaryJavaClassNodeCollector` compiles successfully
  - Check for any warnings or issues

### Step 5: Verify Collector Registration

- [ ] **Step 5.1**: Add debug logging to verify discovery
  - Add log statement in collector constructor
  - Add log statement when getName() is called
  
- [ ] **Step 5.2**: Run InspectorRegistry test or simple main method
  - Verify collector appears in registry
  - Check `getClassNodeCollectors()` returns our collector
  - Verify count increases in statistics
  
- [ ] **Step 5.3**: Optional: Add unit test
  - Test collector can be instantiated
  - Test getName() returns expected value
  - Test canCollect() accepts .class files
  - Test canCollect() rejects non-.class files

### Step 6: Document Decision on Old Inspector

- [ ] **Step 6.1**: Analyze BinaryJavaClassNodeInspectorV2
  - ✅ Already confirmed: It ANALYZES nodes (adds properties, validates)
  - ✅ Confirmed: Does NOT create nodes anymore (Phase 3 migration complete)
  - Decision: **KEEP IT** - it serves analysis purpose in Phase 4
  
- [ ] **Step 6.2**: Update JavaDoc on InspectorV2
  - Clarify it's a Phase 4 analyzer, not Phase 2 creator
  - Add note about BinaryJavaClassNodeCollector handling creation
  - Update @since tag to reflect architectural split
  
- [ ] **Step 6.3**: Document the split in memory bank
  - Node CREATION: BinaryJavaClassNodeCollector (Phase 2)
  - Node ANALYSIS: BinaryJavaClassNodeInspectorV2 (Phase 4)
  - Clear separation of concerns achieved ✅

### Step 7: Update Documentation

- [ ] **Step 7.1**: Update memory bank activeContext.md
  - Add Phase 3 completion entry (BinaryJavaClassNodeCollector created)
  - Document collector/inspector split pattern
  - Note this as template for future collector migrations
  
- [ ] **Step 7.2**: Update memory bank progress.md  
  - Mark Phase 3 (Step 3.1) as COMPLETE
  - Update "Next Priority" section
  - Document any blockers or discoveries
  
- [ ] **Step 7.3**: Update memory bank changelog.md
  - Add v1.3.1 entry for Phase 3 completion
  - Note: BinaryJavaClassNodeCollector created
  - Note: Collector/Inspector separation pattern established

### Step 8: Final Verification

- [ ] **Step 8.1**: Re-read implementation plan Step 3 section
  - Verify all requirements met
  - Check success criteria achieved
  
- [ ] **Step 8.2**: Verify no regressions
  - Compilation still successful
  - No increase in error count
  - Existing tests still pass (if running)
  
- [ ] **Step 8.3**: Mark Phase 3 (Step 3.1) COMPLETE ✅

---

## Success Criteria

### Functional ✅
- [x] BinaryJavaClassNodeCollector created and compiles
- [ ] Collector extends AbstractBinaryClassCollector correctly
- [ ] PicoContainer discovers and registers the collector
- [ ] InspectorRegistry.getClassNodeCollectors() returns the collector
- [ ] canCollect() works for .class files
- [ ] getName() returns correct identifier

### Code Quality ✅
- [ ] Zero new compilation errors
- [ ] Proper JavaDoc documentation
- [ ] Follows existing code patterns
- [ ] Uses @Inject for dependency injection
- [ ] Follows Baby Steps™ methodology

### Architectural ✅
- [ ] Clear separation: Collector CREATES, Inspector ANALYZES
- [ ] BinaryJavaClassNodeInspectorV2 kept for analysis
- [ ] Pattern documented for future migrations
- [ ] Memory bank updated with progress

---

## Key Implementation Notes

### 🎯 Simplicity is Key
- AbstractBinaryClassCollector has **ALL** the logic
- We only need to implement `getName()`
- Constructor just calls `super(resourceResolver)`
- That's it! 3 methods total.

### 📦 Auto-Discovery
- PicoContainer scans configured packages automatically
- Either put in scanned package OR update config
- `com.analyzer.core.collector` might need to be added to scan list
- Alternative: Put in `com.analyzer.rules.graph` (already scanned)

### 🔍 Testing Strategy
- Start with compilation test (mvn compile)
- Then verify registration (logging + registry check)
- Unit tests optional but recommended
- Integration tests come in Phase 4/5

### 📚 References
- Implementation Plan: `docs/implementation/tasks/04_Collector_Architecture_Refactoring.md`
- Base Class: `src/main/java/com/analyzer/core/collector/AbstractBinaryClassCollector.java`
- Registry: `src/main/java/com/analyzer/core/inspector/InspectorRegistry.java`
- Config: `src/main/java/com/analyzer/core/inspector/InspectorContainerConfig.java`

---

## Next Steps After Phase 3

Once Phase 3 (Step 3.1) is complete:

**Phase 4 - Add Phase 2 to AnalysisEngine** (Step 4 in plan)
- Create `collectClassNodesFromFiles()` method
- Call collectors on all ProjectFiles
- Insert between current Phase 1 and Phase 3

**Phase 5 - Add Phase 4 to AnalysisEngine** (Step 5 in plan)
- Create `executeMultiPassOnClassNodes()` method  
- Multi-pass analysis on JavaClassNode objects
- Use convergence detection

---

**Date Created:** October 21, 2025  
**Methodology:** Baby Steps™ - One substantial accomplishment at a time  
**Estimated Time:** 2-3 hours for Phase 3 alone
