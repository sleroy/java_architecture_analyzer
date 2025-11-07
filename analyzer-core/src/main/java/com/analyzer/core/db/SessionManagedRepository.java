package com.analyzer.core.db;

import com.analyzer.core.db.entity.GraphEdgeEntity;
import com.analyzer.core.db.entity.GraphNodeEntity;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

/**
 * A session-managed wrapper around H2GraphStorageRepository that holds a
 * SqlSession.
 * This class implements Closeable to ensure proper session lifecycle
 * management.
 * 
 * <p>
 * This design solves H2's CLOB lazy-loading issue by keeping the SqlSession
 * open
 * for as long as the repository is in use. When you close this repository, the
 * session
 * is closed, invalidating any lazy CLOB references.
 * 
 * <p>
 * <b>Usage Pattern:</b>
 * 
 * <pre>{@code
 * try (SessionManagedRepository repo = database.createSessionManagedRepository()) {
 *     List<GraphNodeEntity> nodes = repo.findAllNodes();
 *     // Process nodes while session is still open
 *     // CLOBs can be accessed here
 * } // Session automatically closed here
 * }</pre>
 * 
 * <p>
 * <b>Important:</b> Do NOT access CLOB fields (properties, metrics, tags) after
 * closing this repository, as the connection will be closed and you'll get
 * "Connection is broken: session closed" errors.
 *
 * @see H2GraphStorageRepository
 * @see H2GraphDatabase#createSessionManagedRepository()
 */
public class SessionManagedRepository implements AutoCloseable {

    private final H2GraphStorageRepository repository;
    private final SqlSession session;
    private boolean closed = false;

    /**
     * Package-private constructor. Use
     * {@link H2GraphDatabase#createSessionManagedRepository()}
     * to create instances.
     *
     * @param repository The underlying repository
     * @param session    The SqlSession to use for all operations
     */
    SessionManagedRepository(final H2GraphStorageRepository repository, final SqlSession session) {
        this.repository = repository;
        this.session = session;
    }

    /**
     * Find all nodes in the database.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @return List of all nodes
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findAllNodes() {
        ensureOpen();
        return repository.findAll(session);
    }

    /**
     * Find all nodes of a specific type.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param nodeType The node type
     * @return List of nodes
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findNodesByType(final String nodeType) {
        ensureOpen();
        return repository.findNodesByType(session, nodeType);
    }

    /**
     * Find all edges in the database.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @return List of all edges
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphEdgeEntity> findAllEdges() {
        ensureOpen();
        return repository.findAllEdges(session);
    }

    /**
     * Find all edges of a specific type.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param edgeType The edge type
     * @return List of edges
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphEdgeEntity> findEdgesByType(final String edgeType) {
        ensureOpen();
        return repository.findEdgesByType(session, edgeType);
    }

    /**
     * Find a node by its ID.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param nodeId The node ID
     * @return GraphNodeEntity or null if not found
     * @throws IllegalStateException if repository is closed
     */
    public GraphNodeEntity findNodeById(final String nodeId) {
        ensureOpen();
        return repository.findNodeById(session, nodeId);
    }

    /**
     * Find nodes by a single tag.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param tag The tag to search for
     * @return List of nodes containing this tag
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findNodesByTag(final String tag) {
        ensureOpen();
        return repository.findNodesByTag(session, tag);
    }

    /**
     * Find nodes having any of the provided tags (OR condition).
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param tags List of tags
     * @return List of nodes containing any of these tags
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findNodesByAnyTags(final java.util.List<String> tags) {
        ensureOpen();
        return repository.findNodesByAnyTags(session, tags);
    }

    /**
     * Find nodes having all of the provided tags (AND condition).
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param tags List of tags
     * @return List of nodes containing all of these tags
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findNodesByAllTags(final java.util.List<String> tags) {
        ensureOpen();
        return repository.findNodesByAllTags(session, tags);
    }

    /**
     * Find nodes by type and having any of the provided tags (OR condition).
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param nodeType The node type
     * @param tags     List of tags
     * @return List of nodes of this type containing any of these tags
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findNodesByTypeAndAnyTags(final String nodeType,
            final java.util.List<String> tags) {
        ensureOpen();
        return repository.findNodesByTypeAndAnyTags(session, nodeType, tags);
    }

    /**
     * Find nodes by type and having all of the provided tags (AND condition).
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param nodeType The node type
     * @param tags     List of tags
     * @return List of nodes of this type containing all of these tags
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findNodesByTypeAndAllTags(final String nodeType,
            final java.util.List<String> tags) {
        ensureOpen();
        return repository.findNodesByTypeAndAllTags(session, nodeType, tags);
    }

    /**
     * Check if a node exists.
     * The session remains open.
     *
     * @param nodeId The node ID
     * @return true if exists
     * @throws IllegalStateException if repository is closed
     */
    public boolean nodeExists(final String nodeId) {
        ensureOpen();
        return repository.nodeExists(session, nodeId);
    }

    /**
     * Find all outgoing edges from a node.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param nodeId Source node ID
     * @return List of edges
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphEdgeEntity> findOutgoingEdges(final String nodeId) {
        ensureOpen();
        return repository.findOutgoingEdges(session, nodeId);
    }

    /**
     * Find all incoming edges to a node.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param nodeId Target node ID
     * @return List of edges
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphEdgeEntity> findIncomingEdges(final String nodeId) {
        ensureOpen();
        return repository.findIncomingEdges(session, nodeId);
    }

    /**
     * Find nodes with a specific property value using JSON path query.
     * The session remains open, so CLOB fields can be accessed after this call.
     *
     * @param jsonPath JSON path (e.g., '$.java.fullyQualifiedName')
     * @param value    Property value to match
     * @return List of matching nodes
     * @throws IllegalStateException if repository is closed
     */
    public List<GraphNodeEntity> findNodesByPropertyValue(final String jsonPath, final String value) {
        ensureOpen();
        return repository.findNodesByPropertyValue(session, jsonPath, value);
    }

    /**
     * Check if this repository is still open.
     *
     * @return true if open, false if closed
     */
    public boolean isOpen() {
        return !closed;
    }

    /**
     * Close the underlying SqlSession.
     * After calling this, any attempt to use the repository or access CLOB fields
     * from previously retrieved entities will fail.
     */
    @Override
    public void close() {
        if (!closed) {
            session.close();
            closed = true;
        }
    }

    /**
     * Ensure the repository is still open.
     *
     * @throws IllegalStateException if repository is closed
     */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SessionManagedRepository is closed");
        }
    }
}
