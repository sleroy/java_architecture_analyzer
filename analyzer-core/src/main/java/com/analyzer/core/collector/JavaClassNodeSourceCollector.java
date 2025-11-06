package com.analyzer.core.collector;

import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.InputStream;
import java.util.Optional;

import static com.analyzer.core.inspector.InspectorTags.FORMAT_SOURCE;

/**
 * Collector that creates JavaClassNode objects from Java source files using
 * JavaParser.
 * <p>
 * This collector parses .java source files to extract class declarations and
 * create
 * JavaClassNode objects. It's particularly useful for:
 * <ul>
 * <li>Analyzing uncompiled source code</li>
 * <li>Working with source-only projects</li>
 * <li>Extracting source-level class information</li>
 * <li>Understanding class structure from source</li>
 * </ul>
 * <p>
 * <strong>Comparison with Binary Collectors:</strong>
 * <ul>
 * <li><strong>BinaryJavaClassNodeCollector:</strong> Uses ASM to read compiled
 * bytecode</li>
 * <li><strong>ClassLoaderJavaClassNodeCollector:</strong> Uses reflection on
 * loaded classes</li>
 * <li><strong>SourceJavaClassNodeCollector:</strong> Parses source code
 * directly</li>
 * </ul>
 * <p>
 * <strong>Usage in Analysis Pipeline:</strong>
 *
 * <pre>
 * Phase 2: ClassNode Collection
 *   └── SourceJavaClassNodeCollector creates JavaClassNode from .java source files
 * </pre>
 *
 * @see ClassNodeCollector
 * @see JavaClassNodeBinaryCollector
 * @since 1.3.2 - Additional Collector Types
 */
public class JavaClassNodeSourceCollector implements ClassNodeCollector {

    private static final Logger logger = LoggerFactory.getLogger(JavaClassNodeSourceCollector.class);

    protected final ResourceResolver resourceResolver;
    protected final JavaParser javaParser;

    /**
     * Constructs a new SourceJavaClassNodeCollector.
     * <p>
     * Requires ResourceResolver for accessing source file content.
     *
     * @param resourceResolver resolver for accessing file content
     */
    @Inject
    public JavaClassNodeSourceCollector(final ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        javaParser = new JavaParser();
    }

    /**
     * Returns the name of this collector.
     * <p>
     * Used for logging, debugging, and registry identification.
     *
     * @return the collector name
     */
    @Override
    public String getName() {
        return "SourceJavaClassNodeCollector";
    }

    /**
     * Checks if this collector can process the given ProjectFile.
     * <p>
     * By default, returns true only for .java files.
     *
     * @param source the ProjectFile to evaluate
     * @return true if the file has .java extension
     */
    @Override
    public boolean canCollect(final ProjectFile source) {
        return source != null && source.hasFileExtension("java");
    }

    /**
     * Collects JavaClassNode from a Java source file using JavaParser.
     *
     * @param source  the .java file to process
     * @param context the collection context
     */
    @Override
    public void collect(final ProjectFile source, final CollectionContext context) {

        try {
            // Extract FQN by parsing source
            final String fqn = extractFQNFromSource(source);

            if (fqn == null || fqn.isEmpty()) {
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

            logger.debug("Created JavaClassNode for {} from {} using JavaParser",
                    fqn, source.getRelativePath());

        } catch (final Exception e) {
            logger.error("Error collecting class node from {}: {}",
                    source.getRelativePath(), e.getMessage(), e);
        }
    }

    /**
     * Extracts the fully qualified name from a .java source file using JavaParser.
     * <p>
     * This method parses the source code and extracts the package name and primary
     * type declaration to construct the FQN.
     *
     * @param source the .java file
     * @return the fully qualified name, or null if extraction fails
     */
    protected String extractFQNFromSource(final ProjectFile source) {
        try {
            // Create ResourceLocation from file path
            final ResourceLocation location = ResourceLocation.file(source.getFilePath().toString());

            // Check if resource exists
            if (!resourceResolver.exists(location)) {
                logger.warn("Resource does not exist: {}", source.getRelativePath());
                return null;
            }

            // Parse the source file
            try (final InputStream inputStream = resourceResolver.openStream(location)) {
                if (inputStream == null) {
                    logger.warn("Could not open input stream for {}", source.getRelativePath());
                    return null;
                }

                final ParseResult<CompilationUnit> parseResult = javaParser.parse(inputStream);

                if (!parseResult.isSuccessful()) {
                    logger.warn("Failed to parse {}: {}", source.getRelativePath(),
                            parseResult.getProblems());
                    return null;
                }

                final CompilationUnit cu = parseResult.getResult().orElse(null);
                if (cu == null) {
                    logger.warn("No compilation unit for {}", source.getRelativePath());
                    return null;
                }

                // Extract package name
                final String packageName = cu.getPackageDeclaration()
                                             .map(pd -> pd.getNameAsString())
                                             .orElse("");

                // Find primary type declaration
                final TypeDeclaration<?> primaryType = cu.getType(0);
                if (primaryType == null) {
                    logger.warn("No primary type found in {}", source.getRelativePath());
                    return null;
                }

                final String className = primaryType.getNameAsString();

                // Construct FQN
                return packageName.isEmpty() ? className : packageName + "." + className;
            }

        } catch (final Exception e) {
            logger.error("Failed to parse source from {}: {}",
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
    protected JavaClassNode createClassNode(final String fqn, final ProjectFile source) {
        final JavaClassNode classNode = new JavaClassNode(fqn);
        classNode.setSourceFilePath(source.getFilePath().toString());
        classNode.setSourceType(FORMAT_SOURCE);
        classNode.addSourceAliasPath(source.getFilePath().toString());
        return classNode;
    }
}
