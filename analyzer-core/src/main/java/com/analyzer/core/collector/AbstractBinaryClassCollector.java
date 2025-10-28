package com.analyzer.core.collector;

import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.collector.Collector;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.api.resource.ResourceResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static com.analyzer.core.inspector.InspectorTags.*;
import static com.analyzer.core.inspector.InspectorTags.FORMAT_BINARY;
import static com.analyzer.core.inspector.InspectorTags.LANGUAGE_JAVA;
import static com.analyzer.core.inspector.InspectorTags.TAG_LANGUAGE;

/**
 * Abstract base class for collectors that create JavaClassNode objects from binary .class files.
 * <p>
 * This class provides the foundation for extracting class information from Java bytecode
 * using ASM (a bytecode manipulation framework). It handles:
 * <ul>
 *   <li>File validation (.class file checks)</li>
 *   <li>Bytecode reading via ASM ClassReader</li>
 *   <li>FQN extraction from bytecode</li>
 *   <li>JavaClassNode creation and linking</li>
 *   <li>Error handling and logging</li>
 * </ul>
 * <p>
 * Subclasses need only implement {@link #getName()} to provide collector identification.
 * Additional customization can be done by overriding other methods as needed.
 * <p>
 * Example implementation:
 * <pre>{@code
 * public class BinaryClassNodeCollector extends AbstractBinaryClassCollector {
 *     
 *     @Inject
 *     public BinaryClassNodeCollector(ResourceResolver resourceResolver) {
 *         super(resourceResolver);
 *     }
 *     
 *     @Override
 *     public String getName() {
 *         return "BinaryClassNodeCollector";
 *     }
 * }
 * }</pre>
 *
 * @see ClassNodeCollector
 * @see Collector
 * @since Phase 2 - Collector Architecture Refactoring
 */
public abstract class AbstractBinaryClassCollector implements ClassNodeCollector {

    private static final Logger logger = LoggerFactory.getLogger(AbstractBinaryClassCollector.class);
    
    protected final ResourceResolver resourceResolver;

    /**
     * Constructor with ResourceResolver for reading .class files.
     *
     * @param resourceResolver resolver for accessing file content
     */
    protected AbstractBinaryClassCollector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * Checks if this collector can process the given ProjectFile.
     * <p>
     * By default, this returns true only for .class files.
     * Subclasses can override to add additional checks.
     *
     * @param source the ProjectFile to evaluate
     * @return true if the file has .class extension
     */
    @Override
    public boolean canCollect(ProjectFile source) {
        return source != null && source.hasFileExtension("class");
    }

    /**
     * Collects JavaClassNode from a binary .class file.
     * <p>
     * This implementation:
     * <ol>
     *   <li>Validates the file is processable</li>
     *   <li>Reads bytecode using ASM ClassReader</li>
     *   <li>Extracts FQN via ClassVisitor</li>
     *   <li>Creates JavaClassNode</li>
     *   <li>Links node to source file</li>
     *   <li>Stores in repository via context</li>
     * </ol>
     *
     * @param source  the .class file to process
     * @param context the collection context
     */
    @Override
    public void collect(ProjectFile source, CollectionContext context) {
        if (!canCollect(source)) {
            logger.debug("Collector {} cannot process file: {}", 
                        getName(), source.getRelativePath());
            return;
        }

        try {
            // Extract FQN from bytecode
            String fqn = extractFQNFromBytecode(source);
            
            if (fqn == null || fqn.isEmpty()) {
                logger.warn("Could not extract FQN from {}", source.getRelativePath());
                return;
            }

            // Check if node already exists
            if (context.classNodeExists(fqn)) {
                logger.debug("JavaClassNode already exists for {}, skipping", fqn);
                return;
            }

            // Create JavaClassNode
            JavaClassNode classNode = createClassNode(fqn, source);
            
            // Store via context
            context.addClassNode(classNode);
            context.linkClassNodeToFile(classNode, source);
            
            logger.debug("Created JavaClassNode for {} from {}", fqn, source.getRelativePath());
            
        } catch (Exception e) {
            logger.error("Error collecting class node from {}: {}", 
                        source.getRelativePath(), e.getMessage(), e);
        }
    }

    /**
     * Extracts the fully qualified name from a .class file using ASM.
     * <p>
     * This method reads the bytecode and uses a simple ClassVisitor to
     * extract the internal class name, which is then converted to FQN format.
     *
     * @param source the .class file
     * @return the fully qualified name, or null if extraction fails
     */
    protected String extractFQNFromBytecode(ProjectFile source) {
        try {
            // Create ResourceLocation from file path
            ResourceLocation location = ResourceLocation.file(source.getFilePath().toString());
            
            // Check if resource exists
            if (!resourceResolver.exists(location)) {
                logger.warn("Resource does not exist: {}", source.getRelativePath());
                return null;
            }
            
            // Open stream and read bytecode
            try (InputStream inputStream = resourceResolver.openStream(location)) {
                if (inputStream == null) {
                    logger.warn("Could not open input stream for {}", source.getRelativePath());
                    return null;
                }

                ClassReader classReader = new ClassReader(inputStream);
                
                // Use simple visitor to extract class name
                FQNExtractorVisitor visitor = new FQNExtractorVisitor();
                classReader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                
                return visitor.getFqn();
            }
            
        } catch (IOException e) {
            logger.error("Failed to read bytecode from {}: {}", 
                        source.getRelativePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Creates a JavaClassNode with the given FQN and links it to the source file.
     * <p>
     * Subclasses can override this method to set additional properties on the node.
     *
     * @param fqn    the fully qualified name
     * @param source the source ProjectFile
     * @return the created JavaClassNode
     */
    protected JavaClassNode createClassNode(String fqn, ProjectFile source) {
        JavaClassNode classNode = new JavaClassNode(fqn);
        classNode.setSourceFilePath(source.getFilePath().toString());

        return classNode;
    }

    /**
     * Simple ClassVisitor that extracts the fully qualified name from bytecode.
     */
    private static class FQNExtractorVisitor extends ClassVisitor {
        private String fqn;

        public FQNExtractorVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                         String superName, String[] interfaces) {
            // Convert internal name (e.g., "com/example/MyClass") to FQN (e.g., "com.example.MyClass")
            if (name != null) {
                this.fqn = name.replace('/', '.');
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        public String getFqn() {
            return fqn;
        }
    }
}
