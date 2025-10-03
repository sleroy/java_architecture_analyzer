package com.analyzer.core;

import com.analyzer.inspectors.core.binary.BinaryClassInspector;
import com.analyzer.inspectors.core.source.SourceFileInspector;
import com.analyzer.inspectors.rules.binary.TypeInspector;
import com.analyzer.inspectors.rules.binary.MethodCountInspector;
import com.analyzer.inspectors.rules.source.ClocInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Enumeration;

/**
 * Registry for managing and loading inspectors from default built-ins and
 * plugin JAR files.
 * Handles the loading of inspectors from the --plugins directory and provides
 * access to inspectors by name or type.
 */
public class InspectorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(InspectorRegistry.class);
    private final Map<String, Inspector> inspectors = new HashMap<>();
    private final File pluginsDirectory;
    private final ResourceResolver resourceResolver;

    public InspectorRegistry(File pluginsDirectory, ResourceResolver resourceResolver) {
        this.pluginsDirectory = pluginsDirectory;
        this.resourceResolver = resourceResolver;
        loadDefaultInspectors();
        loadPluginInspectors();
    }

    /**
     * Loads built-in default inspectors as specified in purpose.md:
     * - cloc: returns the number of lines of codes (using a source inspector)
     * - type: returns the type of declaration using a binary inspector (class,
     * interface, record, enum etc)
     * - method-count: returns the number of methods in a class (using binary inspector)
     */
    private void loadDefaultInspectors() {
        logger.info("Loading default inspectors...");

        // Load the default inspectors with ResourceResolver
        registerInspector(new ClocInspector(resourceResolver));
        registerInspector(new TypeInspector(resourceResolver));
        registerInspector(new MethodCountInspector(resourceResolver));
        registerInspector(new CyclomaticComplexityInspector(resourceResolver));

        logger.info("Default inspectors loaded: {}", inspectors.size());
    }

    /**
     * Loads inspector plugins from JAR files in the plugins directory.
     * Scans for classes that extend SourceFileInspector or BinaryClassInspector.
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
                            Inspector inspector = (Inspector) clazz.getDeclaredConstructor().newInstance();
                            registerInspector(inspector);
                            loaded++;
                            logger.info("Loaded plugin inspector: {}", inspector.getName());
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
            if (inspector instanceof SourceFileInspector) {
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
            if (inspector instanceof BinaryClassInspector) {
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
     * Gets registry statistics for debugging/logging.
     */
    public String getStatistics() {
        return String.format("InspectorRegistry: %d total inspectors (%d source, %d binary)",
                getInspectorCount(), getSourceInspectorCount(), getBinaryInspectorCount());
    }
}
