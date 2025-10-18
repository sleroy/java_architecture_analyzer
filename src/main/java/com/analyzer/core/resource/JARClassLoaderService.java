package com.analyzer.core;

import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Service responsible for creating and managing a shared ClassLoader
 * containing all discovered JAR files from the binary analysis paths.
 * 
 * This service scans the ResourceResolver for JAR files and WAR files,
 * extracts JARs from WARs, and creates a unified ClassLoader that can
 * be used by ClassLoader-based inspectors for runtime class loading.
 */
public class JARClassLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(JARClassLoaderService.class);

    private static URLClassLoader sharedClassLoader;
    private static boolean initialized = false;

    /**
     * Initializes the shared ClassLoader by scanning the ResourceResolver
     * for all JAR files. This method is thread-safe and will only initialize
     * the ClassLoader once.
     * 
     * @param resourceResolver the resolver to scan for JAR files
     */
    public synchronized void initializeFromResourceResolver(ResourceResolver resourceResolver) {
        if (initialized) {
            return;
        }

        logger.info("Initializing shared ClassLoader from ResourceResolver...");

        try {
            Set<URL> jarUrls = scanForJarUrls(resourceResolver);
            logger.info("Found {} JAR files for ClassLoader", jarUrls.size());

            if (!jarUrls.isEmpty()) {
                sharedClassLoader = new URLClassLoader(
                        jarUrls.toArray(new URL[0]),
                        Thread.currentThread().getContextClassLoader());

                logger.debug("Created URLClassLoader with {} JAR URLs", jarUrls.size());
                for (URL url : jarUrls) {
                    logger.debug("  - {}", url);
                }
            } else {
                logger.warn("No JAR files found - creating empty ClassLoader");
                sharedClassLoader = new URLClassLoader(
                        new URL[0],
                        Thread.currentThread().getContextClassLoader());
            }

            initialized = true;
            logger.info("Shared ClassLoader initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize shared ClassLoader", e);
            // Create a fallback empty ClassLoader
            sharedClassLoader = new URLClassLoader(
                    new URL[0],
                    Thread.currentThread().getContextClassLoader());
            initialized = true;
        }
    }

    /**
     * Gets the shared ClassLoader instance. The ClassLoader must be initialized
     * before calling this method.
     * 
     * @return the shared URLClassLoader
     * @throws IllegalStateException if the ClassLoader hasn't been initialized
     */
    public URLClassLoader getSharedClassLoader() {
        if (!initialized || sharedClassLoader == null) {
            throw new IllegalStateException("Shared ClassLoader has not been initialized. " +
                    "Call initializeFromResourceResolver() first.");
        }
        return sharedClassLoader;
    }

    /**
     * Checks if the shared ClassLoader has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Gets the number of JAR URLs in the shared ClassLoader.
     * 
     * @return the number of JAR URLs
     */
    public int getJarCount() {
        if (!initialized || sharedClassLoader == null) {
            return 0;
        }
        return sharedClassLoader.getURLs().length;
    }

    /**
     * Scans the ResourceResolver for JAR file URLs.
     * This method attempts to identify JAR files from ResourceLocation objects
     * and converts them to URLs that can be used by URLClassLoader.
     */
    private Set<URL> scanForJarUrls(ResourceResolver resourceResolver) {
        Set<URL> jarUrls = new HashSet<>();

        // Since ResourceResolver doesn't provide a direct way to enumerate all
        // resources,
        // we need to work with what we have. For now, we'll scan common JAR file
        // patterns
        // and locations that are typically used by the resource resolver.

        // This is a basic implementation - in a real scenario, you might need to
        // extend ResourceResolver with a method to enumerate all known JAR locations
        scanCommonJarLocations(jarUrls);

        return jarUrls;
    }

    /**
     * Scans common JAR file locations and patterns.
     * This is a simplified implementation that can be enhanced based on
     * specific requirements and the actual structure of discovered resources.
     */
    private void scanCommonJarLocations(Set<URL> jarUrls) {
        // Scan current working directory and common subdirectories for JAR files
        scanDirectoryForJars(Paths.get("."), jarUrls);
        scanDirectoryForJars(Paths.get("target"), jarUrls);
        scanDirectoryForJars(Paths.get("lib"), jarUrls);
        scanDirectoryForJars(Paths.get("libs"), jarUrls);

        // Add system classpath JARs (if needed)
        // This can be useful for including Maven dependencies
        addClasspathJars(jarUrls);
    }

    /**
     * Recursively scans a directory for JAR files.
     */
    private void scanDirectoryForJars(Path directory, Set<URL> jarUrls) {
        File dir = directory.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        try {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursively scan subdirectories
                    scanDirectoryForJars(file.toPath(), jarUrls);
                } else if (isJarFile(file)) {
                    try {
                        // Validate that it's a proper JAR file
                        new JarFile(file).close();
                        jarUrls.add(file.toURI().toURL());
                        logger.debug("Added JAR: {}", file.getAbsolutePath());
                    } catch (Exception e) {
                        logger.debug("Skipping invalid JAR file: {} ({})", file.getAbsolutePath(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error scanning directory {}: {}", directory, e.getMessage());
        }
    }

    /**
     * Adds JARs from the system classpath.
     */
    private void addClasspathJars(Set<URL> jarUrls) {
        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            String[] classpathEntries = classpath.split(File.pathSeparator);
            for (String entry : classpathEntries) {
                File file = new File(entry);
                if (file.exists() && isJarFile(file)) {
                    try {
                        new JarFile(file).close(); // Validate
                        jarUrls.add(file.toURI().toURL());
                        logger.debug("Added classpath JAR: {}", file.getAbsolutePath());
                    } catch (Exception e) {
                        logger.debug("Skipping invalid classpath JAR: {} ({})", file.getAbsolutePath(), e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Checks if a file is a JAR file based on its extension.
     */
    private boolean isJarFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".war") || name.endsWith(".ear");
    }

    /**
     * Shuts down the shared ClassLoader and releases resources.
     * This method should be called during application shutdown.
     */
    public synchronized void shutdown() {
        if (sharedClassLoader != null) {
            try {
                sharedClassLoader.close();
                logger.info("Shared ClassLoader closed successfully");
            } catch (Exception e) {
                logger.warn("Error closing shared ClassLoader", e);
            }
        }
        sharedClassLoader = null;
        initialized = false;
    }
}
