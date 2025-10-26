package com.analyzer.inspectors.core.detection;

import com.analyzer.core.inspector.BeanFactory;
import com.analyzer.rules.std.JavaSourceFileInspector;
import org.picocontainer.MutablePicoContainer;

public class FileDetectionBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {

        // Add core file detection inspectors
        container.addComponent("java-detector", FileExtensionDetector.createJavaInspector());
        container.addComponent("config-detector", FileExtensionDetector.createConfigInspector());
        container.addComponent("binary-detector", FileExtensionDetector.createBinaryInspector());
        container.addComponent("xml-inspector", FileExtensionDetector.createXmlInspector());

        // Add filename-based inspectors
        container.addComponent("buildfile-detector", FilenameDetector.createBuildFileInspector());
        container.addComponent("readme-detector", FilenameDetector.createReadmeInspector());
        container.addComponent("filename-detector", FilenameDetector.createDockerInspector());

        // Binary class detector
        container.addComponent(SourceFileDetector.class);
        container.addComponent(BinaryClassFileDetector.class);
    }
}
