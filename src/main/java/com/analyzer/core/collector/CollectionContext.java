package com.analyzer.core.collector;

import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.model.ProjectFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Context provided to Collectors for accessing repositories and creating nodes.
 * <p>
 * This class acts as a facade that:
 * <ul>
 *   <li>Provides access to underlying repositories without direct injection</li>
 *   <li>Encapsulates node creation and linking logic</li>
 *   <li>Maintains clean separation between collectors and repository implementation</li>
 * </ul>
 * <p>
 * Collectors should ONLY access repositories through this context, never via
 * direct injection. This allows the collection process to be more testable and
 * provides a single point of control for node creation.
 * <p>
 * Example usage:
 * <pre>{@code
 * public void collect(ProjectFile source, CollectionContext context) {
 *     // Extract class information from .class file
 *     String fqn = extractFQN(source);
 *     
 *     // Create node
 *     JavaClassNode node = new JavaClassNode(fqn);
 *     node.setSourceFilePath(source.getAbsolutePath().toString());
 *     
 *     // Store via context
 *     context.addClassNode(node);
 *     context.linkClassNodeToFile(node, source);
 * }
 * }</pre>
 *
 * @see Collector
 * @since Phase 2 - Collector Architecture Refactoring
 */
public class CollectionContext {

    private final ProjectFileRepository projectFileRepository;
    private final ClassNodeRepository classNodeRepository;

    /**
     * Creates a new collection context with the given repositories.
     *
     * @param projectFileRepository repository for ProjectFile management
     * @param classNodeRepository   repository for JavaClassNode management
     */
    public CollectionContext(ProjectFileRepository projectFileRepository,
                             ClassNodeRepository classNodeRepository) {
        this.projectFileRepository = projectFileRepository;
        this.classNodeRepository = classNodeRepository;
    }

    // ==================== ClassNode Operations ====================

    /**
     * Adds a JavaClassNode to the repository.
     * <p>
     * This is the primary method for storing newly created class nodes.
     * The node will be indexed by its fully qualified name.
     *
     * @param node the JavaClassNode to add
     * @throws IllegalArgumentException if node is null or FQN is invalid
     */
    public void addClassNode(JavaClassNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot add null ClassNode");
        }
        if (node.getFullyQualifiedName() == null || node.getFullyQualifiedName().isEmpty()) {
            throw new IllegalArgumentException("ClassNode must have a fully qualified name");
        }
        classNodeRepository.save(node);
    }

    /**
     * Links a JavaClassNode to its source ProjectFile by setting the source file path.
     * <p>
     * This establishes the relationship between a class and the file it came from,
     * enabling traceability and dependency tracking.
     * <p>
     * Note: This method sets the sourceFilePath property on the ClassNode.
     * The actual linking is done through this property rather than a separate relationship.
     *
     * @param classNode the JavaClassNode to link
     * @param file      the source ProjectFile
     */
    public void linkClassNodeToFile(JavaClassNode classNode, ProjectFile file) {
        if (classNode == null || file == null) {
            throw new IllegalArgumentException("Both classNode and file must be non-null");
        }
        // Link by setting the source file path on the class node
        classNode.setSourceFilePath(file.getFilePath().toString());
    }

    /**
     * Retrieves a JavaClassNode by its fully qualified name.
     *
     * @param fqn the fully qualified class name
     * @return Optional containing the node if found, empty otherwise
     */
    public Optional<JavaClassNode> getClassNode(String fqn) {
        return classNodeRepository.findById(fqn);
    }

    /**
     * Gets or creates a JavaClassNode by its fully qualified name.
     * <p>
     * If a node with the given FQN exists, it is returned.
     * Otherwise, a new node is created and saved.
     *
     * @param fqn the fully qualified class name
     * @return the existing or newly created JavaClassNode
     */
    public JavaClassNode getOrCreateClassNode(String fqn) {
        return classNodeRepository.getOrCreateByFqn(fqn);
    }

    /**
     * Checks if a JavaClassNode already exists for the given FQN.
     *
     * @param fqn the fully qualified class name
     * @return true if a node exists, false otherwise
     */
    public boolean classNodeExists(String fqn) {
        return classNodeRepository.findById(fqn).isPresent();
    }

    // ==================== ProjectFile Operations ====================

    /**
     * Retrieves a ProjectFile by its path.
     *
     * @param path the file path (can be relative or absolute)
     * @return Optional containing the ProjectFile if found, empty otherwise
     */
    public Optional<ProjectFile> getProjectFile(Path path) {
        return projectFileRepository.findByPath(path);
    }

    /**
     * Retrieves a ProjectFile by its string path.
     * Convenience method that converts string to Path.
     *
     * @param pathString the file path as a string
     * @return Optional containing the ProjectFile if found, empty otherwise
     */
    public Optional<ProjectFile> getProjectFile(String pathString) {
        return projectFileRepository.findByPath(Paths.get(pathString));
    }

    /**
     * Retrieves all JavaClassNodes associated with a ProjectFile.
     * <p>
     * A single .class file typically contains one class, but inner classes
     * and nested classes may result in multiple JavaClassNode objects.
     * <p>
     * This method finds all ClassNodes whose sourceFilePath matches the given file.
     *
     * @param file the ProjectFile to query
     * @return collection of associated JavaClassNode objects (may be empty)
     */
    public java.util.Collection<JavaClassNode> getClassNodesForFile(ProjectFile file) {
        String filePath = file.getFilePath().toString();
        return classNodeRepository.findAll().stream()
                .filter(node -> filePath.equals(node.getSourceFilePath()))
                .collect(java.util.stream.Collectors.toList());
    }

    // ==================== Repository Access (Limited) ====================

    /**
     * Gets the ProjectFileRepository.
     * <p>
     * WARNING: Direct repository access should be avoided when possible.
     * Use the convenience methods provided by this context instead.
     * <p>
     * This method is provided for advanced use cases where collectors need
     * functionality not exposed by this context.
     *
     * @return the ProjectFileRepository
     */
    public ProjectFileRepository getProjectFileRepository() {
        return projectFileRepository;
    }

    /**
     * Gets the ClassNodeRepository.
     * <p>
     * WARNING: Direct repository access should be avoided when possible.
     * Use the convenience methods provided by this context instead.
     * <p>
     * This method is provided for advanced use cases where collectors need
     * functionality not exposed by this context.
     *
     * @return the ClassNodeRepository
     */
    public ClassNodeRepository getClassNodeRepository() {
        return classNodeRepository;
    }
}
