package com.analyzer.core.inspector;

import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.collector.Collector;
import com.analyzer.api.detector.FileDetector;
import com.analyzer.api.inspector.BeanFactory;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.engine.AnalysisEngine;
import com.analyzer.core.resource.JARClassLoaderService;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Registry for managing and loading inspectors and collectors using
 * PicoContainer dependency injection.
 * Handles the loading of inspectors and collectors from the classpath with
 * automatic dependency injection support.
 *
 * <p>
 * Architecture:
 * </p>
 * <ul>
 * <li><b>Collectors</b>: Create nodes (e.g., ProjectFile → JavaClassNode)</li>
 * <li><b>Inspectors</b>: Analyze existing nodes (e.g., JavaClassNode →
 * tags/properties)</li>
 * </ul>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>PicoContainer-based dependency injection</li>
 * annotation</li>
 * <li>Classpath-based component scanning</li>
 * <li>Constructor injection support</li>
 * </ul>
 */
public class InspectorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(InspectorRegistry.class);

    private final ResourceResolver resourceResolver;
    private final PicoContainer applicationContainer; // Long-lived parent conta
    private final PicoContainerConfig containerConfig;
    private final MutablePicoContainer analysisContainer;

    /**
     * Creates a new InspectorRegistry with hierarchical PicoContainer architecture.
     * <p>
     * Creates an application-level parent container that lives for the entire
     * session,
     * containing long-lived services like ResourceResolver and this
     * InspectorRegistry itself.
     * </p>
     *
     * @param resourceResolver the ResourceResolver instance for resource access
     * @param scanPackages     packages to scan for @AutoRegister annotated classes
     * @param excludePackages  packages to exclude from scanning
     */
    private InspectorRegistry(ResourceResolver resourceResolver, List<String> scanPackages,
            List<String> excludePackages) {
        this.resourceResolver = resourceResolver;

        // Initialize application container configuration
        this.containerConfig = new PicoContainerConfig(resourceResolver, scanPackages, excludePackages);

        // Create application-level parent container with self-registration
        this.applicationContainer = containerConfig.createApplicationContainer(this);

        logger.info("InspectorRegistry initialized with hierarchical containers: {}",
                containerConfig.getConfigurationStats());

        logger.info("Creating fresh analysis container for new analysis run...");

        analysisContainer = containerConfig.createAnalysisContainer(applicationContainer);

        logger.info("Analysis container ready with {} inspectors, {} collectors",
                getAllInspectors().size(), getAllCollectors().size());

    }

    public static InspectorRegistry newInspectorRegistry(ResourceResolver _resourceResolver) {
        return new InspectorRegistry(_resourceResolver, Arrays.asList("com.analyzer, com.rules"),
                Arrays.asList("com.analyzer.inspectors.test", "com.analyzer.rules.test"));
    }

    /**
     * Gets an inspector by name.
     */
    public Inspector getInspector(String name) {
        return getAllInspectors().stream().filter(i -> i.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Gets all registered inspectors.
     */
    public List<Inspector> getAllInspectors() {
        return analysisContainer.getComponents(Inspector.class);
    }

    /**
     * Gets all JavaClassNode inspectors (NEW - for Phase 4 analysis).
     * These inspectors analyze JavaClassNode objects.
     *
     * @return list of inspectors that work on JavaClassNode
     */
    @SuppressWarnings("unchecked")
    public List<Inspector> getClassNodeInspectors() {
        List<Inspector> classNodeInspectors = new ArrayList<>();
        for (Inspector inspector : getAllInspectors()) {
            if (inspector.getTargetType() == InspectorTargetType.JAVA_CLASS_NODE) {
                classNodeInspectors.add(inspector);
            }
        }
        return classNodeInspectors;
    }

    /**
     * Gets all global ClassNode inspectors that require all nodes to be processed
     * before execution.
     * These inspectors run in Phase 5 after the multi-pass Phase 4 completes.
     *
     * @return list of global inspectors for JavaClassNode
     */
    @SuppressWarnings("unchecked")
    public List<Inspector> getGlobalClassNodeInspectors() {
        List<Inspector> globalInspectors = new ArrayList<>();
        for (Inspector inspector : getAllInspectors()) {
            if (inspector.getTargetType() == InspectorTargetType.JAVA_CLASS_NODE
                    && isGlobalInspector(inspector)) {
                globalInspectors.add(inspector);
            }
        }
        return globalInspectors;
    }

    /**
     * Gets all non-global ClassNode inspectors that run node-by-node.
     * These are the regular inspectors that run in Phase 4 multi-pass execution.
     *
     * @return list of non-global inspectors for JavaClassNode
     */
    @SuppressWarnings("unchecked")
    public List<Inspector> getNonGlobalClassNodeInspectors() {
        List<Inspector> nonGlobalInspectors = new ArrayList<>();
        for (Inspector inspector : getAllInspectors()) {
            if (inspector.getTargetType() == InspectorTargetType.JAVA_CLASS_NODE
                    && !isGlobalInspector(inspector)) {
                nonGlobalInspectors.add(inspector);
            }
        }
        return nonGlobalInspectors;
    }

    /**
     * Gets all global ProjectFile inspectors that require all nodes to be processed
     * before execution.
     * These inspectors run in Phase 3.5 after the multi-pass Phase 3 completes.
     *
     * @return list of global inspectors for ProjectFile
     */
    @SuppressWarnings("unchecked")
    public List<Inspector> getGlobalProjectFileInspectors() {
        List<Inspector> globalInspectors = new ArrayList<>();
        for (Inspector inspector : getAllInspectors()) {
            InspectorTargetType targetType = inspector.getTargetType();
            if ((targetType == InspectorTargetType.PROJECT_FILE || targetType == InspectorTargetType.ANY)
                    && isGlobalInspector(inspector)) {
                globalInspectors.add(inspector);
            }
        }
        return globalInspectors;
    }

    /**
     * Gets all non-global ProjectFile inspectors that run node-by-node.
     * These are the regular inspectors that run in Phase 3 multi-pass execution.
     *
     * @return list of non-global inspectors for ProjectFile
     */
    @SuppressWarnings("unchecked")
    public List<Inspector> getNonGlobalProjectFileInspectors() {
        List<Inspector> nonGlobalInspectors = new ArrayList<>();
        for (Inspector inspector : getAllInspectors()) {
            InspectorTargetType targetType = inspector.getTargetType();
            if ((targetType == InspectorTargetType.PROJECT_FILE || targetType == InspectorTargetType.ANY)
                    && !isGlobalInspector(inspector)) {
                nonGlobalInspectors.add(inspector);
            }
        }
        return nonGlobalInspectors;
    }

    /**
     * Checks if an inspector is a global inspector that requires all nodes
     * to be processed before execution.
     *
     * @param inspector the inspector to check
     * @return true if the inspector has requiresAllNodesProcessed=true
     */
    private boolean isGlobalInspector(Inspector inspector) {
        com.analyzer.api.inspector.InspectorDependencies annotation = inspector.getClass()
                .getAnnotation(com.analyzer.api.inspector.InspectorDependencies.class);
        return annotation != null && annotation.requiresAllNodesProcessed();
    }

    // ==================== Collector Retrieval (NEW) ====================

    /**
     * Gets a collector by name.
     *
     * @param name the collector name
     * @return the collector, or null if not found
     */
    public Collector<?, ?> getCollector(String name) {
        return getAllCollectors().stream().filter(i -> i.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Gets all registered collectors.
     *
     * @return list of all collectors
     */
    public List<Collector> getAllCollectors() {
        return analysisContainer.getComponents(Collector.class);
    }

    /**
     * Gets all ClassNodeCollectors (for Phase 2 collection).
     * These collectors create JavaClassNode objects from ProjectFiles.
     *
     * @return list of ClassNodeCollector instances
     */
    @SuppressWarnings("unchecked")
    public List<ClassNodeCollector> getClassNodeCollectors() {
        List<ClassNodeCollector> classNodeCollectors = new ArrayList<>();
        for (Collector<?, ?> collector : getAllCollectors()) {
            if (collector instanceof ClassNodeCollector) {
                classNodeCollectors.add((ClassNodeCollector) collector);
            }
        }
        return classNodeCollectors;
    }

    /**
     * Gets the total number of registered collectors.
     *
     * @return collector count
     */
    public int getCollectorCount() {
        return getAllCollectors().size();
    }

    /**
     * Gets the number of ClassNodeCollectors.
     *
     * @return ClassNodeCollector count
     */
    public int getClassNodeCollectorCount() {
        return getClassNodeCollectors().size();
    }

    /**
     * Gets all collector names.
     *
     * @return list of collector names
     */
    public List<String> getCollectorNames() {
        return getAllCollectors().stream().map(Collector::getName).toList();
    }

    /**
     * Checks if a collector with the given name is registered.
     *
     * @param name the collector name
     * @return true if registered, false otherwise
     */
    public boolean hasCollector(String name) {
        return getAllCollectors().stream().anyMatch(i -> i.getName().equals(name));
    }

    /**
     * Gets the total number of registered inspectors.
     */
    public int getInspectorCount() {
        return getAllInspectors().size();
    }

    /**
     * Gets all inspector names.
     */
    public List<String> getInspectorNames() {
        return getAllInspectors().stream().map(Inspector::getName).toList();
    }

    /**
     * Checks if an inspector with the given name is registered.
     */
    public boolean hasInspector(String name) {
        return getAllInspectors().stream().anyMatch(i -> i.getName().equals(name));
    }

    /**
     * Gets the application-level parent container (explicit name).
     *
     * @return the application PicoContainer instance
     */
    public PicoContainer getApplicationContainer() {
        return applicationContainer;
    }

    /**
     * Gets registry statistics for debugging/logging.
     */
    public String getStatistics() {
        return String.format("InspectorRegistry: %d inspectors ( %d classnode), " +
                "%d collectors (%d classnode) [Application Container: %d components]",
                getInspectorCount(),
                getClassNodeInspectors().size(),
                getCollectorCount(), getClassNodeCollectorCount(),
                getCollectorCount(), getClassNodeCollectorCount(),
                applicationContainer.getComponents().size());
    }

    public void registerComponents(Class<? extends BeanFactory> collectorBeanFactoryClass) {
        try {
            collectorBeanFactoryClass.getConstructor().newInstance().registerBeans(analysisContainer);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

    }

    public AnalysisEngine getAnalysisEngine() {
        return analysisContainer.getComponent(AnalysisEngine.class);
    }

    public List<FileDetector> getFileDetectors() {
        return analysisContainer.getComponents(FileDetector.class);
    }

    public JARClassLoaderService getJarClassLoaderService() {
        return analysisContainer.getComponent(JARClassLoaderService.class);
    }
}
