package com.analyzer.inspectors.binary;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;

/**
 * Type inspector that detects the declaration type of a class from binary
 * files.
 * This is one of the default inspectors specified in purpose.md.
 * 
 * Detects: class, interface, record, enum, annotation
 * 
 * Extends BinaryClassInspector to analyze binary class files only.
 */
public class TypeInspector extends BinaryClassInspector {

    public TypeInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public String getName() {
        return "type";
    }

    @Override
    public String getColumnName() {
        return "class_type";
    }

    @Override
    public String getDescription() {
        return "Detects the type of declaration (class, interface, record, enum, annotation) from binary files";
    }

    @Override
    protected InspectorResult analyzeClassFile(Clazz clazz, ResourceLocation binaryLocation,
            InputStream classInputStream) throws IOException {
        try {
            // First check if the Clazz already has type information from discovery
            if (clazz.getClassType() != null) {
                return new InspectorResult(getName(), clazz.getClassType().toString());
            }

            // If not available from discovery, analyze the binary file
            String classType = analyzeClassFileStream(classInputStream);
            return new InspectorResult(getName(), classType);
        } catch (Exception e) {
            return InspectorResult.error(getName(), "Error analyzing class type: " + e.getMessage());
        }
    }

    /**
     * Analyzes a class file to determine its type.
     * Uses basic bytecode analysis to detect class file structure.
     */
    private String analyzeClassFileStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead = inputStream.read(buffer);

        if (bytesRead < 10) {
            return "UNKNOWN";
        }

        // Basic bytecode analysis
        // Look for class file magic number and access flags
        if (buffer[0] == (byte) 0xCA && buffer[1] == (byte) 0xFE &&
                buffer[2] == (byte) 0xBA && buffer[3] == (byte) 0xBE) {

            // This is a valid class file
            // Access flags are at offset 6-7 (after magic + minor + major version)
            if (bytesRead >= 8) {
                int accessFlags = ((buffer[6] & 0xFF) << 8) | (buffer[7] & 0xFF);
                return determineTypeFromAccessFlags(accessFlags);
            }
        }

        return "CLASS"; // Default assumption
    }

    /**
     * Determines class type from access flags.
     * Based on JVM specification access flags.
     */
    private String determineTypeFromAccessFlags(int accessFlags) {
        // JVM access flags (simplified)
        final int ACC_INTERFACE = 0x0200;
        final int ACC_ENUM = 0x4000;
        final int ACC_ANNOTATION = 0x2000;

        if ((accessFlags & ACC_ANNOTATION) != 0) {
            return "ANNOTATION";
        } else if ((accessFlags & ACC_INTERFACE) != 0) {
            return "INTERFACE";
        } else if ((accessFlags & ACC_ENUM) != 0) {
            return "ENUM";
        } else {
            // For records, we'd need more sophisticated analysis
            // For now, assume CLASS (could be CLASS or RECORD)
            return "CLASS";
        }
    }

    @Override
    public boolean supports(Clazz clazz) {
        // Supports classes that have binary locations OR already have type information
        return clazz.getBinaryLocation() != null || clazz.getClassType() != null;
    }
}
