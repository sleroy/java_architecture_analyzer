package com.analyzer.rules.std;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Type inspector that detects the declaration type of a class from binary
 * files.
 * This is one of the default inspectors specified in purpose.md.
 * <p>
 * Detects: class, interface, record, enum, annotation
 * <p>
 * Extends AbstractASMInspector to use ASM library for bytecode analysis.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_BINARY }, produces = { TypeInspectorASMInspector.TAGS.TAG_CLASS_TYPE })
public class TypeInspectorASMInspector extends AbstractASMInspector {

    private static final Logger logger = LoggerFactory.getLogger(TypeInspectorASMInspector.class);

    public static class TAGS {
        public static final String TAG_CLASS_TYPE = "type_inspector_asm.class_type";
    }

    private final GraphRepository graphRepository;

    @Inject
    public TypeInspectorASMInspector(ResourceResolver resourceResolver, GraphRepository graphRepository) {
        super(resourceResolver);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "Type Declaration Inspector";
    }

    @Override
    protected ASMClassVisitor createClassVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        // Always analyze bytecode to determine Java language type (CLASS, INTERFACE,
        // ENUM, etc.)
        // Note: projectFile may have discovery type tags (SOURCE_ONLY, BINARY_ONLY,
        // BOTH)
        // which is different from Java language type, so we always use bytecode
        // analysis
        return new TypeExtractorVisitor(projectFile, projectFileDecorator);
    }

    /**
     * ASM ClassVisitor to extract type information from bytecode.
     */
    private static class TypeExtractorVisitor extends ASMClassVisitor {
        private int accessFlags;

        public TypeExtractorVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
            super(projectFile, projectFileDecorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            this.accessFlags = access;
            logger.debug("ASM visit() called - access flags: 0x{}, name: {}, superName: {}",
                    Integer.toHexString(access), name, superName);

            // Set the result based on the access flags
            String classType = determineTypeFromAccessFlags(accessFlags);
            setTag(TAGS.TAG_CLASS_TYPE, classType);
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

}
