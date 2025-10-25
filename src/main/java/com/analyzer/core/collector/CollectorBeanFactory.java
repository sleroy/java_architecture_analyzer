package com.analyzer.core.collector;

import com.analyzer.core.inspector.BeanFactory;
import com.analyzer.inspectors.core.collectors.BinaryJavaClassNodeCollector;
import com.analyzer.inspectors.core.collectors.SourceJavaClassNodeCollector;
import org.picocontainer.MutablePicoContainer;

public class CollectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(BinaryJavaClassNodeCollector.class);
        container.addComponent(SourceJavaClassNodeCollector.class);

    }
}
