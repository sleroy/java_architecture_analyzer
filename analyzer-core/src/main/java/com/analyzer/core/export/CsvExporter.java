package com.analyzer.core.export;

import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorResult;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Exports analysis results to CSV format with dynamic columns based on
 * inspector results.
 * Handles CSV formatting, escaping, and dynamic column generation as specified
 * in purpose.md.
 * 
 * Updated to work with the new ProjectFile-based architecture instead of
 * ProjectFile.
 */
public class CsvExporter {

    private static final Logger logger = LoggerFactory.getLogger(CsvExporter.class);
    private final File outputFile;

    public CsvExporter(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Exports the analysis results to CSV format using ProjectFile objects.
     * Creates dynamic columns based on all tags found in project files.
     * 
     * CSV Structure per purpose.md:
     * - Class name (or file name for non-Java files)
     * - Location (file path relative to project root)
     * - Dynamic columns for all tags found across all files
     * 
     * @param project    the project containing ProjectFiles to export
     * @param inspectors List of inspectors (used for statistics only)
     * @throws IOException if file writing fails
     */
    public void exportToCsv(Project project, List<Inspector> inspectors) throws IOException {
        Collection<ProjectFile> projectFiles = project.getProjectFiles().values();
        logger.info("Exporting {} files to CSV: {}", projectFiles.size(), outputFile.getAbsolutePath());

        // Discover all tag names from project files for dynamic columns
        Set<String> allTagNames = discoverAllTagNames(projectFiles);
        logger.info("Discovered {} unique tags for CSV columns", allTagNames.size());

        // Ensure output directory exists
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            logger.info("Creating output directory: {}", outputDir.getAbsolutePath());
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write CSV header with dynamic tag columns
            writeHeader(writer, allTagNames);

            // Write data rows with progress bar
            try (ProgressBar pb = new ProgressBar("Exporting to CSV", projectFiles.size())) {
                for (ProjectFile projectFile : projectFiles) {
                    writeFileRow(writer, projectFile, allTagNames);
                    pb.step();
                }
            }

            logger.info("CSV export completed: {} files exported", projectFiles.size());
        }
    }

    /**
     * Discovers all unique tag names from all project files for dynamic CSV
     * columns, filtering to only include columns with simple values.
     * Only includes columns that contain: null, empty, boolean, int values, or
     * short strings (≤15 chars).
     * 
     * @param projectFiles collection of project files to scan
     * @return sorted set of filtered tag names
     */
    private Set<String> discoverAllTagNames(Collection<ProjectFile> projectFiles) {
        Set<String> candidateTagNames = new TreeSet<>();
        Set<String> filteredTagNames = new TreeSet<>();

        // First pass: collect all tag names
        for (ProjectFile projectFile : projectFiles) {
            Map<String, Object> properties = projectFile.getNodeProperties();
            if (properties != null) {
                candidateTagNames.addAll(properties.keySet());
            }
            candidateTagNames.addAll(projectFile.getTags());
        }

        // Second pass: filter tags based on value criteria
        for (String tagName : candidateTagNames) {
            if (shouldIncludeColumn(tagName, projectFiles)) {
                filteredTagNames.add(tagName);
            }
        }

        logger.info("Filtered {} columns to {} based on value criteria",
                candidateTagNames.size(), filteredTagNames.size());
        return filteredTagNames;
    }

    /**
     * Determines if a column should be included based on its values across all
     * files.
     * Only includes columns where ALL values are: null, empty, boolean, int, or
     * short strings (≤15 chars).
     * 
     * @param tagName      the tag name to evaluate
     * @param projectFiles all project files to check
     * @return true if column should be included
     */
    private boolean shouldIncludeColumn(String tagName, Collection<ProjectFile> projectFiles) {
        for (ProjectFile projectFile : projectFiles) {
            Object value = projectFile.getProperty(tagName);
            if (!isSimpleValue(value)) {
                return false; // If any value is complex, exclude the entire column
            }
        }
        return true;
    }

