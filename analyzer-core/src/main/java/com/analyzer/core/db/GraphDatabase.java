package com.analyzer.core.db;

import com.analyzer.api.graph.GraphRepository;

public interface GraphDatabase {

    void load();

    GraphRepository snapshot();


    void persist(GraphRepository graphRepository);
}
