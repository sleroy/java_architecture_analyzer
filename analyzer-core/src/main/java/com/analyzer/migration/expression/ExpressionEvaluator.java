package com.analyzer.migration.expression;

import com.analyzer.migration.context.MigrationContext;
import org.apache.commons.jexl3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Evaluates boolean expressions using Apache Commons JEXL.
 * Expressions can reference variables from the MigrationContext.
 * 
 * <p>
 * Example expressions:
 * <ul>
 * <li>{@code migrate_db == true}</li>
 * <li>{@code ejb_count > 50}</li>
 * <li>{@code environment == 'production'}</li>
 * <li>{@code migrate_db && backup_enabled}</li>
 * <li>{@code (ejb_count > 100 || complexity_score >= 7) && environment != 'dev'}</li>
 * </ul>
 * 
 * <p>
 * This class is thread-safe and uses a singleton pattern for the JEXL engine.
 */
public class ExpressionEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(ExpressionEvaluator.class);

    private static final JexlEngine JEXL_ENGINE;

    static {
        // Initialize JEXL engine with safe configuration
        JEXL_ENGINE = new JexlBuilder()
                .silent(false) // Log expression errors
                .strict(true) // Strict variable checking
                .safe(false) // Allow null pointer exceptions to be caught
                .create();
    }

    /**
     * Private constructor to prevent instantiation.
     * Use static methods instead.
     */
    private ExpressionEvaluator() {
    }

    /**
     * Evaluate a boolean expression against the MigrationContext.
     * 
     * @param expression The JEXL expression to evaluate
     * @param context    The migration context containing variables
     * @return true if expression evaluates to true, false otherwise
     * @throws ExpressionEvaluationException if expression evaluation fails
     */
    public static boolean evaluate(String expression, MigrationContext context) {
        return evaluate(expression, context, false);
    }

    /**
     * Evaluate a boolean expression against the MigrationContext.
     * 
     * @param expression     The JEXL expression to evaluate
     * @param context        The migration context containing variables
     * @param defaultOnError The value to return if evaluation fails
     * @return true if expression evaluates to true, false otherwise, or
     *         defaultOnError on failure
     */
    public static boolean evaluate(String expression, MigrationContext context, boolean defaultOnError) {
        if (expression == null || expression.trim().isEmpty()) {
            logger.debug("Empty expression, returning true");
            return true;
        }

        try {
            // First, resolve FreeMarker-style variables (${variable_name})
            String resolvedExpression = expression;
            if (expression.contains("${")) {
                logger.debug("Resolving FreeMarker variables in expression: {}", expression);
                resolvedExpression = context.substituteVariables(expression);
                logger.debug("Expression after variable resolution: {}", resolvedExpression);
            }

            // Get all variables from context
            Map<String, Object> allVariables = context.getAllVariables();

            // Create JEXL context with all variables
            JexlContext jexlContext = new MapContext(allVariables);

            // Parse and evaluate expression
            JexlExpression jexlExpr = JEXL_ENGINE.createExpression(resolvedExpression);
            Object result = jexlExpr.evaluate(jexlContext);

            // Convert result to boolean
            boolean booleanResult = toBoolean(result);

            logger.debug("Expression '{}' (resolved: '{}') evaluated to: {}", expression, resolvedExpression,
                    booleanResult);
            return booleanResult;

        } catch (JexlException.Variable e) {
            logger.warn("Variable not found in expression '{}': {}", expression, e.getMessage());
            logger.debug("Available variables: {}", context.getAllVariables().keySet());
            return defaultOnError;
        } catch (JexlException e) {
            logger.error("Failed to evaluate expression '{}': {}", expression, e.getMessage());
            throw new ExpressionEvaluationException(
                    "Expression evaluation failed: " + expression, e);
        } catch (Exception e) {
            logger.error("Unexpected error evaluating expression '{}': {}", expression, e.getMessage());
            throw new ExpressionEvaluationException(
                    "Unexpected error during expression evaluation: " + expression, e);
        }
    }

    /**
     * Test if an expression is syntactically valid.
     * 
     * @param expression The JEXL expression to validate
     * @return true if expression is valid, false otherwise
     */
    public static boolean isValid(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        try {
            JEXL_ENGINE.createExpression(expression);
            return true;
        } catch (JexlException e) {
            logger.debug("Invalid expression '{}': {}", expression, e.getMessage());
            return false;
        }
    }

    /**
     * Convert an object to a boolean value.
     * Follows JEXL's truthiness rules:
     * - null -> false
     * - Boolean -> as-is
     * - Number -> true if non-zero
     * - String -> true if non-empty
     * - Collection -> true if non-empty
     * - Other -> true
     * 
     * @param value The value to convert
     * @return The boolean representation
     */
    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }

        if (value instanceof String) {
            String str = (String) value;
            // Empty string is false
            if (str.isEmpty()) {
                return false;
            }
            // Special case for "false" string
            if ("false".equalsIgnoreCase(str)) {
                return false;
            }
            // All other non-empty strings are true
            return true;
        }

        if (value instanceof Iterable) {
            return ((Iterable<?>) value).iterator().hasNext();
        }

        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }

        // All other objects are considered true
        return true;
    }

    /**
     * Exception thrown when expression evaluation fails.
     */
    public static class ExpressionEvaluationException extends RuntimeException {
        public ExpressionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
