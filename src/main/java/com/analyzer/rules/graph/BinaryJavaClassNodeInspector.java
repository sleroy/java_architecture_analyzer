package com.analyzer.rules.graph;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Graph-aware inspector that creates JavaClassNode instances by analyzing Java
 * binary code.
 * Uses ASM to extract class declarations from .class files and creates
 * corresponding graph nodes
 * with complete class metadata.
 *
 * <p>
 * This inspector:
 * </p>
 * <ul>
 * <li>Scans Java binary files (.class) for class declarations</li>
 * <li>Creates JavaClassNode instances for each discovered class</li>
 * <li>Links nodes to their source ProjectFile</li>
 * <li>Extracts package information, class names, and types from bytecode</li>
 * <li>Contributes nodes to the project graph for relationship analysis</li>
 * </ul>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_BINARY }, produces = {
        BinaryJavaClassNodeInspector.TAGS.TAG_JAVA_CLASS_NODE_BINARY })
public class BinaryJavaClassNodeInspector extends AbstractASMInspector {

    private static final Logger logger = LoggerFactory.getLogger(BinaryJavaClassNodeInspector.class);
    private final GraphRepository graphRepository;

    @Inject
    public BinaryJavaClassNodeInspector(ResourceResolver resourceResolver, GraphRepository graphRepository) {
        super(resourceResolver);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "Binary Java Class Node Inspector";
    }

    // supports() method removed as filtering is handled by @InspectorDependencies

    @Override
    protected ASMClassVisitor createClassVisitor(ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {

        return new BinaryClassNodeVisitor(projectFile, projectFileDecorator);
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

    private JavaClassNode createJavaClassNode(String fullyQualifiedName, String classType, ProjectFile projectFile) {
        return JavaClassNode.create(
                fullyQualifiedName,
                classType,
                JavaClassNode.SOURCE_TYPE_BINARY,
                projectFile.getId(),
                projectFile.getRelativePath().toString());
    }

    public static class TAGS {
        public static final String TAG_JAVA_CLASS_NODE_BINARY = "java.class_node.binary";

        // This class is kept for the @InspectorDependencies annotation reference
    }

    /**
     * No-op visitor used when graph repository is not available.
     */
    private static class NoOpClassVisitor extends ASMClassVisitor {

        public NoOpClassVisitor(ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
            super(projectFile, projectFileDecorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            // Do nothing - graph repository not available
        }
    }

    /**
     * ASM ClassVisitor that extracts class information and creates JavaClassNode
     * instances.
     */
    private class BinaryClassNodeVisitor extends ASMClassVisitor {

        public BinaryClassNodeVisitor(ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
            super(projectFile, projectFileDecorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            try {
                // Convert internal class name (com/example/MyClass) to fully qualified name
                // (com.example.MyClass)
                String fullyQualifiedName = name.replace('/', '.');

                // Determine class type from access flags
                String classType = determineClassType(access);

                // Create JavaClassNode
                JavaClassNode classNode = BinaryJavaClassNodeInspector.this.createJavaClassNode(fullyQualifiedName,
                        classType, projectFile);
                JavaClassNode existingNode = (JavaClassNode) BinaryJavaClassNodeInspector.this.graphRepository
                        .getOrCreateNode(classNode);

                logger.debug("Created/found class node from binary: {} (type: {})", fullyQualifiedName, classType);

                // Set tag to indicate this class node was created from binary analysis
                decorator.setProperty(TAGS.TAG_JAVA_CLASS_NODE_BINARY, fullyQualifiedName);

            } catch (Exception e) {
                logger.warn("Error processing binary class {}: {}", name, e.getMessage());
                decorator.error(e.getMessage());
            }
        }
    }

}
