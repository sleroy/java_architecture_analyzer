package com.analyzer.core.collector;

import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLClassLoader;

/**
 * Abstract base class for collectors that create JavaClassNode objects using
 * Java ClassLoader.
 * <p>
 * This collector loads .class files using a URLClassLoader and uses Java
 * reflection
 * to extract class information. It's particularly useful when you need access
 * to:
 * <ul>
 * <li>Class metadata through reflection</li>
 * <li>Annotation information</li>
 * <li>Type parameters and generics</li>
 * <li>Runtime type information</li>
 * </ul>
 * <p>
 * Unlike {@link JavaClassNodeBinaryCollector} which uses ASM for low-level
 * bytecode access,
 * this collector uses the standard Java ClassLoader mechanism, making it
 * suitable for
 * higher-level class introspection.
 * <p>
 * <strong>Usage in Analysis Pipeline:</strong>
 * 
 * <pre>
 * Phase 2: ClassNode Collection
 *   └── ClassLoaderCollector creates JavaClassNode using reflection
 * </pre>
 *
 * @see ClassNodeCollector
 * @see JavaClassNodeBinaryCollector
 * @since 1.3.2 - Additional Collector Types
 */
public abstract class AbstractClassLoaderCollector implements ClassNodeCollector {

    private static final Logger logger = LoggerFactory.getLogger(AbstractClassLoaderCollector.class);

    protected final ResourceResolver resourceResolver;
    protected final JARClassLoaderService jarClassLoaderService;

    /**
     * Constructor with ResourceResolver and JARClassLoaderService.
     *
     * @param resourceResolver      resolver for accessing file content
     * @param jarClassLoaderService service for managing classloaders
     */
    protected AbstractClassLoaderCollector(ResourceResolver resourceResolver,
            JARClassLoaderService jarClassLoaderService) {
        this.resourceResolver = resourceResolver;
        this.jarClassLoaderService = jarClassLoaderService;
    }

    /**
     * Checks if this collector can process the given ProjectFile.
     * <p>
     * By default, returns true only for .class files.
     *
     * @param source the ProjectFile to evaluate
     * @return true if the file has .class extension
     */
    @Override
    public boolean canCollect(ProjectFile source) {
        return source != null && source.hasFileExtension("class");
    }

    /**
     * Collects JavaClassNode from a .class file using ClassLoader reflection.
     *
     * @param source  the .class file to process
     * @param context the collection context
     */
    @Override
    public void collect(ProjectFile source, CollectionContext context) {
        if (!canCollect(source)) {
            logger.debug("Collector {} cannot process file: {}",
                    getName(), source.getRelativePath());
            return;
        }

        try {
            // Extract FQN by loading class with ClassLoader
            String fqn = extractFQNUsingClassLoader(source);

            if (fqn == null || fqn.isEmpty()) {
                logger.warn("Could not extract FQN from {}", source.getRelativePath());
                return;
            }

            // Check if node already exists
            if (context.classNodeExists(fqn)) {
                logger.debug("JavaClassNode already exists for {}, skipping", fqn);
                return;
            }

            // Create JavaClassNode
            JavaClassNode classNode = createClassNode(fqn, source);

            // Store via context
            context.addClassNode(classNode);
            context.linkClassNodeToFile(classNode, source);

            logger.debug("Created JavaClassNode for {} from {} using ClassLoader",
                    fqn, source.getRelativePath());

        } catch (Exception e) {
            logger.error("Error collecting class node from {}: {}",
                    source.getRelativePath(), e.getMessage(), e);
        }
    }

    /**
     * Extracts the fully qualified name by loading the class with ClassLoader.
     * <p>
     * This method uses JARClassLoaderService's shared classloader
     * to load the class and get its canonical name.
     *
     * @param source the .class file
     * @return the fully qualified name, or null if extraction fails
     */
    protected String extractFQNUsingClassLoader(ProjectFile source) {
        try {

            URLClassLoader classLoader = jarClassLoaderService.getSharedClassLoader();

            // Convert file path to class name
            String relativePath = source.getRelativePath();
            if (relativePath.endsWith(".class")) {
                relativePath = relativePath.substring(0, relativePath.length() - 6);
            }
            String className = relativePath.replace('/', '.').replace('\\', '.');

            // Load the class
            Class<?> loadedClass = classLoader.loadClass(className);

            // Return canonical name (handles inner classes properly)
            return loadedClass.getCanonicalName() != null
                    ? loadedClass.getCanonicalName()
                    : loadedClass.getName();

        } catch (ClassNotFoundException e) {
            logger.error("Class not found for {}: {}", source.getRelativePath(), e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Failed to load class from {}: {}",
                    source.getRelativePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Creates a JavaClassNode with the given FQN and links it to the source file.
     * <p>
     * Subclasses can override this method to set additional properties on the node.
     *
     * @param fqn    the fully qualified name
     * @param source the source ProjectFile
     * @return the created JavaClassNode
     */
    protected JavaClassNode createClassNode(String fqn, ProjectFile source) {
        JavaClassNode classNode = new JavaClassNode(fqn);
        classNode.setSourceFilePath(source.getFilePath().toString());
        return classNode;
    }
}
