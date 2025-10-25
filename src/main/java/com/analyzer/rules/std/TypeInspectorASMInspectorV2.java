package com.analyzer.rules.std;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.inspectors.core.binary.AbstractASMClassInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Class-centric type inspector that detects the declaration type from bytecode.
 * This is a Phase 3 migration demonstrating the new class-centric architecture.
 * <p>
 * Key Differences from TypeInspectorASMInspector:
 * - Extends AbstractASMClassInspector (class-centric) instead of
 * AbstractASMInspector (file-centric)
 * - Receives JavaClassNode as input instead of ProjectFile
 * - Writes type information to JavaClassNode properties
 * - Uses NodeDecorator&lt;JavaClassNode&gt; for property aggregation
 * <p>
 * Detects Java language types:
 * - CLASS (regular class)
 * - INTERFACE
 * - ENUM
 * - ANNOTATION
 * - RECORD (Java 14+)
 * <p>
 * The detected type is written to JavaClassNode.PROP_CLASS_TYPE property.
 *
 * @author Phase 3 - Class-Centric Architecture Migration
 * @since Phase 3 - Systematic Inspector Migration
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_BINARY }, produces = {})
public class TypeInspectorASMInspectorV2 extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(TypeInspectorASMInspectorV2.class);

    // Property key for JavaClassNode type information
    public static final String PROP_CLASS_TYPE = "java.class.type";

    @Inject
    public TypeInspectorASMInspectorV2(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "Type Declaration Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        return new TypeExtractorVisitor(classNode, decorator);
    }

    /**
     * ASM visitor that extracts type information from bytecode access flags.
     */
    private static class TypeExtractorVisitor extends ASMClassNodeVisitor {
        private int accessFlags;

        protected TypeExtractorVisitor(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
            super(classNode, decorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            this.accessFlags = access;
            logger.debug("ASM visit() called - access flags: 0x{}, name: {}, superName: {}",
                    Integer.toHexString(access), name, superName);

            // Determine and store the class type based on access flags
            String classType = determineTypeFromAccessFlags(accessFlags);
            setProperty(PROP_CLASS_TYPE, classType);

            // Also enable tag for backward compatibility with dependency system
            enableTag(TypeInspectorASMInspector.TAGS.TAG_CLASS_TYPE);

            super.visit(version, access, name, signature, superName, interfaces);
        }

        /**
         * Determines class type from access flags using JVM constants.
         * Based on JVM specification access flags and ASM Opcodes.
         */
        private String determineTypeFromAccessFlags(int accessFlags) {
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
}
