package com.analyzer.core.collector;

import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;

/**
 * Collector interface for creating JavaClassNode objects from ProjectFile sources.
 * <p>
 * ClassNodeCollectors are responsible for extracting class information from various
 * file types (.class, .java, etc.) and creating JavaClassNode representations.
 * <p>
 * This is a key part of Phase 2 in the analysis pipeline:
 * <pre>
 * Phase 1: File Discovery → ProjectFiles created
 * Phase 2: ClassNode Collection → JavaClassNodes created from ProjectFiles ← THIS
 * Phase 3: ProjectFile Analysis → Inspectors analyze ProjectFiles
 * Phase 4: ClassNode Analysis → Inspectors analyze JavaClassNodes
 * </pre>
 * <p>
 * Example implementations:
 * <ul>
 *   <li>BinaryClassNodeCollector: Reads .class files using ASM to extract FQN and basic metadata</li>
 *   <li>SourceClassNodeCollector: Parses .java files using JavaParser to extract class structure</li>
 * </ul>
 * <p>
 * Implementation guidelines:
 * <ul>
 *   <li>Use {@link #canCollect(ProjectFile)} to check file type/extension before processing</li>
 *   <li>Extract fully qualified name (FQN) from the file</li>
 *   <li>Create JavaClassNode with FQN</li>
 *   <li>Set basic properties (source file path, package, simple name)</li>
 *   <li>Store via {@link CollectionContext#addClassNode(JavaClassNode)}</li>
 *   <li>Link to source file via {@link CollectionContext#linkClassNodeToFile(JavaClassNode, ProjectFile)}</li>
 * </ul>
 * <p>
 * Example implementation:
 * <pre>{@code
 * public class BinaryClassNodeCollector implements ClassNodeCollector {
 *     
 *     @Override
 *     public String getName() {
 *         return "BinaryClassNodeCollector";
 *     }
 *     
 *     @Override
 *     public boolean canCollect(ProjectFile source) {
 *         return source.hasFileExtension("class");
 *     }
 *     
 *     @Override
 *     public void collect(ProjectFile source, CollectionContext context) {
 *         // Read .class file using ASM
 *         String fqn = extractFQNFromBytecode(source);
 *         
 *         // Create node
 *         JavaClassNode node = new JavaClassNode(fqn);
 *         
 *         // Store via context
 *         context.addClassNode(node);
 *         context.linkClassNodeToFile(node, source);
 *     }
 * }
 * }</pre>
 *
 * @see Collector
 * @see CollectionContext
 * @see JavaClassNode
 * @since Phase 2 - Collector Architecture Refactoring
 */
public interface ClassNodeCollector extends Collector<ProjectFile, JavaClassNode> {
    
    /**
     * Determines if this collector can create JavaClassNode(s) from the given ProjectFile.
     * <p>
     * Typical checks include:
     * <ul>
     *   <li>File extension (.class, .java, etc.)</li>
     *   <li>File tags or properties</li>
     *   <li>File content markers</li>
     * </ul>
     * <p>
     * This method should be lightweight and fast, avoiding expensive I/O operations.
     *
     * @param source the ProjectFile to evaluate
     * @return true if this collector can process the file, false otherwise
     */
    @Override
    boolean canCollect(ProjectFile source);
    
    /**
     * Collects (creates) JavaClassNode(s) from the given ProjectFile.
     * <p>
     * This method should:
     * <ol>
     *   <li>Read the file to extract class information</li>
     *   <li>Extract fully qualified name (FQN)</li>
     *   <li>Create JavaClassNode with FQN</li>
     *   <li>Set basic node properties (source file, package, etc.)</li>
     *   <li>Store via context.addClassNode()</li>
     *   <li>Link to source via context.linkClassNodeToFile()</li>
     * </ol>
     * <p>
     * Note: A single file may produce multiple JavaClassNodes (e.g., inner classes).
     * Each should be added individually via the context.
     * <p>
     * Error handling:
     * <ul>
     *   <li>Log errors but don't throw exceptions - allow other collectors to run</li>
     *   <li>Mark problematic files with error properties if needed</li>
     *   <li>Skip files that can't be processed</li>
     * </ul>
     *
     * @param source  the ProjectFile to collect from
     * @param context the collection context providing repository access
     */
    @Override
    void collect(ProjectFile source, CollectionContext context);
}
