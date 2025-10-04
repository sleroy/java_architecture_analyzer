package com.analyzer.examples;

import com.analyzer.core.ProjectFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Example demonstrating usage of ProjectFile's new typed getter methods
 */
public class TypedGetterExample {

    public static void main(String[] args) {
        // Create a sample ProjectFile
        Path projectRoot = Paths.get("/example/project");
        Path javaFile = Paths.get("/example/project/src/main/java/com/example/MyService.java");
        ProjectFile projectFile = new ProjectFile(javaFile, projectRoot);

        // Simulate setting various tags that inspectors might add
        projectFile.setTag("java.className", "MyService");
        projectFile.setTag("java.packageName", "com.example");
        projectFile.setTag("java.lineCount", 150);
        projectFile.setTag("java.methodCount", 12);
        projectFile.setTag("java.complexity", 8.5);
        projectFile.setTag("java.hasMainMethod", false);
        projectFile.setTag("java.isLibrary", "true"); // String representation of boolean
        projectFile.setTag("java.imports",
                Arrays.asList("java.util.List", "java.io.File", "org.springframework.stereotype.Service"));

        // Demonstrate typed getter usage
        System.out.println("=== ProjectFile Typed Getter Examples ===");

        // String getters
        String className = projectFile.getStringTag("java.className");
        String packageName = projectFile.getStringTag("java.packageName", "default.package");
        String nonExistent = projectFile.getStringTag("nonexistent", "DEFAULT_VALUE");

        System.out.println("Class Name: " + className);
        System.out.println("Package Name: " + packageName);
        System.out.println("Non-existent with default: " + nonExistent);

        // Integer getters
        Integer lineCount = projectFile.getIntegerTag("java.lineCount");
        Integer methodCount = projectFile.getIntegerTag("java.methodCount", 0);
        Integer missing = projectFile.getIntegerTag("java.missing", 999);

        System.out.println("Line Count: " + lineCount);
        System.out.println("Method Count: " + methodCount);
        System.out.println("Missing count with default: " + missing);

        // Double getters
        Double complexity = projectFile.getDoubleTag("java.complexity");
        Double defaultComplexity = projectFile.getDoubleTag("java.unknownComplexity", 0.0);

        System.out.println("Complexity: " + complexity);
        System.out.println("Default complexity: " + defaultComplexity);

        // Boolean getters - demonstrating automatic string conversion
        Boolean hasMainMethod = projectFile.getBooleanTag("java.hasMainMethod");
        Boolean isLibrary = projectFile.getBooleanTag("java.isLibrary"); // Will convert "true" string to Boolean.TRUE
        Boolean defaultFlag = projectFile.getBooleanTag("java.missingFlag", true);

        System.out.println("Has Main Method: " + hasMainMethod);
        System.out.println("Is Library (from string): " + isLibrary);
        System.out.println("Default flag: " + defaultFlag);

        // List getters
        List<String> imports = projectFile.getListTag("java.imports");
        List<String> defaultList = projectFile.getListTag("java.missingList", Arrays.asList("default1", "default2"));

        System.out.println("Imports: " + imports);
        System.out.println("Default list: " + defaultList);

        // Demonstrate type safety and error handling
        System.out.println("\n=== Type Safety Examples ===");

        // Set invalid data for testing
        projectFile.setTag("invalidNumber", "not a number");
        projectFile.setTag("invalidBoolean", "maybe");

        Integer invalidInt = projectFile.getIntegerTag("invalidNumber");
        Boolean invalidBool = projectFile.getBooleanTag("invalidBoolean");

        System.out.println("Invalid integer (should be null): " + invalidInt);
        System.out.println("Invalid boolean (should be null): " + invalidBool);

        // Demonstrate mixed type handling
        projectFile.setTag("numberAsLong", 42L);
        projectFile.setTag("numberAsDouble", 3.14);

        Integer intFromLong = projectFile.getIntegerTag("numberAsLong");
        Double doubleFromInt = projectFile.getDoubleTag("java.lineCount"); // Convert Integer to Double

        System.out.println("Integer from Long: " + intFromLong);
        System.out.println("Double from Integer: " + doubleFromInt);

        // Show practical usage pattern
        System.out.println("\n=== Practical Usage Pattern ===");
        generateReport(projectFile);
    }

    /**
     * Example of practical usage in a reporting method
     */
    private static void generateReport(ProjectFile file) {
        String className = file.getStringTag("java.className", "Unknown");
        String packageName = file.getStringTag("java.packageName", "");
        Integer lineCount = file.getIntegerTag("java.lineCount", 0);
        Double complexity = file.getDoubleTag("java.complexity", 0.0);
        Boolean isLibrary = file.getBooleanTag("java.isLibrary", false);
        List<String> imports = file.getListTag("java.imports", Arrays.asList());

        System.out.println("=== FILE REPORT ===");
        System.out.println("File: " + file.getRelativePath());
        System.out.println("Class: " + (packageName.isEmpty() ? className : packageName + "." + className));
        System.out.println("Lines of Code: " + lineCount);
        System.out.println("Complexity: " + String.format("%.2f", complexity));
        System.out.println("Is Library: " + (isLibrary ? "Yes" : "No"));
        System.out.println("Import Count: " + imports.size());

        if (lineCount > 100) {
            System.out.println("⚠️  Large file detected");
        }
        if (complexity > 10.0) {
            System.out.println("⚠️  High complexity detected");
        }
    }
}
