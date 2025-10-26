package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.dev.inspectors.classloader.AbstractClassLoaderBasedInspector;
import com.analyzer.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that detects ThreadLocal usage within Java classes using runtime
 * class loading.
 * 
 * <p>
 * This inspector analyzes classes to identify fields of type
 * {@link ThreadLocal} or its subclasses.
 * When ThreadLocal usage is detected, it marks the corresponding JavaClassNode
 * with appropriate tags
 * and stores detailed metrics about the ThreadLocal fields found.
 * </p>
 * 
 * <p>
 * ThreadLocal variables are commonly used for maintaining thread-specific state
 * in multi-threaded
 * applications. Detecting their usage is important for:
 * </p>
 * <ul>
 * <li>Understanding thread-safety patterns in the codebase</li>
 * <li>Identifying potential memory leak risks (ThreadLocal instances not
 * properly cleaned up)</li>
 * <li>Analyzing concurrency design patterns</li>
 * <li>Migration planning when moving to different concurrency models</li>
 * </ul>
 * 
 * <p>
 * <strong>Two-Level Metrics System:</strong>
 * </p>
 * <ul>
 * <li><strong>Class-Level Metrics</strong> (JavaClassNode): Detailed
 * ThreadLocal usage per class</li>
 * <li><strong>File-Level Aggregates</strong> (ProjectFile): SUM aggregation
 * strategy - total ThreadLocal
 * count across all classes in each file</li>
 * </ul>
 * 
 * <p>
 * Aggregated file-level properties:
 * </p>
 * <ul>
 * <li>threadlocal.count.sum - Total ThreadLocal fields across all classes</li>
 * <li>threadlocal.count.classes_analyzed - Number of classes analyzed</li>
 * </ul>
 * 
 * <p>
 * The inspector uses reflection to examine all declared fields in a class and
 * checks if they
 * are assignable from ThreadLocal. It also captures the generic type parameter
 * if present.
 * </p>
 * 
 * @author Java Architecture Analyzer
 * @since Phase 2.4 - ClassLoader-Based Metrics
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED }, produces = { "threadlocal.detected",
        "threadlocal.count" })
public class ThreadLocalUsageInspector extends AbstractClassLoaderBasedInspector {

    private static final Logger logger = LoggerFactory.getLogger(ThreadLocalUsageInspector.class);

    // Property keys for JavaClassNode metrics
    public static final String PROP_THREADLOCAL_DETECTED = "threadlocal.detected";
    public static final String PROP_THREADLOCAL_COUNT = "threadlocal.count";
    public static final String PROP_THREADLOCAL_FIELDS = "threadlocal.fields";
    public static final String TAG_USES_THREADLOCAL = "uses.threadlocal";

    private final GraphRepository graphRepository;

    /**
     * Creates a new ThreadLocalUsageInspector with required dependencies.
     * 
     * @param resourceResolver   the resolver for accessing resources
     * @param classLoaderService the service providing the shared ClassLoader
     * @param graphRepository    the graph repository for storing analysis results
     */
    @Inject
    public ThreadLocalUsageInspector(
            ResourceResolver resourceResolver,
            JARClassLoaderService classLoaderService,
            GraphRepository graphRepository) {
        super(resourceResolver, classLoaderService);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "ThreadLocalUsageInspector";
    }

