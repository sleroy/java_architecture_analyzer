package com.analyzer.core.collector;

/**
 * Base interface for collectors that create nodes of type T from sources of type S.
 * <p>
 * Collectors are responsible for NODE CREATION, while Inspectors analyze existing nodes.
 * This separation clarifies the architecture:
 * <ul>
 *   <li>Collectors: Create nodes (e.g., ProjectFile → JavaClassNode)</li>
 *   <li>Inspectors: Analyze nodes (e.g., JavaClassNode → tags/properties)</li>
 * </ul>
 * <p>
 * Example implementations:
 * <ul>
 *   <li>FileCollector: Creates ProjectFile objects from filesystem paths</li>
 *   <li>ClassNodeCollector: Creates JavaClassNode objects from ProjectFile objects</li>
 * </ul>
 * <p>
 * Collectors operate within a {@link CollectionContext} which provides access to
 * repositories for storing created nodes and establishing relationships.
 *
 * @param <S> Source type - the input being processed
 * @param <T> Target type - the node type being created
 * @see CollectionContext
 * @since Phase 2 - Collector Architecture Refactoring
 */
public interface Collector<S, T> {

    /**
     * Returns the unique name of this collector.
     * <p>
     * The name is used for:
     * <ul>
     *   <li>Logging and debugging</li>
     *   <li>Registry management</li>
     *   <li>Configuration and dependency resolution</li>
     * </ul>
     *
     * @return unique collector name (e.g., "BinaryClassNodeCollector")
     */
    String getName();

    /**
     * Determines if this collector can process the given source.
     * <p>
     * This method performs a lightweight check (typically based on file extension,
     * type checks, or simple metadata) to determine if collection is applicable.
     * <p>
     * Examples:
     * <ul>
     *   <li>BinaryClassNodeCollector: checks if file ends with ".class"</li>
     *   <li>SourceClassNodeCollector: checks if file ends with ".java"</li>
     * </ul>
     *
     * @param source the source object to evaluate
     * @return true if this collector can process the source, false otherwise
     */
    boolean canCollect(S source);

    /**
     * Collects (creates) one or more target nodes from the given source.
     * <p>
     * This method performs the actual node creation logic:
     * <ol>
     *   <li>Extract necessary information from source</li>
     *   <li>Create target node(s)</li>
     *   <li>Store node(s) via CollectionContext</li>
     *   <li>Establish relationships between nodes and sources</li>
     * </ol>
     * <p>
     * Implementation notes:
     * <ul>
     *   <li>Use context to access repositories - do NOT inject them directly</li>
     *   <li>Handle errors gracefully - log and continue when possible</li>
     *   <li>Multiple nodes can be created from a single source if appropriate</li>
     *   <li>Nodes should be linked to their source for traceability</li>
     * </ul>
     * <p>
     * Example:
     * <pre>{@code
     * @Override
     * public void collect(ProjectFile source, CollectionContext context) {
     *     String fqn = extractFQN(source);
     *     JavaClassNode node = new JavaClassNode(fqn);
     *     node.setSourceFilePath(source.getAbsolutePath().toString());
     *     
     *     context.addClassNode(node);
     *     context.linkClassNodeToFile(node, source);
     * }
     * }</pre>
     *
     * @param source  the source object to collect from
     * @param context the collection context providing repository access
     * @throws CollectionException if collection fails and cannot be recovered
     */
    void collect(S source, CollectionContext context);
}
