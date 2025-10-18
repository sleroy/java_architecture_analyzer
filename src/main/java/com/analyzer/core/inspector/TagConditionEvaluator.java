package com.analyzer.core.inspector;

import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.inspector.TagCondition.TagOperator;
import com.analyzer.core.inspector.TagCondition.TagDataType;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates TagCondition annotations against ProjectFile tags to determine
 * if complex dependency conditions are satisfied.
 * 
 * <p>
 * This class supports sophisticated condition evaluation including:
 * </p>
 * <ul>
 * <li>Value-based comparisons (equals, greater than, less than)</li>
 * <li>String operations (contains, regex matching)</li>
 * <li>Set operations (in, not in)</li>
 * <li>Existence checks (exists, not exists)</li>
 * <li>Type-aware comparisons (string, numeric, boolean, complexity levels)</li>
 * </ul>
 */
public class TagConditionEvaluator {
    
    // Complexity levels in ascending order for comparison
    private static final List<String> COMPLEXITY_LEVELS = Arrays.asList(
        "NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL"
    );

    /**
     * Evaluates a single TagCondition against a ProjectFile.
     * 
     * @param condition the condition to evaluate
     * @param projectFile the project file to check against
     * @return true if the condition is satisfied, false otherwise
     */
    public boolean evaluate(TagCondition condition, ProjectFile projectFile) {
        if (condition == null || projectFile == null) {
            return false;
        }

        String tagName = condition.tag();
        TagOperator operator = condition.operator();
        String expectedValue = condition.value();
        TagDataType dataType = condition.dataType();

        // Handle existence checks first (don't need actual values)
        if (operator == TagOperator.EXISTS) {
            return projectFile.hasTag(tagName);
        }
        
        if (operator == TagOperator.NOT_EXISTS) {
            return !projectFile.hasTag(tagName);
        }

        // Get the actual tag value
        Object actualValue = projectFile.getTag(tagName);
        
        // If tag doesn't exist, most operations fail (except NOT_EXISTS which was handled above)
        if (actualValue == null) {
            return false;
        }

        // Evaluate based on data type and operator
        return evaluateWithDataType(actualValue, expectedValue, operator, dataType);
    }

    /**
     * Evaluates multiple TagConditions using AND logic.
     * All conditions must be satisfied for the result to be true.
     * 
     * @param conditions array of conditions to evaluate
     * @param projectFile the project file to check against
     * @return true if ALL conditions are satisfied, false otherwise
     */
    public boolean evaluateAll(TagCondition[] conditions, ProjectFile projectFile) {
        if (conditions == null || conditions.length == 0) {
            return true; // No conditions means no restrictions
        }

        for (TagCondition condition : conditions) {
            if (!evaluate(condition, projectFile)) {
                return false; // First failed condition fails the whole evaluation
            }
        }
        
        return true;
    }

    /**
     * Performs type-aware comparison based on the specified data type.
     */
    private boolean evaluateWithDataType(Object actualValue, String expectedValue, 
                                       TagOperator operator, TagDataType dataType) {
        try {
            switch (dataType) {
                case STRING:
                    return evaluateStringCondition(actualValue.toString(), expectedValue, operator);
                
                case INTEGER:
                    return evaluateIntegerCondition(actualValue, expectedValue, operator);
                
                case DOUBLE:
                    return evaluateDoubleCondition(actualValue, expectedValue, operator);
                
                case BOOLEAN:
                    return evaluateBooleanCondition(actualValue, expectedValue, operator);
                
                case COMPLEXITY:
                    return evaluateComplexityCondition(actualValue.toString(), expectedValue, operator);
                
                default:
                    // Fallback to string comparison
                    return evaluateStringCondition(actualValue.toString(), expectedValue, operator);
            }
        } catch (Exception e) {
            // If any conversion or comparison fails, condition is not satisfied
            return false;
        }
    }

    /**
     * Evaluates string-based conditions.
     */
    private boolean evaluateStringCondition(String actual, String expected, TagOperator operator) {
        if (actual == null) actual = "";
        if (expected == null) expected = "";

        switch (operator) {
            case EQUALS:
                return actual.equals(expected);
            
            case NOT_EQUALS:
                return !actual.equals(expected);
            
            case GREATER_THAN:
                return actual.compareTo(expected) > 0;
            
            case GREATER_THAN_OR_EQUAL:
                return actual.compareTo(expected) >= 0;
            
            case LESS_THAN:
                return actual.compareTo(expected) < 0;
            
            case LESS_THAN_OR_EQUAL:
                return actual.compareTo(expected) <= 0;
            
            case CONTAINS:
                return actual.contains(expected);
            
            case NOT_CONTAINS:
                return !actual.contains(expected);
            
            case MATCHES:
                return evaluateRegexMatch(actual, expected);
            
            case IN:
                return evaluateInList(actual, expected);
            
            case NOT_IN:
                return !evaluateInList(actual, expected);
            
            default:
                return false;
        }
    }

