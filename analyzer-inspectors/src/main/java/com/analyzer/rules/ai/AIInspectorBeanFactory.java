package com.analyzer.rules.ai;

import com.analyzer.api.inspector.BeanFactory;
import org.picocontainer.MutablePicoContainer;

/**
 * Bean factory for AI-powered inspectors.
 * Registers inspectors that use AWS Bedrock for code analysis.
 */
public class AIInspectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(BusinessVsFrameworkClassifierInspector.class);
    }
}