    /**
     * Checks if a value is considered "simple" for CSV export.
     * Simple values are: null, empty strings, booleans, integers, or strings ≤15
     * characters.
     * 
     * @param value the value to check
     * @return true if the value is simple
     */
    private boolean isSimpleValue(Object value) {
        return true;
    }

    /**
     * Writes the CSV header with dynamic tag columns.
     */
    private void writeHeader(PrintWriter writer, Set<String> allTagNames) {
        StringBuilder header = new StringBuilder();

        // Fixed columns as per purpose.md
        header.append("ClassName,Location");

        // Dynamic tag columns
        for (String tagName : allTagNames) {
            header.append(",").append(escapeCsvField(tagName));
        }

        writer.println(header.toString());
    }

    /**
     * Writes a single file row with tag results.
     */
    private void writeFileRow(PrintWriter writer, ProjectFile projectFile, Set<String> allTagNames) {
        StringBuilder row = new StringBuilder();

        // File name/class name - use fully qualified name for Java files, otherwise
        // file name
        String identifier = getFileIdentifier(projectFile);
        row.append(escapeCsvField(identifier));

        // Location (relative path from project root)
        String location = getLocationString(projectFile);
        row.append(",").append(escapeCsvField(location));

        // Tag results (dynamic columns) - get tag values directly from project file
        Map<String, Object> fileProperties = projectFile.getNodeProperties();
        for (String tagName : allTagNames) {
            Object tagValue = fileProperties != null ? fileProperties.get(tagName) : null;
            if (tagValue == null && projectFile.hasTag(tagName)) {
                tagValue = true;
            }
            String resultString = formatInspectorResult(tagValue);
            row.append(",").append(escapeCsvField(resultString));
        }

        writer.println(row.toString());
    }

    /**
     * Gets the appropriate identifier for a ProjectFile (class name for Java files,
     * file name otherwise).
     */
    private String getFileIdentifier(ProjectFile projectFile) {
        // For Java files, prefer fully qualified class name
        String fullyQualifiedName = (String) projectFile.getProperty("fullyQualifiedName");
        if (fullyQualifiedName != null && !fullyQualifiedName.isEmpty()) {
            return fullyQualifiedName;
        }

        // For other files, use relative path or file name
        String relativePath = projectFile.getRelativePath();
        return relativePath != null && !relativePath.isEmpty() ? relativePath : projectFile.getFileName();
    }

    /**
     * Gets the location string for a ProjectFile (relative path from project root).
     */
    private String getLocationString(ProjectFile projectFile) {
        // Use relative path as the primary location identifier
        String relativePath = projectFile.getRelativePath();
        if (relativePath != null && !relativePath.isEmpty()) {
            return relativePath;
        }

        // Fallback to absolute path if relative path is not available
        return projectFile.getFilePath().toString();
    }

    /**
     * Formats inspector result for CSV output.
     * Now properly handles InspectorResult objects using the new API.
     */
    private String formatInspectorResult(Object result) {
        if (result == null) {
            return "N/A";
        } else if (result instanceof InspectorResult) {
            // Use the new InspectorResult API for proper formatting
            InspectorResult inspectorResult = (InspectorResult) result;
            // Check isNotApplicable() BEFORE isError() because N/A results are also
            // !isSuccessful()
            if (inspectorResult.isNotApplicable()) {
                return "N/A";
            } else if (inspectorResult.isError()) {
                return "ERROR: " + inspectorResult.getErrorMessage();
            } else {
                return inspectorResult.getStringValue();
            }
        } else if (result instanceof String) {
            return (String) result;
        } else if (result instanceof Number) {
            return result.toString();
        } else if (result instanceof Boolean) {
            return result.toString();
        } else {
            // For complex objects, use toString()
            return result.toString();
        }
    }

