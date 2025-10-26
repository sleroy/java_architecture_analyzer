package com.analyzer.api.inspector;

import org.picocontainer.MutablePicoContainer;

/**
 * This interface describes a capability to add some beans to the analysis container.
 */
@FunctionalInterface
public interface BeanFactory {

    void registerBeans(MutablePicoContainer container);
}
