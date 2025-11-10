package com.analyzer.rules.metrics;

import com.analyzer.api.inspector.BeanFactory;
import org.picocontainer.MutablePicoContainer;

public class MetricsInspectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(AnnotationCountInspector.class);
        container.addComponent(ClassMetricsInspectorV2.class);
        container.addComponent(ClocInspector.class);
        container.addComponent(CodeQualityInspector.class);
        container.addComponent(CyclomaticComplexityInspector.class);
        container.addComponent(FileMetricsInspector.class);
        container.addComponent(InheritanceDepthInspector.class);
        container.addComponent(InterfaceNumberInspector.class);
        container.addComponent(MethodCountInspectorV2.class);
        container.addComponent(ThreadLocalUsageInspector.class);
        container.addComponent(TypeUsageInspector.class);

        container.addComponent(CouplingMetricsInspector.class);
        container.addComponent(PackageCouplingMetricsInspector.class);
    }
}
