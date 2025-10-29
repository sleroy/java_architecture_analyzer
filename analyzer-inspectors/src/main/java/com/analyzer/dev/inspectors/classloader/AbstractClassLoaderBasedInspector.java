package com.analyzer.dev.inspectors.classloader;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.inspector.InspectorTargetType;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.resource.JARClassLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLClassLoader;

/**
 * Abstract base class for inspectors that need to load classes at runtime
 * using a shared ClassLoader containing all discovered JAR files.
 * <p>
 * This inspector provides a template method pattern where:
 * 1. The base class attempts to load the class using the shared ClassLoader
 * 2. If successful, it delegates to the concrete implementation's
 * analyzeLoadedClass() method
 * 3. If loading fails, it logs the error without calling the analysis method
 * <p>
 * Concrete inspectors extending this class can use reflection on the loaded
 * Class<?> object to perform runtime analysis that would not be possible
 * with static bytecode analysis alone.
 * <p>
 * This inspector operates in Phase 4 (ClassNode Analysis) on JavaClassNode
 * objects.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED }, produces = {
        InspectorTags.TAG_JAVA_CLASSLOADER })
public abstract class AbstractClassLoaderBasedInspector implements Inspector<JavaClassNode> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractClassLoaderBasedInspector.class);

    protected final JARClassLoaderService classLoaderService;

    /**
     * Creates a new AbstractClassLoaderBasedInspector with the required
     * dependencies.
     *
     * @param classLoaderService the service providing the shared ClassLoader
     */
    protected AbstractClassLoaderBasedInspector(JARClassLoaderService classLoaderService) {
        this.classLoaderService = classLoaderService;
    }

    @Override
    public final void inspect(JavaClassNode classNode, NodeDecorator<JavaClassNode> nodeDecorator) {

        try {
            // Get the shared ClassLoader
            URLClassLoader sharedClassLoader = classLoaderService.getSharedClassLoader();

            // Attempt to load the class by its fully qualified name
            String fullyQualifiedName = classNode.getFullyQualifiedName();
            if (fullyQualifiedName == null || fullyQualifiedName.isEmpty()) {
                logger.error("No fully qualified class name found for class node: {}", classNode.getId());
                nodeDecorator.error("No fully qualified class name available");
                return;
            }
            logger.debug("Attempting to load class: {}", fullyQualifiedName);

            Class<?> loadedClass = sharedClassLoader.loadClass(fullyQualifiedName);

            // SUCCESS: Class loaded successfully, delegate to concrete implementation
            logger.debug("Successfully loaded class: {}", fullyQualifiedName);
            nodeDecorator.setProperty(InspectorTags.TAG_JAVA_CLASSLOADER, true);
            analyzeLoadedClass(loadedClass, classNode, nodeDecorator);

        } catch (ClassNotFoundException e) {
            // Class not found - this is expected for many classes
            logger.warn("Class not found in ClassLoader: {} ({})",
                    classNode.getFullyQualifiedName(), e.getMessage());
            nodeDecorator.error(e);

        } catch (NoClassDefFoundError e) {
            // Missing dependencies - also expected
            logger.warn("Missing dependencies for class: {} ({})",
                    classNode.getFullyQualifiedName(), e.getMessage());
            nodeDecorator.error(e);

        } catch (LinkageError e) {
            // Linkage issues - treat as not applicable
            logger.warn("Linkage error loading class: {} ({})",
                    classNode.getFullyQualifiedName(), e.getMessage());
            nodeDecorator.error(e);

        } catch (SecurityException e) {
            // Security restrictions - treat as not applicable
            logger.warn("Security restriction loading class: {} ({})",
                    classNode.getFullyQualifiedName(), e.getMessage());
            nodeDecorator.error(e);

        } catch (Exception e) {
            // Unexpected errors - return as error
            logger.warn("Unexpected error loading class: {} ({})",
                    classNode.getFullyQualifiedName(), e.getMessage());
            nodeDecorator.error(
                    "Unexpected error loading class: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(JavaClassNode classNode) {
        // By default, support any class node
        return classNode != null;
    }

    @Override
    public InspectorTargetType getTargetType() {
        return InspectorTargetType.JAVA_CLASS_NODE;
    }

    /**
     * Template method implemented by concrete rule inspectors.
     * This method is only called when the class has been successfully loaded
     * by the shared ClassLoader.
     * <p>
     * Implementers can use reflection on the loadedClass to perform runtime
     * analysis such as:
     * - Reading annotation values and metadata
     * - Analyzing actual inheritance hierarchies
     * - Inspecting method signatures and generic types
     * - Checking interface implementations
     * - Accessing field types and modifiers
     *
     * @param loadedClass the successfully loaded Class<?> object
     * @param classNode   the JavaClassNode metadata object
     * @param decorator   the decorator for setting properties and tags on the class
     *                    node
     */
    protected abstract void analyzeLoadedClass(Class<?> loadedClass, JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator);

    /**
     * Gets the shared ClassLoader service used by this inspector.
     * Subclasses can access this for advanced ClassLoader operations.
     *
     * @return the JARClassLoaderService instance
     */
    protected JARClassLoaderService getClassLoaderService() {
        return classLoaderService;
    }

    /**
     * Utility method to check if a class is loadable without actually loading it.
     * This can be useful for preliminary checks in subclasses.
     *
     * @param fullyQualifiedClassName the fully qualified class name to check
     * @return true if the class can be loaded, false otherwise
     */
    protected boolean isClassLoadable(String fullyQualifiedClassName) {
        try {
            URLClassLoader sharedClassLoader = classLoaderService.getSharedClassLoader();
            sharedClassLoader.loadClass(fullyQualifiedClassName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Utility method to safely load a class without throwing exceptions.
     * Returns null if the class cannot be loaded for any reason.
     *
     * @param fullyQualifiedClassName the fully qualified class name to load
     * @return the loaded Class<?> object, or null if loading failed
     */
    protected Class<?> safeLoadClass(String fullyQualifiedClassName) {
        try {
            URLClassLoader sharedClassLoader = classLoaderService.getSharedClassLoader();
            return sharedClassLoader.loadClass(fullyQualifiedClassName);
        } catch (Exception e) {
            logger.debug("Could not load class: {} ({})", fullyQualifiedClassName, e.getMessage());
            return null;
        }
    }

    /**
     * Gets information about the shared ClassLoader for debugging purposes.
     *
     * @return a string describing the ClassLoader status
     */
    protected String getClassLoaderInfo() {
        int jarCount = classLoaderService.getJarCount();
        return String.format("ClassLoader initialized with %d JAR(s)", jarCount);
    }
}
