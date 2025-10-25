package com.analyzer.rules.graph;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractJavaParserInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Graph-aware inspector that creates JavaClassNode instances by analyzing Java
 * source code.
 * Uses JavaParser to extract class declarations and creates corresponding graph
 * nodes
 * with complete class metadata.
 *
 * <p>
 * This inspector:
 * </p>
 * <ul>
 * <li>Scans Java source files for class/interface/enum/record/annotation
 * declarations</li>
 * <li>Creates JavaClassNode instances for each discovered class</li>
 * <li>Links nodes to their source ProjectFile</li>
 * <li>Extracts package information, class names, and types</li>
 * <li>Contributes nodes to the project graph for relationship analysis</li>
 * </ul>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, need = {
         }, produces = {
                SourceJavaClassNodeInspector.TAGS.TAG_JAVA_CLASS_NODE_SOURCE })
public class SourceJavaClassNodeInspector extends AbstractJavaParserInspector {

    private static final Logger logger = LoggerFactory.getLogger(SourceJavaClassNodeInspector.class);

    public static class TAGS {
        public static final String TAG_JAVA_CLASS_NODE_SOURCE = "java.class_node.source";

        // This class is kept for the @InspectorDependencies annotation reference
    }

    private final GraphRepository graphRepository;

    @Inject
    public SourceJavaClassNodeInspector(ResourceResolver resourceResolver, GraphRepository graphRepository) {
        super(resourceResolver);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "Source Java Class Node Inspector";
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // All filtering is handled by @InspectorDependencies
        return true;
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
                                          NodeDecorator projectFileDecorator) {

        try {
            // Extract package name
            String packageName = cu.getPackageDeclaration()
                    .map(PackageDeclaration::getNameAsString)
                    .orElse("");

            // Process class and interface declarations
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                processClassOrInterface(classDecl, packageName, projectFile, projectFileDecorator);
            });

            // Process enum declarations
            cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
                processEnum(enumDecl, packageName, projectFile, projectFileDecorator);
            });

            // Process record declarations (Java 14+)
            cu.findAll(RecordDeclaration.class).forEach(recordDecl -> {
                processRecord(recordDecl, packageName, projectFile, projectFileDecorator);
            });

            // Process annotation declarations
            cu.findAll(AnnotationDeclaration.class).forEach(annotationDecl -> {
                processAnnotation(annotationDecl, packageName, projectFile, projectFileDecorator);
            });

        } catch (Exception e) {
            logger.warn("Error processing source file {} for class nodes: {}",
                    projectFile.getRelativePath(), e.getMessage());
            projectFileDecorator.error(e.getMessage());
        }
    }

    private void processClassOrInterface(ClassOrInterfaceDeclaration classDecl, String packageName,
            ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
        String className = classDecl.getNameAsString();
        String fullyQualifiedName = buildFullyQualifiedName(packageName, className);
        String classType = classDecl.isInterface() ? "interface" : "class";

        JavaClassNode classNode = createJavaClassNode(fullyQualifiedName, classType, projectFile);
        JavaClassNode existingNode = (JavaClassNode) graphRepository.getOrCreateNode(classNode);

        logger.debug("Created/found class node from source: {} (type: {})", fullyQualifiedName, classType);

        // Set tag to indicate this class node was created from source analysis
        projectFileDecorator.setProperty(TAGS.TAG_JAVA_CLASS_NODE_SOURCE, fullyQualifiedName);
    }

    private void processEnum(EnumDeclaration enumDecl, String packageName,
            ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
        String enumName = enumDecl.getNameAsString();
        String fullyQualifiedName = buildFullyQualifiedName(packageName, enumName);

        JavaClassNode enumNode = createJavaClassNode(fullyQualifiedName, "enum", projectFile);
        JavaClassNode existingNode = (JavaClassNode) graphRepository.getOrCreateNode(enumNode);

        logger.debug("Created/found enum node from source: {}", fullyQualifiedName);
        projectFileDecorator.setProperty(TAGS.TAG_JAVA_CLASS_NODE_SOURCE, fullyQualifiedName);
    }

    private void processRecord(RecordDeclaration recordDecl, String packageName,
            ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
        String recordName = recordDecl.getNameAsString();
        String fullyQualifiedName = buildFullyQualifiedName(packageName, recordName);

        JavaClassNode recordNode = createJavaClassNode(fullyQualifiedName, "record", projectFile);
        JavaClassNode existingNode = (JavaClassNode) graphRepository.getOrCreateNode(recordNode);

        logger.debug("Created/found record node from source: {}", fullyQualifiedName);
        projectFileDecorator.setProperty(TAGS.TAG_JAVA_CLASS_NODE_SOURCE, fullyQualifiedName);
    }

    private void processAnnotation(AnnotationDeclaration annotationDecl, String packageName,
            ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
        String annotationName = annotationDecl.getNameAsString();
        String fullyQualifiedName = buildFullyQualifiedName(packageName, annotationName);

        JavaClassNode annotationNode = createJavaClassNode(fullyQualifiedName, "annotation", projectFile);
        JavaClassNode existingNode = (JavaClassNode) graphRepository.getOrCreateNode(annotationNode);

        logger.debug("Created/found annotation node from source: {}", fullyQualifiedName);
        projectFileDecorator.setProperty(TAGS.TAG_JAVA_CLASS_NODE_SOURCE, fullyQualifiedName);
    }

    private String buildFullyQualifiedName(String packageName, String className) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return className;
        }
        return packageName + "." + className;
    }

    private JavaClassNode createJavaClassNode(String fullyQualifiedName, String classType, ProjectFile projectFile) {
        return JavaClassNode.create(
                fullyQualifiedName,
                classType,
                JavaClassNode.SOURCE_TYPE_SOURCE,
                projectFile.getId(),
                projectFile.getRelativePath().toString());
    }

}
