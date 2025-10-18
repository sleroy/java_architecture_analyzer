package com.analyzer.core.inspector;
import com.analyzer.core.inspector.InspectorDependencies;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builder class for collecting and managing inspector tag dependencies.
 * 
 * This class provides a clean, fluent API for declaring inspector dependencies
 * that works well with inheritance hierarchies. Inspectors can call
 * super.depends(tags) to automatically inherit parent dependencies, then
 * add their own specific requirements.
 * 
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>{@code
 * @Override
 * public void depends(RequiredTags tags) {
 *     super.depends(tags); // Inherit parent dependencies
 *     tags.requires(InspectorTags.JAVA_DETECTED)
 *             .requires(InspectorTags.JAVA_IS_SOURCE);
 * }
 * }</pre>
 */
public class RequiredTags {

    private final Set<String> tags = new LinkedHashSet<>();

    /**
     * Adds a required tag dependency.
     * Null, empty, or whitespace-only tags are ignored.
     * 
     * @param tag the tag name to require
     * @return this instance for method chaining
     */
    public RequiredTags requires(String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            tags.add(tag.trim());
        }
        return this;
    }

    /**
     * Adds multiple required tag dependencies.
     * Null, empty, or whitespace-only tags are ignored.
     * 
     * @param tags the tag names to require
     * @return this instance for method chaining
     */
    public RequiredTags requiresAll(String... tags) {
        if (tags != null) {
            for (String tag : tags) {
                requires(tag);
            }
        }
        return this;
    }

    /**
     * Adds required tag dependencies from another RequiredTags instance.
     * This is useful for composing dependencies from multiple sources.
     * 
     * @param other the other RequiredTags instance to merge
     * @return this instance for method chaining
     */
    public RequiredTags requiresAll(RequiredTags other) {
        if (other != null) {
            this.tags.addAll(other.tags);
        }
        return this;
    }

    /**
     * Converts the collected dependencies to a String array.
     * The order is preserved (LinkedHashSet maintains insertion order).
     * 
     * @return array of required tag names
     */
    public String[] toArray() {
        return tags.toArray(new String[0]);
    }

    /**
     * Checks if no dependencies are required.
     * 
     * @return true if no tags have been added, false otherwise
     */
    public boolean isEmpty() {
        return tags.isEmpty();
    }

    /**
     * Gets the number of required dependencies.
     * 
     * @return count of required tags
     */
    public int size() {
        return tags.size();
    }

    /**
     * Checks if a specific tag is required.
     * 
     * @param tag the tag name to check
     * @return true if the tag is required, false otherwise
     */
    public boolean contains(String tag) {
        return tag != null && tags.contains(tag.trim());
    }

    /**
     * Creates a copy of this RequiredTags instance.
     * 
     * @return a new RequiredTags instance with the same dependencies
     */
    public RequiredTags copy() {
        RequiredTags copy = new RequiredTags();
        copy.tags.addAll(this.tags);
        return copy;
    }

    /**
     * Returns a string representation of the required tags.
     * Useful for debugging and logging.
     * 
     * @return string representation of required tags
     */
    @Override
    public String toString() {
        return "RequiredTags" + tags;
    }

    /**
     * Creates a new RequiredTags instance with the specified tag.
     * Convenience factory method for single-tag requirements.
     * 
     * @param tag the initial required tag
     * @return new RequiredTags instance
     */
    public static RequiredTags of(String tag) {
        return new RequiredTags().requires(tag);
    }

    /**
     * Creates a new RequiredTags instance with the specified tags.
     * Convenience factory method for multiple-tag requirements.
     * 
     * @param tags the initial required tags
     * @return new RequiredTags instance
     */
    public static RequiredTags of(String... tags) {
        return new RequiredTags().requiresAll(tags);
    }
}
