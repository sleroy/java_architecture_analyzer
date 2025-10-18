package com.analyzer.inspectors.core.binary;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public abstract class AbstractBinaryClassNodeInspector extends AbstractBinaryClassInspector {

    protected ClassNodeRepository classNodeRepository;

    protected AbstractBinaryClassNodeInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    protected void analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation,
                                    InputStream classInputStream, ProjectFileDecorator projectFileDecorator) throws IOException {
        String fqn = projectFile.getFullyQualifiedName();
        if (fqn != null) {
            Optional<JavaClassNode> classNodeOptional = classNodeRepository.getOrCreateClassNodeByFqn(fqn);
            if (classNodeOptional.isPresent()) {
                analyzeClassNode(projectFile, classNodeOptional.get(), binaryLocation, classInputStream, projectFileDecorator);
            }
        }
    }

    public abstract void analyzeClassNode(ProjectFile projectFile, JavaClassNode classNode, ResourceLocation binaryLocation,
                                          InputStream classInputStream, ProjectFileDecorator projectFileDecorator) throws IOException;
}
