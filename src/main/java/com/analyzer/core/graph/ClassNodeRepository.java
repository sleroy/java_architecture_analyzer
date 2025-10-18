package com.analyzer.core.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.Optional;

/**
 * Repository for creating and retrieving JavaClassNode instances.
 * This provides a specialized interface for inspectors that work with Java classes.
 */
public interface ClassNodeRepository {

    /**
     * Gets or creates a JavaClassNode based on a TypeDeclaration.
     *
     * @param typeDeclaration the TypeDeclaration from the parsed source file.
     * @return the existing or newly created JavaClassNode.
     */
    Optional<JavaClassNode> getOrCreateClassNode(TypeDeclaration<?> typeDeclaration);

    /**
     * Gets or creates a JavaClassNode based on a CompilationUnit's primary type.
     *
     * @param compilationUnit the CompilationUnit from the parsed source file.
     * @return the existing or newly created JavaClassNode if a primary type is present.
     */
    Optional<JavaClassNode> getOrCreateClassNode(CompilationUnit compilationUnit);

    /**
     * Gets or creates a JavaClassNode based on a fully qualified name.
     *
     * @param fqn the fully qualified name of the class.
     * @return the existing or newly created JavaClassNode.
     */
    Optional<JavaClassNode> getOrCreateClassNodeByFqn(String fqn);
}
