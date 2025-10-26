package com.analyzer.core.resource;

import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the classpath for inspector classes and automatically instantiates
 * them.
 * Discovers classes that extend AbstractSourceFileInspector or AbstractBinaryClassInspector and
 * handles constructor dependency injection automatically.
 */
public class ClasspathInspectorScanner {

    private static final Logger logger = LoggerFactory.getLogger(ClasspathInspectorScanner.class);

    private final ResourceResolver resourceResolver;
    private final JARClassLoaderService jarClassLoaderService;
    private final List<String> scanPackages;
    private final List<String> excludePackages;
    private final boolean discoveryLogging;

    public ClasspathInspectorScanner(ResourceResolver resourceResolver, JARClassLoaderService jarClassLoaderService) {
        this.resourceResolver = resourceResolver;
        this.jarClassLoaderService = jarClassLoaderService;

        // Load configuration from application.properties
        Properties props = loadApplicationProperties();
        this.scanPackages = loadPackageList(props, "analyzer.inspector.scan.packages",
                "com.analyzer.inspectors,com.analyzer.rules,com.rules");
        this.excludePackages = loadPackageList(props, "analyzer.inspector.exclude.packages",
                "com.analyzer.inspectors.test,com.analyzer.rules.test");
        this.discoveryLogging = Boolean.parseBoolean(props.getProperty("analyzer.inspector.discovery.logging", "true"));

        if (discoveryLogging) {
            logger.info("Inspector scanner configured:");
            logger.info("  Scan packages: {}", scanPackages);
            logger.info("  Exclude packages: {}", excludePackages);
        }
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

            logger.info("Scanning {} classpath entries", classpathEntries.length);

            for (String entry : classpathEntries) {
                File file = new File(entry);
                if (file.exists()) {
                    if (file.isDirectory()) {
                        logger.info("Scanning directory: {}", entry);
                        scanDirectory(file, file, discoveredInspectors);
                    } else if (entry.toLowerCase().endsWith(".jar")) {
                        logger.debug("Scanning JAR file: {}", entry);
                        scanJarFile(file, discoveredInspectors);
                    }
                } else {
                    logger.debug("Classpath entry does not exist: {}", entry);
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
     * Loads application.properties from the classpath.
     */
    private Properties loadApplicationProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                logger.debug("Loaded application.properties for inspector configuration");
            } else {
                logger.warn("application.properties not found on classpath, using defaults");
            }
        } catch (Exception e) {
            logger.warn("Error loading application.properties: {}, using defaults", e.getMessage());
        }
        return props;
    }

    /**
     * Loads a comma-separated list of packages from properties.
     */
    private List<String> loadPackageList(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key, defaultValue);
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(value.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Determines if a class should be scanned based on its package name.
     * Uses configurable packages from application.properties.
     */
    private boolean shouldScanClass(String className) {
        // Skip inner classes and test classes
        if (className.contains("$") || className.endsWith("Test")) {
            if (discoveryLogging) {
                logger.debug("Skipping class (inner class or test): {}", className);
            }
            return false;
        }

        // Check if class is in an excluded package
        for (String excludePackage : excludePackages) {
            if (className.startsWith(excludePackage + ".") || className.equals(excludePackage)) {
                if (discoveryLogging) {
                    logger.debug("Excluding class from scan (excluded package): {}", className);
                }
                return false;
            }
        }

        // Check if class is in a scan package
        for (String scanPackage : scanPackages) {
            if (className.startsWith(scanPackage + ".") || className.equals(scanPackage)) {
                if (discoveryLogging) {
                    logger.debug("Including class in scan: {}", className);
                }
                return true;
            }
        }

        if (discoveryLogging) {
            logger.debug("Class not in scan packages: {} (scan packages: {})", className, scanPackages);
        }
        return false;
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
     * Checks if a class is an inspector class (implements Inspector interface
     * directly or extends AbstractSourceFileInspector or AbstractBinaryClassInspector).
     */
    private boolean isInspectorClass(Class<?> clazz) {
        boolean implementsInspector = Inspector.class.isAssignableFrom(clazz);
        boolean isDirectImplementation = isDirectInspectorImplementation(clazz);

        if (discoveryLogging) {
            logger.debug("Checking inspector class {}: implements={}, extendsSource={}, extendsBinary={}, isDirect={}",
                    clazz.getName(), implementsInspector, isDirectImplementation);
        }

        return implementsInspector && ( isDirectImplementation);
    }

    /**
     * Checks if a class directly implements Inspector interface without extending
     * the standard base classes. This allows for core inspectors like
     * SourceFileDetector that don't need the full framework of the base
     * classes.
     */
    private boolean isDirectInspectorImplementation(Class<?> clazz) {
        // Check if it directly implements Inspector but doesn't extend the base classes
        boolean implementsInspector = Inspector.class.isAssignableFrom(clazz);
        boolean hasPackage = clazz.getPackage() != null;
        boolean correctPackage = hasPackage &&
                (clazz.getPackage().getName().startsWith("com.analyzer.inspectors") ||
                        clazz.getPackage().getName().startsWith("com.analyzer.rules") ||
                        clazz.getPackage().getName().startsWith("com.rules"));

        boolean result = implementsInspector  && correctPackage;

        if (discoveryLogging && implementsInspector) {
            logger.debug(
                    "Direct implementation check for {}: impl={},  pkg={}, correctPkg={}, result={}",
                    clazz.getName(), implementsInspector,
                    hasPackage ? clazz.getPackage().getName() : "null", correctPackage, result);
        }

        return result;
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
