package com.analyzer.core;

/**
 * CSV record representing a class entry in the inventory output.
 * 
 * CSV Structure:
 * Type,Name,Package,ClassType,SourceLocation,LineCount,ClassCount
 * Class,MyClass,com.example.core,SOURCE_ONLY,file:///path/to/MyClass.java,150,1
 */
public class ClassCsvRecord implements CsvRecord {

    private final String className;
    private final String packageName;
    private final String classType;
    private final String sourceLocation;
    private final int lineCount;

    public ClassCsvRecord(String className, String packageName, String classType,
            String sourceLocation, int lineCount) {
        this.className = className != null ? className : "";
        this.packageName = packageName != null ? packageName : "";
        this.classType = classType != null ? classType : "";
        this.sourceLocation = sourceLocation != null ? sourceLocation : "";
        this.lineCount = lineCount;
    }

    public ClassCsvRecord(ProjectFile clazz) {
        this(
                (String) clazz.getTag("java.className", ""),
                (String) clazz.getTag("java.packageName", ""),
                (String) clazz.getTag("java.classType", ""),
                clazz.getFilePath().toString(),
                extractLineCount(clazz));
    }

    /**
     * Extracts line count from inspector results, defaults to 0 if not available.
     */
    private static int extractLineCount(ProjectFile clazz) {
        Object lineCountResult = clazz.getInspectorResult("lineCount");
        if (lineCountResult instanceof Integer) {
            return (Integer) lineCountResult;
        }
        return 0;
    }

    @Override
    public String getCsvHeader() {
        return "Type,Name,Package,ClassType,SourceLocation,LineCount,ClassCount";
    }

    @Override
    public String toCsvRow() {
        return String.format("Class,%s,%s,%s,%s,%d,%d",
                escapeCsvField(className),
                escapeCsvField(packageName),
                escapeCsvField(classType),
                escapeCsvField(sourceLocation),
                lineCount,
                1); // Each class entry represents 1 class
    }

    @Override
    public String getRecordType() {
        return "Class";
    }

    // Getters for data access
    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassType() {
        return classType;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public int getLineCount() {
        return lineCount;
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
        return "ClassCsvRecord{" +
                "className='" + className + '\'' +
                ", packageName='" + packageName + '\'' +
                ", classType='" + classType + '\'' +
                ", sourceLocation='" + sourceLocation + '\'' +
                ", lineCount=" + lineCount +
                '}';
    }
}
