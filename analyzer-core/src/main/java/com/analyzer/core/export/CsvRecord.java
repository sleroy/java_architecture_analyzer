package com.analyzer.core.export;

/**
 * Base interface for CSV record types that can be written to inventory output.
 * Each record type represents a row in the CSV with specific fields.
 */
public interface CsvRecord {

    /**
     * Returns the CSV header row for this record type.
     * Should be consistent across all records of the same type.
     */
    String getCsvHeader();

    /**
     * Returns the CSV data row for this specific record instance.
     * Fields should match the order defined in getCsvHeader().
     */
    String toCsvRow();

    /**
     * Returns the record type identifier (e.g., "Package", "Class").
     */
    String getRecordType();
}
