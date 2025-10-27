# Project Architecture Issues Analysis

## Date: 2025-10-27

## Issues Identified

### 1. Redundant ProjectFile Storage in Project.java

**Problem:**
The `Project` class maintains an in-memory `ConcurrentHashMap<String, ProjectFile>` that becomes redundant after database serialization.

**Current Flow:**
1. Analysis Phase: `Project` collects `ProjectFiles` in memory during analysis
2. Serialization Phase: All `ProjectFiles` are serialized to H2 database
3. Export Phase: Both `CsvExportCommand` and `JsonExportCommand` load data FROM the database, not from `Project`

**Why This is Redundant:**
- The H2 database is the **source of truth** for all export operations
- After serialization, the in-memory `Project.projectFiles` map serves no purpose
- Memory is wasted storing potentially thousands of ProjectFiles
- The `Project` object is only needed during analysis, not during export

**Impact:**
- Unnecessary memory consumption
- Confusion about data flow (database vs in-memory)
- Maintenance overhead keeping two data stores in sync

---

### 2. Metrics Are NOT Being Persisted to Database

**Problem:**
Despite having a `metrics` column in the database schema, metrics are NOT being serialized.

**Evidence:**

#### Database Schema Has Metrics Support:
```xml
<!-- From NodeMapper.xml -->
<result property="metricsMap" column="metrics"/>

<insert id="insertNode">
    INSERT INTO nodes (id, node_type, display_label, properties, metrics, ...)
    VALUES (#{id}, #{nodeType}, #{displayLabel}, #{properties}, #{metricsMap}, ...)
</insert>
```

#### But GraphDatabaseSerializer Does NOT Serialize Metrics:
```java
// From GraphDatabaseSerializer.java line ~85
String propertiesJson = jsonMapper.writeValueAsString(file.getNodeProperties());

// Creates node WITHOUT metrics!
GraphNodeEntity node = new GraphNodeEntity(nodeId, nodeType, displayLabel, propertiesJson);
nodeMapper.mergeNode(node);
```

**The Problem:**
- `GraphNodeEntity` has a 4-parameter constructor that doesn't include `metricsMap`
- Metrics are lost during database serialization
- When loading from database for export, no metrics are available

**Metrics Storage Locations:**
Metrics are currently stored in `NodeProperties` with keys like:
- `metrics.fileSize`
- `metrics.linesOfCode`  
- `metrics.methodCount`
- `metrics.cyclomaticComplexity`
- etc.

These are mixed with regular properties, but should be stored in the separate `metrics` column.

---

### 3. Metrics Are NOT Being Exported in JSON

**Problem:**
Since metrics aren't in the database, they can't be exported.

**Current JSON Export Flow:**
1. `JsonExportCommand` loads nodes from database
2. Deserializes properties: `jsonSerializer.deserializeProperties(entity.getProperties())`
3. But `entity.getMetricsMap()` is null because metrics were never serialized!
4. `ProjectSerializer` exports node properties but no metrics

**Expected vs Actual:**
- **Expected:** JSON export should include a `metrics` section with all metric values
- **Actual:** Metrics are completely missing from JSON export

---

## Proposed Solutions

### Solution 1: Eliminate Redundant Project.projectFiles Storage

**Option A: Make Project a Lightweight Container (Recommended)**
```java
public class Project {
    private final Path projectPath;
    private final String projectName;
    private final Map<String, Object> projectData;  // Project-level metadata only
    private final Date createdAt;
    private Date lastAnalyzed;
    
    // REMOVE: private final Map<String, ProjectFile> projectFiles;
    // REMOVE: All ProjectFile storage methods
    
    // Keep only project-level operations
}
```

**Benefits:**
- Clear separation: Database is source of truth
- Reduced memory footprint
- Simpler architecture

**Option B: Make Project a Facade Over Database**
```java
public class Project {
    private final ProjectFileRepository repository;  // Points to database
    
    public Collection<ProjectFile> getProjectFiles() {
        return repository.findAll();  // Load from DB on demand
    }
}
```

**Benefits:**
- Maintains existing API
- Lazy loading from database
- No redundant storage

---

### Solution 2: Fix Metrics Serialization

