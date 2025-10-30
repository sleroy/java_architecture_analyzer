# Multi-Pass Executor Refactoring

## Overview

This document describes the refactoring of the multi-pass analysis logic in `AnalysisEngine` to eliminate code duplication by introducing a generic `MultiPassExecutor` class.

## Problem Statement

The `AnalysisEngine` class had two nearly identical methods for multi-pass analysis:

1. **`executeMultiPassInspectors()`** - Phase 3: ProjectFile analysis (~90 lines)
2. **`executeMultiPassOnClassNodes()`** - Phase 4: ClassNode analysis (~90 lines)

Both methods implemented the same pattern:
- Multi-pass loop with convergence detection
- Progress tracking with ExecutionProfile and ProgressBar
- Per-item analysis with execution tracking
- Triggered inspector tracking per pass
- Convergence detection (stop when no items need processing)
- Comprehensive logging and execution reports

This duplication made maintenance difficult and error-prone.

## Solution

### Created `MultiPassExecutor<T extends GraphNode>`

A generic, reusable multi-pass executor that works with any `GraphNode` subtype.

#### Key Components

1. **`ExecutionConfig<T>`** - Configuration object containing:
   - `phaseName` - Display name (e.g., "Phase 3", "Phase 4")
   - `maxPasses` - Maximum number of passes
   - `executionPhase` - For ExecutionProfile tracking
   - `itemSupplier` - Supplier for items to process
   - `inspectors` - List of inspectors to execute
   - `itemAnalyzer` - Function to analyze individual items

2. **`ItemAnalyzer<T>`** - Functional interface:
   ```java
   Set<String> analyze(T item,
                      List<Inspector<T>> inspectors,
                      LocalDateTime passStartTime,
                      ExecutionProfile executionProfile,
                      int pass);
   ```

3. **`ExecutionResult`** - Result containing:
   - `passesExecuted` - Number of passes completed
   - `converged` - Whether convergence was achieved
   - `totalItemsProcessed` - Total items processed across all passes
   - `executionProfile` - Execution profile with performance data

### Refactored Methods

#### Phase 3: ProjectFile Analysis (Before: ~90 lines → After: ~30 lines)

```java
private void executeMultiPassInspectors(Project project, List<String> requestedInspectors, int maxPasses) {
    List<Inspector<ProjectFile>> projectFileInspectors = getProjectFileInspectors(requestedInspectors);
    
    // Create multi-pass executor configuration
    MultiPassExecutor<ProjectFile> executor = new MultiPassExecutor<>();
    MultiPassExecutor.ExecutionConfig<ProjectFile> config = new MultiPassExecutor.ExecutionConfig<>(
            "Phase 3",
            maxPasses,
            ExecutionProfile.ExecutionPhase.PHASE_3_PROJECTFILE_ANALYSIS,
            () -> project.getProjectFiles().values(),
            projectFileInspectors,
            this::analyzeProjectFileWithTrackingAndCollection);
    
    // Execute multi-pass analysis
    MultiPassExecutor.ExecutionResult result = executor.execute(config);
    
    logger.info("Phase 3 completed: {} passes executed, converged: {}",
            result.getPassesExecuted(), result.isConverged());
    
    progressTracker.markTrackingCompleted();
    progressTracker.logProgressReport();
}
```

#### Phase 4: ClassNode Analysis (Before: ~90 lines → After: ~25 lines)

```java
private void executeMultiPassOnClassNodes(Project project, int maxPasses) {
    List<Inspector<JavaClassNode>> inspectors = getClassNodeInspectors();
    
    // Create multi-pass executor configuration
    MultiPassExecutor<JavaClassNode> executor = new MultiPassExecutor<>();
    MultiPassExecutor.ExecutionConfig<JavaClassNode> config = new MultiPassExecutor.ExecutionConfig<>(
            "Phase 4",
            maxPasses,
            ExecutionProfile.ExecutionPhase.PHASE_4_CLASSNODE_ANALYSIS,
            classNodeRepository::findAll,
            inspectors,
            this::analyzeClassNodeWithTracking);
    
    // Execute multi-pass analysis
    MultiPassExecutor.ExecutionResult result = executor.execute(config);
    
    logger.info("Phase 4 completed: {} passes executed, converged: {}",
            result.getPassesExecuted(), result.isConverged());
}
```

## Benefits

1. **Eliminated Duplication** - Single implementation of multi-pass algorithm (~180 lines reduced to ~200 lines total with cleaner separation)
2. **Type-Safe** - Generic parameter ensures type consistency
3. **Flexible** - Works with any GraphNode subtype
4. **Testable** - Can test multi-pass logic independently
5. **Maintainable** - Changes to convergence logic in one place
6. **Extensible** - Easy to add new phases (e.g., Phase 5 for EdgeInspectors)

## Code Metrics

### Lines of Code Reduction

| Component | Before | After | Reduction |
|-----------|--------|-------|-----------|
| Phase 3 Method | ~90 lines | ~30 lines | ~67% |
| Phase 4 Method | ~90 lines | ~25 lines | ~72% |
| **Total in AnalysisEngine** | **~180 lines** | **~55 lines** | **~69%** |
| MultiPassExecutor (new) | 0 lines | ~200 lines | N/A |

### Net Result
- **Code in AnalysisEngine**: Reduced by ~125 lines
- **Total codebase**: Added ~75 lines (but gained reusability and testability)
- **Complexity**: Significantly reduced through abstraction

## Testing

### Compilation Test
✅ **BUILD SUCCESS** - All modules compiled without errors

### Next Steps for Validation
1. Run existing unit tests to ensure behavior unchanged
2. Verify convergence behavior in integration tests
3. Compare execution logs between old and new implementation
4. Performance benchmarking to ensure no regression

## Future Enhancements

1. **Unit Tests** - Add dedicated tests for `MultiPassExecutor`
2. **Edge Inspector Support** - Extend for future edge-based analysis phases
3. **Parallel Execution** - Add optional parallel processing for independent items
4. **Metrics Collection** - Enhanced performance metrics and bottleneck detection
5. **Configurable Convergence** - Custom convergence criteria beyond "no items processed"

## Related Files

- `analyzer-core/src/main/java/com/analyzer/core/engine/MultiPassExecutor.java` (new)
- `analyzer-core/src/main/java/com/analyzer/core/engine/AnalysisEngine.java` (refactored)
- `analyzer-core/src/main/java/com/analyzer/core/engine/ExecutionProfile.java` (unchanged)

## Author
Created: 2025-10-29

## Status
✅ Implemented and compiled successfully
⏳ Pending comprehensive testing
