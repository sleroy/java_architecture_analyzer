package com.analyzer.rules.std;

import com.analyzer.api.inspector.BeanFactory;
import org.picocontainer.MutablePicoContainer;

public class StdInspectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(BinaryClassFQNInspectorV2.class);
        container.addComponent(JavaSourceVersionInspector.class);
        container.addComponent(TypeInspectorASMInspectorV2.class);
        container.addComponent(JavaSourceFileInspector.class);
        container.addComponent(ApplicationPackageTagInspector.class);
    }
}