**Step 1: Extract Metrics from Properties**
```java
// In GraphDatabaseSerializer.serialize()
Map<String, Object> properties = file.getNodeProperties();
Map<String, Object> regularProps = new HashMap<>();
Map<String, Object> metrics = new HashMap<>();

// Separate metrics from properties
for (Map.Entry<String, Object> entry : properties.entrySet()) {
    if (entry.getKey().startsWith("metrics.")) {
        String metricName = entry.getKey().substring("metrics.".length());
        metrics.put(metricName, entry.getValue());
    } else {
        regularProps.put(entry.getKey(), entry.getValue());
    }
}

String propertiesJson = jsonMapper.writeValueAsString(regularProps);
String metricsJson = jsonMapper.writeValueAsString(metrics);

// Use 5-parameter constructor
GraphNodeEntity node = new GraphNodeEntity(
    nodeId, nodeType, displayLabel, propertiesJson, metricsJson
);
```

**Step 2: Load Metrics During Export**
```java
// In JsonExportCommand.loadDataFromDatabase()
Map<String, Object> properties = jsonSerializer.deserializeProperties(entity.getProperties());
Map<String, Object> metrics = jsonSerializer.deserializeProperties(entity.getMetricsMap());

// Set properties
for (Map.Entry<String, Object> entry : properties.entrySet()) {
    projectFile.setProperty(entry.getKey(), entry.getValue());
}

// Set metrics with prefix
for (Map.Entry<String, Object> entry : metrics.entrySet()) {
    projectFile.setProperty("metrics." + entry.getKey(), entry.getValue());
}
```

**Step 3: Export Metrics in JSON**
```java
// In ProjectSerializer.serializeNode()
Map<String, Object> nodeData = new HashMap<>();
nodeData.put("id", id);
nodeData.put("nodeType", nodeType);
nodeData.put("displayLabel", node.getDisplayLabel());
nodeData.put("tags", node.getTags());

// Separate properties and metrics
Map<String, Object> properties = new HashMap<>();
Map<String, Object> metrics = new HashMap<>();

for (Map.Entry<String, Object> entry : node.getNodeProperties().entrySet()) {
    if (entry.getKey().startsWith("metrics.")) {
        String metricName = entry.getKey().substring("metrics.".length());
        metrics.put(metricName, entry.getValue());
    } else {
        properties.put(entry.getKey(), entry.getValue());
    }
}

nodeData.put("properties", properties);
nodeData.put("metrics", metrics);  // Add metrics section!
```

---

## Recommendations

### Priority 1: Fix Metrics Serialization (CRITICAL)
- Metrics are being calculated but lost
- Must be fixed to provide value to users
- Relatively straightforward fix

### Priority 2: Refactor Project Class (IMPORTANT)
- Reduces memory footprint
- Clarifies architecture
- Requires more careful refactoring

### Implementation Order:
1. Fix metrics serialization in `GraphDatabaseSerializer`
2. Fix metrics loading in export commands
3. Add metrics section to JSON export
4. Refactor `Project` class to remove redundant storage
5. Update documentation

---

## Impact Analysis

### Breaking Changes:
- If external code directly accesses `Project.getProjectFiles()` during export phase, it would break
- Most likely limited impact as exports now load from database

### Performance Impact:
- **Positive:** Reduced memory usage (no duplicate ProjectFile storage)
- **Neutral:** Metrics separation adds minor serialization overhead
- **Positive:** Clearer data flow makes optimization easier

### Testing Required:
- Unit tests for metrics serialization/deserialization
- Integration tests for complete analysis → database → export flow
- Memory profiling to verify reduction
- JSON export validation with metrics

---

## Questions for Discussion

1. **Project.projectFiles Usage**: Are there any analysis components that rely on `Project.getProjectFiles()` AFTER database serialization?

2. **Metrics Schema**: Should we keep the separate `metrics` column or merge everything into `properties`?
   - **Separate column (current)**: Clean separation, easier to query metrics
   - **Merged into properties**: Simpler schema, but metrics mixed with regular properties

3. **Migration Strategy**: How do we handle existing databases that don't have metrics serialized?

4. **API Compatibility**: Should we maintain the `Project.getProjectFiles()` API for backward compatibility?
