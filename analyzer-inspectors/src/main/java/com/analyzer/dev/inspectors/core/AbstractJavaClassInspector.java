package com.analyzer.dev.inspectors.core;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.core.inspector.InspectorTargetType;
import com.analyzer.core.model.ProjectFile;

import javax.inject.Inject;
import java.util.Optional;

/**
 * Base abstract class for inspectors that analyze JavaClassNode objects.
 * This provides the foundation for class-centric analysis where metrics
 * and properties are attached directly to JavaClassNode rather than ProjectFile.
 * <p>
 * Concrete implementations should extend this class and implement the
 * analyzeClass() method to perform their specific class-level analysis.
 * <p>
 * This class handles:
 * - Type safety for JavaClassNode objects
 * - Access to the source ProjectFile for bytecode reading
 * - Template method pattern for analysis
 * - Error handling and decorator integration
 *
 * @since Phase 2 - Class-Centric Architecture Refactoring
 */
public abstract class AbstractJavaClassInspector implements Inspector<JavaClassNode> {

    protected final ProjectFileRepository projectFileRepository;

    /**
     * Constructor with ProjectFileRepository injection.
     * Required for accessing bytecode from ProjectFile when analyzing classes.
     *
     * @param projectFileRepository repository for looking up ProjectFile instances
     */
    @Inject
    protected AbstractJavaClassInspector(ProjectFileRepository projectFileRepository) {
        this.projectFileRepository = projectFileRepository;
    }

    @Override
    public final void inspect(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
        try {
            analyzeClass(classNode, decorator);
        } catch (Exception e) {
            decorator.error("Error analyzing class " + classNode.getFullyQualifiedName() + ": " + e.getMessage());
        }
    }

    @Override
    public boolean supports(JavaClassNode classNode) {
        // By default, support any JavaClassNode - subclasses can override
        return classNode != null;
    }

    @Override
    public InspectorTargetType getTargetType() {
        return InspectorTargetType.JAVA_CLASS_NODE;
    }

    /**
     * Template method implemented by concrete inspectors.
     * This method is called when a JavaClassNode has been validated and
     * is ready for analysis.
     *
     * @param classNode the JavaClassNode to analyze
     * @param decorator the decorator for setting properties and tags on the class node
     */
    protected abstract void analyzeClass(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator);

    /**
     * Utility method to find the ProjectFile associated with this class.
     * This is useful for inspectors that need to read bytecode or source files.
     *
     * @param classNode the class node to find the file for
     * @return Optional containing the ProjectFile if found, empty otherwise
     */
    protected Optional<ProjectFile> findProjectFile(JavaClassNode classNode) {
        String sourceFilePath = classNode.getSourceFilePath();
        if (sourceFilePath == null || sourceFilePath.isEmpty()) {
            return Optional.empty();
        }
        return projectFileRepository.findByPath(java.nio.file.Paths.get(sourceFilePath));
    }

    /**
     * Utility method to check if a class node has a specific property.
     *
     * @param classNode    the class node to check
     * @param propertyName the name of the property
     * @return true if the property exists, false otherwise
     */
    protected boolean hasProperty(JavaClassNode classNode, String propertyName) {
        Object value = classNode.getProperty(propertyName);
        return value != null;
    }

    /**
     * Utility method to get a property value with type safety.
     *
     * @param classNode    the class node to get the property from
     * @param propertyName the name of the property
     * @param expectedType the expected type of the property value
     * @param <T>          the type parameter
     * @return the property value cast to the expected type, or null if not found or wrong type
     */
    protected <T> T getProperty(JavaClassNode classNode, String propertyName, Class<T> expectedType) {
        Object value = classNode.getProperty(propertyName);
        if (value != null && expectedType.isInstance(value)) {
            return expectedType.cast(value);
        }
        return null;
    }
}
