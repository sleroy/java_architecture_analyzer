package com.analyzer.core.inspector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for defining complex tag-based dependency conditions.
 * This enables sophisticated inspector dependencies that go beyond simple tag presence,
 * supporting value-based conditions, comparisons, and pattern matching.
 * 
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * @InspectorDependencies(
 *     requires = {"JAVA"}, // Simple tag presence
 *     complexRequires = {
 *         @TagCondition(tag = "migrationComplexity", operator = GREATER_THAN_OR_EQUAL, value = "MEDIUM"),
 *         @TagCondition(tag = "fileExtension", operator = EQUALS, value = "XML")
 *     }
 * )
 * public class AdvancedInspector extends AbstractInspector {
 *     // This inspector runs only on Java files with medium+ migration complexity
 *     // AND XML file extension
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TagCondition {

    /**
     * The tag name to evaluate the condition against.
     * This should be a valid tag name that exists in the project file.
     * 
     * @return the tag name
     */
    String tag();

    /**
     * The comparison operator to use for evaluating the condition.
     * Different operators support different value types and comparison logic.
     * 
     * @return the operator to use
     */
    TagOperator operator() default TagOperator.EQUALS;

    /**
     * The value to compare against the tag's current value.
     * The interpretation depends on the operator:
     * - EQUALS/NOT_EQUALS: exact string comparison
     * - GREATER_THAN/LESS_THAN: numeric or lexicographic comparison
     * - CONTAINS: substring search
     * - MATCHES: regex pattern matching
     * - IN: comma-separated list of values
     * 
     * @return the comparison value
     */
    String value();

    /**
     * Optional description of what this condition checks.
     * Used for documentation and debugging purposes.
     * 
     * @return condition description
     */
    String description() default "";

    /**
     * The data type of the tag value for proper comparison.
     * This helps the condition evaluator perform the correct type of comparison.
     * 
     * @return the expected data type
     */
    TagDataType dataType() default TagDataType.STRING;

    /**
     * Supported comparison operators for tag conditions.
     */
    enum TagOperator {
        /** Exact equality comparison */
        EQUALS,
        
        /** Inequality comparison */
        NOT_EQUALS,
        
        /** Greater than comparison (numeric or lexicographic) */
        GREATER_THAN,
        
        /** Greater than or equal comparison */
        GREATER_THAN_OR_EQUAL,
        
        /** Less than comparison */
        LESS_THAN,
        
        /** Less than or equal comparison */
        LESS_THAN_OR_EQUAL,
        
        /** String contains substring */
        CONTAINS,
        
        /** String does not contain substring */
        NOT_CONTAINS,
        
        /** Regular expression pattern matching */
        MATCHES,
        
        /** Value is in a comma-separated list */
        IN,
        
        /** Value is not in a comma-separated list */
        NOT_IN,
        
        /** Tag exists (ignore value) */
        EXISTS,
        
        /** Tag does not exist */
        NOT_EXISTS
    }

    /**
     * Data types for proper tag value comparison.
     */
    enum TagDataType {
        /** String comparison */
        STRING,
        
        /** Integer numeric comparison */
        INTEGER,
        
        /** Double numeric comparison */
        DOUBLE,
        
        /** Boolean comparison */
        BOOLEAN,
        
        /** Complexity level comparison (NONE < LOW < MEDIUM < HIGH < CRITICAL) */
        COMPLEXITY
    }
}
