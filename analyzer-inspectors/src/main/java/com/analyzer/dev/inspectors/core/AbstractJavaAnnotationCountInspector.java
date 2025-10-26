package com.analyzer.dev.inspectors.core;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.dev.inspectors.classloader.AbstractProjectFileClassLoaderInspector;
import com.analyzer.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Annotation;

/**
 * ProjectFile-based inspector that counts the total number of annotations
 * present on a class.
 * This includes both class-level annotations and annotations on methods,
 * fields, and constructors.
 * <p>
 * This is the ProjectFile equivalent of AnnotationCountInspectorAbstract,
 * demonstrating
 * how existing ProjectFile-based inspectors can be migrated to the new
 * architecture.
 * <p>
 * The inspector works by:
 * 1. Using ProjectFile tags to identify Java class files
 * 2. Loading classes at runtime using the shared ClassLoader
 * 3. Using reflection to analyze annotation metadata
 * 4. Gracefully handling classes that cannot be loaded
 * <p>
 * This type of analysis requires runtime class loading and would not be
 * possible
 * with static bytecode analysis alone, as it needs access to actual annotation
 * metadata and potentially annotation values.
 */
@InspectorDependencies(requires = {InspectorTags.TAG_JAVA_DETECTED}, produces = {})
public class AbstractJavaAnnotationCountInspector extends AbstractProjectFileClassLoaderInspector {

    private static final Logger logger = LoggerFactory.getLogger(AbstractJavaAnnotationCountInspector.class);

    private final GraphRepository graphRepository;

    /**
     * Creates a new AbstractJavaAnnotationCountInspector with the required
     * dependencies.
     *
     * @param resourceResolver   the resolver for accessing resources
     * @param classLoaderService the service providing the shared ClassLoader
     * @param graphRepository    the graph repository for storing analysis results
     */
    @Inject
    public AbstractJavaAnnotationCountInspector(ResourceResolver resourceResolver,
                                                JARClassLoaderService classLoaderService,
                                                GraphRepository graphRepository) {
        super(resourceResolver, classLoaderService);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "ProjectFile Annotation Count";
    }

    public String getColumnName() {
        return "pf-annotation-count";
    }

    @Override
    protected void analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile,
                                      NodeDecorator<ProjectFile> projectFileDecorator) {
        try {
            int annotationCount = countAllAnnotations(loadedClass);

            logger.debug("Found {} annotations on class {} (ProjectFile: {})",
                    annotationCount, loadedClass.getName(), projectFile.getRelativePath());

            projectFileDecorator.setProperty(getColumnName(), annotationCount);

        } catch (Exception e) {
            logger.warn("Error analyzing annotations for class {} (ProjectFile: {}): {}",
                    loadedClass.getName(), projectFile.getRelativePath(), e.getMessage());
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

    @Override
    public boolean supports(ProjectFile projectFile) {
        // Support Java class files that have been detected and tagged appropriately
        return super.supports(projectFile) &&
                (projectFile.hasTag(InspectorTags.TAG_JAVA_DETECTED) ||
                        projectFile.hasTag(InspectorTags.TAG_JAVA_IS_BINARY) ||
                        projectFile.hasTag(InspectorTags.TAG_JAVA_IS_SOURCE));
    }
}
