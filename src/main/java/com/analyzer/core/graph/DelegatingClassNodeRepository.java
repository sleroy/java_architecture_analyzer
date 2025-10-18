package com.analyzer.core.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import javax.inject.Inject;
import java.util.Optional;

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
    public Optional<JavaClassNode> getOrCreateClassNodeByFqn(String fqn) {
        return Optional.of((JavaClassNode) graphRepository.getOrCreateNode(new JavaClassNode(fqn)));
    }
}
