package com.analyzer.core.inspector;

import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.DelegatingClassNodeRepository;
import com.analyzer.core.graph.InMemoryProjectFileRepository;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.resource.ResourceResolver;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.behaviors.Caching;
import org.picocontainer.lifecycle.JavaEE5LifecycleStrategy;
import org.picocontainer.monitors.LifecycleComponentMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Configuration class for setting up hierarchical PicoContainer dependency injection.
 * <p>
 * Manages two container levels:
 * <ul>
 * <li><b>Application Container</b> (Parent): Long-lived singleton containing core services
 * like ResourceResolver, JARClassLoaderService, and InspectorRegistry</li>
 * <li><b>Analysis Container</b> (Child): Per-analysis container with fresh instances of
 * repositories, AnalysisEngine, and all inspectors. Inherits from parent.</li>
 * </ul>
 * <p>
 * </p>
 * <p>
 * Benefits:
 * <ul>
 * <li>Solves circular dependency between InspectorRegistry and AnalysisEngine</li>
 * <li>Ensures clean state for each analysis (fresh repositories/inspectors)</li>
 * <li>Reuses application-level services (ResourceResolver stays loaded)</li>
 * <li>Better memory management (inspector instances GC'd after analysis)</li>
 * <li>Explicit component registration via @AutoRegister annotation</li>
 * </ul>
 */
public class PicoContainerConfig {

    private static final Logger logger = LoggerFactory.getLogger(PicoContainerConfig.class);

    private final ResourceResolver resourceResolver;
    private final List<String> scanPackages;
    private final List<String> excludePackages;

    /**
     * Creates a new container configuration.
     *
     * @param resourceResolver the ResourceResolver instance to register
     * @param scanPackages     packages to scan for @AutoRegister annotated classes
     * @param excludePackages  packages to exclude from scanning
     */
    public PicoContainerConfig(ResourceResolver resourceResolver,
                               List<String> scanPackages, List<String> excludePackages) {
        this.resourceResolver = resourceResolver;
        this.scanPackages = scanPackages;
        this.excludePackages = excludePackages;
    }


    /**
     * Creates the application-level parent container with long-lived singleton services.
     * This container lives for the entire CLI session and contains:
     * - ResourceResolver
     * - JARClassLoaderService
     * - InspectorRegistry itself
     *
     * @param inspectorRegistry the InspectorRegistry to register in the container
     * @return configured application container
     */
    public PicoContainer createApplicationContainer(InspectorRegistry inspectorRegistry) {
        logger.info("Creating application-level PicoContainer...");

        // Create child container that inherits from parent
        JavaEE5LifecycleStrategy lifecycleStrategy = new JavaEE5LifecycleStrategy(new LifecycleComponentMonitor());
        MutablePicoContainer container = new DefaultPicoContainer(new Caching(), lifecycleStrategy, null);


        // Register long-lived application services
        container.addComponent(ResourceResolver.class, resourceResolver);
        container.addComponent(JARClassLoaderService.class);

        // Register InspectorRegistry itself so AnalysisEngine can depend on it
        container.addComponent(InspectorRegistry.class, inspectorRegistry);

        logger.info("Application container created with {} core services", container.getComponents().size());
        return container;
    }

    /**
     * Creates a per-analysis child container with fresh instances for each analysis.
     * This container inherits from the parent and adds:
     * - Fresh GraphRepository
     * - Fresh ProjectFileRepository
     * - Fresh ClassNodeRepository
     * - Fresh InspectorProgressTracker
     * - Fresh AnalysisEngine (with auto-injected dependencies)
     *
     * @param parent the application container to inherit from
     * @return configured analysis container ready for a single analysis run
     */
    public MutablePicoContainer createAnalysisContainer(PicoContainer parent) {
        logger.info("Creating per-analysis child PicoContainer...");

        // Create child container that inherits from parent
        JavaEE5LifecycleStrategy lifecycleStrategy = new JavaEE5LifecycleStrategy(new LifecycleComponentMonitor());
        MutablePicoContainer container = new DefaultPicoContainer(new Caching(), lifecycleStrategy, parent);

        // Register per-analysis repositories (fresh instances)
        container.addComponent(com.analyzer.core.graph.GraphRepository.class,
                com.analyzer.core.graph.InMemoryGraphRepository.class);
        container.addComponent(ClassNodeRepository.class, DelegatingClassNodeRepository.class);
        container.addComponent(ProjectFileRepository.class, InMemoryProjectFileRepository.class);

        // Register per-analysis services
        container.addComponent(com.analyzer.core.inspector.InspectorProgressTracker.class);

        // Register AnalysisEngine - PicoContainer will auto-inject all dependencies from parent + child
        container.addComponent(com.analyzer.core.engine.AnalysisEngine.class);

        logger.info("Analysis container created with {} components ({} inspectors)",
                container.getComponents().size(), container.getComponents(Inspector.class).size());
        return container;
    }


    /**
     * Gets statistics about the container configuration.
     */
    public String getConfigurationStats() {
        return String.format("PicoContainerConfig: scanning %s, excluding %s",
                scanPackages, excludePackages);
    }
}
