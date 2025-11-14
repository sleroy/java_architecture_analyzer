package com.analyzer.api.inspector;

import java.lang.annotation.*;

/**
 * Annotation for declaring inspector dependencies in a clean, declarative way.
 * This eliminates the need for manual super.depends() calls and provides
 * automatic dependency inheritance through the class hierarchy.
 * 
 * <p>
 * The annotation supports both tag-based dependencies and inspector-class
 * based dependencies for better type safety and IDE support.
 * </p>
 * 
 * <p>
 * Example usage with inspector-class based dependencies:
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     &#64;InspectorDependencies(need = { FileExtensionDetector.class, JavaSourceFileInspector.class }, produces = {
 *             InspectorTags.JAVA_DETECTED, InspectorTags.JAVA_IS_SOURCE })
 *     public class JavaParserInspector extends AbstractSourceFileInspector {
 *         // Depends on specific inspector classes and declares what tags it produces
 *     }
 * 
 *     &#64;InspectorDependencies(need = { JavaParserInspector.class }, produces = { InspectorTags.SESSION_BEAN_DETECTED })
 *     public class SessionBeanJavaSourceInspector extends JavaParserInspector {
 *         // Depends on JavaParserInspector and produces session bean tags
 *     }
 * }
 * </pre>
 * 
 * <p>
 * Tag-based dependencies are also supported:
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     &#64;InspectorDependencies(requires = { InspectorTags.SOURCE_FILE })
 *     public abstract class SourceFileInspector extends ProjectFileInspector {
 *         // Tag-based dependency
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface InspectorDependencies {

    /**
     * Required tags for this inspector.
     * These will be combined with inherited dependencies from parent classes.
     * 
     * @return array of required tag constants from InspectorTags
     */
    String[] requires() default {};

    /**
     * Inspector classes that this inspector depends on.
     * This provides type-safe dependencies and better IDE support compared to
     * tag-based dependencies.
     * The dependency resolver will ensure these inspectors run before this one.
     * 
     * @return array of inspector classes that must execute before this inspector
     */
    Class<? extends Inspector>[] need() default {};

    /**
     * Tags that this inspector produces when it runs successfully.
     * This creates a clear contract of what tags other inspectors can depend on.
     * These tags will be available for other inspectors to use in their
     * {@link #requires()} dependencies.
     * 
     * @return array of tag constants from InspectorTags that this inspector sets
     */
    String[] produces();

    /**
     * Indicates whether this inspector requires all nodes to be processed by its
     * dependencies before it can execute.
     * 
     * <p>
     * Global inspectors (requiresAllNodesProcessed = true) have special execution
     * semantics:
     * </p>
     * <ul>
     * <li>They run AFTER the multi-pass node-by-node phase completes</li>
     * <li>They are executed in a separate "Global Inspectors" phase</li>
     * <li>All their dependency inspectors have already processed all nodes</li>
     * <li>They can iterate over all nodes or use completed graph structures</li>
     * </ul>
     * 
     * <p>
     * Example use case: CouplingMetricsInspector needs
     * BinaryClassCouplingGraphInspector
     * to run on ALL classes before it can calculate coupling metrics, because it
     * needs
     * the complete graph structure.
     * </p>
     * 
     * <p>
     * Example usage:
     * </p>
     * 
     * <pre>
     * {
     *     &#64;code
     *     &#64;InspectorDependencies(need = BinaryClassCouplingGraphInspector.class, produces = "java.class.coupling_metrics.calculated", requiresAllNodesProcessed = true // This
     *                                                                                                                                                                  // inspector
     *                                                                                                                                                                  // needs
     *                                                                                                                                                                  // complete
     *                                                                                                                                                                  // graph
     *     )
     *     public class CouplingMetricsInspector implements Inspector<JavaClassNode> {
     *         // This will run after ALL classes have been processed by
     *         // BinaryClassCouplingGraphInspector
     *     }
     * }
     * </pre>
     * 
     * @return true if this inspector needs all nodes processed before execution,
     *         false for
     *         normal node-by-node execution (default: false)
     */
    boolean requiresAllNodesProcessed() default false;

}
