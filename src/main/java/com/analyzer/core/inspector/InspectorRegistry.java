package com.analyzer.core.inspector;

import com.analyzer.core.resource.ClasspathInspectorScanner;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.inspectors.core.binary.AbstractBinaryClassInspector;
import com.analyzer.inspectors.core.source.AbstractSourceFileInspector;
import com.analyzer.resource.ResourceResolver;
import org.picocontainer.PicoContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Registry for managing and loading inspectors using PicoContainer dependency injection.
 * Handles the loading of inspectors from the classpath and plugin JAR files with 
 * automatic dependency injection support.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>PicoContainer-based dependency injection</li>
 *   <li>Automatic inspector discovery and registration</li>
 *   <li>Plugin support from JAR files</li>
 *   <li>@Inject annotation support for constructor injection</li>
 * </ul>
 */
public class InspectorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(InspectorRegistry.class);
    
    private final Map<String, Inspector> inspectors = new HashMap<>();
    private final File pluginsDirectory;
    private final ResourceResolver resourceResolver;
    private final JARClassLoaderService jarClassLoaderService;
    private final ClasspathInspectorScanner classpathScanner;
    private final PicoContainer container;
    private final InspectorContainerConfig containerConfig;

    /**
     * Creates a new InspectorRegistry with PicoContainer-based dependency injection.
     * 
     * @param pluginsDirectory optional directory containing plugin JAR files
     * @param resourceResolver the ResourceResolver instance for resource access
     */
    public InspectorRegistry(File pluginsDirectory, ResourceResolver resourceResolver, List<String> scanPackages, List<String> excludePackages) {
        this.pluginsDirectory = pluginsDirectory;
        this.resourceResolver = resourceResolver;
        this.jarClassLoaderService = new JARClassLoaderService();
        this.classpathScanner = new ClasspathInspectorScanner(resourceResolver, jarClassLoaderService);
        
        // Initialize PicoContainer configuration
        this.containerConfig = new InspectorContainerConfig(resourceResolver, pluginsDirectory, scanPackages, excludePackages);
        this.container = containerConfig.createContainer();
        
        logger.info("InspectorRegistry initialized with PicoContainer: {}", containerConfig.getConfigurationStats());

        loadDefaultInspectors();
        loadContainerInspectors();
        loadPluginInspectors();
    }

    public InspectorRegistry(File pluginsDirectory, ResourceResolver resourceResolver) {
        this(pluginsDirectory, resourceResolver, Arrays.asList("com.analyzer.inspectors", "com.analyzer.rules", "com.rules"), Arrays.asList("com.analyzer.inspectors.test", "com.analyzer.rules.test"));
    }

    /**
     * Loads built-in default inspectors as specified in purpose.md:
     * - cloc: returns the number of lines of codes (using a source inspector)
     * - type: returns the type of declaration using a binary inspector (class,
     * interface, record, enum etc)
     * - method-count: returns the number of methods in a class (using binary
     * inspector)
     */
    private void loadDefaultInspectors() {
        logger.info("Loading default inspectors...");
        // Default inspectors can be manually registered here if needed
        logger.info("Default inspectors loaded: {}", inspectors.size());
    }

    /**
     * Loads inspectors from the PicoContainer.
     * This replaces the manual classpath scanning with container-managed instantiation.
     */
    private void loadContainerInspectors() {
        logger.info("Loading inspectors from PicoContainer...");
        
        int registeredCount = 0;
        
        // Get all inspector instances from the container
        Collection<Inspector> containerInspectors = container.getComponents(Inspector.class);
        
        for (Inspector inspector : containerInspectors) {
            String name = inspector.getName();
            
            // Only register if not already manually registered (manual takes precedence)
            if (!inspectors.containsKey(name)) {
                inspectors.put(name, inspector);
                registeredCount++;
                logger.debug("Registered container inspector: {} ({})", name, inspector.getClass().getSimpleName());
            } else {
                logger.debug("Skipping container inspector '{}' - already manually registered", name);
            }
        }
        
        logger.info("Container-based inspector loading completed: {} inspectors registered", registeredCount);
        logger.info("Total inspectors after container loading: {}", inspectors.size());
    }

    /**
     * Loads inspector plugins from JAR files in the plugins directory.
     * For now, this keeps the legacy manual instantiation for plugin JARs
     * until we extend PicoContainer to handle plugin classloaders.
     */
    private void loadPluginInspectors() {
        if (pluginsDirectory == null || !pluginsDirectory.exists() || !pluginsDirectory.isDirectory()) {
            logger.info("No plugins directory found, skipping plugin loading");
            return;
        }

        logger.info("Loading plugins from: {}", pluginsDirectory.getAbsolutePath());

        File[] jarFiles = pluginsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            logger.info("No JAR files found in plugins directory");
            return;
        }

        int loadedPlugins = 0;
        for (File jarFile : jarFiles) {
            try {
                loadedPlugins += loadInspectorsFromJar(jarFile);
            } catch (Exception e) {
                logger.error("Error loading plugins from {}: {}", jarFile.getName(), e.getMessage());
            }
        }

        logger.info("Loaded {} plugin inspectors from {} JAR files", loadedPlugins, jarFiles.length);
    }

    /**
     * Loads inspector classes from a single JAR file.
     * This uses manual instantiation for now, but could be enhanced
     * to use PicoContainer with custom classloaders in the future.
     */
    private int loadInspectorsFromJar(File jarFile) throws Exception {
        int loaded = 0;

        try (JarFile jar = new JarFile(jarFile)) {
            URL jarUrl = jarFile.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[] { jarUrl }, this.getClass().getClassLoader());

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    try {
                        Class<?> clazz = classLoader.loadClass(className);

                        // Check if it's an inspector class
                        if (Inspector.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                            // Try PicoContainer instantiation first, fall back to manual
                            Inspector inspector = createInspectorInstance(clazz);
                            if (inspector != null) {
                                registerInspector(inspector);
                                loaded++;
                                logger.info("Loaded plugin inspector: {}", inspector.getName());
                            }
                        }
                    } catch (Exception e) {
                        // Skip classes that can't be loaded or instantiated
                        logger.debug("Skipping class {}: {}", className, e.getMessage());
                    }
                }
            }
        }

        return loaded;
    }

    /**
     * Creates an inspector instance, preferring PicoContainer but falling back to manual instantiation.
     */
    private Inspector createInspectorInstance(Class<?> clazz) {
        try {
            // Try to get instance from container first
            return (Inspector) container.getComponent(clazz);
        } catch (Exception e) {
            logger.debug("Container instantiation failed for {}, trying manual: {}", clazz.getName(), e.getMessage());
            
            // Fall back to manual instantiation
            try {
                return (Inspector) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception manualException) {
                logger.debug("Manual instantiation also failed for {}: {}", clazz.getName(), manualException.getMessage());
                return null;
            }
        }
    }

    /**
     * Registers an inspector with the registry.
     */
    public void registerInspector(Inspector inspector) {
        String name = inspector.getName();
        if (inspectors.containsKey(name)) {
            logger.warn("Inspector '{}' already registered, replacing with new instance", name);
        }
        inspectors.put(name, inspector);
    }

    /**
     * Gets an inspector by name.
     */
    public Inspector getInspector(String name) {
        return inspectors.get(name);
    }

    /**
     * Gets all registered inspectors.
     */
    public List<Inspector> getAllInspectors() {
        return new ArrayList<>(inspectors.values());
    }

    /**
     * Gets all source file inspectors.
     */
    public List<Inspector> getSourceInspectors() {
        List<Inspector> sourceInspectors = new ArrayList<>();
        for (Inspector inspector : inspectors.values()) {
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
        for (Inspector inspector : inspectors.values()) {
            if (inspector instanceof AbstractBinaryClassInspector) {
                binaryInspectors.add(inspector);
            }
        }
        return binaryInspectors;
    }

    /**
     * Gets the total number of registered inspectors.
     */
    public int getInspectorCount() {
        return inspectors.size();
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
        return new ArrayList<>(inspectors.keySet());
    }

    /**
     * Checks if an inspector with the given name is registered.
     */
    public boolean hasInspector(String name) {
        return inspectors.containsKey(name);
    }

    /**
     * Gets the underlying PicoContainer instance.
     * This can be used for advanced container operations if needed.
     * 
     * @return the PicoContainer instance
     */
    public PicoContainer getContainer() {
        return container;
    }

    /**
     * Gets registry statistics for debugging/logging.
     */
    public String getStatistics() {
        return String.format("InspectorRegistry: %d total inspectors (%d source, %d binary) [PicoContainer: %d components]",
                getInspectorCount(), getSourceInspectorCount(), getBinaryInspectorCount(), 
                container.getComponents().size());
    }
}
