package com.analyzer.core.resource;

import com.analyzer.core.model.Project;
import com.analyzer.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Service responsible for creating and managing a shared ClassLoader
 * containing all discovered JAR files from the binary analysis paths.
 * <p>
 * This service scans the ResourceResolver for JAR files and WAR files,
 * extracts JARs from WARs, and creates a unified ClassLoader that can
 * be used by ClassLoader-based inspectors for runtime class loading.
 */
public class JARClassLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(JARClassLoaderService.class);

    private URLClassLoader parentClassLoader; // For common JARs (.m2, system libs)
    private URLClassLoader childClassLoader; // For application JARs

    @Inject
    public JARClassLoaderService(ResourceResolver resourceResolver) {
        // ResourceResolver parameter required for dependency injection but not
        // currently used
        // as JAR scanning is done via filesystem and classpath instead
    }

    /**
     * Gets the shared ClassLoader instance. Returns the child ClassLoader which
     * delegates to the parent ClassLoader for common dependencies.
     * The ClassLoader must be initialized before calling this method.
     *
     * @return the child URLClassLoader (application JARs)
     * @throws IllegalStateException if the ClassLoader hasn't been initialized
     */
    public URLClassLoader getSharedClassLoader() {
        if (childClassLoader == null) {
            throw new IllegalStateException("Shared ClassLoader has not been initialized. " +
                    "Call scanProjectJars() first.");
        }
        return childClassLoader;
    }

    /**
     * Gets the number of JAR URLs in the shared ClassLoader hierarchy.
     *
     * @return the total number of JAR URLs (parent + child)
     */
    public int getJarCount() {
        int count = 0;
        if (parentClassLoader != null) {
            count += parentClassLoader.getURLs().length;
        }
        if (childClassLoader != null) {
            count += childClassLoader.getURLs().length;
        }
        return count;
    }

    /**
     * Gets the parent ClassLoader containing common dependencies.
     *
     * @return the parent URLClassLoader, or null if not initialized
     */
    public URLClassLoader getParentClassLoader() {
        return parentClassLoader;
    }

    /**
     * Gets the child ClassLoader containing application JARs.
     *
     * @return the child URLClassLoader, or null if not initialized
     */
    public URLClassLoader getChildClassLoader() {
        return childClassLoader;
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
     * Recursively scans a directory for JAR files using Files.walk.
     * This method walks the directory tree and adds all valid JAR files to the
     * provided set.
     *
     * @param directory the directory to scan
     * @param jarUrls   the set to collect JAR URLs into
     */
    private void scanDirectoryForJars(Path directory, Set<URL> jarUrls) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isJarFile(path.toFile()))
                    .forEach(path -> {
                        try {
                            // Validate that it's a proper JAR file
                            new JarFile(path.toFile()).close();
                            jarUrls.add(path.toUri().toURL());
                            logger.debug("Added JAR: {}", path.toAbsolutePath());
                        } catch (Exception e) {
                            logger.debug("Skipping invalid JAR file: {} ({})", path.toAbsolutePath(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
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
     * Shuts down the ClassLoader hierarchy and releases resources.
     * This method should be called during application shutdown.
     */
    @PreDestroy
    public synchronized void shutdown() {
        // Close child first, then parent
        if (childClassLoader != null) {
            try {
                childClassLoader.close();
                logger.info("Child ClassLoader closed successfully");
            } catch (Exception e) {
                logger.warn("Error closing child ClassLoader", e);
            }
            childClassLoader = null;
        }

        if (parentClassLoader != null) {
            try {
                parentClassLoader.close();
                logger.info("Parent ClassLoader closed successfully");
            } catch (Exception e) {
                logger.warn("Error closing parent ClassLoader", e);
            }
            parentClassLoader = null;
        }
    }

    /**
     * Scans the given Project for JAR files and creates a parent-child ClassLoader
     * hierarchy.
     * Common JARs (from .m2, system classpath) are loaded in the parent
     * ClassLoader,
     * and application-specific JARs are loaded in the child ClassLoader.
     * This method can be called to refresh the ClassLoader if new JARs are added
     * to the project during runtime.
     *
     * @param project the Project to scan for JAR files
     */
    public void scanProjectJars  (Project project) {
        logger.info("Initializing ClassLoader hierarchy from project JARs...");
        try {
            // Collect all JAR URLs
            Set<URL> commonJars = new HashSet<>();
            Set<URL> applicationJars = new HashSet<>();

            // Scan for all JARs
            Set<URL> allJars = new HashSet<>();
            scanCommonJarLocations(allJars);
            scanDirectoryForJars(project.getProjectPath(), allJars);

            // Classify JARs into common and application categories
            classifyJars(allJars, commonJars, applicationJars);

            logger.info("Found {} common JARs (parent ClassLoader)", commonJars.size());
            logger.info("Found {} application JARs (child ClassLoader)", applicationJars.size());

            // Create parent ClassLoader for common JARs
            if (!commonJars.isEmpty()) {
                parentClassLoader = new URLClassLoader(
                        commonJars.toArray(new URL[0]),
                        Thread.currentThread().getContextClassLoader());

                logger.debug("Created parent URLClassLoader with {} JAR URLs", commonJars.size());
                for (URL url : commonJars) {
                    logger.debug("  [Parent] {}", url);
                }
            } else {
                logger.debug("No common JARs found - using thread context ClassLoader as parent");
                parentClassLoader = null;
            }

            // Create child ClassLoader for application JARs
            ClassLoader parent = parentClassLoader != null ? parentClassLoader
                    : Thread.currentThread().getContextClassLoader();

            if (!applicationJars.isEmpty()) {
                childClassLoader = new URLClassLoader(
                        applicationJars.toArray(new URL[0]),
                        parent);

                logger.debug("Created child URLClassLoader with {} JAR URLs", applicationJars.size());
                for (URL url : applicationJars) {
                    logger.debug("  [Child] {}", url);
                }
            } else {
                logger.warn("No application JARs found - creating empty child ClassLoader");
                childClassLoader = new URLClassLoader(new URL[0], parent);
            }

            logger.info("ClassLoader hierarchy initialized successfully");
            logger.info("Total JARs: {} (Parent: {}, Child: {})",
                    getJarCount(),
                    parentClassLoader != null ? parentClassLoader.getURLs().length : 0,
                    childClassLoader.getURLs().length);

        } catch (Exception e) {
            logger.error("Failed to initialize ClassLoader hierarchy", e);
            // Create fallback empty ClassLoaders
            parentClassLoader = new URLClassLoader(
                    new URL[0],
                    Thread.currentThread().getContextClassLoader());
            childClassLoader = new URLClassLoader(
                    new URL[0],
                    parentClassLoader);
        }
    }

    /**
     * Classifies JARs into common (library) JARs and application-specific JARs.
     * Common JARs include:
     * - JARs from Maven local repository (.m2)
     * - JARs from system classpath
     * - Standard library JARs
     * 
     * Application JARs include:
     * - JARs from project directories (target, lib, libs)
     * - Project-specific build artifacts
     *
     * @param allJars         all discovered JAR URLs
     * @param commonJars      set to populate with common JAR URLs
     * @param applicationJars set to populate with application JAR URLs
     */
    private void classifyJars(Set<URL> allJars, Set<URL> commonJars, Set<URL> applicationJars) {
        for (URL jarUrl : allJars) {
            String jarPath = jarUrl.getPath();

            // Check if JAR is from Maven local repository
            if (jarPath.contains(".m2/repository") || jarPath.contains(".m2\\repository")) {
                commonJars.add(jarUrl);
                logger.trace("Classified as common JAR (Maven): {}", jarPath);
            }
            // Check if JAR is from system classpath (not in project directory)
            else if (isSystemClasspathJar(jarUrl)) {
                commonJars.add(jarUrl);
                logger.trace("Classified as common JAR (System): {}", jarPath);
            }
            // Everything else is application-specific
            else {
                applicationJars.add(jarUrl);
                logger.trace("Classified as application JAR: {}", jarPath);
            }
        }
    }

    /**
     * Determines if a JAR is from the system classpath (not project-specific).
     * This includes JARs from standard locations like JRE/JDK libs.
     *
     * @param jarUrl the JAR URL to check
     * @return true if the JAR is from system classpath
     */
    private boolean isSystemClasspathJar(URL jarUrl) {
        String jarPath = jarUrl.getPath();

        // Check for common system library locations
        return jarPath.contains("/jre/lib/") ||
                jarPath.contains("/jdk/lib/") ||
                jarPath.contains("\\jre\\lib\\") ||
                jarPath.contains("\\jdk\\lib\\") ||
                jarPath.contains("java.home");
    }
}
