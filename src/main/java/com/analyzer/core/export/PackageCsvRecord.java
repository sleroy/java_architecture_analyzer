package com.analyzer.core;

/**
 * CSV record representing a package entry in the inventory output.
 * 
 * CSV Structure:
 * Type,Name,Package,ClassType,SourceLocation,LineCount,ClassCount
 * Package,com.example.core,com.example.core,,file:///path/to/package,0,5
 */
public class PackageCsvRecord implements CsvRecord {

    private final String packageName;
    private final String sourceLocation;
    private final int classCount;

    public PackageCsvRecord(String packageName, String sourceLocation, int classCount) {
        this.packageName = packageName != null ? packageName : "";
        this.sourceLocation = sourceLocation != null ? sourceLocation : "";
        this.classCount = classCount;
    }

    public PackageCsvRecord(Package pkg) {
        this(
                pkg.getPackageName(),
                pkg.getSourceLocation() != null ? pkg.getSourceLocation().getUri().toString() : "",
                pkg.getClassCount());
    }

    @Override
    public String getCsvHeader() {
        return "Type,Name,Package,ClassType,SourceLocation,LineCount,ClassCount";
    }

    @Override
    public String toCsvRow() {
        return String.format("Package,%s,%s,,%s,%d,%d",
                escapeCsvField(packageName),
                escapeCsvField(packageName), // Package name appears in both Name and Package columns
                escapeCsvField(sourceLocation),
                0, // Line count not applicable for packages
                classCount);
    }

    @Override
    public String getRecordType() {
        return "Package";
    }

    // Getters for data access
    public String getPackageName() {
        return packageName;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public int getClassCount() {
        return classCount;
    }

    /**
     * Escapes CSV fields by surrounding with quotes and escaping internal quotes.
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

    @Override
    public String toString() {
        return "PackageCsvRecord{" +
                "packageName='" + packageName + '\'' +
                ", sourceLocation='" + sourceLocation + '\'' +
                ", classCount=" + classCount +
                '}';
    }
}
