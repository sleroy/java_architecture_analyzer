package com.analyzer.inspectors.rules.binary;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorTags;
import com.analyzer.inspectors.core.binary.ASMInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Type inspector that detects the declaration type of a class from binary
 * files.
 * This is one of the default inspectors specified in purpose.md.
 * 
 * Detects: class, interface, record, enum, annotation
 * 
 * Extends ASMInspector to use ASM library for bytecode analysis.
 */
public class TypeInspector extends ASMInspector {

    private static final Logger logger = LoggerFactory.getLogger(TypeInspector.class);

    public TypeInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public String getName() {
        return "Type Declaration Inspector";
    }

    @Override
    public String getColumnName() {
        return "class_type";
    }

    @Override
    protected ASMClassVisitor createClassVisitor(ProjectFile projectFile) {
        // Always analyze bytecode to determine Java language type (CLASS, INTERFACE,
        // ENUM, etc.)
        // Note: projectFile may have discovery type tags (SOURCE_ONLY, BINARY_ONLY,
        // BOTH)
        // which is different from Java language type, so we always use bytecode
        // analysis
        return new TypeExtractorVisitor(getColumnName());
    }

    /**
     * ASM ClassVisitor to extract type information from bytecode.
     */
    private static class TypeExtractorVisitor extends ASMClassVisitor {
        private int accessFlags;

        public TypeExtractorVisitor(String inspectorName) {
            super(inspectorName);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.accessFlags = access;
            logger.debug("ASM visit() called - access flags: 0x{}, name: {}, superName: {}",
                    Integer.toHexString(access), name, superName);

            // Set the result based on the access flags
            String classType = determineTypeFromAccessFlags(accessFlags);
            setResult(classType);
        }
    }

    /**
     * Determines class type from access flags using proper JVM constants.
     * Based on JVM specification access flags and ASM Opcodes.
     */
    private static String determineTypeFromAccessFlags(int accessFlags) {
        logger.debug("Analyzing access flags: 0x{}", Integer.toHexString(accessFlags));

        // Check for annotation first (annotations are also interfaces)
        if ((accessFlags & Opcodes.ACC_ANNOTATION) != 0) {
            logger.debug("Detected ANNOTATION type");
            return "ANNOTATION";
        }
        // Check for interface
        else if ((accessFlags & Opcodes.ACC_INTERFACE) != 0) {
            logger.debug("Detected INTERFACE type");
            return "INTERFACE";
        }
        // Check for enum
        else if ((accessFlags & Opcodes.ACC_ENUM) != 0) {
            logger.debug("Detected ENUM type");
            return "ENUM";
        }
        // Check for record (Java 14+)
        else if ((accessFlags & Opcodes.ACC_RECORD) != 0) {
            logger.debug("Detected RECORD type");
            return "RECORD";
        }
        // Default to class
        else {
            logger.debug("Detected CLASS type (default)");
            return "CLASS";
        }
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // Supports project files that have binary code or are Java class files
        // Also supports source-only files that have class type information
        return projectFile != null && (projectFile.getBooleanTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY, false) ||
                projectFile.hasFileExtension("class") ||
                projectFile.getFilePath().toString().endsWith(".class") ||
                projectFile.hasTag("class_type"));
    }
}
