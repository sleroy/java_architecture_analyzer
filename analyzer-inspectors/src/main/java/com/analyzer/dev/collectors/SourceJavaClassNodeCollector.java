package com.analyzer.dev.collectors;

import com.analyzer.core.collector.AbstractSourceParserCollector;
import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.resource.ResourceResolver;

import javax.inject.Inject;

/**
 * Collector that creates JavaClassNode objects from Java source files using
 * JavaParser.
 * <p>
 * This collector parses .java source files to extract class declarations and
 * create
 * JavaClassNode objects. It's particularly useful for:
 * <ul>
 * <li>Analyzing uncompiled source code</li>
 * <li>Working with source-only projects</li>
 * <li>Extracting source-level class information</li>
 * <li>Understanding class structure from source</li>
 * </ul>
 * <p>
 * <strong>Comparison with Binary Collectors:</strong>
 * <ul>
 * <li><strong>BinaryJavaClassNodeCollector:</strong> Uses ASM to read compiled
 * bytecode</li>
 * <li><strong>ClassLoaderJavaClassNodeCollector:</strong> Uses reflection on
 * loaded classes</li>
 * <li><strong>SourceJavaClassNodeCollector:</strong> Parses source code
 * directly</li>
 * </ul>
 * <p>
 * <strong>Usage in Analysis Pipeline:</strong>
 * 
 * <pre>
 * Phase 2: ClassNode Collection
 *   └── SourceJavaClassNodeCollector creates JavaClassNode from .java source files
 * </pre>
 *
 * @see AbstractSourceParserCollector
 * @see ClassNodeCollector
 * @see BinaryJavaClassNodeCollector
 * @see ClassLoaderJavaClassNodeCollector
 * @since 1.3.2 - Additional Collector Types
 */
public class SourceJavaClassNodeCollector extends AbstractSourceParserCollector {

    /**
     * Constructs a new SourceJavaClassNodeCollector.
     * <p>
     * Requires ResourceResolver for accessing source file content.
     *
     * @param resourceResolver resolver for accessing file content
     */
    @Inject
    public SourceJavaClassNodeCollector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    /**
     * Returns the name of this collector.
     * <p>
     * Used for logging, debugging, and registry identification.
     *
     * @return the collector name
     */
    @Override
    public String getName() {
        return "SourceJavaClassNodeCollector";
    }
}
