package com.analyzer.core.detector;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.model.ProjectFile;

/**
 * Generic interface for all detectors.
 * Inspectors analyze graph nodes and decorate them with properties and tags.
 *
 * @param <T> the type of GraphNode this inspector can analyze
 */
public interface FileDetector {

    boolean supports(ProjectFile projectFile);

    /**
     * Inspects and analyzes the given node, decorating it with properties and tags.
     *
     * @param node      the node to inspect
     * @param decorator the decorator for setting properties (with aggregation) and
     *                  tags
     */
    void detect(NodeDecorator<ProjectFile> decorator);

    /**
     * Gets the unique name of this inspector.
     * This is used for configuration and logging.
     *
     * @return the inspector name
     */
    String getName();

}
