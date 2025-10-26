package com.analyzer.core.graph;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an edge in the dependency graph showing tag dependency
 * relationships.
 * This record represents the relationship where one inspector (producer)
 * produces one or more tags that another inspector (consumer) requires.
 * Multiple tags between the same producer-consumer pair are consolidated
 * into a single edge to reduce graph complexity.
 * 
 * @param tags     The set of tags that create the dependency relationship
 * @param producer The inspector node that produces the tags
 * @param consumer The inspector node that consumes/requires the tags
 */
public record TagDependencyEdge(Set<String> tags, InspectorNode producer, InspectorNode consumer) {

    /**
     * Creates a new tag dependency edge.
     * 
     * @param tags     The set of tags that create the dependency relationship (must
     *                 not be
     *                 null or empty)
     * @param producer The inspector node that produces the tags (must not be null)
     * @param consumer The inspector node that consumes/requires the tags (must not
     *                 be null)
     * @throws IllegalArgumentException if any parameter is null or tags is empty
     */
    public TagDependencyEdge(Set<String> tags, InspectorNode producer, InspectorNode consumer) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("Tags cannot be null or empty");
        }
        if (producer == null) {
            throw new IllegalArgumentException("Producer cannot be null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer cannot be null");
        }
        // Create defensive copy
        this.tags = Collections.unmodifiableSet(new HashSet<>(tags));
        this.producer = producer;
        this.consumer = consumer;
    }

    /**
     * Returns the set of tags that create this dependency relationship.
     * 
     * @return the set of tag names (unmodifiable)
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * Returns the first tag (for backward compatibility).
     * If multiple tags exist, returns one arbitrarily.
     * 
     * @return a tag name
     * @deprecated Use getTags() instead for multiple tag support
     */
    @Deprecated
    public String getTag() {
        return tags.iterator().next();
    }

    /**
     * Returns the inspector node that produces the tag.
     * 
     * @return the producer inspector node
     */
    public InspectorNode getProducer() {
        return producer;
    }

    /**
     * Returns the inspector node that consumes/requires the tag.
     * 
     * @return the consumer inspector node
     */
    public InspectorNode getConsumer() {
        return consumer;
    }

    /**
     * Returns a string representation of this dependency edge.
     * 
     * @return a formatted string showing the dependency relationship
     */
    @Override
    public String toString() {
        String tagList = tags.size() == 1 ? tags.iterator().next() : String.join(", ", tags);
        return String.format("%s -[%s]-> %s", producer.inspectorName(), tagList, consumer.inspectorName());
    }
}
