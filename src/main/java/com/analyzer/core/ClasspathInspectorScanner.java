package com.analyzer.core;

import com.analyzer.inspectors.core.binary.BinaryClassInspector;
import com.analyzer.inspectors.core.source.SourceFileInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the classpath for inspector classes and automatically instantiates
 * them.
 * Discovers classes that extend SourceFileInspector or BinaryClassInspector and
 * handles constructor dependency injection automatically.
 */
public class ClasspathInspectorScanner {

    private static final Logger logger = LoggerFactory.getLogger(ClasspathInspectorScanner.class);

    private final ResourceResolver resourceResolver;
    private final JARClassLoaderService jarClassLoaderService;

    public ClasspathInspectorScanner(ResourceResolver resourceResolver, JARClassLoaderService jarClassLoaderService) {
        this.resourceResolver = resourceResolver;
        this.jarClassLoaderService = jarClassLoaderService;
    }

    /**
     * Scans the classpath for inspector classes and returns instantiated
     * inspectors.
     * Focuses on classes in the com.analyzer.inspectors and com.rules packages.
     * 
     * @return list of discovered inspector instances
     */
    public List<Inspector> scanForInspectors() {
        List<Inspector> discoveredInspectors = new ArrayList<>();

        logger.info("Starting automatic classpath scan for inspector classes...");

        try {
            // Get the current classpath
            String classpath = System.getProperty("java.class.path");
            String[] classpathEntries = classpath.split(File.pathSeparator);

            for (String entry : classpathEntries) {
                File file = new File(entry);
                if (file.exists()) {
                    if (file.isDirectory()) {
                        scanDirectory(file, file, discoveredInspectors);
                    } else if (entry.toLowerCase().endsWith(".jar")) {
                        scanJarFile(file, discoveredInspectors);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error during classpath scanning: {}", e.getMessage(), e);
        }

        logger.info("Classpath scan completed. Found {} inspector classes", discoveredInspectors.size());
        return discoveredInspectors;
    }

    /**
     * Scans a directory for class files.
     */
    private void scanDirectory(File baseDir, File currentDir, List<Inspector> inspectors) {
        File[] files = currentDir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(baseDir, file, inspectors);
            } else if (file.getName().endsWith(".class")) {
                String relativePath = baseDir.toURI().relativize(file.toURI()).getPath();
                String className = relativePath.replace('/', '.').replace(".class", "");

                if (shouldScanClass(className)) {
                    tryCreateInspector(className, inspectors);
                }
            }
        }
    }

    /**
     * Scans a JAR file for class files.
     */
    private void scanJarFile(File jarFile, List<Inspector> inspectors) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    if (shouldScanClass(className)) {
                        tryCreateInspector(className, inspectors);
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Could not scan JAR file {}: {}", jarFile.getName(), e.getMessage());
        }
    }

    /**
     * Determines if a class should be scanned based on its package name.
     * Only scans classes in inspector-related packages to avoid performance issues.
     */
    private boolean shouldScanClass(String className) {
        return (className.startsWith("com.analyzer.inspectors.") ||
                className.startsWith("com.rules.")) &&
                !className.contains("$") && // Skip inner classes
                !className.endsWith("Test"); // Skip test classes
    }

    /**
     * Attempts to create an inspector instance from a class name.
     */
    private void tryCreateInspector(String className, List<Inspector> inspectors) {
        try {
            Class<?> clazz = Class.forName(className);

            // Check if it's an inspector class and not abstract
            if (isInspectorClass(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                Inspector inspector = createInspectorInstance(clazz);
                if (inspector != null) {
                    inspectors.add(inspector);
                    logger.debug("Auto-discovered inspector: {} ({})", inspector.getName(), className);
                }
            }
        } catch (ClassNotFoundException e) {
            logger.debug("Class not found during auto-discovery: {}", className);
        } catch (Exception e) {
            logger.debug("Could not instantiate inspector class {}: {}", className, e.getMessage());
        }
    }

    /**
     * Checks if a class is an inspector class (extends SourceFileInspector or
     * BinaryClassInspector).
     */
    private boolean isInspectorClass(Class<?> clazz) {
        return Inspector.class.isAssignableFrom(clazz) &&
                (SourceFileInspector.class.isAssignableFrom(clazz) ||
                        BinaryClassInspector.class.isAssignableFrom(clazz));
    }

    /**
     * Creates an inspector instance using constructor dependency injection.
     * Handles various constructor patterns automatically.
     */
    private Inspector createInspectorInstance(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors();

        // Try constructors in order of preference
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();

            try {
                if (paramTypes.length == 0) {
                    // Default constructor
                    return (Inspector) constructor.newInstance();

                } else if (paramTypes.length == 1 && paramTypes[0] == ResourceResolver.class) {
                    // ResourceResolver constructor
                    return (Inspector) constructor.newInstance(resourceResolver);

                } else if (paramTypes.length == 2 &&
                        paramTypes[0] == ResourceResolver.class &&
                        paramTypes[1] == JARClassLoaderService.class) {
                    // ResourceResolver + JARClassLoaderService constructor
                    return (Inspector) constructor.newInstance(resourceResolver, jarClassLoaderService);
                }

            } catch (Exception e) {
                logger.debug("Failed to create inspector using constructor {}: {}",
                        constructor, e.getMessage());
            }
        }

        logger.debug("No suitable constructor found for inspector class: {}", clazz.getName());
        return null;
    }
}
