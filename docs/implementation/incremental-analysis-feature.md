# Incremental Analysis Feature Implementation

## Overview
Modified `InventoryCommand` to load existing database data before running analysis, enabling incremental updates to the analysis database.

## Changes Made

### Modified Files
- `analyzer-app/src/main/java/com/analyzer/cli/InventoryCommand.java`

### Key Modifications

#### 1. Added Database Loading Imports
```java
import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.serialization.JsonSerializationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
```

#### 2. Load Existing Database Before Analysis
Added call to `loadExistingDatabase()` in the `call()` method:
```java
// Load existing database if it exists
java.nio.file.Path projectDir = java.nio.file.Paths.get(projectPath);
Project existingProject = loadExistingDatabase(projectDir, analysisEngine);

// 5. Analyze the project using new architecture with multi-pass algorithm
Project project = analysisEngine.analyzeProject(projectDir, inspectors, maxPasses);
```

#### 3. Implemented Database Loading Logic

**`loadExistingDatabase()` Method:**
- Checks if database file exists at `<project>/.analysis/analyzer_graph.mv.db`
- If found:
  - Initializes database connection using `GraphDatabaseConfig`
  - Creates `GraphRepository` to read from database
  - Logs database statistics
  - Calls `loadDataIntoEngine()` to populate the analysis engine's graph repository
  - Returns a minimal `Project` object
- If not found:
  - Logs that fresh analysis will be performed
  - Returns `null`
- On error:
  - Logs warning and continues with fresh analysis

**`loadDataIntoEngine()` Method:**
- Retrieves the `GraphRepository` from the `AnalysisEngine`
- Loads nodes from database:
  - Queries common node types: `java`, `xml`, `properties`, `yaml`, `json`, `file`
  - Deserializes node properties and metrics from JSON
  - Reconstructs `ProjectFile` objects with all properties
  - Adds nodes to the graph repository
- Loads edges from database:
  - Queries common edge types: `depends_on`, `contains`, `calls`, `extends`, `implements`, `uses`
  - Reconstructs edges between loaded nodes
  - Adds edges to the graph repository
- Logs statistics for nodes and edges loaded

## Benefits

1. **Incremental Updates**: Running `inventory` command multiple times on the same project now updates existing data rather than starting from scratch
2. **Performance**: Reusing existing analysis data can speed up subsequent runs
3. **Data Continuity**: Preserves historical analysis data when re-analyzing projects
4. **Consistent with Export Commands**: Uses same database loading pattern as `JsonExportCommand` and `CsvExportCommand`

## Usage

No changes to command-line interface. Simply run:
```bash
./analyzer.sh inventory --project /path/to/project
```

On first run: Creates fresh database
On subsequent runs: Loads existing database, then updates with new analysis results

## Implementation Notes

### Similarities with JsonExportCommand
Both commands now share similar database loading logic:
- Same database connection initialization pattern
- Same node/edge loading from database
- Same property/metrics deserialization

### Differences
- **JsonExportCommand**: Loads data for export (read-only)
- **InventoryCommand**: Loads data for incremental analysis (read-write)

### Edge Types
The current implementation loads common edge types. This list may need to be expanded as new inspector types are added that create different edge types.

### Node Types  
Similarly, the node type list covers common file types but may need expansion for specialized node types introduced by future inspectors.

## Testing Recommendations

1. Run inventory on a project twice and verify:
   - First run creates database
   - Second run loads existing data and updates it
   - Database statistics show incremental changes

2. Verify that:
   - Nodes maintain their IDs across runs
   - Edges are preserved correctly
   - Properties and metrics are retained

3. Test edge cases:
   - Corrupted database (should fall back to fresh analysis)
   - Missing database file (should create new)
   - Project with no changes (should maintain existing data)

## Future Enhancements

1. **Selective Loading**: Add options to control which node/edge types to load
2. **Change Detection**: Optimize by only re-analyzing changed files
3. **Database Cleanup**: Remove nodes for deleted files
4. **Version Tracking**: Track when nodes were last analyzed
5. **Statistics Comparison**: Show what changed between runs
