package com.analyzer.inspectors.rules.binary;

import com.analyzer.core.InspectorResult;
import com.analyzer.core.JARClassLoaderService;
import com.analyzer.core.ProjectFile;
import com.analyzer.inspectors.core.ProjectFileClassLoaderInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;

/**
 * ProjectFile-based inspector that counts the total number of annotations
 * present on a class.
 * This includes both class-level annotations and annotations on methods,
 * fields, and constructors.
 * 
 * This is the ProjectFile equivalent of AnnotationCountInspector, demonstrating
 * how existing ProjectFile-based inspectors can be migrated to the new
 * architecture.
 * 
 * The inspector works by:
 * 1. Using ProjectFile tags to identify Java class files
 * 2. Loading classes at runtime using the shared ClassLoader
 * 3. Using reflection to analyze annotation metadata
 * 4. Gracefully handling classes that cannot be loaded
 * 
 * This type of analysis requires runtime class loading and would not be
 * possible
 * with static bytecode analysis alone, as it needs access to actual annotation
 * metadata and potentially annotation values.
 */
public class ProjectFileAnnotationCountInspector extends ProjectFileClassLoaderInspector {

    private static final Logger logger = LoggerFactory.getLogger(ProjectFileAnnotationCountInspector.class);

    /**
     * Creates a new ProjectFileAnnotationCountInspector with the required
     * dependencies.
     * 
     * @param resourceResolver   the resolver for accessing resources
     * @param classLoaderService the service providing the shared ClassLoader
     */
    public ProjectFileAnnotationCountInspector(ResourceResolver resourceResolver,
            JARClassLoaderService classLoaderService) {
        super(resourceResolver, classLoaderService);
    }

    @Override
    public String getName() {
        return "ProjectFile Annotation Count";
    }

    @Override
    public String getColumnName() {
        return "pf-annotation-count";
    }

    @Override
    protected InspectorResult analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile) {
        try {
            int annotationCount = countAllAnnotations(loadedClass);

            logger.debug("Found {} annotations on class {} (ProjectFile: {})",
                    annotationCount, loadedClass.getName(), projectFile.getRelativePath());

            return new InspectorResult(getColumnName(), annotationCount);

        } catch (Exception e) {
            logger.warn("Error analyzing annotations for class {} (ProjectFile: {}): {}",
                    loadedClass.getName(), projectFile.getRelativePath(), e.getMessage());
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
        // Support Java class files that have been detected and tagged appropriately
        return super.supports(projectFile) &&
                (hasTag(projectFile, "detect_java") || hasTag(projectFile, "detect_class"));
    }
}
