package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.dev.inspectors.classloader.AbstractClassLoaderBasedInspector;
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
 * with appropriate tags and stores detailed metrics about the ThreadLocal
 * fields found.
 * </p>
 * 
 * <p>
 * ThreadLocal variables are commonly used for maintaining thread-specific state
 * in multi-threaded applications. Detecting their usage is important for:
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
 * The inspector uses reflection to examine all declared fields in a class and
 * checks if they are assignable from ThreadLocal. It also captures the generic
 * type parameter if present.
 * </p>
 * 
 * <p>
 * Operates in Phase 4 on JavaClassNode objects.
 * </p>
 * 
 * @author Java Architecture Analyzer
 * @since Phase 2.4 - ClassLoader-Based Metrics
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED, InspectorTags.TAG_APPLICATION_CLASS }, produces = {
        "threadlocal.detected",
        "threadlocal.count" })
public class ThreadLocalUsageInspector extends AbstractClassLoaderBasedInspector {

    private static final Logger logger = LoggerFactory.getLogger(ThreadLocalUsageInspector.class);

    // Tags and metrics for JavaClassNode
    public static final String TAG_THREADLOCAL_DETECTED = "threadlocal.detected";
    public static final String METRIC_THREADLOCAL_COUNT = "threadlocal.count";
    public static final String PROP_THREADLOCAL_FIELDS = "threadlocal.fields";
    public static final String TAG_USES_THREADLOCAL = "uses.threadlocal";

    private final GraphRepository graphRepository;

    /**
     * Creates a new ThreadLocalUsageInspector with required dependencies.
     * 
     * @param classLoaderService the service providing the shared ClassLoader
     * @param graphRepository    the graph repository for storing analysis results
     */
    @Inject
    public ThreadLocalUsageInspector(
            JARClassLoaderService classLoaderService,
            GraphRepository graphRepository) {
        super(classLoaderService);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "ThreadLocalUsageInspector";
    }

    @Override
    protected void analyzeLoadedClass(Class<?> loadedClass, JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        try {
            logger.debug("Analyzing ThreadLocal usage for class: {}", loadedClass.getName());

            // Scan for ThreadLocal fields
            ThreadLocalUsageMetrics metrics = analyzeThreadLocalUsage(loadedClass);

            if (metrics.hasThreadLocalFields()) {
                // Mark the class as using ThreadLocal
                logger.info("ThreadLocal usage detected in class: {} - {} field(s) found",
                        loadedClass.getName(), metrics.getThreadLocalCount());

                decorator.enableTag(TAG_THREADLOCAL_DETECTED);
                decorator.setMetric(METRIC_THREADLOCAL_COUNT, metrics.getThreadLocalCount());
                decorator.setProperty(PROP_THREADLOCAL_FIELDS, String.join(", ", metrics.getFieldNames()));
                decorator.enableTag(TAG_USES_THREADLOCAL);

                logger.debug("Stored ThreadLocal usage metrics on JavaClassNode: {} ({} fields)",
                        classNode.getFullyQualifiedName(), metrics.getThreadLocalCount());
            } else {
                logger.debug("No ThreadLocal usage detected in class: {}", loadedClass.getName());
            }

        } catch (Exception e) {
            logger.warn("Error analyzing ThreadLocal usage for class: {} - {}",
                    loadedClass.getName(), e.getMessage());
            decorator.error("Failed to analyze ThreadLocal usage: " + e.getMessage());
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
