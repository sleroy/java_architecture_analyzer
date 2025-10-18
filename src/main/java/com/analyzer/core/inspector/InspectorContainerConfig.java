package com.analyzer.core.inspector;

import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.DelegatingClassNodeRepository;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.resource.ResourceResolver;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.behaviors.Caching;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Configuration class for setting up PicoContainer dependency injection for
 * inspectors.
 * This class manages the creation and configuration of the DI container used to
 * instantiate
 * inspector instances with proper dependency injection.
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic registration of core dependencies (ResourceResolver,
 * JARClassLoaderService)</li>
 * <li>Auto-discovery and registration of inspector classes from classpath</li>
 * <li>Support for @Inject annotation-based constructor injection</li>
 * <li>Singleton lifecycle management for core services</li>
 * <li>Plugin inspector loading from JAR files</li>
 * </ul>
 */
public class InspectorContainerConfig {

    private static final Logger logger = LoggerFactory.getLogger(InspectorContainerConfig.class);

    private final ResourceResolver resourceResolver;
    private final File pluginsDirectory;
    private final List<String> scanPackages;
    private final List<String> excludePackages;

    /**
     * Creates a new container configuration.
     *
     * @param resourceResolver the ResourceResolver instance to register in the
     *                         container
     * @param pluginsDirectory optional directory containing plugin JAR files
     */
    public InspectorContainerConfig(ResourceResolver resourceResolver, File pluginsDirectory, List<String> scanPackages, List<String> excludePackages) {
        this.resourceResolver = resourceResolver;
        this.pluginsDirectory = pluginsDirectory;
        this.scanPackages = scanPackages;
        this.excludePackages = excludePackages;
    }

    public InspectorContainerConfig(ResourceResolver resourceResolver, File pluginsDirectory) {
        this(resourceResolver, pluginsDirectory, Arrays.asList("com.analyzer.inspectors", "com.analyzer.rules", "com.rules"), Arrays.asList("com.analyzer.inspectors.test", "com.analyzer.rules.test"));
    }

    /**
     * Creates and configures a PicoContainer with all necessary dependencies and
     * inspectors.
     *
     * @return configured PicoContainer instance ready for inspector instantiation
     */
    public PicoContainer createContainer() {
        logger.info("Creating PicoContainer with inspector dependencies...");

        // Create container with caching behavior for singleton services
        MutablePicoContainer container = new DefaultPicoContainer(new Caching());

        // Register core dependencies
        registerCoreDependencies(container);

        // Register inspector classes from classpath
        registerClasspathInspectors(container);

        // Register plugin inspectors from JAR files
        registerPluginInspectors(container);

        logger.info("PicoContainer created with {} components", container.getComponents().size());
        return container;
    }

    /**
     * Registers core service dependencies in the container.
     * These are singleton services that inspectors depend on.
     */
    private void registerCoreDependencies(MutablePicoContainer container) {
        logger.debug("Registering core dependencies...");

        // Register ResourceResolver as singleton
        container.addComponent(ResourceResolver.class, resourceResolver);

        // Register JARClassLoaderService as singleton - will be instantiated when
        // needed
        container.addComponent(JARClassLoaderService.class);

        // Register GraphRepository as singleton - use InMemoryGraphRepository implementation
        container.addComponent(com.analyzer.core.graph.GraphRepository.class,
                com.analyzer.core.graph.InMemoryGraphRepository.class);

        container.addComponent(ClassNodeRepository.class, DelegatingClassNodeRepository.class);

        logger.debug("Core dependencies registered: ResourceResolver, JARClassLoaderService, GraphRepository");
    }

