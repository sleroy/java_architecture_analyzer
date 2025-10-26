package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.core.graph.DelegatingClassNodeRepository;
import com.analyzer.core.graph.InMemoryGraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.FileResourceResolver;
import com.analyzer.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the EJB2 to Spring Boot migration stereotype inspectors.
 * Tests the following inspectors using sample Java files:
 * - FactoryBeanProviderInspector (CS-060)
 * - CacheSingletonInspector (CS-061)
 * - InterceptorAopInspector (CS-062)
 * - ConfigurationConstantsInspector (CS-063)
 * - MutableServiceInspector (CS-070)
 */
public class EjbStereotypeInspectorsTest {

    private static final Logger logger = LoggerFactory.getLogger(EjbStereotypeInspectorsTest.class);

    private ResourceResolver resourceResolver;
    private ClassNodeRepository classNodeRepository;
    
    // Inspectors under test
    private FactoryBeanProviderInspector factoryBeanProviderInspector;
    private CacheSingletonInspector cacheSingletonInspector;
    private InterceptorAopInspector interceptorAopInspector;
    private ConfigurationConstantsInspector configurationConstantsInspector;
    private MutableServiceInspector mutableServiceInspector;

    @BeforeEach
    void setUp() {
        // Use FileResourceResolver to actually read test files from disk
        resourceResolver = new FileResourceResolver();
        InMemoryGraphRepository graphRepo = new InMemoryGraphRepository();
        classNodeRepository = new DelegatingClassNodeRepository(graphRepo);
        
        // Initialize inspectors
        factoryBeanProviderInspector = new FactoryBeanProviderInspector(resourceResolver, classNodeRepository);
        cacheSingletonInspector = new CacheSingletonInspector(resourceResolver, classNodeRepository);
        interceptorAopInspector = new InterceptorAopInspector(resourceResolver, classNodeRepository);
        configurationConstantsInspector = new ConfigurationConstantsInspector(resourceResolver, classNodeRepository);
        mutableServiceInspector = new MutableServiceInspector(resourceResolver, classNodeRepository);
    }

