package com.analyzer.dev.collectors;

import com.analyzer.api.inspector.BeanFactory;
import org.picocontainer.MutablePicoContainer;

public class CollectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(BinaryJavaClassNodeCollector.class);
        container.addComponent(SourceJavaClassNodeCollector.class);

    }
}
