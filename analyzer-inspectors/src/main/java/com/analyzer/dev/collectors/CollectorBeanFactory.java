package com.analyzer.dev.collectors;

import com.analyzer.api.inspector.BeanFactory;
import com.analyzer.core.collector.JavaClassNodeBinaryCollector;
import com.analyzer.core.collector.JavaClassNodeSourceCollector;
import com.analyzer.core.collector.PackageNodeCache;
import org.picocontainer.MutablePicoContainer;

public class CollectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        // Register collectors
        container.addComponent(JavaClassNodeBinaryCollector.class);
        container.addComponent(JavaClassNodeSourceCollector.class);

        // Register shared cache for PackageNode creation
        container.addComponent(PackageNodeCache.class);
    }
}
