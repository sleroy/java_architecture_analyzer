package com.analyzer.rules.graph;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.GraphNode;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.AbstractProjectFileInspector;
import com.analyzer.resource.ResourceResolver;
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
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        JavaImportGraphInspector.TAGS.TAG_IMPORT_DEPENDENCIES_PROCESSED })
public class JavaImportGraphInspector extends AbstractProjectFileInspector {

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

    public static class TAGS {
        public static final String TAG_IMPORT_DEPENDENCIES_PROCESSED = "java.import.dependencies.processed";
        public static final String TAG_GRAPH_PROCESSING_ERROR = "java.graph.processing.error";
    }

    @Inject
    public JavaImportGraphInspector(ResourceResolver resourceResolver, GraphRepository graphRepository) {
        this.resourceResolver = resourceResolver;
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "JavaImportGraphInspector";
    }

    @Override
    public boolean supports(ProjectFile file) {
        // All filtering is handled by @InspectorDependencies
        return true;
    }

    @Override
    protected void analyzeProjectFile(ProjectFile file, NodeDecorator<ProjectFile> decorator) {
        try {
            // Read file content
            String content = readFileContent(file);
            if (content == null) {
                logger.debug("Could not read content for file: {}", file.getRelativePath());
                return;
            }

            // Create node for this Java file
            GraphNode fileNode = createJavaFileNode(file);
            fileNode = graphRepository.getOrCreateNode(fileNode);

            // Extract imports and create dependency edges
            Set<String> imports = extractImports(content);
            String currentPackage = extractPackageName(content);

            logger.debug("Processing {} imports for file: {}", imports.size(), file.getRelativePath());

            for (String importedClass : imports) {
                processImportDependency(fileNode, importedClass, currentPackage, file);
            }

            decorator.setProperty(TAGS.TAG_IMPORT_DEPENDENCIES_PROCESSED, String.valueOf(imports.size()));

        } catch (Exception e) {
            logger.warn("Error processing Java import graph for file {}: {}", file.getRelativePath(), e.getMessage());
            decorator.error(e.getMessage());
        }
    }

    private String readFileContent(ProjectFile file) {
        try {
            return Files.readString(file.getFilePath());
        } catch (IOException e) {
            logger.warn("Could not read file content for {}: {}", file.getRelativePath(), e.getMessage());
            return null;
        }
    }

    private Set<String> extractImports(String content) {
        Set<String> imports = new HashSet<>();
        Matcher matcher = IMPORT_PATTERN.matcher(content);

        while (matcher.find()) {
            String importStatement = matcher.group(1);
            // Skip wildcard imports for now (could be enhanced later)
            if (!importStatement.endsWith(".*")) {
                imports.add(importStatement);
            }
        }

        return imports;
    }

    private String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void processImportDependency(GraphNode sourceFileNode, String importedClass,
            String currentPackage, ProjectFile sourceFile) {

        // Skip java.lang imports (implicit)
        if (importedClass.startsWith("java.lang.") && !importedClass.contains(".lang.reflect.")) {
            return;
        }

        // Create a node for the imported class/package
        GraphNode importedNode = createImportedClassNode(importedClass);
        importedNode = graphRepository.getOrCreateNode(importedNode);

        // Create dependency edge
        graphRepository.getOrCreateEdge(sourceFileNode, importedNode, "imports");

        logger.debug("Created import edge: {} imports {}",
                sourceFile.getRelativePath(), importedClass);
    }

    private GraphNode createJavaFileNode(ProjectFile file) {
        JavaFileGraphNode node = new JavaFileGraphNode(file.getId(), file);

        // Set properties
        node.setProperty("file_name", file.getFileName());
        node.setProperty("relative_path", file.getRelativePath());
        node.setProperty("absolute_path", file.getId());

        // Try to get file size from filesystem
        try {
            node.setProperty("file_size", Files.size(file.getFilePath()));
        } catch (IOException e) {
            node.setProperty("file_size", -1);
        }

        // Add existing tags from the ProjectFile
        java.util.Map<String, Object> existingTags = file.getAllProperties();
        if (existingTags != null && !existingTags.isEmpty()) {
            node.setProperty("project_file_tags", existingTags);
        }

        return node;
    }

    /**
     * Concrete GraphNode implementation for Java file nodes in the import graph.
     */
    private static class JavaFileGraphNode extends com.analyzer.core.graph.BaseGraphNode {
        private final ProjectFile sourceFile;

        public JavaFileGraphNode(String nodeId, ProjectFile sourceFile) {
            super(nodeId, "java_file");
            this.sourceFile = sourceFile;
        }

        @Override
        public String getDisplayLabel() {
            return sourceFile.getFileName() + " (Java File)";
        }
    }

    private GraphNode createImportedClassNode(String fullyQualifiedClassName) {
        ImportedClassGraphNode node = new ImportedClassGraphNode(fullyQualifiedClassName);

        // Set properties
        node.setProperty("fully_qualified_name", fullyQualifiedClassName);
        node.setProperty("simple_name", getSimpleName(fullyQualifiedClassName));
        node.setProperty("package", getPackageName(fullyQualifiedClassName));

        // Add classification based on package
        node.setProperty("is_jdk_class", fullyQualifiedClassName.startsWith("java.") ||
                fullyQualifiedClassName.startsWith("javax."));
        node.setProperty("is_third_party", !fullyQualifiedClassName.startsWith("com.analyzer.") &&
                !fullyQualifiedClassName.startsWith("java.") &&
                !fullyQualifiedClassName.startsWith("javax."));

        return node;
    }

    /**
     * Concrete GraphNode implementation for imported class nodes in the import
     * graph.
     */
    private static class ImportedClassGraphNode extends com.analyzer.core.graph.BaseGraphNode {
        private final String fullyQualifiedClassName;

        public ImportedClassGraphNode(String fullyQualifiedClassName) {
            super(fullyQualifiedClassName, determineNodeType(fullyQualifiedClassName));
            this.fullyQualifiedClassName = fullyQualifiedClassName;
        }

        private static String determineNodeType(String fqn) {
            if (fqn.startsWith("java.")) {
                return "java_class";
            } else if (fqn.startsWith("javax.")) {
                return "java_class";
            } else if (fqn.startsWith("org.springframework.")) {
                return "java_class";
            } else if (fqn.startsWith("com.analyzer.")) {
                return "java_class";
            } else {
                return "java_class";
            }
        }

        @Override
        public String getDisplayLabel() {
            String simpleName = fullyQualifiedClassName;
            int lastDot = fullyQualifiedClassName.lastIndexOf('.');
            if (lastDot >= 0) {
                simpleName = fullyQualifiedClassName.substring(lastDot + 1);
            }
            return simpleName + " (Class)";
        }
    }

    private String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }

    private String getPackageName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(0, lastDot) : "";
    }

}
