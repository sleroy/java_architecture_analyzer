package com.analyzer.core.graph;

import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.PackageNode;
import com.analyzer.api.graph.PackageNodeRepository;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository implementation for PackageNode that delegates to GraphRepository.
 * 
 * <p>
 * This implementation provides type-safe access to PackageNode instances
 * while using GraphRepository as the underlying storage mechanism.
 * Follows the same pattern as DelegatingClassNodeRepository.
 * </p>
 * 
 * @since Coupling Metrics Enhancement - Package Level
 */
public class DelegatingPackageNodeRepository implements PackageNodeRepository {

    private final GraphRepository graphRepository;

    @Inject
    public DelegatingPackageNodeRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public Optional<PackageNode> findById(String id) {
        return graphRepository.getNodeById(id)
                .filter(node -> node instanceof PackageNode)
                .map(node -> (PackageNode) node);
    }

    @Override
    public PackageNode getOrCreate(String id) {
        Optional<PackageNode> existing = findById(id);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new PackageNode
        PackageNode packageNode = new PackageNode(id);
        graphRepository.addNode(packageNode);
        return packageNode;
    }

    @Override
    public List<PackageNode> findAll() {
        return new ArrayList<>(graphRepository.getNodesByClass(PackageNode.class));
    }

    @Override
    public List<PackageNode> findByTag(String tag) {
        return graphRepository.getNodesByClass(PackageNode.class).stream()
                .filter(packageNode -> packageNode.hasTag(tag))
                .toList();
    }

    @Override
    public void save(PackageNode node) {
        graphRepository.addNode(node);
    }

    @Override
    public Optional<PackageNode> getByPackageName(String packageName) {
        // Normalize package name
        String normalizedPackageName = (packageName == null || packageName.trim().isEmpty())
                ? "(default)"
                : packageName;

        return findById(normalizedPackageName);
    }

    @Override
    public PackageNode getOrCreateByPackageName(String packageName) {
        // Normalize package name
        String normalizedPackageName = (packageName == null || packageName.trim().isEmpty())
                ? "(default)"
                : packageName;

        return getOrCreate(normalizedPackageName);
    }
}
