package com.analyzer.dev.inspectors.binary;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractBinaryClassNodeInspector extends AbstractBinaryClassInspector {

    protected ClassNodeRepository classNodeRepository;

    protected AbstractBinaryClassNodeInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    protected void analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation,
            InputStream classInputStream, NodeDecorator<ProjectFile> projectFileDecorator) throws IOException {
        String fqn = projectFile
                .getStringProperty(com.analyzer.core.inspector.InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME);
        if (fqn != null) {
            JavaClassNode classNode = classNodeRepository.getOrCreateByFqn(fqn);
            analyzeClassNode(projectFile, classNode, binaryLocation, classInputStream, projectFileDecorator);
        }
    }

    public abstract void analyzeClassNode(ProjectFile projectFile, JavaClassNode classNode,
            ResourceLocation binaryLocation,
            InputStream classInputStream, NodeDecorator<ProjectFile> projectFileDecorator) throws IOException;
}
