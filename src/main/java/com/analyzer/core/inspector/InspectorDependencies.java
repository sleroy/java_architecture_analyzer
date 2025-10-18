package com.analyzer.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declaring inspector dependencies in a clean, declarative way.
 * This eliminates the need for manual super.depends() calls and provides
 * automatic dependency inheritance through the class hierarchy.
 * 
 * <p>
 * The annotation supports both legacy tag-based dependencies and the new
 * inspector-class based dependencies for better type safety and IDE support.
 * </p>
 * 
 * <p>
 * Example usage with new inspector-class based dependencies:
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     &#64;InspectorDependencies(need = { FileExtensionInspector.class, JavaSourceFileInspector.class }, produces = {
 *             InspectorTags.JAVA_DETECTED, InspectorTags.JAVA_IS_SOURCE })
 *     public class JavaParserInspector extends AbstractSourceFileInspector {
 *         // Depends on specific inspector classes and declares what tags it produces
 *     }
 * 
 *     &#64;InspectorDependencies(need = { JavaParserInspector.class }, produces = { InspectorTags.SESSION_BEAN_DETECTED })
 *     public class SessionBeanInspector extends JavaParserInspector {
 *         // Depends on JavaParserInspector and produces session bean tags
 *     }
 * }
 * </pre>
 * 
 * <p>
 * Legacy tag-based dependencies are still supported:
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     &#64;InspectorDependencies(requires = { InspectorTags.SOURCE_FILE })
 *     public abstract class SourceFileInspector extends ProjectFileInspector {
 *         // Legacy tag-based dependency (still supported)
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface InspectorDependencies {

    /**
     * Required tags for this specific inspector class.
     * These will be combined with inherited dependencies from parent classes.
     * 
     * @deprecated Use {@link #need()} for type-safe inspector class dependencies
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
    String[] produces() ;

    /**
     * Whether to inherit dependencies from parent inspector classes.
     * When true (default), this class's dependencies are added to parent
     * dependencies.
     * When false, only this class's dependencies are used.
     * 
     * @return true to inherit parent dependencies, false to ignore them
     */
    boolean inheritParent() default true;

    /**
     * Whether to override parent dependencies instead of adding to them.
     * When true, parent dependencies are completely replaced by this class's
     * dependencies.
     * Use with caution as this breaks the inheritance chain.
     * 
     * <p>
     * This is useful for special cases where an inspector needs a completely
     * different set of dependencies than its parent class.
     * </p>
     * 
     * @return true to replace parent dependencies, false to add to them
     */
    boolean overrideParent() default false;
}
