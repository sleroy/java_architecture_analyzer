package com.analyzer.inspectors.core.binary;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.AbstractJavaClassInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Abstract base class for inspectors that analyze JavaClassNode objects using ASM bytecode analysis.
 * This is the class-centric equivalent of AbstractASMInspector, but writes results to JavaClassNode
 * instead of ProjectFile.
 * <p>
 * This class bridges the gap between:
 * - JavaClassNode (target for metrics/properties)
 * - ProjectFile (source of bytecode)
 * - ASM (bytecode analysis tool)
 * <p>
 * Workflow:
 * 1. Receives JavaClassNode to analyze
 * 2. Finds associated ProjectFile (contains .class bytecode)
 * 3. Reads bytecode using ASM
 * 4. Writes analysis results to JavaClassNode
 * <p>
 * Subclasses must implement createClassVisitor() to provide their specific ASM analysis logic.
 *
 * @since Phase 2 - Class-Centric Architecture Refactoring
 */
public abstract class AbstractASMClassInspector extends AbstractJavaClassInspector {

    protected final ResourceResolver resourceResolver;

    /**
     * Constructor with required dependencies.
     *
     * @param projectFileRepository repository for finding ProjectFile instances
     * @param resourceResolver      resolver for accessing bytecode resources
     */
    @Inject
    protected AbstractASMClassInspector(ProjectFileRepository projectFileRepository, 
                                       ResourceResolver resourceResolver) {
        super(projectFileRepository);
        this.resourceResolver = resourceResolver;
    }

    @Override
    protected final void analyzeClass(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
        // Find the ProjectFile containing the bytecode for this class
        Optional<ProjectFile> projectFileOpt = findProjectFile(classNode);
        
        if (projectFileOpt.isEmpty()) {
            decorator.error("Cannot find ProjectFile for class: " + classNode.getFullyQualifiedName());
            return;
        }
        
        ProjectFile projectFile = projectFileOpt.get();
        
        // Verify this is a binary class file
        if (!projectFile.hasTag(InspectorTags.TAG_JAVA_IS_BINARY)) {
            decorator.error("ProjectFile is not a binary class file: " + projectFile.getFilePath());
            return;
        }
        
        // Get the binary location and analyze with ASM
        try {
            ResourceLocation binaryLocation = new ResourceLocation(projectFile.getFilePath().toUri());
            
            try (InputStream classInputStream = resourceResolver.openStream(binaryLocation)) {
                if (classInputStream == null) {
                    decorator.error("Could not open binary class stream for: " + projectFile.getFilePath());
                    return;
                }
                analyzeClassWithASM(classNode, classInputStream, decorator);
            }
        } catch (IOException e) {
            decorator.error("Error reading class file: " + e.getMessage());
        } catch (Exception e) {
            decorator.error("Error analyzing class: " + e.getMessage());
        }
    }

    /**
     * Analyzes the class bytecode using ASM.
     *
     * @param classNode       the class node to analyze
     * @param classInputStream input stream containing .class bytecode
     * @param decorator       decorator for writing results to the class node
     * @throws IOException if bytecode cannot be read
     */
    protected void analyzeClassWithASM(JavaClassNode classNode, InputStream classInputStream,
                                      NodeDecorator<JavaClassNode> decorator) throws IOException {
        try {
            // Validate input stream
            if (classInputStream == null) {
                decorator.error("Class input stream is null");
                return;
            }

            // Read all bytes first to validate content
            byte[] classBytes = classInputStream.readAllBytes();

            // Check for empty class files
            if (classBytes.length == 0) {
                decorator.error("Empty class file (0 bytes)");
                return;
            }

            // Check for minimum valid class file size
            if (classBytes.length < 50) {
                decorator.error("Class file too small (" + classBytes.length + " bytes), likely corrupted");
                return;
            }

            // Create ClassReader from validated bytes
            ClassReader classReader = new ClassReader(classBytes);

            // Create the analysis visitor
            ASMClassNodeVisitor visitor = createClassVisitor(classNode, decorator);

            // Perform the analysis
            classReader.accept(visitor, 0);

        } catch (IOException e) {
            decorator.error("Error reading class file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // ASM ClassReader throws IllegalArgumentException for invalid bytecode
            decorator.error("Invalid bytecode format: " + e.getMessage());
        } catch (Exception e) {
            decorator.error("ASM analysis error: " + e.getMessage());
        }
    }

    /**
     * Creates a class visitor for analyzing the bytecode.
     * Subclasses must implement this method to provide their specific analysis logic.
     *
     * @param classNode the class node being analyzed
     * @param decorator decorator for writing results to the class node
     * @return an ASMClassNodeVisitor that will perform the analysis
     */
    protected abstract ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode, 
                                                             NodeDecorator<JavaClassNode> decorator);

    /**
     * Base class for ASM class visitors that write analysis results to JavaClassNode.
     * Subclasses should extend this class and implement their analysis logic
     * in the various visit methods.
     */
    public static abstract class ASMClassNodeVisitor extends ClassVisitor {

        protected final JavaClassNode classNode;
        protected final NodeDecorator<JavaClassNode> decorator;

        /**
         * Creates an ASMClassNodeVisitor.
         *
         * @param classNode the class node to write results to
         * @param decorator the decorator for setting properties and tags
         */
        protected ASMClassNodeVisitor(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
            super(Opcodes.ASM9);
            this.classNode = classNode;
            this.decorator = decorator;
        }

        /**
         * Sets a property on the class node.
         *
         * @param propertyName the property name
         * @param value        the property value
         */
        protected void setProperty(String propertyName, Object value) {
            decorator.setProperty(propertyName, value);
        }

        /**
         * Enables a tag on the class node.
         *
         * @param tagName the tag name
         */
        protected void enableTag(String tagName) {
            decorator.enableTag(tagName);
        }

        /**
         * Reports an error using the decorator.
         *
         * @param errorMessage the error message
         */
        protected void reportError(String errorMessage) {
            decorator.error(errorMessage);
        }
    }
}
