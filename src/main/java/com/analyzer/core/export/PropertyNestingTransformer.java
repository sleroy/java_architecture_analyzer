package com.analyzer.core.export;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to transform flat property maps with dotted keys into nested
 * structures.
 * 
 * <p>
 * Example transformation:
 * </p>
 * 
 * <pre>
 * Input (flat):
 *   "file.type" = "java"
 *   "file.size" = 1024
 *   "ejb.type" = "stateless"
 *   "ejb.version" = "3.0"
 *   "complexity" = 5
 * 
 * Output (nested):
 * {
 *   "file": {
 *     "type": "java",
 *     "size": 1024
 *   },
 *   "ejb": {
 *     "type": "stateless",
 *     "version": "3.0"
 *   },
 *   "complexity": 5
 * }
 * </pre>
 */
public class PropertyNestingTransformer {

    /**
     * Transforms a flat map of properties into a nested structure based on
     * dot-separated keys.
     * 
     * <p>
     * Rules:
     * </p>
     * <ul>
     * <li>Keys without dots remain at the root level</li>
     * <li>Keys with dots create nested maps (e.g., "a.b.c" = value becomes {a: {b:
     * {c: value}}})</li>
     * <li>Existing nested structures are preserved and extended</li>
     * <li>Null values are preserved</li>
     * </ul>
     * 
     * @param flatProperties The flat property map to transform
     * @return A new map with nested structures for dotted keys
     */
    public static Map<String, Object> nestProperties(Map<String, Object> flatProperties) {
        if (flatProperties == null || flatProperties.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : flatProperties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.contains(".")) {
                // Split the key and create nested structure
                String[] parts = key.split("\\.");
                insertNested(result, parts, value, 0);
            } else {
                // No dots - add directly at root level
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * Recursively inserts a value into a nested map structure.
     * 
     * @param current The current map level
     * @param parts   The key parts (split by dots)
     * @param value   The value to insert
     * @param index   Current index in the parts array
     */
    @SuppressWarnings("unchecked")
    private static void insertNested(Map<String, Object> current, String[] parts, Object value, int index) {
        String currentPart = parts[index];

        if (index == parts.length - 1) {
            // Last part - insert the value
            current.put(currentPart, value);
        } else {
            // Not the last part - need to go deeper
            Object existing = current.get(currentPart);
            Map<String, Object> nested;

            if (existing instanceof Map) {
                // Map already exists - use it
                nested = (Map<String, Object>) existing;
            } else {
                // Create new nested map
                nested = new HashMap<>();
                current.put(currentPart, nested);
            }

            // Recurse to next level
            insertNested(nested, parts, value, index + 1);
        }
    }

    /**
     * Flattens a nested map structure back into a flat map with dotted keys.
     * This is the inverse operation of nestProperties().
     * 
     * @param nestedProperties The nested map to flatten
     * @return A flat map with dot-separated keys
     */
    public static Map<String, Object> flattenProperties(Map<String, Object> nestedProperties) {
        Map<String, Object> result = new HashMap<>();
        flattenHelper(nestedProperties, "", result);
        return result;
    }

    /**
     * Helper method for recursive flattening.
     * 
     * @param current Current map level
     * @param prefix  Current key prefix
     * @param result  Result accumulator
     */
    @SuppressWarnings("unchecked")
    private static void flattenHelper(Map<String, Object> current, String prefix, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : current.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (value instanceof Map) {
                // Recurse into nested map
                flattenHelper((Map<String, Object>) value, fullKey, result);
            } else {
                // Leaf value - add to result
                result.put(fullKey, value);
            }
        }
    }
}
