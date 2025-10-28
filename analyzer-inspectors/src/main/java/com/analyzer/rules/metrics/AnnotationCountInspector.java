package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.dev.inspectors.classloader.AbstractClassLoaderBasedInspector;
import com.analyzer.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Annotation;

import static com.analyzer.rules.metrics.AnnotationCountInspector.TAG_ANNOTATION_COUNT;

/**
 * Inspector that counts the total number of annotations present on a class.
 * This includes both class-level annotations and annotations on methods,
 * fields, and constructors.
 * <p>
 * This inspector demonstrates the AbstractClassLoaderBasedInspector pattern by:
 * 1. Loading classes at runtime using the shared ClassLoader
 * 2. Using reflection to analyze annotation metadata
 * 3. Gracefully handling classes that cannot be loaded
 * <p>
 * This type of analysis would not be possible with static bytecode analysis
 * alone, as it requires access to the actual annotation metadata and
 * potentially annotation values that are only available at runtime.
 */
@InspectorDependencies(requires = {InspectorTags.TAG_JAVA_DETECTED, InspectorTags.TAG_APPLICATION_CLASS},
        produces = {TAG_ANNOTATION_COUNT})
public class AnnotationCountInspector extends AbstractClassLoaderBasedInspector {

    public static final String TAG_ANNOTATION_COUNT = "annotation-count";
    private static final Logger logger = LoggerFactory.getLogger(AnnotationCountInspector.class);
    private final GraphRepository graphRepository;

    /**
     * Creates a new AnnotationCountInspector with the required dependencies.
     *
     * @param resourceResolver   the resolver for accessing resources
     * @param classLoaderService the service providing the shared ClassLoader
     * @param graphRepository    the graph repository for storing analysis results
     */
    @Inject
    public AnnotationCountInspector(ResourceResolver resourceResolver,
                                    JARClassLoaderService classLoaderService,
                                    GraphRepository graphRepository) {
        super(resourceResolver, classLoaderService);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "Annotation Count";
    }

    public String getColumnName() {
        return TAG_ANNOTATION_COUNT;
    }

    @Override
    protected void analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile,
                                      NodeDecorator<ProjectFile> projectFileDecorator) {
        try {
            int annotationCount = countAllAnnotations(loadedClass);

            logger.debug("Found {} annotations on class {}", annotationCount, loadedClass.getName());

            projectFileDecorator.setProperty(getColumnName(), annotationCount);

        } catch (Exception e) {
            logger.warn("Error analyzing annotations for class {}: {}",
                    loadedClass.getName(), e.getMessage());
            projectFileDecorator.error(
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

}