    @Test
    void testFactoryBeanProviderInspector() throws IOException {
        // Load the test file
        Path testFile = Paths.get("src/test/resources/test_samples/factory_bean/ProductFactory.java");
        Path projectRoot = Paths.get("src/test/resources/test_samples");
        
        // Create project file
        ProjectFile projectFile = new ProjectFile(testFile, projectRoot);
        projectFile.addTag(InspectorTags.TAG_JAVA_IS_SOURCE);
        projectFile.addTag(InspectorTags.TAG_JAVA_DETECTED);
        projectFile.addTag(InspectorTags.TAG_SOURCE_FILE);
        
        // Create decorator
        ProjectFileDecorator decorator = new ProjectFileDecorator(projectFile);
        
        // Apply inspector
        factoryBeanProviderInspector.inspect(projectFile, decorator);
        
        // Verify tags on the underlying ProjectFile
        JavaClassNode classNode = classNodeRepository.getOrCreateByFqn("com.example.factory_bean.ProductFactory");
        assertTrue(classNode.hasTag(EjbMigrationTags.SPRING_CONFIG_CONVERSION),
                "Factory bean should be tagged for Spring config conversion");
        assertTrue(classNode.hasTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION),
                "Factory bean should be tagged for Spring component conversion");
        assertTrue(classNode.hasTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM),
                "Factory bean should be tagged with medium migration complexity");
                
        logger.info("FactoryBeanProviderInspector test passed");
    }

    @Test
    void testCacheSingletonInspector() throws IOException {
        // Load the test file
        Path testFile = Paths.get("src/test/resources/test_samples/cache_singleton/UserRegistry.java");
        Path projectRoot = Paths.get("src/test/resources/test_samples");
        
        // Create project file
        ProjectFile projectFile = new ProjectFile(testFile, projectRoot);
        projectFile.addTag(InspectorTags.TAG_JAVA_IS_SOURCE);
        projectFile.addTag(InspectorTags.TAG_JAVA_DETECTED);
        projectFile.addTag(InspectorTags.TAG_SOURCE_FILE);
        
        // Create decorator
        ProjectFileDecorator decorator = new ProjectFileDecorator(projectFile);
        
        // Apply inspector
        cacheSingletonInspector.inspect(projectFile, decorator);
        
        // Verify tags
        JavaClassNode classNode = classNodeRepository.getOrCreateByFqn("com.example.cache_singleton.UserRegistry");
        assertTrue(classNode.hasTag(EjbMigrationTags.EJB_CACHING_PATTERN),
                "Cache singleton should be tagged with EJB_CACHING_PATTERN");
        assertTrue(classNode.hasTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION),
                "Cache singleton should be tagged for Spring component conversion");
        assertTrue(classNode.hasTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM),
                "Cache singleton should be tagged with medium migration complexity");
        
        // Check for double-checked locking detection
        assertTrue(classNode.hasTag("cache.singleton.has_dcl"),
                "Double-checked locking pattern should be detected");
        
        logger.info("CacheSingletonInspector test passed");
    }
    
    @Test
    void testInterceptorAopInspector() throws IOException {
        // Load the test file
        Path testFile = Paths.get("src/test/resources/test_samples/interceptor_aop/LoggingInterceptor.java");
        Path projectRoot = Paths.get("src/test/resources/test_samples");
        
        // Create project file
        ProjectFile projectFile = new ProjectFile(testFile, projectRoot);
        projectFile.addTag(InspectorTags.TAG_JAVA_IS_SOURCE);
        projectFile.addTag(InspectorTags.TAG_JAVA_DETECTED);
        projectFile.addTag(InspectorTags.TAG_SOURCE_FILE);
        
        // Create decorator
        ProjectFileDecorator decorator = new ProjectFileDecorator(projectFile);
        
        // Apply inspector
        interceptorAopInspector.inspect(projectFile, decorator);
        
        // Verify tags
        JavaClassNode classNode = classNodeRepository.getOrCreateByFqn("com.example.interceptor_aop.LoggingInterceptor");
        assertTrue(classNode.hasTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION),
                "Interceptor should be tagged for Spring component conversion");
        assertTrue(classNode.hasTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM),
                "Interceptor should be tagged with medium migration complexity");
        assertTrue(classNode.hasTag(EjbMigrationTags.CODE_MODERNIZATION),
                "Interceptor should be tagged for code modernization");
        
        // Check for specific AOP detection
        assertTrue(classNode.hasTag("aop.interceptor.detected"),
                "Interceptor should be detected");
        assertTrue(classNode.hasTag("spring.target.aspect"),
                "Interceptor should be tagged as Spring aspect candidate");
        assertTrue(classNode.hasTag("aop.around_advice_candidate"),
                "Interceptor should be tagged as around advice candidate");
        
        logger.info("InterceptorAopInspector test passed");
    }
    
    @Test
    void testConfigurationConstantsInspector() throws IOException {
        // Load the test file
        Path testFile = Paths.get("src/test/resources/test_samples/config_constants/AppConfig.java");
        Path projectRoot = Paths.get("src/test/resources/test_samples");
        
        // Create project file
        ProjectFile projectFile = new ProjectFile(testFile, projectRoot);
        projectFile.addTag(InspectorTags.TAG_JAVA_IS_SOURCE);
        projectFile.addTag(InspectorTags.TAG_JAVA_DETECTED);
        projectFile.addTag(InspectorTags.TAG_SOURCE_FILE);
        
        // Create decorator
        ProjectFileDecorator decorator = new ProjectFileDecorator(projectFile);
        
        // Apply inspector
        configurationConstantsInspector.inspect(projectFile, decorator);
        
        // Verify tags
        JavaClassNode classNode = classNodeRepository.getOrCreateByFqn("com.example.config_constants.AppConfig");
        assertTrue(classNode.hasTag(EjbMigrationTags.SPRING_CONFIG_CONVERSION),
                "Config constants should be tagged for Spring config conversion");
        assertTrue(classNode.hasTag(EjbMigrationTags.CODE_MODERNIZATION),
                "Config constants should be tagged for code modernization");
        assertTrue(classNode.hasTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW),
                "Config constants should be tagged with low migration complexity");
        
        // Check for specific config detection
        assertTrue(classNode.hasTag("config.constants.detected"),
                "Config constants should be detected");
        assertTrue(classNode.hasTag("spring.target.application_yml"),
                "Config constants should be tagged as application.yml candidate");
        assertTrue(classNode.hasTag("config.string_heavy"),
                "Config constants should be tagged as string heavy");
        
        logger.info("ConfigurationConstantsInspector test passed");
    }
    
    @Test
    void testMutableServiceInspector() throws IOException {
        // Load the test file
        Path testFile = Paths.get("src/test/resources/test_samples/mutable_service/UserService.java");
        Path projectRoot = Paths.get("src/test/resources/test_samples");
        
        // Create project file
        ProjectFile projectFile = new ProjectFile(testFile, projectRoot);
        projectFile.addTag(InspectorTags.TAG_JAVA_IS_SOURCE);
        projectFile.addTag(InspectorTags.TAG_JAVA_DETECTED);
        projectFile.addTag(InspectorTags.TAG_SOURCE_FILE);
        
        // Create decorator
        ProjectFileDecorator decorator = new ProjectFileDecorator(projectFile);
        
        // Apply inspector
        mutableServiceInspector.inspect(projectFile, decorator);
        
        // Verify tags
        JavaClassNode classNode = classNodeRepository.getOrCreateByFqn("com.example.mutable_service.UserService");
        assertTrue(classNode.hasTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM),
                "Mutable service should be tagged with medium migration complexity");
        assertTrue(classNode.hasTag(EjbMigrationTags.CODE_MODERNIZATION),
                "Mutable service should be tagged for code modernization");
        assertTrue(classNode.hasTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION),
                "Mutable service should be tagged for Spring component conversion");
        
        // Check for specific mutability detection
        assertTrue(classNode.hasTag("mutable.service.detected"),
                "Mutable service should be detected");
        assertTrue(classNode.hasTag("thread_safety.cross_method_mutation"),
                "Cross-method field modification should be detected");
        assertTrue(classNode.hasTag("thread_safety.partial_synchronized"),
                "Partial synchronization should be detected");
        assertTrue(classNode.hasTag("thread_safety.high_mutation"),
                "High mutation should be detected");
        
        logger.info("MutableServiceInspector test passed");
    }
}
