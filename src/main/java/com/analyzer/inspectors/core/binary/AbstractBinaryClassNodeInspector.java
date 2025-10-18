package com.analyzer.inspectors.core.binary;

import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.inspector.InspectorResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

public abstract class AbstractBinaryClassNodeInspector extends AbstractBinaryClassInspector {

    @Autowired
    private GraphRepository graphRepository;

    @Override
    public InspectorResult inspect(ProjectFile projectFile) {
        String fqn = projectFile.getFqn();
        if (fqn != null) {
            Optional<JavaClassNode> classNodeOptional = graphRepository.findClassByFqn(fqn);
            if (classNodeOptional.isPresent()) {
                return inspect(projectFile, classNodeOptional.get());
            }
        }
        return null;
    }

    public abstract InspectorResult inspect(ProjectFile projectFile, JavaClassNode classNode);
}
