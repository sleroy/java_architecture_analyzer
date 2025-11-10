package com.analyzer.rules.graph;

import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.dev.inspectors.binary.AbstractASMClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.rules.std.ApplicationPackageTagInspector;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Binary inspector that builds coupling graph edges between JavaClassNode
 * instances.
 * This inspector analyzes bytecode to discover class dependencies and creates
 * directed edges in the GraphRepository representing different types of
 * coupling.
 *
 * <p>
 * Edge Types Created:
 * </p>
 * <ul>
 * <li><b>extends</b> - Inheritance relationship (class extends superclass)</li>
 * <li><b>implements</b> - Interface implementation</li>
 * <li><b>uses</b> - General usage dependency (fields, method signatures)</li>
 * </ul>
 *
 * <p>
 * This inspector requires JavaClassNode instances to exist before it runs,
 * so it should be executed after BinaryJavaClassNodeInspector or
 * BinaryJavaClassNodeInspectorV2.
 * </p>
 *
 * @author Java Architecture Analyzer
 * @since Class-Centric Architecture - Graph Phase
 */
@InspectorDependencies(need = ApplicationPackageTagInspector.class, produces = {
})
public class BinaryClassCouplingGraphInspector extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(BinaryClassCouplingGraphInspector.class);
    private final GraphRepository graphRepository;
    private final ClassNodeRepository classNodeRepository;

    @Inject
    public BinaryClassCouplingGraphInspector(
            final ProjectFileRepository projectFileRepository,
            final ResourceResolver resourceResolver,
            final GraphRepository graphRepository,
            final ClassNodeRepository classNodeRepository,
            final LocalCache localCache) {
        super(projectFileRepository, resourceResolver, localCache);
        this.graphRepository = graphRepository;
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    public String getName() {
        return "Binary Class Coupling Graph Inspector";
    }

    @Override
    protected AbstractASMClassInspector.ASMClassNodeVisitor createClassVisitor(
            final JavaClassNode classNode,
            final NodeDecorator<JavaClassNode> decorator) {
        return new ClassCouplingVisitor(classNode, decorator, graphRepository, classNodeRepository);
    }

    public enum TAGS {
        ;
        public static final String METRIC_CLASS_COUPLING_EDGES_CREATED = "java.class.coupling.edges.created";
    }

    /**
     * ASM visitor that analyzes class dependencies and creates graph edges.
     */
    private static class ClassCouplingVisitor extends AbstractASMClassInspector.ASMClassNodeVisitor {
        private final GraphRepository graphRepository;
        private final JavaClassNode sourceNode;
        private final ClassNodeRepository classNodeRepository1;
        private final Set<String> processedDependencies = new HashSet<>();
        private int edgeCount;

        protected ClassCouplingVisitor(
                final JavaClassNode classNode,
                final NodeDecorator<JavaClassNode> decorator,
                final GraphRepository graphRepository,
                final ClassNodeRepository classNodeRepository) {
            super(classNode, decorator);
            this.graphRepository = graphRepository;
            sourceNode = classNode;
            classNodeRepository1 = classNodeRepository;
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature,
                          final String superName, final String[] interfaces) {

            // Create edge for superclass (extends relationship)
            if (superName != null && !"java/lang/Object".equals(superName)) {
                final String superClassName = Type.getObjectType(superName).getClassName();
                createCouplingEdge(superClassName, "extends");
            }

            // Create edges for implemented interfaces
            if (interfaces != null) {
                for (final String interfaceName : interfaces) {
                    final String interfaceClassName = Type.getObjectType(interfaceName).getClassName();
                    createCouplingEdge(interfaceClassName, "implements");
                }
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(
                final int access, final String name, final String descriptor,
                final String signature, final Object value) {

            // Create edge for field type dependency
            final Type fieldType = Type.getType(descriptor);
            addUsageDependency(fieldType);

            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(
                final int access, final String name, final String descriptor,
                final String signature, final String[] exceptions) {

            // Create edges for method signature dependencies
            final Type methodType = Type.getMethodType(descriptor);

            // Return type dependency
            addUsageDependency(methodType.getReturnType());

            // Parameter type dependencies
            for (final Type paramType : methodType.getArgumentTypes()) {
                addUsageDependency(paramType);
            }

            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            // Record the number of edges created
            setMetric(TAGS.METRIC_CLASS_COUPLING_EDGES_CREATED, edgeCount);

            logger.debug("Created {} coupling edges for class: {}",
                    edgeCount, sourceNode.getFullyQualifiedName());

            super.visitEnd();
        }

        /**
         * Adds a usage dependency edge for a type, handling arrays and primitives.
         */
        private void addUsageDependency(Type type) {
            // Extract the base type from arrays
            while (type.getSort() == Type.ARRAY) {
                type = type.getElementType();
            }

            // Skip primitive types
            if (type.getSort() < Type.ARRAY) {
                return;
            }

            final String className = type.getClassName();
            createCouplingEdge(className, "uses");
        }

        /**
         * Creates a coupling edge between the source class and target class.
         * Filters out self-references and java.* package dependencies.
         * Ensures each dependency is only processed once.
         */
        private void createCouplingEdge(final String targetClassName, final String edgeType) {
            // Skip self-references
            if (targetClassName.equals(sourceNode.getFullyQualifiedName())) {
                return;
            }

            // Create unique key for this dependency
            final String dependencyKey = edgeType + ":" + targetClassName;
            if (processedDependencies.contains(dependencyKey)) {
                return; // Already processed
            }
            processedDependencies.add(dependencyKey);

            // Find or create the target class node

            final JavaClassNode targetNode = classNodeRepository1.getOrCreateByFqn(targetClassName);


            // Create the edge in the graph repository
            graphRepository.getOrCreateEdge(sourceNode, targetNode, edgeType);
            edgeCount++;

            logger.trace("Created {} edge: {} -> {}",
                    edgeType, sourceNode.getFullyQualifiedName(), targetClassName);

        }
    }
}
