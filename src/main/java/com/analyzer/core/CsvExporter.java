package com.analyzer.core;

import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Exports analysis results to CSV format with dynamic columns based on
 * inspector results.
 * Handles CSV formatting, escaping, and dynamic column generation as specified
 * in purpose.md.
 */
public class CsvExporter {

    private static final Logger logger = LoggerFactory.getLogger(CsvExporter.class);
    private final File outputFile;

    public CsvExporter(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Exports the analysis results to CSV format.
     * Creates dynamic columns based on the inspector names and includes their
     * results.
     * 
     * CSV Structure per purpose.md:
     * - Class name
     * - Location (source file path or JAR file path)
     * - Result of each inspector (dynamic columns)
     * 
     * @param analyzedClasses Map of class name to analyzed Clazz objects
     * @param inspectors      List of inspectors for dynamic columns
     * @throws IOException if file writing fails
     */
    public void exportToCsv(Map<String, Clazz> analyzedClasses, List<Inspector> inspectors) throws IOException {
        logger.info("Exporting {} classes to CSV: {}", analyzedClasses.size(), outputFile.getAbsolutePath());

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
            try (ProgressBar pb = new ProgressBar("Exporting to CSV", analyzedClasses.size())) {
                for (Clazz clazz : analyzedClasses.values()) {
                    writeClassRow(writer, clazz, inspectors);
                    pb.step();
                }
            }

            logger.info("CSV export completed: {} classes exported", analyzedClasses.size());
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
     * Writes a single class row with inspector results.
     */
    private void writeClassRow(PrintWriter writer, Clazz clazz, List<Inspector> inspectors) {
        StringBuilder row = new StringBuilder();

        // Class name (fully qualified)
        row.append(escapeCsvField(clazz.getFullyQualifiedName()));

        // Location (source file or JAR location)
        String location = getLocationString(clazz);
        row.append(",").append(escapeCsvField(location));

        // Inspector results (dynamic columns) - use inspector getName() for result
        // lookup
        for (Inspector inspector : inspectors) {
            Object result = clazz.getInspectorResult(inspector.getName());
            String resultString = formatInspectorResult(result);
            row.append(",").append(escapeCsvField(resultString));
        }

        writer.println(row.toString());
    }

    /**
     * Gets the location string for a class (source file path or JAR path).
     */
    private String getLocationString(Clazz clazz) {
        if (clazz.getSourceLocation() != null) {
            return clazz.getSourceLocation().getUri().toString();
        } else if (clazz.getBinaryLocation() != null) {
            return clazz.getBinaryLocation().getUri().toString();
        } else {
            return "UNKNOWN";
        }
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
     * Gets export statistics for logging.
     */
    public ExportStatistics getStatistics(Map<String, Clazz> analyzedClasses, List<Inspector> inspectors) {
        int classCount = analyzedClasses.size();
        int columnCount = 2 + inspectors.size(); // ClassName + Location + inspector columns
        long estimatedFileSize = estimateFileSize(analyzedClasses, inspectors);

        return new ExportStatistics(classCount, columnCount, inspectors.size(), estimatedFileSize);
    }

    /**
     * Estimates the CSV file size for logging purposes.
     */
    private long estimateFileSize(Map<String, Clazz> analyzedClasses, List<Inspector> inspectors) {
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
            return String.format("CSV Export: %d classes, %d columns (%d inspectors), ~%d KB",
                    classCount, totalColumns, inspectorColumns, estimatedFileSize / 1024);
        }
    }
}
