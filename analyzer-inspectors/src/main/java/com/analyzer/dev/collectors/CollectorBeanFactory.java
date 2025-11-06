package com.analyzer.dev.collectors;

import com.analyzer.api.inspector.BeanFactory;
import com.analyzer.core.collector.JavaClassNodeBinaryCollector;
import com.analyzer.core.collector.JavaClassNodeSourceCollector;
import org.picocontainer.MutablePicoContainer;

public class CollectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(JavaClassNodeBinaryCollector.class);
        container.addComponent(JavaClassNodeSourceCollector.class);

    }
}