    /**
     * Evaluates integer-based conditions.
     */
    private boolean evaluateIntegerCondition(Object actualObj, String expectedStr, TagOperator operator) {
        int actual = convertToInteger(actualObj);
        int expected = Integer.parseInt(expectedStr);

        switch (operator) {
            case EQUALS:
                return actual == expected;
            case NOT_EQUALS:
                return actual != expected;
            case GREATER_THAN:
                return actual > expected;
            case GREATER_THAN_OR_EQUAL:
                return actual >= expected;
            case LESS_THAN:
                return actual < expected;
            case LESS_THAN_OR_EQUAL:
                return actual <= expected;
            case IN:
                return evaluateIntegerInList(actual, expectedStr);
            case NOT_IN:
                return !evaluateIntegerInList(actual, expectedStr);
            default:
                return false;
        }
    }

    /**
     * Evaluates double-based conditions.
     */
    private boolean evaluateDoubleCondition(Object actualObj, String expectedStr, TagOperator operator) {
        double actual = convertToDouble(actualObj);
        double expected = Double.parseDouble(expectedStr);

        switch (operator) {
            case EQUALS:
                return Double.compare(actual, expected) == 0;
            case NOT_EQUALS:
                return Double.compare(actual, expected) != 0;
            case GREATER_THAN:
                return actual > expected;
            case GREATER_THAN_OR_EQUAL:
                return actual >= expected;
            case LESS_THAN:
                return actual < expected;
            case LESS_THAN_OR_EQUAL:
                return actual <= expected;
            default:
                return false;
        }
    }

    /**
     * Evaluates boolean-based conditions.
     */
    private boolean evaluateBooleanCondition(Object actualObj, String expectedStr, TagOperator operator) {
        boolean actual = convertToBoolean(actualObj);
        boolean expected = Boolean.parseBoolean(expectedStr);

        switch (operator) {
            case EQUALS:
                return actual == expected;
            case NOT_EQUALS:
                return actual != expected;
            default:
                return false;
        }
    }

    /**
     * Evaluates complexity level conditions using the standard progression:
     * NONE < LOW < MEDIUM < HIGH < CRITICAL
     */
    private boolean evaluateComplexityCondition(String actual, String expected, TagOperator operator) {
        int actualLevel = getComplexityLevel(actual);
        int expectedLevel = getComplexityLevel(expected);

        switch (operator) {
            case EQUALS:
                return actualLevel == expectedLevel;
            case NOT_EQUALS:
                return actualLevel != expectedLevel;
            case GREATER_THAN:
                return actualLevel > expectedLevel;
            case GREATER_THAN_OR_EQUAL:
                return actualLevel >= expectedLevel;
            case LESS_THAN:
                return actualLevel < expectedLevel;
            case LESS_THAN_OR_EQUAL:
                return actualLevel <= expectedLevel;
            case IN:
                return evaluateComplexityInList(actual, expected);
            case NOT_IN:
                return !evaluateComplexityInList(actual, expected);
            default:
                return false;
        }
    }

    /**
     * Evaluates regex pattern matching with error handling.
     */
    private boolean evaluateRegexMatch(String actual, String pattern) {
        try {
            return Pattern.matches(pattern, actual);
        } catch (PatternSyntaxException e) {
            // Invalid regex pattern fails the condition
            return false;
        }
    }

    /**
     * Evaluates if a string value is in a comma-separated list.
     */
    private boolean evaluateInList(String actual, String list) {
        if (list == null || list.trim().isEmpty()) {
            return false;
        }
        
        String[] values = list.split(",");
        for (String value : values) {
            if (actual.equals(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates if an integer value is in a comma-separated list.
     */
    private boolean evaluateIntegerInList(int actual, String list) {
        if (list == null || list.trim().isEmpty()) {
            return false;
        }
        
        String[] values = list.split(",");
        for (String value : values) {
            try {
                if (actual == Integer.parseInt(value.trim())) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // Skip invalid integers in the list
            }
        }
        return false;
    }

    /**
     * Evaluates if a complexity level is in a comma-separated list.
     */
    private boolean evaluateComplexityInList(String actual, String list) {
        if (list == null || list.trim().isEmpty()) {
            return false;
        }
        
        String[] values = list.split(",");
        for (String value : values) {
            if (actual.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts various object types to integer.
     */
    private int convertToInteger(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else {
            return Integer.parseInt(obj.toString());
        }
    }

    /**
     * Converts various object types to double.
     */
    private double convertToDouble(Object obj) {
        if (obj instanceof Double) {
            return (Double) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        } else {
            return Double.parseDouble(obj.toString());
        }
    }

    /**
     * Converts various object types to boolean.
     */
    private boolean convertToBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else {
            return Boolean.parseBoolean(obj.toString());
        }
    }

    /**
     * Gets the numeric level for a complexity string.
     * Returns the index in COMPLEXITY_LEVELS, or 2 (MEDIUM) for unknown levels.
     */
    private int getComplexityLevel(String complexity) {
        if (complexity == null) {
            return 0; // NONE
        }
        
        int level = COMPLEXITY_LEVELS.indexOf(complexity.toUpperCase());
        return level >= 0 ? level : 2; // Default to MEDIUM for unknown levels
    }
}
