package com.analyzer.core.collector;

import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

import static com.analyzer.core.inspector.InspectorTags.*;
import static com.analyzer.core.inspector.InspectorTags.FORMAT_BINARY;
import static com.analyzer.core.inspector.InspectorTags.LANGUAGE_JAVA;
import static com.analyzer.core.inspector.InspectorTags.TAG_LANGUAGE;

/**
 * Abstract base class for collectors that create JavaClassNode objects from
 * Java source files.
 * <p>
 * This collector uses JavaParser to parse .java source files and extract class
 * information.
 * It's particularly useful for:
 * <ul>
 * <li>Analyzing source code structure</li>
 * <li>Extracting package and class declarations</li>
 * <li>Understanding source-level relationships</li>
 * <li>Working with unparsed/uncompiled code</li>
 * </ul>
 * <p>
 * Unlike binary collectors (ASM or ClassLoader), this collector works directly
 * with
 * source code, making it suitable for analyzing projects that haven't been
 * compiled yet.
 * <p>
 * <strong>Usage in Analysis Pipeline:</strong>
 * 
 * <pre>
 * Phase 2: ClassNode Collection
 *   └── SourceParserCollector creates JavaClassNode from .java files
 * </pre>
 *
 * @see ClassNodeCollector
 * @see AbstractBinaryClassCollector
 * @since 1.3.2 - Additional Collector Types
 */
public abstract class AbstractSourceParserCollector implements ClassNodeCollector {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSourceParserCollector.class);

    protected final ResourceResolver resourceResolver;
    protected final JavaParser javaParser;

    /**
     * Constructor with ResourceResolver for reading source files.
     *
     * @param resourceResolver resolver for accessing file content
     */
    protected AbstractSourceParserCollector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        this.javaParser = new JavaParser();
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
    public boolean canCollect(ProjectFile source) {
        return source != null && source.hasFileExtension("java");
    }

    /**
     * Collects JavaClassNode from a Java source file using JavaParser.
     *
     * @param source  the .java file to process
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
            // Extract FQN by parsing source
            String fqn = extractFQNFromSource(source);

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

            logger.debug("Created JavaClassNode for {} from {} using JavaParser",
                    fqn, source.getRelativePath());

        } catch (Exception e) {
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
    protected String extractFQNFromSource(ProjectFile source) {
        try {
            // Create ResourceLocation from file path
            ResourceLocation location = ResourceLocation.file(source.getFilePath().toString());

            // Check if resource exists
            if (!resourceResolver.exists(location)) {
                logger.warn("Resource does not exist: {}", source.getRelativePath());
                return null;
            }

            // Parse the source file
            try (InputStream inputStream = resourceResolver.openStream(location)) {
                if (inputStream == null) {
                    logger.warn("Could not open input stream for {}", source.getRelativePath());
                    return null;
                }

                ParseResult<CompilationUnit> parseResult = javaParser.parse(inputStream);

                if (!parseResult.isSuccessful()) {
                    logger.warn("Failed to parse {}: {}", source.getRelativePath(),
                            parseResult.getProblems());
                    return null;
                }

                CompilationUnit cu = parseResult.getResult().orElse(null);
                if (cu == null) {
                    logger.warn("No compilation unit for {}", source.getRelativePath());
                    return null;
                }

                // Extract package name
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString())
                        .orElse("");

                // Find primary type declaration
                TypeDeclaration<?> primaryType = cu.getType(0);
                if (primaryType == null) {
                    logger.warn("No primary type found in {}", source.getRelativePath());
                    return null;
                }

                String className = primaryType.getNameAsString();

                // Construct FQN
                return packageName.isEmpty() ? className : packageName + "." + className;
            }

        } catch (Exception e) {
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
    protected JavaClassNode createClassNode(String fqn, ProjectFile source) {
        JavaClassNode classNode = new JavaClassNode(fqn);
        classNode.setSourceFilePath(source.getFilePath().toString());
        return classNode;
    }
}
