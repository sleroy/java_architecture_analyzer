package com.analyzer.rules.ejb2spring;

import com.analyzer.api.inspector.BeanFactory;
import org.picocontainer.MutablePicoContainer;

public class Ejb2SpringInspectorBeanFactory implements BeanFactory {
    @Override
    public void registerBeans(MutablePicoContainer container) {
        container.addComponent(ApplicationServerConfigDetector.class);
        container.addComponent(BusinessDelegatePatternJavaSourceInspector.class);
        container.addComponent(CacheSingletonInspector.class);
        container.addComponent(CmpFieldMappingJavaBinaryInspector.class);
        container.addComponent(ComplexCmpRelationshipJavaSourceInspector.class);
        container.addComponent(ConfigurationConstantsInspector.class);
        container.addComponent(CustomDataTransferPatternJavaSourceInspector.class);
        container.addComponent(DaoRepositoryInspector.class);
        container.addComponent(DatabaseResourceManagementInspector.class);
        container.addComponent(EjbBinaryClassInspector.class);
        container.addComponent(EjbClassLoaderInspector.class);
        container.addComponent(EjbCreateMethodUsageInspector.class);
        container.addComponent(EjbDeploymentDescriptorDetector.class);
        container.addComponent(EjbDeploymentDescriptorInspector.class);
        container.addComponent(EjbHomeInterfaceInspector.class);
        container.addComponent(EjbRemoteInterfaceInspector.class);
        container.addComponent(EntityBeanJavaSourceInspector.class);
        container.addComponent(FactoryBeanProviderInspector.class);
        container.addComponent(FormBeanDtoInspector.class);
        container.addComponent(IdentifyServletSourceInspector.class);
        container.addComponent(InterceptorAopInspector.class);
        container.addComponent(JBossEjbConfigurationInspector.class);
        container.addComponent(JdbcDataAccessPatternInspector.class);
        container.addComponent(JndiLookupInspector.class);
        container.addComponent(LegacyFrameworkDetector.class);
        container.addComponent(MessageDrivenBeanInspector.class);
        container.addComponent(MutableServiceInspector.class);
        container.addComponent(SecurityFacadeInspector.class);
        container.addComponent(ServiceLocatorInspector.class);
        container.addComponent(ServletInspector.class);
        container.addComponent(SessionBeanJavaSourceInspector.class);
        container.addComponent(StatefulSessionStateInspector.class);
        container.addComponent(TimerBeanInspector.class);
        container.addComponent(TransactionScriptInspector.class);
        container.addComponent(UtilityHelperInspector.class);
    }
}
