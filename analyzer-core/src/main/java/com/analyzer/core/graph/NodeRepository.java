package com.analyzer.core.graph;

import java.util.List;
import java.util.Optional;

public interface NodeRepository<T extends GraphNode> {
    Optional<T> findById(String id);

    T getOrCreate(String id);

    List<T> findAll();

    List<T> findByTag(String tag);

    void save(T node);
}
