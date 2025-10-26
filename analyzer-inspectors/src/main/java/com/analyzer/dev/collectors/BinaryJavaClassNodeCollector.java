package com.analyzer.dev.collectors;

import com.analyzer.core.collector.AbstractBinaryClassCollector;
import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.collector.Collector;
import com.analyzer.api.resource.ResourceResolver;

import javax.inject.Inject;

/**
 * Collector that creates JavaClassNode objects from binary .class files.
 * <p>
 * This collector is responsible for the <strong>creation</strong> of JavaClassNode
 * objects during Phase 2 (ClassNode Collection) of the analysis pipeline. It reads
 * compiled Java bytecode (.class files) using ASM and extracts the fully-qualified
 * class name (FQN) to create node instances.
 * <p>
 * <strong>Architectural Note - Separation of Concerns:</strong>
 * <ul>
 *   <li><strong>This Collector (Phase 2):</strong> CREATES JavaClassNode objects from .class files</li>
 *   <li><strong>BinaryJavaClassNodeInspectorV2 (Phase 4):</strong> ANALYZES existing JavaClassNode objects</li>
 * </ul>
 * This clear separation follows the Collector Architecture Refactoring pattern where:
 * <ul>
 *   <li><strong>Collectors:</strong> Create nodes (e.g., {@code Collector<ProjectFile, JavaClassNode>})</li>
 *   <li><strong>Inspectors:</strong> Analyze nodes (e.g., {@code Inspector<JavaClassNode>})</li>
 * </ul>
 * <p>
 * <strong>Implementation Details:</strong>
 * <p>
 * This collector extends {@link AbstractBinaryClassCollector} which provides all the
 * core functionality:
 * <ul>
 *   <li>File validation (.class file checks)</li>
 *   <li>Bytecode reading via ASM ClassReader</li>
 *   <li>FQN extraction from bytecode</li>
 *   <li>JavaClassNode creation and initialization</li>
 *   <li>Linking nodes to source ProjectFiles</li>
 *   <li>Repository storage via CollectionContext</li>
 *   <li>Error handling and logging</li>
 * </ul>
 * <p>
 * <strong>Usage in Analysis Pipeline:</strong>
 * <pre>
 * Phase 1: File Discovery
 *   └── Scan filesystem, create ProjectFile objects
 * 
 * Phase 2: ClassNode Collection ← THIS COLLECTOR
 *   └── BinaryJavaClassNodeCollector creates JavaClassNode from .class files
 * 
 * Phase 3: Multi-Pass ProjectFile Analysis
 *   └── Inspectors analyze ProjectFiles until convergence
 * 
 * Phase 4: Multi-Pass ClassNode Analysis
 *   └── BinaryJavaClassNodeInspectorV2 analyzes JavaClassNode objects
 * </pre>
 * <p>
 * <strong>Migration History:</strong>
 * <p>
 * This collector was created as part of the Collector Architecture Refactoring
 * (Phase 3, Step 3.1) to properly separate node creation from node analysis.
 * Previously, the old BinaryJavaClassNodeInspector performed both creation and
 * analysis, which violated the single responsibility principle and made the
 * architecture harder to understand.
 * <p>
 * After this refactoring:
 * <ul>
 *   <li>Node CREATION moved to: {@code BinaryJavaClassNodeCollector} (this class)</li>
 *   <li>Node ANALYSIS moved to: {@code BinaryJavaClassNodeInspectorV2}</li>
 * </ul>
 *
 * @see AbstractBinaryClassCollector
 * @see ClassNodeCollector
 * @see Collector
 * @see com.analyzer.rules.graph.BinaryJavaClassNodeInspectorV2
 * @author Phase 3 - Collector Architecture Refactoring
 * @since 1.3.1 - Collector/Inspector Separation
 */
public class BinaryJavaClassNodeCollector extends AbstractBinaryClassCollector {

    /**
     * Constructs a new BinaryJavaClassNodeCollector.
     * <p>
     * The ResourceResolver is required for reading .class file contents from
     * the filesystem or JAR archives.
     *
     * @param resourceResolver resolver for accessing file content
     */
    @Inject
    public BinaryJavaClassNodeCollector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    /**
     * Returns the name of this collector.
     * <p>
     * This name is used for:
     * <ul>
     *   <li>Logging and debugging</li>
     *   <li>Registry identification</li>
     *   <li>Execution profiling</li>
     *   <li>Error reporting</li>
     * </ul>
     *
     * @return the collector name
     */
    @Override
    public String getName() {
        return "BinaryJavaClassNodeCollector";
    }
}
