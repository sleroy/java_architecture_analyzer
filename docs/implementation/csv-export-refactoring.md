# CSV Export Command Refactoring

## Overview
The CsvExportCommand has been completely rebuilt with a cleaner, simpler architecture that directly exports data from the H2 database to CSV files without unnecessary dependencies.

## Changes Made

### Removed Components
- **Old CsvExporter class** (`analyzer-core/src/main/java/com/analyzer/core/export/CsvExporter.java`)
  - This class had complex dependencies on Inspector and Project objects
  - Not needed for simple CSV data export

### New Implementation
- **Rebuilt CsvExportCommand** (`analyzer-app/src/main/java/com/analyzer/cli/CsvExportCommand.java`)
  - Direct H2 database loading
  - No inspector dependencies
  - No Project object creation
  - Simple CSV generation using Java standard library

## Architecture

### Data Flow
```
H2 Database → GraphRepository → Node Grouping by Type → CSV Files (one per node type)
```

### CSV Structure
Each node type gets its own CSV file with the following structure:

#### Fixed Columns
- `node_id` - Unique identifier for the node
- `node_type` - Type of node (e.g., "file", "java_class")
- `display_label` - Human-readable label

#### Dynamic Tag Columns
- `tag:<tagname>` - Boolean (true/false) indicating tag presence
- Examples: `tag:java`, `tag:source`, `tag:interface`

#### Dynamic Metric Columns
- `metric:<metricname>` - Numeric value or empty
- Examples: `metric:lines_of_code`, `metric:complexity`, `metric:method_count`

### Example Output Files

**file_nodes.csv:**
```csv
node_id,node_type,display_label,tag:java,tag:source,metric:lines_of_code,metric:complexity
file_1,file,MyClass.java,true,true,150,5
file_2,file,Test.java,true,true,80,3
```

**java_class_nodes.csv:**
```csv
node_id,node_type,display_label,tag:interface,tag:abstract,metric:method_count,metric:field_count
class_1,java_class,com.example.MyClass,false,false,10,5
class_2,java_class,com.example.MyInterface,true,false,3,0
```

## Command Usage

### Basic Export
```bash
java-architecture-analyzer csv_export --project /path/to/project
```
Exports all node types to `<project>/.analysis/csv/`

### Custom Output Directory
```bash
java-architecture-analyzer csv_export --project /path/to/project --output-dir /custom/path
```

### Filter by Node Type
```bash
java-architecture-analyzer csv_export --project /path/to/project --node-types file,java_class
```
Exports only specified node types

## Benefits

1. **Simplicity**: No complex dependencies, just direct data export
2. **Flexibility**: Dynamic columns adapt to whatever tags/metrics exist
3. **Type-Safe**: One CSV per node type with relevant columns
4. **Maintainable**: Clear code structure without inspector coupling
5. **Efficient**: Direct database-to-CSV with minimal memory overhead

## Implementation Details

### CSV Escaping
The implementation properly handles CSV escaping:
- Wraps values containing commas, quotes, or newlines in quotes
- Escapes internal quotes by doubling them
- Handles null values as empty strings

### Tag Handling
- Tags are collected across all nodes of a type
- Each tag becomes a boolean column
- Missing tags are represented as `false`

### Metric Handling
- Metrics are collected across all nodes of a type  
- Each metric becomes a numeric column
- Missing metrics are represented as empty string

### Performance
- Uses TreeSet for sorted column order
- Streams nodes efficiently
- Writes directly to file without buffering all data in memory

## Future Enhancements

Possible improvements:
1. Add option to merge all node types into single CSV with type column
2. Add CSV format options (delimiter, quote character, etc.)
3. Add option to export only specific tags/metrics
4. Add support for compressed output (CSV.gz)
5. Add summary statistics in separate file

## Related Files
- `analyzer-app/src/main/java/com/analyzer/cli/CsvExportCommand.java` - Main implementation
- `analyzer-app/src/main/java/com/analyzer/cli/JsonExportCommand.java` - Similar pattern used here
- `analyzer-core/src/main/java/com/analyzer/api/graph/GraphNode.java` - Node interface
- `analyzer-core/src/main/java/com/analyzer/api/metrics/Metrics.java` - Metrics interface
