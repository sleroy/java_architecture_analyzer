package com.analyzer.inspectors.core.detection;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractBinaryClassInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;

import static com.analyzer.core.inspector.InspectorTags.*;

@InspectorDependencies(requires = {}, produces = {
        TAG_JAVA_IS_BINARY,
        TAG_JAVA_FORMAT,
        TAG_LANGUAGE,
        TAG_JAVA_DETECTED
})
public class BinaryClassDetector extends AbstractBinaryClassInspector {
    /**
     * Creates a new AbstractBinaryClassInspector with the specified ResourceResolver.
     *
     * @param resourceResolver the resolver for accessing class file resources
     */
    @Inject
    public BinaryClassDetector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected void analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation, InputStream classInputStream, ProjectFileDecorator projectFileDecorator) throws IOException {
        // Set standardized Java language tags using own TAGS constants
        projectFile.setTag(TAG_JAVA_IS_BINARY, true);
        projectFile.setTag(TAG_JAVA_FORMAT, FORMAT_BINARY);
        projectFile.setTag(TAG_LANGUAGE, LANGUAGE_JAVA);
        projectFile.setTag(TAG_JAVA_DETECTED, true);

    }

    @Override
    public String getName() {
        return "Java Binary class detector";
    }
}
