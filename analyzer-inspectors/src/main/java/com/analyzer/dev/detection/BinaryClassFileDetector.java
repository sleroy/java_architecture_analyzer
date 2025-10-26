package com.analyzer.dev.detection;

import com.analyzer.api.detector.FileDetector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.model.ProjectFile;

import javax.inject.Inject;

import static com.analyzer.core.inspector.InspectorTags.*;

public class BinaryClassFileDetector implements FileDetector {
    @Inject
    public BinaryClassFileDetector() {

    }

    @Override
    public void detect(NodeDecorator<ProjectFile> decorator) {
        ProjectFile projectFile = decorator.getNode();
// Set standardized Java language tags using own TAGS constants
        decorator.enableTag(TAG_JAVA_IS_BINARY);
        decorator.enableTag(TAG_JAVA_DETECTED);
        decorator.setProperty(TAG_JAVA_FORMAT, FORMAT_BINARY);
        decorator.setProperty(TAG_LANGUAGE, LANGUAGE_JAVA);
    }

    public boolean supports(ProjectFile projectFile) {
        // Focus on file format validation only - dependencies are handled by
        // canProcess()
        return projectFile != null &&
                (projectFile.hasFileExtension("class") ||
                        projectFile.getFilePath().toString().endsWith(".class"));
    }

    @Override
    public String getName() {
        return "Java Binary class detector";
    }
}
