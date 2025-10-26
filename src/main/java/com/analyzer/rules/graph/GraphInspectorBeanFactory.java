package com.analyzer.rules.graph;

import com.analyzer.core.inspector.BeanFactory;
import org.picocontainer.MutablePicoContainer;

public class GraphInspectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(BinaryJavaClassNodeInspectorV2.class);
        container.addComponent(BinaryClassCouplingGraphInspector.class);
        container.addComponent(JavaImportGraphInspector.class);
        container.addComponent(SourceJavaClassNodeInspector.class);
    }
}
