package com.analyzer.inspectors.core;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorTags;
import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorResult;
import com.analyzer.core.JARClassLoaderService;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLClassLoader;

/**
 * Abstract base class for inspectors that need to load classes at runtime
 * using a shared ClassLoader containing all discovered JAR files.
 * 
 * This inspector provides a template method pattern where:
 * 1. The base class attempts to load the class using the shared ClassLoader
 * 2. If successful, it delegates to the concrete implementation's
 * analyzeLoadedClass() method
 * 3. If loading fails, it returns notApplicable without calling the analysis
 * method
 * 
 * Concrete inspectors extending this class can use reflection on the loaded
 * Class<?> object to perform runtime analysis that would not be possible
 * with static bytecode analysis alone.
 */
public abstract class ClassLoaderBasedInspector implements Inspector<ProjectFile> {

    private static final Logger logger = LoggerFactory.getLogger(ClassLoaderBasedInspector.class);

    protected final ResourceResolver resourceResolver;
    protected final JARClassLoaderService classLoaderService;

    /**
     * Creates a new ClassLoaderBasedInspector with the required dependencies.
     * 
     * @param resourceResolver   the resolver for accessing resources
     * @param classLoaderService the service providing the shared ClassLoader
     */
    protected ClassLoaderBasedInspector(ResourceResolver resourceResolver,
            JARClassLoaderService classLoaderService) {
        this.resourceResolver = resourceResolver;
        this.classLoaderService = classLoaderService;

        // Initialize the shared ClassLoader on first use
        if (!classLoaderService.isInitialized()) {
            classLoaderService.initializeFromResourceResolver(resourceResolver);
        }
    }

    @Override
    public final InspectorResult decorate(ProjectFile projectFile) {
        if (!supports(projectFile)) {
            return InspectorResult.notApplicable(getColumnName());
        }

        try {
            // Get the shared ClassLoader
            URLClassLoader sharedClassLoader = classLoaderService.getSharedClassLoader();

            // Attempt to load the class by its fully qualified name
            String fullyQualifiedName = projectFile.getFullyQualifiedName();
            logger.debug("Attempting to load class: {}", fullyQualifiedName);

            Class<?> loadedClass = sharedClassLoader.loadClass(fullyQualifiedName);

            // SUCCESS: Class loaded successfully, delegate to concrete implementation
            logger.debug("Successfully loaded class: {}", fullyQualifiedName);
            return analyzeLoadedClass(loadedClass, projectFile);

        } catch (ClassNotFoundException e) {
            // Class not found - this is expected for many classes
            logger.debug("Class not found in ClassLoader: {} ({})",
                    projectFile.getFullyQualifiedName(), e.getMessage());
            return InspectorResult.notApplicable(getColumnName(), e);

        } catch (NoClassDefFoundError e) {
            // Missing dependencies - also expected
            logger.debug("Missing dependencies for class: {} ({})",
                    projectFile.getFullyQualifiedName(), e.getMessage());
            return InspectorResult.notApplicable(getColumnName(), e);

        } catch (LinkageError e) {
            // Linkage issues - treat as not applicable
            logger.debug("Linkage error loading class: {} ({})",
                    projectFile.getFullyQualifiedName(), e.getMessage());
            return InspectorResult.notApplicable(getColumnName(), e);

        } catch (SecurityException e) {
            // Security restrictions - treat as not applicable
            logger.debug("Security restriction loading class: {} ({})",
                    projectFile.getFullyQualifiedName(), e.getMessage());
            return InspectorResult.notApplicable(getColumnName(), e);

        } catch (Exception e) {
            // Unexpected errors - return as error
            logger.warn("Unexpected error loading class: {} ({})",
                    projectFile.getFullyQualifiedName(), e.getMessage());
            return InspectorResult.error(getColumnName(),
                    "Unexpected error loading class: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // By default, support any project file that represents a Java class
        return projectFile != null && (projectFile.getBooleanTag(InspectorTags.RESOURCE_HAS_JAVA_SOURCE, false) ||
                projectFile.getBooleanTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY, false) ||
                projectFile.hasFileExtension("class"));
    }

    /**
     * Template method implemented by concrete rule inspectors.
     * This method is only called when the class has been successfully loaded
     * by the shared ClassLoader.
     * 
     * Implementers can use reflection on the loadedClass to perform runtime
     * analysis such as:
     * - Reading annotation values and metadata
     * - Analyzing actual inheritance hierarchies
     * - Inspecting method signatures and generic types
     * - Checking interface implementations
     * - Accessing field types and modifiers
     * 
     * @param loadedClass the successfully loaded Class<?> object
     * @param projectFile the ProjectFile metadata object containing file locations
     * @return the result of the analysis
     */
    protected abstract InspectorResult analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile);

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
     * Gets the resource resolver used by this inspector.
     * Subclasses can access this for additional resource operations.
     * 
     * @return the ResourceResolver instance
     */
    protected ResourceResolver getResourceResolver() {
        return resourceResolver;
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
        if (!classLoaderService.isInitialized()) {
            return "ClassLoader not initialized";
        }

        int jarCount = classLoaderService.getJarCount();
        return String.format("ClassLoader initialized with %d JAR(s)", jarCount);
    }
}
