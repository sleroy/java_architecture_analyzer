package com.analyzer.core.db.validation;

import com.analyzer.core.inspector.InspectorTags;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates JSON properties before database insertion.
 * Application-level validation to ensure data quality.
 */
public class PropertiesValidator {

    private static final Set<String> REQUIRED_PROPERTIES = Set.of(
        InspectorTags.TAG_FILE_NAME,
        InspectorTags.TAG_FILE_EXTENSION,
        InspectorTags.TAG_RELATIVE_PATH
    );

    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    /**
     * Validate properties map before serialization.
     * 
     * @param properties The properties map to validate
     * @throws ValidationException if validation fails
     */
    public static void validate(Map<String, Object> properties) {
        if (properties == null) {
            throw new ValidationException("Properties map cannot be null");
        }

        // 1. Check required properties exist
        validateRequiredProperties(properties);

        // 2. Validate property types
        validatePropertyTypes(properties);

        // 3. Check property key format
        validatePropertyKeys(properties);

        // 4. Check value constraints
        validatePropertyValues(properties);
    }

    private static void validateRequiredProperties(Map<String, Object> properties) {
        for (String required : REQUIRED_PROPERTIES) {
            if (!properties.containsKey(required)) {
                throw new ValidationException(
                    "Missing required property: " + required
                );
            }
        }
    }

    private static void validatePropertyTypes(Map<String, Object> properties) {
        // Metrics must be numbers
        properties.entrySet().stream()
            .filter(e -> e.getKey().startsWith("metrics."))
            .forEach(e -> {
                if (!(e.getValue() instanceof Number)) {
                    throw new ValidationException(
                        "Metric property must be numeric: " + e.getKey() + 
                        " (got: " + e.getValue().getClass().getSimpleName() + ")"
                    );
                }
            });

        // Boolean properties
        properties.entrySet().stream()
            .filter(e -> e.getKey().contains(".is_") || e.getKey().startsWith("java.is_"))
            .forEach(e -> {
                if (!(e.getValue() instanceof Boolean)) {
                    throw new ValidationException(
                        "Boolean property must be true/false: " + e.getKey() +
                        " (got: " + e.getValue().getClass().getSimpleName() + ")"
                    );
                }
            });
    }

    private static void validatePropertyKeys(Map<String, Object> properties) {
        properties.keySet().forEach(key -> {
            if (!VALID_KEY_PATTERN.matcher(key).matches()) {
                throw new ValidationException(
                    "Invalid property key format: " + key + 
                    " (must match pattern: " + VALID_KEY_PATTERN.pattern() + ")"
                );
            }
            if (key.length() > 255) {
                throw new ValidationException(
                    "Property key too long (max 255 chars): " + key
                );
            }
        });
    }

    private static void validatePropertyValues(Map<String, Object> properties) {
        properties.forEach((key, value) -> {
            // Null values not allowed
            if (value == null) {
                throw new ValidationException(
                    "Null value not allowed for property: " + key
                );
            }

            // Validate specific property constraints
            if (key.equals(InspectorTags.TAG_METRIC_LINES_OF_CODE)) {
                int loc = ((Number) value).intValue();
                if (loc < 0) {
                    throw new ValidationException(
                        "Lines of code cannot be negative: " + loc
                    );
                }
            }

            if (key.equals(InspectorTags.TAG_METRIC_FILE_SIZE)) {
                long size = ((Number) value).longValue();
                if (size < 0) {
                    throw new ValidationException(
                        "File size cannot be negative: " + size
                    );
                }
            }

            // Validate string length for string properties
            if (value instanceof String) {
                String str = (String) value;
                if (str.length() > 10000) {
                    throw new ValidationException(
                        "Property value too long (max 10000 chars): " + key
                    );
                }
            }
        });
    }

    /**
     * Validate without throwing exception.
     * 
     * @param properties The properties map to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(Map<String, Object> properties) {
        try {
            validate(properties);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }
}