    @Override
    protected void analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile,
            NodeDecorator<ProjectFile> projectFileDecorator) {
        try {
            logger.debug("Analyzing ThreadLocal usage for class: {}", loadedClass.getName());

            // Scan for ThreadLocal fields
            ThreadLocalUsageMetrics metrics = analyzeThreadLocalUsage(loadedClass);

            if (metrics.hasThreadLocalFields()) {
                // Mark the class as using ThreadLocal
                attachThreadLocalMetricsToGraphNode(projectFile, metrics, projectFileDecorator);

                logger.info("ThreadLocal usage detected in class: {} - {} field(s) found",
                        loadedClass.getName(), metrics.getThreadLocalCount());
            } else {
                logger.debug("No ThreadLocal usage detected in class: {}", loadedClass.getName());
            }

        } catch (Exception e) {
            logger.warn("Error analyzing ThreadLocal usage for class: {} - {}",
                    loadedClass.getName(), e.getMessage());
            projectFileDecorator.error("Failed to analyze ThreadLocal usage: " + e.getMessage());
        }
    }

    /**
     * Analyzes a class for ThreadLocal field usage.
     * 
     * @param clazz the class to analyze
     * @return metrics containing ThreadLocal usage information
     */
    private ThreadLocalUsageMetrics analyzeThreadLocalUsage(Class<?> clazz) {
        ThreadLocalUsageMetrics metrics = new ThreadLocalUsageMetrics();

        // Examine all declared fields (including private)
        for (Field field : clazz.getDeclaredFields()) {
            if (isThreadLocalField(field)) {
                String fieldInfo = buildFieldInfo(field);
                metrics.addThreadLocalField(fieldInfo);

                logger.debug("ThreadLocal field detected: {} in class {}",
                        field.getName(), clazz.getName());
            }
        }

        return metrics;
    }

    /**
     * Checks if a field is of type ThreadLocal or its subclasses.
     * 
     * @param field the field to check
     * @return true if the field is a ThreadLocal, false otherwise
     */
    private boolean isThreadLocalField(Field field) {
        Class<?> fieldType = field.getType();
        return ThreadLocal.class.isAssignableFrom(fieldType);
    }

    /**
     * Builds a descriptive string for a ThreadLocal field including its generic
     * type if available.
     * 
     * @param field the ThreadLocal field
     * @return a string describing the field
     */
    private String buildFieldInfo(Field field) {
        StringBuilder info = new StringBuilder();
        info.append(field.getName());
        info.append(": ");
        info.append(field.getType().getSimpleName());

        // Try to extract generic type parameter
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) genericType;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length > 0) {
                info.append("<");
                info.append(typeArgs[0].getTypeName());
                info.append(">");
            }
        }

        return info.toString();
    }

    /**
     * Attaches ThreadLocal usage metrics to the corresponding JavaClassNode
     * AND aggregates file-level metrics on ProjectFile using SUM strategy.
     * 
     * <p>
     * Aggregation Strategy: SUM - accumulates total ThreadLocal usage count across
     * all classes in the file
     * </p>
     * 
     * @param projectFile          the project file context
     * @param metrics              the ThreadLocal usage metrics
     * @param projectFileDecorator the decorator for adding tags
     */
    private void attachThreadLocalMetricsToGraphNode(ProjectFile projectFile,
            ThreadLocalUsageMetrics metrics,
            NodeDecorator<ProjectFile> projectFileDecorator) {
        if (graphRepository == null) {
            logger.warn("GraphRepository not available - metrics cannot be attached to JavaClassNode.");
            return;
        }

        String fullyQualifiedName = projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME);
        if (fullyQualifiedName == null) {
            logger.warn("Could not find fullyQualifiedName for project file: {}",
                    projectFile.getRelativePath());
            return;
        }

        // LEVEL 1: Store detailed metrics on JavaClassNode (class-level)
        graphRepository.getNodeById(fullyQualifiedName).ifPresent(node -> {
            if (node instanceof JavaClassNode) {
                JavaClassNode classNode = (JavaClassNode) node;
                logger.debug("Marking JavaClassNode as using ThreadLocal: {}", fullyQualifiedName);

                classNode.setProperty(PROP_THREADLOCAL_DETECTED, true);
                classNode.setProperty(PROP_THREADLOCAL_COUNT, metrics.getThreadLocalCount());
                classNode.setProperty(PROP_THREADLOCAL_FIELDS, String.join(", ", metrics.getFieldNames()));
                classNode.addTag(TAG_USES_THREADLOCAL);

                logger.debug("Stored ThreadLocal usage metrics on JavaClassNode: {} ({} fields)",
                        fullyQualifiedName, metrics.getThreadLocalCount());
            } else {
                logger.debug("Node found for {} is not a JavaClassNode", fullyQualifiedName);
            }
        });

        // LEVEL 2: Aggregate metrics on ProjectFile (file-level summary)
        // Use SUM strategy - accumulate total ThreadLocal usage count
        aggregateSumMetric(projectFile, "threadlocal.count", metrics.getThreadLocalCount());

        logger.debug(
                "Aggregated ThreadLocal metrics on ProjectFile: {} (total_sum={}, classes_analyzed={})",
                projectFile.getRelativePath(),
                projectFile.getDoubleProperty("threadlocal.count.sum"),
                projectFile.getIntegerProperty("threadlocal.count.classes_analyzed"));
    }

    /**
     * Data class holding ThreadLocal usage metrics for a class.
     */
    public static class ThreadLocalUsageMetrics {
        private final List<String> threadLocalFields = new ArrayList<>();

        /**
         * Adds a ThreadLocal field to the metrics.
         * 
         * @param fieldInfo descriptive information about the field
         */
        public void addThreadLocalField(String fieldInfo) {
            threadLocalFields.add(fieldInfo);
        }

        /**
         * Checks if any ThreadLocal fields were found.
         * 
         * @return true if ThreadLocal fields exist, false otherwise
         */
        public boolean hasThreadLocalFields() {
            return !threadLocalFields.isEmpty();
        }

        /**
         * Gets the count of ThreadLocal fields.
         * 
         * @return the number of ThreadLocal fields found
         */
        public int getThreadLocalCount() {
            return threadLocalFields.size();
        }

        /**
         * Gets the list of ThreadLocal field names and types.
         * 
         * @return list of field descriptions
         */
        public List<String> getFieldNames() {
            return new ArrayList<>(threadLocalFields);
        }
    }
}
