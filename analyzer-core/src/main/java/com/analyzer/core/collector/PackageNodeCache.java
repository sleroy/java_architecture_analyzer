package com.analyzer.core.collector;

import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.PackageNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cache for PackageNode instances during collection phase.
 * 
 * <p>
 * This cache ensures that only one PackageNode is created per unique package
 * as JavaClassNode collectors process .class and .java files. The cache is
 * thread-safe and can be shared across multiple collectors.
 * </p>
 * 
 * <p>
 * Usage Pattern:
 * </p>
 * 
 * <pre>
 * 1. Collectors call getOrCreatePackageNode(packageName) for each class
 * 2. Cache creates PackageNode if needed or returns existing one
 * 3. Collector updates class counts on the PackageNode
 * 4. PackageNode is registered with GraphRepository
 * </pre>
 * 
 * @since Coupling Metrics Enhancement - Package Level
 */
@Singleton
public class PackageNodeCache {

    private static final Logger logger = LoggerFactory.getLogger(PackageNodeCache.class);

    private final GraphRepository graphRepository;
    private final Map<String, PackageNode> cache = new ConcurrentHashMap<>();

    @Inject
    public PackageNodeCache(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    /**
     * Gets or creates a PackageNode for the given package name.
     * 
     * <p>
     * This method is thread-safe and ensures only one PackageNode exists per
     * package. If the node doesn't exist, it creates it and registers it with
     * the GraphRepository.
     * </p>
     * 
     * @param packageName The package name (e.g., "com.example.service")
     * @return The PackageNode for this package
     */
    public synchronized PackageNode getOrCreatePackageNode(String packageName) {
        // Normalize empty package name
        String normalizedPackageName = (packageName == null || packageName.trim().isEmpty())
                ? "(default)"
                : packageName;

        // Check cache first
        PackageNode packageNode = cache.get(normalizedPackageName);

        if (packageNode == null) {
            // Create new PackageNode
            packageNode = new PackageNode(normalizedPackageName);

            // Register with GraphRepository
            graphRepository.addNode(packageNode);

            // Add to cache
            cache.put(normalizedPackageName, packageNode);

            logger.debug("Created PackageNode for package: {}", normalizedPackageName);
        }

        return packageNode;
    }

    /**
     * Updates package metrics based on a JavaClassNode being added.
     * 
     * <p>
     * This method should be called by collectors after creating a JavaClassNode
     * to update the corresponding PackageNode's class counts.
     * </p>
     * 
     * @param classNode The JavaClassNode that was just created
     */
    public void addClassToPackage(JavaClassNode classNode) {
        String packageName = classNode.getPackageName();
        PackageNode packageNode = getOrCreatePackageNode(packageName);

        // Add class ID to package
        packageNode.addClass(classNode.getId());

        // Update class type counts
        String classType = classNode.getClassType();
        if ("interface".equals(classType)) {
            packageNode.setInterfaceCount(packageNode.getInterfaceCount() + 1);
        } else if ("abstract".equals(classType)) {
            packageNode.setAbstractClassCount(packageNode.getAbstractClassCount() + 1);
        } else {
            packageNode.setConcreteClassCount(packageNode.getConcreteClassCount() + 1);
        }

        packageNode.setClassCount(packageNode.getClassCount() + 1);

        logger.trace("Added class {} to package {}", classNode.getFullyQualifiedName(), packageName);
    }

    /**
     * Clears the cache. Useful for testing or when starting a new analysis.
     */
    public void clear() {
        cache.clear();
        logger.debug("PackageNode cache cleared");
    }

    /**
     * Gets the number of packages in the cache.
     * 
     * @return Number of cached PackageNode instances
     */
    public int size() {
        return cache.size();
    }
}
