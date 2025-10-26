package com.analyzer.rules.graph;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.inspectors.core.binary.AbstractASMClassInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Class-centric graph node inspector for binary Java classes.
 * This is a Phase 3 migration demonstrating the new class-centric architecture.
 * <p>
 * <strong>ARCHITECTURAL NOTE:</strong> In the new class-centric architecture,
 * AbstractASMClassInspector already creates and provides the JavaClassNode
 * before calling inspectors. This makes the original purpose of this inspector
 * (creating JavaClassNode instances) largely redundant. This V2 version now
 * serves to validate and potentially enhance the node's metadata.
 * <p>
 * Key Differences from BinaryJavaClassNodeInspector:
 * - Extends AbstractASMClassInspector (class-centric) instead of
 * AbstractASMInspector (file-centric)
 * - Receives JavaClassNode as input (already created by infrastructure)
 * - Validates node existence and metadata
 * - Can enhance node with additional derived properties
 * <p>
 * This inspector now primarily serves as a validation step and can add
 * supplementary metadata to the JavaClassNode.
 *
 * @author Phase 3 - Class-Centric Architecture Migration
 * @since Phase 3 - Systematic Inspector Migration
 */
@InspectorDependencies(produces = {
        BinaryJavaClassNodeInspector.TAGS.TAG_JAVA_CLASS_NODE_BINARY })
public class BinaryJavaClassNodeInspectorV2 extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(BinaryJavaClassNodeInspectorV2.class);

    @Inject
    public BinaryJavaClassNodeInspectorV2(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public boolean canProcess(JavaClassNode objectToAnalyze) {
        return super.canProcess(objectToAnalyze);
    }

    @Override
    public String getName() {
        return "Binary Java Class Node Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        return new BinaryClassNodeVisitor(classNode, decorator);
    }

    /**
     * ASM visitor that validates and enhances JavaClassNode metadata.
     * In the new architecture, the node already exists, so this primarily
     * validates and can add supplementary properties.
     */
    private static class BinaryClassNodeVisitor extends ASMClassNodeVisitor {

        protected BinaryClassNodeVisitor(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
            super(classNode, decorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            try {
                // Convert internal class name to FQN for validation
                String fullyQualifiedName = name.replace('/', '.');

                // Determine class type from access flags
                String classType = determineClassType(access);

                // Log validation - node already exists via infrastructure
                logger.debug("Validating class node from binary: {} (type: {})",
                        fullyQualifiedName, classType);

                // Store class type as a property for quick access
                setProperty(JavaClassNode.PROP_CLASS_TYPE, classType);

                // Enable tag to indicate this class node was validated from binary
                enableTag(BinaryJavaClassNodeInspector.TAGS.TAG_JAVA_CLASS_NODE_BINARY);

                // Could add additional derived properties here if needed
                // For example: abstract/final flags, visibility modifiers, etc.
                if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                    setProperty("java.class.is_abstract", true);
                }
                if ((access & Opcodes.ACC_FINAL) != 0) {
                    setProperty("java.class.is_final", true);
                }

            } catch (Exception e) {
                logger.warn("Error validating binary class {}: {}", name, e.getMessage());
                reportError(e.getMessage());
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        /**
         * Determines class type from ASM access flags.
         */
        private String determineClassType(int accessFlags) {
            // Check for annotation first (annotations are also interfaces)
            if ((accessFlags & Opcodes.ACC_ANNOTATION) != 0) {
                return "annotation";
            }
            // Check for interface
            else if ((accessFlags & Opcodes.ACC_INTERFACE) != 0) {
                return "interface";
            }
            // Check for enum
            else if ((accessFlags & Opcodes.ACC_ENUM) != 0) {
                return "enum";
            }
            // Check for record (Java 14+)
            else if ((accessFlags & Opcodes.ACC_RECORD) != 0) {
                return "record";
            }
            // Default to class
            else {
                return "class";
            }
        }
    }
}
