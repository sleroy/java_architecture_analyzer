package com.analyzer.core.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of ClassNodeRepository that delegates to a GraphRepository.
 */
public class DelegatingClassNodeRepository implements ClassNodeRepository {

    private final GraphRepository graphRepository;

    @Inject
    public DelegatingClassNodeRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public Optional<JavaClassNode> getOrCreateClassNode(TypeDeclaration<?> typeDeclaration) {
        return typeDeclaration.getFullyQualifiedName()
                .map(fqn -> (JavaClassNode) graphRepository.getOrCreateNode(new JavaClassNode(fqn)));
    }

    @Override
    public Optional<JavaClassNode> getOrCreateClassNode(CompilationUnit compilationUnit) {
        return compilationUnit.getPrimaryType()
                .flatMap(this::getOrCreateClassNode);
    }

    @Override
    public JavaClassNode getOrCreateByFqn(String fqn) {
        return (JavaClassNode) graphRepository.getOrCreateNode(new JavaClassNode(fqn));
    }

    @Override
    public List<JavaClassNode> findByPackage(String packageName) {
        return graphRepository.getNodesByClass(JavaClassNode.class).stream()
                .filter(classNode -> packageName.equals(classNode.getPackageName()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<JavaClassNode> findById(String id) {
        return graphRepository.getNodeById(id)
                .filter(node -> node instanceof JavaClassNode)
                .map(node -> (JavaClassNode) node);
    }

    @Override
    public JavaClassNode getOrCreate(String id) {
        return (JavaClassNode) graphRepository.getOrCreateNode(new JavaClassNode(id));
    }

    @Override
    public List<JavaClassNode> findAll() {
        return graphRepository.getNodesByClass(JavaClassNode.class).stream()
                .collect(Collectors.toList());
    }

    @Override
    public List<JavaClassNode> findByTag(String tag) {
        return graphRepository.getNodesByClass(JavaClassNode.class).stream()
                .filter(classNode -> classNode.hasTag(tag))
                .collect(Collectors.toList());
    }

    @Override
    public void save(JavaClassNode node) {
        graphRepository.addNode(node);
    }
}