    /**
     * Discovers and registers inspector classes from the current classpath.
     * Only registers classes that extend the inspector base classes or implement
     * Inspector directly.
     */
    private void registerClasspathInspectors(MutablePicoContainer container) {
        logger.debug("Scanning classpath for inspector classes...");

        int registeredCount = 0;

        try {
            // Get the current classpath
            String classpath = System.getProperty("java.class.path");
            String[] classpathEntries = classpath.split(File.pathSeparator);

            for (String entry : classpathEntries) {
                File file = new File(entry);
                if (file.exists()) {
                    if (file.isDirectory()) {
                        registeredCount += scanDirectory(file, file, container);
                    } else if (entry.toLowerCase().endsWith(".jar")) {
                        registeredCount += scanJarFile(file, container);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Error during classpath scanning: {}", e.getMessage());
        }

        logger.info("Registered {} inspector classes from classpath", registeredCount);
    }

    /**
     * Scans a directory for inspector class files and registers them.
     */
    private int scanDirectory(File baseDir, File currentDir, MutablePicoContainer container) {
        int registered = 0;
        File[] files = currentDir.listFiles();
        if (files == null)
            return 0;

        for (File file : files) {
            if (file.isDirectory()) {
                registered += scanDirectory(baseDir, file, container);
            } else if (file.getName().endsWith(".class")) {
                String relativePath = baseDir.toURI().relativize(file.toURI()).getPath();
                String className = relativePath.replace('/', '.').replace(".class", "");

                if (shouldScanClass(className) && registerInspectorClass(className, container)) {
                    registered++;
                }
            }
        }

        return registered;
    }

    /**
     * Scans a JAR file for inspector classes and registers them.
     */
    private int scanJarFile(File jarFile, MutablePicoContainer container) {
        int registered = 0;

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    if (shouldScanClass(className) && registerInspectorClass(className, container)) {
                        registered++;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not scan JAR file {}: {}", jarFile.getName(), e.getMessage());
        }

        return registered;
    }

    /**
     * Registers inspector classes from plugin JAR files in the plugins directory.
     */
    private void registerPluginInspectors(MutablePicoContainer container) {
        if (pluginsDirectory == null || !pluginsDirectory.exists() || !pluginsDirectory.isDirectory()) {
            logger.debug("No plugins directory found, skipping plugin inspector registration");
            return;
        }

        logger.debug("Scanning plugins directory: {}", pluginsDirectory.getAbsolutePath());

        File[] jarFiles = pluginsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            logger.debug("No JAR files found in plugins directory");
            return;
        }

        int registeredCount = 0;
        for (File jarFile : jarFiles) {
            registeredCount += scanPluginJarFile(jarFile, container);
        }

        logger.info("Registered {} plugin inspector classes from {} JAR files",
                registeredCount, jarFiles.length);
    }

    /**
     * Scans a plugin JAR file and registers inspector classes with custom
     * classloader.
     */
    private int scanPluginJarFile(File jarFile, MutablePicoContainer container) {
        int registered = 0;

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    // For plugin JARs, we're more permissive about class names
                    if (registerPluginInspectorClass(className, jarFile, container)) {
                        registered++;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error scanning plugin JAR {}: {}", jarFile.getName(), e.getMessage());
        }

        return registered;
    }

    /**
     * Determines if a class should be scanned based on package filtering rules.
     */
    private boolean shouldScanClass(String className) {
        // Skip inner classes and test classes
        if (className.contains("$") || className.endsWith("Test")) {
            return false;
        }

        // Check exclude packages first
        for (String excludePackage : excludePackages) {
            if (className.startsWith(excludePackage + ".") || className.equals(excludePackage)) {
                return false;
            }
        }

        // Check include packages
        for (String scanPackage : scanPackages) {
            if (className.startsWith(scanPackage + ".") || className.equals(scanPackage)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Attempts to register an inspector class in the PicoContainer.
     * Returns true if the class was successfully registered.
     */
    private boolean registerInspectorClass(String className, MutablePicoContainer container) {
        try {
            Class<?> clazz = Class.forName(className);

            // Check if it's a valid inspector class
            if (isInspectorClass(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                // Register the class with PicoContainer
                container.addComponent(clazz);
                logger.debug("Registered inspector class: {}", className);
                return true;
            }

        } catch (ClassNotFoundException e) {
            logger.debug("Class not found: {}", className);
        } catch (NoClassDefFoundError e) {
            logger.debug("Dependencies not available for class: {}", className);
        } catch (Exception e) {
            logger.debug("Could not register inspector class {}: {}", className, e.getMessage());
        }

        return false;
    }

    /**
     * Attempts to register a plugin inspector class with custom classloader
     * handling.
     */
    private boolean registerPluginInspectorClass(String className, File jarFile, MutablePicoContainer container) {
        try {
            // For plugin classes, we might need special classloader handling
            Class<?> clazz = Class.forName(className);

            if (isInspectorClass(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                container.addComponent(clazz);
                logger.info("Registered plugin inspector: {} from {}", className, jarFile.getName());
                return true;
            }

        } catch (Exception e) {
            logger.debug("Could not register plugin class {}: {}", className, e.getMessage());
        }

        return false;
    }

    /**
     * Checks if a class is a valid inspector class.
     * Returns true if the class implements Inspector interface or extends base
     * inspector classes.
     */
    private boolean isInspectorClass(Class<?> clazz) {
        // Must implement Inspector interface
        if (!Inspector.class.isAssignableFrom(clazz)) {
            return false;
        }

        // Must be concrete class
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            return false;
        }

        // Must have a constructor suitable for DI (default or with injectable
        // parameters)
        return hasInjectableConstructor(clazz);
    }

    /**
     * Checks if a class has a constructor that can be used by PicoContainer.
     * PicoContainer can handle default constructors or constructors with registered
     * dependencies.
     */
    private boolean hasInjectableConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors();

        for (Constructor<?> constructor : constructors) {
            // Default constructor is always injectable
            if (constructor.getParameterCount() == 0) {
                return true;
            }

            // Check if all parameter types are registered or can be satisfied
            Class<?>[] paramTypes = constructor.getParameterTypes();
            boolean allSatisfiable = true;

            for (Class<?> paramType : paramTypes) {
                // Check if it's a known dependency type
                if (!isKnownDependencyType(paramType)) {
                    allSatisfiable = false;
                    logger.error("Constructor parameter type {} for class {} is not a known dependency",
                            paramType.getName(), clazz.getName());
                    break;
                }
            }

            return  (allSatisfiable);
        }
        return true;
    }

    /**
     * Checks if a parameter type is a known dependency that can be injected.
     */
    private boolean isKnownDependencyType(Class<?> type) {
        // Core dependency types that will be registered
        return type == ResourceResolver.class ||
                type == JARClassLoaderService.class ||
                type == com.analyzer.core.graph.GraphRepository.class ||
                type == ClassNodeRepository.class ||
                // Allow other inspector types as dependencies
                Inspector.class.isAssignableFrom(type);
    }

    /**
     * Gets statistics about the container configuration.
     */
    public String getConfigurationStats() {
        return String.format("InspectorContainerConfig: scanning %s, excluding %s, plugins=%s",
                scanPackages, excludePackages,
                pluginsDirectory != null ? pluginsDirectory.getPath() : "none");
    }
}
