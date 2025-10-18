package com.analyzer.inspectors.rules.binary;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorTags;
import com.analyzer.core.InspectorResult;
import com.analyzer.core.JARClassLoaderService;
import com.analyzer.inspectors.core.ClassLoaderBasedInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;

/**
 * Inspector that counts the total number of annotations present on a class.
 * This includes both class-level annotations and annotations on methods,
 * fields, and constructors.
 * 
 * This inspector demonstrates the ClassLoaderBasedInspector pattern by:
 * 1. Loading classes at runtime using the shared ClassLoader
 * 2. Using reflection to analyze annotation metadata
 * 3. Gracefully handling classes that cannot be loaded
 * 
 * This type of analysis would not be possible with static bytecode analysis
 * alone, as it requires access to the actual annotation metadata and
 * potentially annotation values that are only available at runtime.
 */
public class AnnotationCountInspector extends ClassLoaderBasedInspector {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationCountInspector.class);

    /**
     * Creates a new AnnotationCountInspector with the required dependencies.
     * 
     * @param resourceResolver   the resolver for accessing resources
     * @param classLoaderService the service providing the shared ClassLoader
     */
    public AnnotationCountInspector(ResourceResolver resourceResolver,
            JARClassLoaderService classLoaderService) {
        super(resourceResolver, classLoaderService);
    }

    @Override
    public String getName() {
        return "Annotation Count";
    }

    @Override
    public String getColumnName() {
        return "annotation-count";
    }

    @Override
    protected InspectorResult analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile) {
        try {
            int annotationCount = countAllAnnotations(loadedClass);

            logger.debug("Found {} annotations on class {}", annotationCount, loadedClass.getName());

            return InspectorResult.success(getColumnName(), annotationCount);

        } catch (Exception e) {
            logger.warn("Error analyzing annotations for class {}: {}",
                    loadedClass.getName(), e.getMessage());
            return InspectorResult.error(getColumnName(),
                    "Error analyzing annotations: " + e.getMessage());
        }
    }

    /**
     * Counts all annotations on the class, including:
     * - Class-level annotations
     * - Method annotations (including inherited methods)
     * - Field annotations
     * - Constructor annotations
     * - Parameter annotations
     */
    private int countAllAnnotations(Class<?> clazz) {
        int count = 0;

        // Count class-level annotations
        Annotation[] classAnnotations = clazz.getAnnotations();
        count += classAnnotations.length;

        logger.debug("Class {} has {} class-level annotations", clazz.getName(), classAnnotations.length);

        // Count method annotations (including inherited methods)
        try {
            java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                Annotation[] methodAnnotations = method.getAnnotations();
                count += methodAnnotations.length;

                // Count parameter annotations
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                for (Annotation[] paramAnnotations : parameterAnnotations) {
                    count += paramAnnotations.length;
                }
            }
            logger.debug("Class {} has annotations on {} declared methods", clazz.getName(), methods.length);
        } catch (Exception e) {
            logger.debug("Could not analyze method annotations for {}: {}", clazz.getName(), e.getMessage());
        }

        // Count field annotations
        try {
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                Annotation[] fieldAnnotations = field.getAnnotations();
                count += fieldAnnotations.length;
            }
            logger.debug("Class {} has annotations on {} declared fields", clazz.getName(), fields.length);
        } catch (Exception e) {
            logger.debug("Could not analyze field annotations for {}: {}", clazz.getName(), e.getMessage());
        }

        // Count constructor annotations
        try {
            java.lang.reflect.Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                Annotation[] constructorAnnotations = constructor.getAnnotations();
                count += constructorAnnotations.length;

                // Count constructor parameter annotations
                Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
                for (Annotation[] paramAnnotations : parameterAnnotations) {
                    count += paramAnnotations.length;
                }
            }
            logger.debug("Class {} has annotations on {} declared constructors", clazz.getName(), constructors.length);
        } catch (Exception e) {
            logger.debug("Could not analyze constructor annotations for {}: {}", clazz.getName(), e.getMessage());
        }

        return count;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // Support all project files that represent Java classes - let the base class
        // handle class loading failures
        return projectFile != null && (projectFile.getBooleanTag(InspectorTags.RESOURCE_HAS_JAVA_SOURCE, false) ||
                projectFile.getBooleanTag(InspectorTags.RESOURCE_HAS_JAVA_BINARY, false) ||
                projectFile.hasFileExtension("class"));
    }
}