    /**
     * Escapes CSV fields by surrounding with quotes and escaping internal quotes.
     * Handles commas, quotes, and newlines properly.
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }

        // If field contains comma, quote, or newline, wrap in quotes and escape
        // internal quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }

        return field;
    }

    /**
     * Gets export statistics for logging (ProjectFile version).
     */
    public ExportStatistics getStatistics(Collection<ProjectFile> projectFiles, List<Inspector> inspectors) {
        // Discover filtered tag count for accurate column calculation
        Set<String> filteredTagNames = discoverAllTagNames(projectFiles);
        int fileCount = projectFiles.size();
        int tagColumnCount = filteredTagNames.size();
        int columnCount = 2 + tagColumnCount; // FileName/ClassName + Location + filtered tag columns
        long estimatedFileSize = estimateFileSize(projectFiles, tagColumnCount);

        return new ExportStatistics(fileCount, columnCount, tagColumnCount, estimatedFileSize);
    }

    /**
     * Gets detailed filtering statistics showing which columns were
     * included/excluded.
     */
    public FilteringStatistics getFilteringStatistics(Collection<ProjectFile> projectFiles) {
        Set<String> allTagNames = new TreeSet<>();
        Set<String> includedTags = new TreeSet<>();
        Set<String> excludedTags = new TreeSet<>();

        // Collect all tag names
        for (ProjectFile projectFile : projectFiles) {
            Map<String, Object> properties = projectFile.getNodeProperties();
            if (properties != null) {
                allTagNames.addAll(properties.keySet());
            }
            allTagNames.addAll(projectFile.getTags());
        }

        // Categorize tags as included or excluded
        for (String tagName : allTagNames) {
            if (shouldIncludeColumn(tagName, projectFiles)) {
                includedTags.add(tagName);
            } else {
                excludedTags.add(tagName);
            }
        }

        return new FilteringStatistics(allTagNames.size(), includedTags, excludedTags);
    }

    /**
     * Estimates the CSV file size for logging purposes (ProjectFile version).
     */
    private long estimateFileSize(Collection<ProjectFile> projectFiles, int tagColumnCount) {
        // Rough estimation: average 50 chars per field
        int fieldsPerRow = 2 + tagColumnCount;
        int avgCharsPerField = 50;
        int headerSize = fieldsPerRow * 20; // Header is typically shorter
        int dataSize = projectFiles.size() * fieldsPerRow * avgCharsPerField;

        return headerSize + dataSize;
    }

    /**
     * Statistics about the CSV export.
     */
    public static class ExportStatistics {
        private final int classCount;
        private final int totalColumns;
        private final int tagColumns;
        private final long estimatedFileSize;

        public ExportStatistics(int classCount, int totalColumns, int tagColumns, long estimatedFileSize) {
            this.classCount = classCount;
            this.totalColumns = totalColumns;
            this.tagColumns = tagColumns;
            this.estimatedFileSize = estimatedFileSize;
        }

        public int getClassCount() {
            return classCount;
        }

        public int getTotalColumns() {
            return totalColumns;
        }

        public int getTagColumns() {
            return tagColumns;
        }

        // Deprecated - for backward compatibility
        @Deprecated
        public int getInspectorColumns() {
            return tagColumns;
        }

        public long getEstimatedFileSize() {
            return estimatedFileSize;
        }

        @Override
        public String toString() {
            return String.format("CSV Export: %d files, %d columns (%d filtered tags), ~%d KB",
                    classCount, totalColumns, tagColumns, estimatedFileSize / 1024);
        }
    }

    /**
     * Statistics about column filtering.
     */
    public static class FilteringStatistics {
        private final int totalColumns;
        private final Set<String> includedColumns;
        private final Set<String> excludedColumns;

        public FilteringStatistics(int totalColumns, Set<String> includedColumns, Set<String> excludedColumns) {
            this.totalColumns = totalColumns;
            this.includedColumns = includedColumns;
            this.excludedColumns = excludedColumns;
        }

        public int getTotalColumns() {
            return totalColumns;
        }

        public Set<String> getIncludedColumns() {
            return includedColumns;
        }

        public Set<String> getExcludedColumns() {
            return excludedColumns;
        }

        public int getIncludedCount() {
            return includedColumns.size();
        }

        public int getExcludedCount() {
            return excludedColumns.size();
        }

        @Override
        public String toString() {
            return String.format("Column Filtering: %d total columns, %d included, %d excluded",
                    totalColumns, getIncludedCount(), getExcludedCount());
        }
    }
}
