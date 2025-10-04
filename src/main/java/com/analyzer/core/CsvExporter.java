package com.analyzer.core;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
     * Creates dynamic columns based on the inspector names and includes their
     * results.
     * 
     * CSV Structure per purpose.md:
     * - Class name (or file name for non-Java files)
     * - Location (file path relative to project root)
     * - Result of each inspector (dynamic columns)
     * 
     * @param projectFiles Collection of analyzed ProjectFile objects
     * @param inspectors   List of inspectors for dynamic columns
     * @throws IOException if file writing fails
     */
    public void exportToCsv(Collection<ProjectFile> projectFiles, List<Inspector> inspectors) throws IOException {
        logger.info("Exporting {} files to CSV: {}", projectFiles.size(), outputFile.getAbsolutePath());

        // Ensure output directory exists
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            logger.info("Creating output directory: {}", outputDir.getAbsolutePath());
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write CSV header with dynamic inspector columns
            writeHeader(writer, inspectors);

            // Write data rows with progress bar
            try (ProgressBar pb = new ProgressBar("Exporting to CSV", projectFiles.size())) {
                for (ProjectFile projectFile : projectFiles) {
                    writeFileRow(writer, projectFile, inspectors);
                    pb.step();
                }
            }

            logger.info("CSV export completed: {} files exported", projectFiles.size());
        }
    }

    /**
     * Legacy method for backward compatibility with ProjectFile-based code.
     * Converts ProjectFile map to ProjectFile collection for migration support.
     * 
     * @deprecated Use exportToCsv(Collection<ProjectFile>, List<Inspector>) instead
     */
    @Deprecated
    public void exportToCsv(Map<String, ProjectFile> analyzedClasses, List<Inspector> inspectors) throws IOException {
        logger.warn("Using deprecated ProjectFile-based export method. Please migrate to ProjectFile-based export.");

        // For now, log the classes but cannot convert to ProjectFile without more
        // context
        // This is a temporary bridge during the migration
        logger.info("Legacy export requested for {} classes - skipping until migration is complete",
                analyzedClasses.size());

        // Create an empty file to prevent blocking the migration
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("# Legacy ProjectFile export - Migration in progress");
            writer.println("# Please use the new ProjectFile-based export method");
        }
    }

    /**
     * Writes the CSV header with dynamic inspector columns.
     */
    private void writeHeader(PrintWriter writer, List<Inspector> inspectors) {
        StringBuilder header = new StringBuilder();

        // Fixed columns as per purpose.md
        header.append("ClassName,Location");

        // Dynamic inspector columns - use getColumnName() instead of getName()
        for (Inspector inspector : inspectors) {
            header.append(",").append(escapeCsvField(inspector.getColumnName()));
        }

        writer.println(header.toString());
    }

    /**
     * Writes a single file row with inspector results.
     */
    private void writeFileRow(PrintWriter writer, ProjectFile projectFile, List<Inspector> inspectors) {
        StringBuilder row = new StringBuilder();

        // File name/class name - use fully qualified name for Java files, otherwise
        // file name
        String identifier = getFileIdentifier(projectFile);
        row.append(escapeCsvField(identifier));

        // Location (relative path from project root)
        String location = getLocationString(projectFile);
        row.append(",").append(escapeCsvField(location));

        // Inspector results (dynamic columns) - use inspector getColumnName() for
        // result lookup
        for (Inspector inspector : inspectors) {
            Object result = projectFile.getInspectorResult(inspector.getColumnName());
            String resultString = formatInspectorResult(result);
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
        String fullyQualifiedName = projectFile.getFullyQualifiedName();
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
     */
    private String formatInspectorResult(Object result) {
        if (result == null) {
            return "N/A";
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
        int fileCount = projectFiles.size();
        int columnCount = 2 + inspectors.size(); // FileName/ClassName + Location + inspector columns
        long estimatedFileSize = estimateFileSize(projectFiles, inspectors);

        return new ExportStatistics(fileCount, columnCount, inspectors.size(), estimatedFileSize);
    }

    /**
     * Gets export statistics for logging (legacy ProjectFile version).
     * 
     * @deprecated Use getStatistics(Collection<ProjectFile>, List<Inspector>)
     *             instead
     */
    @Deprecated
    public ExportStatistics getStatistics(Map<String, ProjectFile> analyzedClasses, List<Inspector> inspectors) {
        int classCount = analyzedClasses.size();
        int columnCount = 2 + inspectors.size(); // ClassName + Location + inspector columns
        long estimatedFileSize = estimateFileSizeLegacy(analyzedClasses, inspectors);

        return new ExportStatistics(classCount, columnCount, inspectors.size(), estimatedFileSize);
    }

    /**
     * Estimates the CSV file size for logging purposes (ProjectFile version).
     */
    private long estimateFileSize(Collection<ProjectFile> projectFiles, List<Inspector> inspectors) {
        // Rough estimation: average 50 chars per field
        int fieldsPerRow = 2 + inspectors.size();
        int avgCharsPerField = 50;
        int headerSize = fieldsPerRow * 20; // Header is typically shorter
        int dataSize = projectFiles.size() * fieldsPerRow * avgCharsPerField;

        return headerSize + dataSize;
    }

    /**
     * Estimates the CSV file size for logging purposes (legacy ProjectFile
     * version).
     * 
     * @deprecated Use estimateFileSize(Collection<ProjectFile>, List<Inspector>)
     *             instead
     */
    @Deprecated
    private long estimateFileSizeLegacy(Map<String, ProjectFile> analyzedClasses, List<Inspector> inspectors) {
        // Rough estimation: average 50 chars per field
        int fieldsPerRow = 2 + inspectors.size();
        int avgCharsPerField = 50;
        int headerSize = fieldsPerRow * 20; // Header is typically shorter
        int dataSize = analyzedClasses.size() * fieldsPerRow * avgCharsPerField;

        return headerSize + dataSize;
    }

    /**
     * Statistics about the CSV export.
     */
    public static class ExportStatistics {
        private final int classCount;
        private final int totalColumns;
        private final int inspectorColumns;
        private final long estimatedFileSize;

        public ExportStatistics(int classCount, int totalColumns, int inspectorColumns, long estimatedFileSize) {
            this.classCount = classCount;
            this.totalColumns = totalColumns;
            this.inspectorColumns = inspectorColumns;
            this.estimatedFileSize = estimatedFileSize;
        }

        public int getClassCount() {
            return classCount;
        }

        public int getTotalColumns() {
            return totalColumns;
        }

        public int getInspectorColumns() {
            return inspectorColumns;
        }

        public long getEstimatedFileSize() {
            return estimatedFileSize;
        }

        @Override
        public String toString() {
            return String.format("CSV Export: %d files, %d columns (%d inspectors), ~%d KB",
                            classCount, totalColumns, inspectorColumns, estimatedFileSize / 1024);
        }
    }
}
