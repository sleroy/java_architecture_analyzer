package com.analyzer.core.collector;

import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.collector.Collector;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.analyzer.core.inspector.InspectorTags.FORMAT_BINARY;

/**
 * Collector that creates JavaClassNode objects from binary .class files.
 * <p>
 * This collector is responsible for the <strong>creation</strong> of
 * JavaClassNode
 * objects during Phase 2 (ClassNode Collection) of the analysis pipeline. It
 * reads
 * compiled Java bytecode (.class files) using ASM and extracts the
 * fully-qualified
 * class name (FQN) to create node instances.
 * <p>
 * <strong>Architectural Note - Separation of Concerns:</strong>
 * <ul>
 * <li><strong>This Collector (Phase 2):</strong> CREATES JavaClassNode objects
 * from .class files</li>
 * <li><strong>BinaryJavaClassNodeInspectorV2 (Phase 4):</strong> ANALYZES
 * existing JavaClassNode objects</li>
 * </ul>
 * This clear separation follows the Collector Architecture Refactoring pattern
 * where:
 * <ul>
 * <li><strong>Collectors:</strong> Create nodes (e.g.,
 * {@code Collector<ProjectFile, JavaClassNode>})</li>
 * <li><strong>Inspectors:</strong> Analyze nodes (e.g.,
 * {@code Inspector<JavaClassNode>})</li>
 * </ul>
 * <p>
 * <strong>Implementation Details:</strong>
 * <p>
 * This collector provides all the core functionality:
 * <ul>
 * <li>File validation (.class file checks)</li>
 * <li>Bytecode reading via ASM ClassReader</li>
 * <li>FQN extraction from bytecode</li>
 * <li>JavaClassNode creation and initialization</li>
 * <li>Linking nodes to source ProjectFiles</li>
 * <li>Repository storage via CollectionContext</li>
 * <li>Error handling and logging</li>
 * </ul>
 * <p>
 * <strong>Usage in Analysis Pipeline:</strong>
 * 
 * <pre>
 * Phase 1: File Discovery
 *   └── Scan filesystem, create ProjectFile objects
 * 
 * Phase 2: ClassNode Collection ← THIS COLLECTOR
 *   └── BinaryJavaClassNodeCollector creates JavaClassNode from .class files
 * 
 * Phase 3: Multi-Pass ProjectFile Analysis
 *   └── Inspectors analyze ProjectFiles until convergence
 * 
 * Phase 4: Multi-Pass ClassNode Analysis
 *   └── BinaryJavaClassNodeInspectorV2 analyzes JavaClassNode objects
 * </pre>
 *
 * @see ClassNodeCollector
 * @see Collector
 * @since Phase 2 - Collector Architecture Refactoring
 */
public class JavaClassNodeBinaryCollector implements ClassNodeCollector {

    private static final Logger logger = LoggerFactory.getLogger(JavaClassNodeBinaryCollector.class);

    protected final ResourceResolver resourceResolver;

    /**
     * Constructs a new BinaryJavaClassNodeCollector.
     * <p>
     * The ResourceResolver is required for reading .class file contents from
     * the filesystem or JAR archives.
     *
     * @param resourceResolver resolver for accessing file content
     */
    @Inject
    public JavaClassNodeBinaryCollector(final ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * Returns the name of this collector.
     * <p>
     * This name is used for:
     * <ul>
     * <li>Logging and debugging</li>
     * <li>Registry identification</li>
     * <li>Execution profiling</li>
     * <li>Error reporting</li>
     * </ul>
     *
     * @return the collector name
     */
    @Override
    public String getName() {
        return "BinaryJavaClassNodeCollector";
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
    public boolean canCollect(final ProjectFile source) {
        return null != source && source.hasFileExtension("class");
    }

    /**
     * Collects JavaClassNode from a binary .class file.
     * <p>
     * This implementation:
     * <ol>
     * <li>Validates the file is processable</li>
     * <li>Reads bytecode using ASM ClassReader</li>
     * <li>Extracts FQN via ClassVisitor</li>
     * <li>Creates JavaClassNode</li>
     * <li>Links node to source file</li>
     * <li>Stores in repository via context</li>
     * </ol>
     *
     * @param source  the .class file to process
     * @param context the collection context
     */
    @Override
    public void collect(final ProjectFile source, final CollectionContext context) {

        try {
            // Extract FQN from bytecode
            final String fqn = extractFQNFromBytecode(source);

            if (null == fqn || fqn.isEmpty()) {
                logger.warn("Could not extract FQN from {}", source.getRelativePath());
                return;
            }

            // Check if node already exists
            final Optional<JavaClassNode> existingNode = context.getClassNode(fqn);
            if (existingNode.isPresent()) {
                // Update source alias
                existingNode.get().addSourceAliasPath(source.getFilePath());
                logger.debug("JavaClassNode already exists for {}, skipping", fqn);
                return;
            }

            // Create JavaClassNode
            final JavaClassNode classNode = createClassNode(fqn, source);

            // Store via context
            context.addClassNode(classNode);
            context.linkClassNodeToFile(classNode, source);

            logger.debug("Created JavaClassNode for {} from {}", fqn, source.getRelativePath());

        } catch (final RuntimeException e) {
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
    protected String extractFQNFromBytecode(final ProjectFile source) {
        try {
            // Create ResourceLocation from file path
            final ResourceLocation location = ResourceLocation.file(source.getFilePath().toString());

            // Check if resource exists
            if (!resourceResolver.exists(location)) {
                logger.warn("Resource does not exist: {}", source.getRelativePath());
                throw new InvalidResourceException("Resource does not exist: " + source.getRelativePath());
            }

            // Open stream and read bytecode
            try (final InputStream inputStream = resourceResolver.openStream(location)) {
                if (null == inputStream) {
                    logger.warn("Could not open input stream for {}", source.getRelativePath());
                    throw new InvalidResourceException("Could not open input stream for " + source.getRelativePath());
                }

                final ClassReader classReader = new ClassReader(inputStream);

                // Use simple visitor to extract class name
                final FQNExtractorVisitor visitor = new FQNExtractorVisitor();
                classReader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                return visitor.getFqn();
            }

        } catch (final IOException e) {
            logger.error("Failed to read bytecode from {}: {}",
                    source.getRelativePath(), e.getMessage());
            throw new InvalidResourceException("Failed to read bytecode from " + source.getRelativePath(), e);
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
    protected static JavaClassNode createClassNode(final String fqn, final ProjectFile source) {
        final JavaClassNode classNode = new JavaClassNode(fqn);
        classNode.setSourceFilePath(source.getFilePath());
        classNode.setSourceType(FORMAT_BINARY);
        classNode.addSourceAliasPath(source.getFilePath());

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
        public void visit(final int version, final int access, final String name, final String signature,
                final String superName, final String[] interfaces) {
            // Convert internal name (e.g., "com/example/MyClass") to FQN (e.g.,
            // "com.example.MyClass")
            if (null != name) {
                fqn = name.replace(File.separatorChar, '.');
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        public String getFqn() {
            return fqn;
        }
    }
}
