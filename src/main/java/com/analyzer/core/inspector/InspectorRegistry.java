package com.analyzer.core.inspector;

import com.analyzer.core.collector.ClassNodeCollector;
import com.analyzer.core.collector.Collector;
import com.analyzer.core.detector.FileDetector;
import com.analyzer.core.engine.AnalysisEngine;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.inspectors.core.AbstractJavaClassInspector;
import com.analyzer.inspectors.core.binary.AbstractBinaryClassInspector;
import com.analyzer.inspectors.core.source.AbstractSourceFileInspector;
import com.analyzer.resource.ResourceResolver;
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
     * Gets all source file inspectors.
     */
    public List<Inspector> getSourceInspectors() {
        List<Inspector> sourceInspectors = new ArrayList<>();
        for (Inspector inspector : getAllInspectors()) {
            if (inspector instanceof AbstractSourceFileInspector) {
                sourceInspectors.add(inspector);
            }
        }
        return sourceInspectors;
    }

    /**
     * Gets all binary class inspectors.
     */
    public List<Inspector> getBinaryInspectors() {
        List<Inspector> binaryInspectors = new ArrayList<>();
        for (Inspector inspector : getAllInspectors()) {
            if (inspector instanceof AbstractBinaryClassInspector) {
                binaryInspectors.add(inspector);
            }
        }
        return binaryInspectors;
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
            if (inspector instanceof AbstractJavaClassInspector) {
                classNodeInspectors.add(inspector);
            }
        }
        return classNodeInspectors;
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
     * Gets the number of source file inspectors.
     */
    public int getSourceInspectorCount() {
        return getSourceInspectors().size();
    }

    /**
     * Gets the number of binary class inspectors.
     */
    public int getBinaryInspectorCount() {
        return getBinaryInspectors().size();
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
        return String.format("InspectorRegistry: %d inspectors (%d source, %d binary, %d classnode), " +
                "%d collectors (%d classnode) [Application Container: %d components]",
                getInspectorCount(), getSourceInspectorCount(), getBinaryInspectorCount(),
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
