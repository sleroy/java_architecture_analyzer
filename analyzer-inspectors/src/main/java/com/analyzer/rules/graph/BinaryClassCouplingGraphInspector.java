package com.analyzer.rules.graph;

import com.analyzer.api.graph.*;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.dev.inspectors.binary.AbstractASMClassInspector;
import com.analyzer.rules.graph.type.TypeInfo;
import com.analyzer.rules.graph.type.TypeParser;
import com.analyzer.rules.std.ApplicationPackageTagInspector;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
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
 * <li><b>annotated_with</b> - Annotation usage (class, field, method
 * annotations)</li>
 * <li><b>type_parameter</b> - Generic type parameter usage</li>
 * <li><b>throws</b> - Exception type declared in throws clause</li>
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

    public static final String EDGE_USES = "uses";
    public static final String EDGE_EXTENDS = "extends";
    public static final String EDGE_IMPLEMENTS = "implements";
    public static final String EDGE_ANNOTATED_WITH = "annotated_with";
    public static final String EDGE_TYPE_PARAMETER = "type_parameter";
    public static final String EDGE_THROWS = "throws";

    // Edge property names
    public static final String PROP_RELATIONSHIP_KIND = "relationshipKind";
    public static final String PROP_CONTAINER_TYPE = "containerType";
    public static final String PROP_TYPE_ARGUMENT_INDEX = "typeArgumentIndex";
    public static final String PROP_WILDCARD_KIND = "wildcardKind";

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
                final java.util.Map<String, Object> props = new java.util.HashMap<>();
                props.put(PROP_RELATIONSHIP_KIND, EDGE_EXTENDS);
                createCouplingEdge(superClassName, EDGE_USES, props);
            }

            // Create edges for implemented interfaces
            if (interfaces != null) {
                for (final String interfaceName : interfaces) {
                    final String interfaceClassName = Type.getObjectType(interfaceName).getClassName();
                    final java.util.Map<String, Object> props = new java.util.HashMap<>();
                    props.put(PROP_RELATIONSHIP_KIND, EDGE_IMPLEMENTS);
                    createCouplingEdge(interfaceClassName, EDGE_USES, props);
                }
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            // Create edge for class-level annotation
            final Type annotationType = Type.getType(descriptor);
            final String annotationClassName = annotationType.getClassName();
            final java.util.Map<String, Object> props = new java.util.HashMap<>();
            props.put(PROP_RELATIONSHIP_KIND, EDGE_ANNOTATED_WITH);
            createCouplingEdge(annotationClassName, EDGE_USES, props);

            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public FieldVisitor visitField(
                final int access, final String name, final String descriptor,
                final String signature, final Object value) {

            // Parse complete type information using unified parser
            final TypeInfo fieldType = TypeParser.parseType(descriptor, signature);
            processTypeInfo(fieldType, null, -1);

            // Return custom field visitor to capture field annotations
            final FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
            return new FieldVisitor(api, fv) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    final Type annotationType = Type.getType(desc);
                    final String annotationClassName = annotationType.getClassName();
                    final java.util.Map<String, Object> props = new java.util.HashMap<>();
                    props.put(PROP_RELATIONSHIP_KIND, EDGE_ANNOTATED_WITH);
                    createCouplingEdge(annotationClassName, EDGE_USES, props);
                    return super.visitAnnotation(desc, visible);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                final int access, final String name, final String descriptor,
                final String signature, final String[] exceptions) {

            // Parse complete method signature using unified parser
            final List<TypeInfo> methodTypes = TypeParser.parseMethodSignature(descriptor, signature);
            for (final TypeInfo type : methodTypes) {
                processTypeInfo(type, null, -1);
            }

            // Create edges for declared exceptions
            if (exceptions != null) {
                for (final String exceptionInternalName : exceptions) {
                    final String exceptionClassName = Type.getObjectType(exceptionInternalName).getClassName();
                    final java.util.Map<String, Object> props = new java.util.HashMap<>();
                    props.put(PROP_RELATIONSHIP_KIND, EDGE_THROWS);
                    createCouplingEdge(exceptionClassName, EDGE_USES, props);
                }
            }

            // Return custom method visitor to capture method annotations
            final MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(api, mv) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    final Type annotationType = Type.getType(desc);
                    final String annotationClassName = annotationType.getClassName();
                    final java.util.Map<String, Object> props = new java.util.HashMap<>();
                    props.put(PROP_RELATIONSHIP_KIND, EDGE_ANNOTATED_WITH);
                    createCouplingEdge(annotationClassName, EDGE_USES, props);
                    return super.visitAnnotation(desc, visible);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc,
                                                                  final boolean visible) {
                    final Type annotationType = Type.getType(desc);
                    final String annotationClassName = annotationType.getClassName();
                    final java.util.Map<String, Object> props = new java.util.HashMap<>();
                    props.put(PROP_RELATIONSHIP_KIND, EDGE_ANNOTATED_WITH);
                    createCouplingEdge(annotationClassName, EDGE_USES, props);
                    return super.visitParameterAnnotation(parameter, desc, visible);
                }
            };
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
         * Processes a TypeInfo object recursively, creating edges for all types
         * involved.
         *
         * @param typeInfo      the type information to process
         * @param containerType the container type (for type arguments), or null
         * @param argIndex      the type argument index, or -1 if not a type argument
         */
        private void processTypeInfo(final TypeInfo typeInfo, final String containerType, final int argIndex) {
            if (typeInfo == null) {
                return;
            }

            final String className = typeInfo.getClassName();
            final TypeInfo.TypeKind kind = typeInfo.getKind();

            // Skip primitives and void
            if (kind == TypeInfo.TypeKind.PRIMITIVE) {
                return;
            }

            // Process based on type kind
            switch (kind) {
                case CLASS:
                    // Create "uses" edge for the class itself
                    final java.util.Map<String, Object> classProps = new java.util.HashMap<>();
                    if (containerType != null) {
                        classProps.put(PROP_CONTAINER_TYPE, containerType);
                        classProps.put(PROP_TYPE_ARGUMENT_INDEX, argIndex);
                    }
                    createCouplingEdge(className, EDGE_USES, classProps);

                    // Process type arguments
                    final List<TypeInfo> typeArgs = typeInfo.getTypeArguments();
                    for (int i = 0; i < typeArgs.size(); i++) {
                        processTypeArgument(typeArgs.get(i), className, i);
                    }
                    break;

                case ARRAY:
                    // Process array component type
                    processTypeInfo(typeInfo.getArrayComponentType(), containerType, argIndex);
                    break;

                case WILDCARD:
                    // Process wildcard bound if present
                    final TypeInfo bound = typeInfo.getWildcardBound();
                    if (bound != null) {
                        // Create edge with wildcard information
                        processTypeInfo(bound, containerType, argIndex);
                    }
                    break;

                case TYPE_VARIABLE:
                    // Type variables reference their bounds
                    if (className != null) {
                        final java.util.Map<String, Object> tvProps = new java.util.HashMap<>();
                        tvProps.put(PROP_RELATIONSHIP_KIND, "type_variable");
                        createCouplingEdge(className, EDGE_USES, tvProps);
                    }
                    break;
            }
        }

        /**
         * Processes a type argument, creating appropriate edges with relationship
         * metadata.
         */
        private void processTypeArgument(final TypeInfo typeArg, final String containerType, final int argIndex) {
            if (typeArg == null || typeArg.getKind() == TypeInfo.TypeKind.PRIMITIVE) {
                return;
            }

            final String className = typeArg.getClassName();

            if (className != null && !"?".equals(className)) {
                // Create uses edge with type parameter metadata
                final java.util.Map<String, Object> properties = new java.util.HashMap<>();
                properties.put(PROP_RELATIONSHIP_KIND, EDGE_TYPE_PARAMETER);
                properties.put(PROP_CONTAINER_TYPE, containerType);
                properties.put(PROP_TYPE_ARGUMENT_INDEX, argIndex);

                // Add wildcard information if present
                if (typeArg.getKind() == TypeInfo.TypeKind.WILDCARD && typeArg.getWildcardBound() != null) {
                    properties.put(PROP_WILDCARD_KIND,
                            typeArg.getWildcardKind() == TypeInfo.WildcardKind.EXTENDS ? "extends" : "super");
                }

                createCouplingEdge(className, EDGE_USES, properties);
            }

            // Recursively process nested type arguments
            for (int i = 0; i < typeArg.getTypeArguments().size(); i++) {
                processTypeArgument(typeArg.getTypeArguments().get(i), className, i);
            }
        }

        /**
         * Creates a coupling edge between the source class and target class.
         * Filters out self-references.
         * Ensures each dependency is only processed once.
         *
         * @param targetClassName the target class name
         * @param edgeType        the edge type
         * @param properties      additional edge properties (may be null)
         */
        private void createCouplingEdge(final String targetClassName, final String edgeType,
                                        final java.util.Map<String, Object> properties) {
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
            final GraphEdge edge = graphRepository.getOrCreateEdge(sourceNode, targetNode, edgeType);

            // Add properties if provided
            if (properties != null && !properties.isEmpty()) {
                for (final java.util.Map.Entry<String, Object> entry : properties.entrySet()) {
                    edge.setProperty(entry.getKey(), entry.getValue());
                }
            }

            edgeCount++;

            logger.trace("Created {} edge: {} -> {} (properties: {})",
                    edgeType, sourceNode.getFullyQualifiedName(), targetClassName,
                    properties != null ? properties.size() : 0);
        }
    }
}
