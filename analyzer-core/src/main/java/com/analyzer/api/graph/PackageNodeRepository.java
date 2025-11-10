package com.analyzer.api.graph;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing PackageNode instances.
 * Provides type-safe access to package nodes with specialized query methods.
 * 
 * <p>
 * This repository follows the same pattern as ClassNodeRepository, providing
 * specialized methods for working with PackageNode objects while delegating
 * to the underlying GraphRepository for storage.
 * </p>
 * 
 * @since Coupling Metrics Enhancement - Package Level
 */
public interface PackageNodeRepository extends NodeRepository<PackageNode> {

    /**
     * Finds all PackageNode instances in the repository.
     * 
     * @return List of all PackageNode objects
     */
    @Override
    List<PackageNode> findAll();

    /**
     * Finds a PackageNode by its package name.
     * 
     * @param packageName The fully qualified package name (e.g.,
     *                    "com.example.service")
     * @return Optional containing the PackageNode if found
     */
    Optional<PackageNode> getByPackageName(String packageName);

    /**
     * Gets or creates a PackageNode for the given package name.
     * If a PackageNode already exists for this package, returns it.
     * Otherwise, creates a new one and stores it.
     * 
     * @param packageName The fully qualified package name
     * @return The PackageNode for this package (existing or newly created)
     */
    PackageNode getOrCreateByPackageName(String packageName);
}
