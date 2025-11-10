package com.analyzer.rules.graph;

import com.analyzer.api.graph.BaseGraphNode;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.ImportedClassGraphNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.core.AbstractProjectFileInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Graph-aware inspector that creates nodes for Java files and edges for
 * import dependencies between files.
 * This inspector demonstrates file-level dependency relationships by
 * analyzing import statements and creating edges between files.
 */
@InspectorDependencies(requires = InspectorTags.TAG_JAVA_IS_SOURCE, produces = JavaImportGraphInspector.TAGS.METRIC_IMPORT_DEPENDENCIES_PROCESSED)
public class JavaImportGraphInspector extends AbstractProjectFileInspector {
    public static final String EDGE_IMPORTS = "imports";
    public static final String METRIC_FILE_SIZE = "file_size";
    public static final String PROP_JAVA_FULLY_QUALIFIED_NAME = "java.fully_qualified_name";
    public static final String PROP_JAVA_SIMPLE_NAME = "java.simple_name";
    public static final String PROP_JAVA_PACKAGE = "java.package";
    public static final String PROP_JAVA_IS_JDK_CLASS = "java.is_jdk_class";
    private static final Logger logger = LoggerFactory.getLogger(JavaImportGraphInspector.class);
    // Pattern to match import statements
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?);",
            Pattern.MULTILINE);
    // Pattern to match package declaration
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "package\\s+([\\w.]+);");
    private final ResourceResolver resourceResolver;
    private final GraphRepository graphRepository;

    @Inject
    public JavaImportGraphInspector(final ResourceResolver resourceResolver, final GraphRepository graphRepository) {
        this.resourceResolver = resourceResolver;
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "JavaImportGraphInspector";
    }

    @Override
    public boolean supports(final ProjectFile file) {
        // All filtering is handled by @InspectorDependencies
        return true;
    }

    @Override
    protected void analyzeProjectFile(final ProjectFile file, final NodeDecorator<ProjectFile> decorator) {
        try {
            // Read file content
            final String content = readFileContent(file);
            if (content == null) {
                logger.debug("Could not read content for file: {}", file.getRelativePath());
                return;
            }

            // Create node for this Java file
            GraphNode fileNode = createJavaFileNode(file);
            fileNode = graphRepository.getOrCreateNode(fileNode);

            // Extract imports and create dependency edges
            final Set<String> imports = extractImports(content);
            final String currentPackage = extractPackageName(content);

            logger.debug("Processing {} imports for file: {}", imports.size(), file.getRelativePath());

            for (final String importedClass : imports) {
                processImportDependency(fileNode, importedClass, currentPackage, file);
            }

            decorator.setMetric(TAGS.METRIC_IMPORT_DEPENDENCIES_PROCESSED, imports.size());

        } catch (final Exception e) {
            logger.warn("Error processing Java import graph for file {}: {}", file.getRelativePath(), e.getMessage());
            decorator.error(e.getMessage());
        }
    }

    private String readFileContent(final ProjectFile file) {
        try {
            return Files.readString(file.getFilePath());
        } catch (final IOException e) {
            logger.warn("Could not read file content for {}: {}", file.getRelativePath(), e.getMessage());
            return null;
        }
    }

    private Set<String> extractImports(final String content) {
        final Set<String> imports = new HashSet<>();
        final Matcher matcher = IMPORT_PATTERN.matcher(content);

        while (matcher.find()) {
            final String importStatement = matcher.group(1);
            // Skip wildcard imports for now (could be enhanced later)
            if (!importStatement.endsWith(".*")) {
                imports.add(importStatement);
            }
        }

        return imports;
    }

    private String extractPackageName(final String content) {
        final Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void processImportDependency(final GraphNode sourceFileNode, final String importedClass,
                                         final String currentPackage, final ProjectFile sourceFile) {

        // Skip java.lang imports (implicit)
        if (importedClass.startsWith("java.lang.") && !importedClass.contains(".lang.reflect.")) {
            return;
        }

        // Create a node for the imported class/package
        GraphNode importedNode = createImportedClassNode(importedClass);
        importedNode = graphRepository.getOrCreateNode(importedNode);

        // Create dependency edge
        graphRepository.getOrCreateEdge(sourceFileNode, importedNode, EDGE_IMPORTS);

        logger.debug("Created import edge: {} imports {}",
                sourceFile.getRelativePath(), importedClass);
    }

    private GraphNode createJavaFileNode(final ProjectFile file) {
        final JavaFileGraphNode node = new JavaFileGraphNode(file.getId(), file);

        // Try to get file size from filesystem
        try {
            node.getMetrics().setMetric(METRIC_FILE_SIZE, Files.size(file.getFilePath()));
        } catch (final IOException e) {
            node.getMetrics().setMetric(METRIC_FILE_SIZE, -1);
        }

        return node;
    }

    private GraphNode createImportedClassNode(final String fullyQualifiedClassName) {
        final ImportedClassGraphNode node = new ImportedClassGraphNode(fullyQualifiedClassName);

        // Set properties
        node.setProperty(PROP_JAVA_FULLY_QUALIFIED_NAME, fullyQualifiedClassName);
        node.setProperty(PROP_JAVA_SIMPLE_NAME, getSimpleName(fullyQualifiedClassName));
        node.setProperty(PROP_JAVA_PACKAGE, getPackageName(fullyQualifiedClassName));

        // Add classification based on package
        node.setProperty(PROP_JAVA_IS_JDK_CLASS, fullyQualifiedClassName.startsWith("java.") ||
                fullyQualifiedClassName.startsWith("javax."));


        return node;
    }

    private String getSimpleName(final String fullyQualifiedName) {
        final int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }

    private String getPackageName(final String fullyQualifiedName) {
        final int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(0, lastDot) : "";
    }

    public enum TAGS {
        ;
        public static final String METRIC_IMPORT_DEPENDENCIES_PROCESSED = "java.import.dependencies.processed";
        public static final String TAG_GRAPH_PROCESSING_ERROR = "java.graph.processing.error";
    }

    /**
     * Concrete GraphNode implementation for Java file nodes in the import graph.
     */
    private static class JavaFileGraphNode extends BaseGraphNode {
        private final ProjectFile sourceFile;

        public JavaFileGraphNode(final String nodeId, final ProjectFile sourceFile) {
            super(nodeId, "java_file");
            this.sourceFile = sourceFile;
        }

        @Override
        public String getDisplayLabel() {
            return sourceFile.getFileName() + " (Java File)";
        }
    }

}
