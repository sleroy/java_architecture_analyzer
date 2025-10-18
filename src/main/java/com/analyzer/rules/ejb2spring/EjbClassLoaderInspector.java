package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.inspectors.core.classloader.AbstractClassLoaderBasedInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * ClassLoader-based EJB inspector that uses runtime reflection to analyze
 * loaded classes.
 * This inspector complements the binary ASM inspector by providing enhanced
 * runtime analysis
 * including complete annotation metadata, generic type resolution, and
 * inheritance analysis.
 * 
 * <p>
 * Capabilities:
 * </p>
 * <ul>
 * <li>Complete annotation parameter resolution</li>
 * <li>Accurate inheritance chain analysis</li>
 * <li>Generic type information extraction</li>
 * <li>Runtime method signature analysis</li>
 * <li>Enhanced migration recommendations</li>
 * </ul>
 * 
 * <p>
 * Works in tandem with EjbBinaryClassInspector to provide comprehensive EJB
 * analysis.
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED }, produces = {
        EjbMigrationTags.EJB_BEAN_DETECTED,
        EjbMigrationTags.EJB_SESSION_BEAN,
        EjbMigrationTags.EJB_ENTITY_BEAN,
        EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN,
        EjbMigrationTags.EJB_HOME_INTERFACE,
        EjbMigrationTags.EJB_REMOTE_INTERFACE,
        EjbMigrationTags.EJB_LOCAL_INTERFACE,
        EjbMigrationTags.EJB_LOCAL_HOME_INTERFACE,
        EjbMigrationTags.EJB_STATELESS_SESSION_BEAN,
        EjbMigrationTags.EJB_STATEFUL_SESSION_BEAN
})
public class EjbClassLoaderInspector extends AbstractClassLoaderBasedInspector {

    private static final Logger logger = LoggerFactory.getLogger(EjbClassLoaderInspector.class);

    // EJB 3.x Annotation Classes (will be loaded at runtime)
    private static final Set<String> EJB3_ANNOTATION_NAMES = Set.of(
            "javax.ejb.Stateless",
            "jakarta.ejb.Stateless",
            "javax.ejb.Stateful",
            "jakarta.ejb.Stateful",
            "javax.persistence.Entity",
            "jakarta.persistence.Entity",
            "javax.ejb.MessageDriven",
            "jakarta.ejb.MessageDriven");

    // EJB 2.x Interface Classes
    private static final Set<String> EJB2_INTERFACE_NAMES = Set.of(
            "javax.ejb.SessionBean",
            "jakarta.ejb.SessionBean",
            "javax.ejb.EntityBean",
            "jakarta.ejb.EntityBean",
            "javax.ejb.MessageDrivenBean",
            "jakarta.ejb.MessageDrivenBean");

    // EJB Standard Interface Classes
    private static final Set<String> EJB_STANDARD_INTERFACE_NAMES = Set.of(
            "javax.ejb.EJBHome",
            "jakarta.ejb.EJBHome",
            "javax.ejb.EJBObject",
            "jakarta.ejb.EJBObject",
            "javax.ejb.EJBLocalHome",
            "jakarta.ejb.EJBLocalHome",
            "javax.ejb.EJBLocalObject",
            "jakarta.ejb.EJBLocalObject");

    private final ClassNodeRepository classNodeRepository;

    @Inject
    public EjbClassLoaderInspector(ResourceResolver resourceResolver, JARClassLoaderService classLoaderService,
            ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classLoaderService);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    public String getName() {
        return "EJB ClassLoader Inspector";
    }

    @Override
    protected void analyzeLoadedClass(Class<?> loadedClass, ProjectFile projectFile,
            ProjectFileDecorator projectFileDecorator) {
        logger.debug("Analyzing loaded class for EJB components: {}", loadedClass.getName());

        classNodeRepository.getOrCreateClassNodeByFqn(loadedClass.getName()).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            EjbRuntimeAnalysis analysis = new EjbRuntimeAnalysis(loadedClass);

            // Perform comprehensive runtime analysis
            boolean isEjbComponent = false;

            // Analyze EJB 3.x annotations with full metadata
            if (analyzeEjb3Annotations(analysis, classNode, projectFile)) {
                isEjbComponent = true;
            }

            // Analyze EJB 2.x interface implementations
            if (analyzeEjb2Interfaces(analysis, classNode, projectFile)) {
                isEjbComponent = true;
            }

            // Analyze EJB standard interfaces
            if (analyzeEjbStandardInterfaces(analysis, classNode, projectFile)) {
                isEjbComponent = true;
            }

            // If EJB component found, perform enhanced analysis
            if (isEjbComponent) {
                projectFile.setTag(EjbMigrationTags.EJB_BEAN_DETECTED, true);
                Map<String, Object> result = performEnhancedAnalysis(analysis, classNode);
                classNode.setProperty("ejb.runtime.analysis", createAnalysisJson(result));
                logger.info("EJB component runtime analysis complete: {}", loadedClass.getName());
            }
        });
    }

    private boolean analyzeEjb3Annotations(EjbRuntimeAnalysis analysis, JavaClassNode classNode,
            ProjectFile projectFile) {
        boolean found = false;
        Class<?> clazz = analysis.getLoadedClass();

        for (Annotation annotation : clazz.getAnnotations()) {
            String annotationName = annotation.annotationType().getName();

            if (EJB3_ANNOTATION_NAMES.contains(annotationName)) {
                logger.debug("Found EJB 3.x annotation: {} with runtime metadata", annotationName);
                analysis.addEjbAnnotation(annotation);

                switch (annotationName) {
                    case "javax.ejb.Stateless":
                    case "jakarta.ejb.Stateless":
                        projectFile.setTag(EjbMigrationTags.EJB_STATELESS_SESSION_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.EJB_SESSION_BEAN, true);
                        extractStatelessAnnotationMetadata(annotation, classNode);
                        found = true;
                        break;

                    case "javax.ejb.Stateful":
                    case "jakarta.ejb.Stateful":
                        projectFile.setTag(EjbMigrationTags.EJB_STATEFUL_SESSION_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.EJB_SESSION_BEAN, true);
                        extractStatefulAnnotationMetadata(annotation, classNode);
                        found = true;
                        break;

                    case "javax.persistence.Entity":
                    case "jakarta.persistence.Entity":
                        projectFile.setTag(EjbMigrationTags.EJB_ENTITY_BEAN, true);
                        extractEntityAnnotationMetadata(annotation, classNode);
                        found = true;
                        break;

                    case "javax.ejb.MessageDriven":
                    case "jakarta.ejb.MessageDriven":
                        projectFile.setTag(EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN, true);
                        extractMessageDrivenAnnotationMetadata(annotation, classNode);
                        found = true;
                        break;
                }
            }
        }

        return found;
    }

    private boolean analyzeEjb2Interfaces(EjbRuntimeAnalysis analysis, JavaClassNode classNode,
            ProjectFile projectFile) {
        boolean found = false;
        Class<?> clazz = analysis.getLoadedClass();

        // Get all interfaces including inherited ones
        Set<Class<?>> allInterfaces = getAllInterfaces(clazz);

        for (Class<?> iface : allInterfaces) {
            String interfaceName = iface.getName();

            if (EJB2_INTERFACE_NAMES.contains(interfaceName)) {
                logger.debug("Found EJB 2.x interface implementation: {}", interfaceName);
                analysis.addEjb2Interface(iface);

                switch (interfaceName) {
                    case "javax.ejb.SessionBean":
                    case "jakarta.ejb.SessionBean":
                        projectFile.setTag(EjbMigrationTags.EJB_SESSION_BEAN, true);
                        analyzeSessionBeanMethods(clazz, classNode);
                        found = true;
                        break;

                    case "javax.ejb.EntityBean":
                    case "jakarta.ejb.EntityBean":
                        projectFile.setTag(EjbMigrationTags.EJB_ENTITY_BEAN, true);
                        analyzeEntityBeanMethods(clazz, classNode);
                        found = true;
                        break;

                    case "javax.ejb.MessageDrivenBean":
                    case "jakarta.ejb.MessageDrivenBean":
                        projectFile.setTag(EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN, true);
                        analyzeMessageDrivenBeanMethods(clazz, classNode);
                        found = true;
                        break;
                }
            }
        }

        return found;
    }

    private boolean analyzeEjbStandardInterfaces(EjbRuntimeAnalysis analysis, JavaClassNode classNode,
            ProjectFile projectFile) {
        if (!analysis.getLoadedClass().isInterface()) {
            return false; // Only interfaces can be EJB standard interfaces
        }

        boolean found = false;
        Class<?> clazz = analysis.getLoadedClass();

        // Get all interfaces including inherited ones
        Set<Class<?>> allInterfaces = getAllInterfaces(clazz);

        for (Class<?> iface : allInterfaces) {
            String interfaceName = iface.getName();

            if (EJB_STANDARD_INTERFACE_NAMES.contains(interfaceName)) {
                logger.debug("Found EJB standard interface: {}", interfaceName);
                analysis.addStandardInterface(iface);

                switch (interfaceName) {
                    case "javax.ejb.EJBHome":
                    case "jakarta.ejb.EJBHome":
                        projectFile.setTag(EjbMigrationTags.EJB_HOME_INTERFACE, true);
                        analyzeHomeInterfaceMethods(clazz, classNode);
                        found = true;
                        break;

                    case "javax.ejb.EJBObject":
                    case "jakarta.ejb.EJBObject":
                        projectFile.setTag(EjbMigrationTags.EJB_REMOTE_INTERFACE, true);
                        analyzeRemoteInterfaceMethods(clazz, classNode);
                        found = true;
                        break;

                    case "javax.ejb.EJBLocalHome":
                    case "jakarta.ejb.EJBLocalHome":
                        projectFile.setTag(EjbMigrationTags.EJB_LOCAL_HOME_INTERFACE, true);
                        analyzeLocalHomeInterfaceMethods(clazz, classNode);
                        found = true;
                        break;

                    case "javax.ejb.EJBLocalObject":
                    case "jakarta.ejb.EJBLocalObject":
                        projectFile.setTag(EjbMigrationTags.EJB_LOCAL_INTERFACE, true);
                        analyzeLocalInterfaceMethods(clazz, classNode);
                        found = true;
                        break;
                }
            }
        }

        return found;
    }

    private Map<String, Object> performEnhancedAnalysis(EjbRuntimeAnalysis analysis, JavaClassNode classNode) {
        Map<String, Object> result = new HashMap<>();
        // Store comprehensive annotation metadata
        Map<String, Object> annotationMetadata = extractAllAnnotationMetadata(analysis);
        result.put("annotationMetadata", annotationMetadata);

        // Analyze inheritance hierarchy
        Map<String, Object> inheritanceAnalysis = analyzeInheritanceHierarchy(analysis);
        result.put("inheritanceAnalysis", inheritanceAnalysis);

        // Extract generic type information
        Map<String, Object> genericTypes = analyzeGenericTypes(analysis);
        result.put("genericTypes", genericTypes);

        // Analyze method signatures for migration patterns
        Map<String, Object> methodSignatures = analyzeMethodSignatures(analysis);
        result.put("methodSignatures", methodSignatures);

        // Generate enhanced migration recommendations
        Map<String, Object> migrationRecommendations = generateEnhancedMigrationRecommendations(analysis);
        result.put("migrationRecommendations", migrationRecommendations);

        return result;
    }

    // Annotation metadata extraction methods
    private void extractStatelessAnnotationMetadata(Annotation annotation, JavaClassNode classNode) {
        Map<String, Object> metadata = new HashMap<>();
        try {
            // Use reflection to extract annotation parameters
            Method nameMethod = annotation.annotationType().getMethod("name");
            Method mappedNameMethod = annotation.annotationType().getMethod("mappedName");
            Method descriptionMethod = annotation.annotationType().getMethod("description");

            metadata.put("name", nameMethod.invoke(annotation));
            metadata.put("mappedName", mappedNameMethod.invoke(annotation));
            metadata.put("description", descriptionMethod.invoke(annotation));

            classNode.setProperty("ejb.stateless.annotation.metadata", metadata);
        } catch (Exception e) {
            logger.debug("Could not extract Stateless annotation metadata: {}", e.getMessage());
        }
    }

    private void extractStatefulAnnotationMetadata(Annotation annotation, JavaClassNode classNode) {
        Map<String, Object> metadata = new HashMap<>();
        try {
            Method nameMethod = annotation.annotationType().getMethod("name");
            Method mappedNameMethod = annotation.annotationType().getMethod("mappedName");
            Method descriptionMethod = annotation.annotationType().getMethod("description");

            metadata.put("name", nameMethod.invoke(annotation));
            metadata.put("mappedName", mappedNameMethod.invoke(annotation));
            metadata.put("description", descriptionMethod.invoke(annotation));

            classNode.setProperty("ejb.stateful.annotation.metadata", metadata);
        } catch (Exception e) {
            logger.debug("Could not extract Stateful annotation metadata: {}", e.getMessage());
        }
    }

    private void extractEntityAnnotationMetadata(Annotation annotation, JavaClassNode classNode) {
        Map<String, Object> metadata = new HashMap<>();
        try {
            Method nameMethod = annotation.annotationType().getMethod("name");
            metadata.put("name", nameMethod.invoke(annotation));

            classNode.setProperty("ejb.entity.annotation.metadata", metadata);
        } catch (Exception e) {
            logger.debug("Could not extract Entity annotation metadata: {}", e.getMessage());
        }
    }

    private void extractMessageDrivenAnnotationMetadata(Annotation annotation, JavaClassNode classNode) {
        Map<String, Object> metadata = new HashMap<>();
        try {
            Method nameMethod = annotation.annotationType().getMethod("name");
            Method mappedNameMethod = annotation.annotationType().getMethod("mappedName");
            Method descriptionMethod = annotation.annotationType().getMethod("description");

            metadata.put("name", nameMethod.invoke(annotation));
            metadata.put("mappedName", mappedNameMethod.invoke(annotation));
            metadata.put("description", descriptionMethod.invoke(annotation));

            classNode.setProperty("ejb.messagedriven.annotation.metadata", metadata);
        } catch (Exception e) {
            logger.debug("Could not extract MessageDriven annotation metadata: {}", e.getMessage());
        }
    }

    // Method analysis methods
    private void analyzeSessionBeanMethods(Class<?> clazz, JavaClassNode classNode) {
        Set<String> ejbMethods = new HashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            String methodName = method.getName();
            if (methodName.startsWith("ejb") || methodName.equals("setSessionContext")) {
                ejbMethods.add(methodName);
            }
        }
        classNode.setProperty("ejb.sessionbean.methods", ejbMethods);
    }

    private void analyzeEntityBeanMethods(Class<?> clazz, JavaClassNode classNode) {
        Set<String> ejbMethods = new HashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            String methodName = method.getName();
            if (methodName.startsWith("ejb") || methodName.equals("setEntityContext")) {
                ejbMethods.add(methodName);
            }
        }
        classNode.setProperty("ejb.entitybean.methods", ejbMethods);
    }

    private void analyzeMessageDrivenBeanMethods(Class<?> clazz, JavaClassNode classNode) {
        Set<String> ejbMethods = new HashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            String methodName = method.getName();
            if (methodName.equals("onMessage") || methodName.equals("setMessageDrivenContext")) {
                ejbMethods.add(methodName);
            }
        }
        classNode.setProperty("ejb.messagedrivenbean.methods", ejbMethods);
    }

    private void analyzeHomeInterfaceMethods(Class<?> clazz, JavaClassNode classNode) {
        Set<String> createMethods = new HashSet<>();
        Set<String> finderMethods = new HashSet<>();

        for (Method method : clazz.getDeclaredMethods()) {
            String methodName = method.getName();
            if (methodName.startsWith("create")) {
                createMethods.add(methodName);
            } else if (methodName.startsWith("find")) {
                finderMethods.add(methodName);
            }
        }

        classNode.setProperty("ejb.home.create.methods", createMethods);
        classNode.setProperty("ejb.home.finder.methods", finderMethods);
    }

    private void analyzeRemoteInterfaceMethods(Class<?> clazz, JavaClassNode classNode) {
        Set<String> businessMethods = new HashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            businessMethods.add(method.getName());
        }
        classNode.setProperty("ejb.remote.business.methods", businessMethods);
    }

    private void analyzeLocalHomeInterfaceMethods(Class<?> clazz, JavaClassNode classNode) {
        analyzeHomeInterfaceMethods(clazz, classNode); // Same logic as remote home
    }

    private void analyzeLocalInterfaceMethods(Class<?> clazz, JavaClassNode classNode) {
        analyzeRemoteInterfaceMethods(clazz, classNode); // Same logic as remote
    }

    // Utility methods
    private Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new HashSet<>();

        // Add direct interfaces
        Collections.addAll(interfaces, clazz.getInterfaces());

        // Add inherited interfaces
        Class<?> current = clazz.getSuperclass();
        while (current != null) {
            Collections.addAll(interfaces, current.getInterfaces());
            current = current.getSuperclass();
        }

        return interfaces;
    }

    private Map<String, Object> extractAllAnnotationMetadata(EjbRuntimeAnalysis analysis) {
        Map<String, Object> metadata = new HashMap<>();

        for (Annotation annotation : analysis.getEjbAnnotations()) {
            String annotationName = annotation.annotationType().getSimpleName();
            try {
                Map<String, Object> annotationData = new HashMap<>();
                for (Method method : annotation.annotationType().getDeclaredMethods()) {
                    if (method.getParameterCount() == 0) {
                        annotationData.put(method.getName(), method.invoke(annotation));
                    }
                }
                metadata.put(annotationName, annotationData);
            } catch (Exception e) {
                logger.debug("Could not extract metadata for annotation: {}", annotationName);
            }
        }

        return metadata;
    }

    private Map<String, Object> analyzeInheritanceHierarchy(EjbRuntimeAnalysis analysis) {
        Map<String, Object> hierarchy = new HashMap<>();
        Class<?> clazz = analysis.getLoadedClass();

        List<String> superClasses = new ArrayList<>();
        Class<?> current = clazz.getSuperclass();
        while (current != null && !current.equals(Object.class)) {
            superClasses.add(current.getName());
            current = current.getSuperclass();
        }

        List<String> interfaceNames = new ArrayList<>();
        for (Class<?> iface : getAllInterfaces(clazz)) {
            interfaceNames.add(iface.getName());
        }

        hierarchy.put("superClasses", superClasses);
        hierarchy.put("interfaces", interfaceNames);
        hierarchy.put("isInterface", clazz.isInterface());
        hierarchy.put("isAbstract", java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()));

        return hierarchy;
    }

    private Map<String, Object> analyzeGenericTypes(EjbRuntimeAnalysis analysis) {
        Map<String, Object> genericInfo = new HashMap<>();
        Class<?> clazz = analysis.getLoadedClass();

        // Analyze generic superclass
        Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
            genericInfo.put("genericSuperclass", parameterizedType.toString());
        }

        // Analyze generic interfaces
        Type[] genericInterfaces = clazz.getGenericInterfaces();
        List<String> genericInterfaceNames = new ArrayList<>();
        for (Type genericInterface : genericInterfaces) {
            genericInterfaceNames.add(genericInterface.toString());
        }
        genericInfo.put("genericInterfaces", genericInterfaceNames);

        return genericInfo;
    }

    private Map<String, Object> analyzeMethodSignatures(EjbRuntimeAnalysis analysis) {
        Map<String, Object> methodInfo = new HashMap<>();
        Class<?> clazz = analysis.getLoadedClass();

        List<Map<String, Object>> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            Map<String, Object> methodData = new HashMap<>();
            methodData.put("name", method.getName());
            methodData.put("parameterCount", method.getParameterCount());
            methodData.put("returnType", method.getReturnType().getName());
            methodData.put("isPublic", java.lang.reflect.Modifier.isPublic(method.getModifiers()));

            List<String> parameterTypes = new ArrayList<>();
            for (Class<?> paramType : method.getParameterTypes()) {
                parameterTypes.add(paramType.getName());
            }
            methodData.put("parameterTypes", parameterTypes);

            methods.add(methodData);
        }

        methodInfo.put("methods", methods);
        methodInfo.put("methodCount", methods.size());

        return methodInfo;
    }

    private Map<String, Object> generateEnhancedMigrationRecommendations(EjbRuntimeAnalysis analysis) {
        Map<String, Object> recommendations = new HashMap<>();

        // Generate specific migration patterns based on runtime analysis
        if (!analysis.getEjbAnnotations().isEmpty()) {
            recommendations.put("migrationPattern", "EJB3_TO_SPRING");
            recommendations.put("complexity", "LOW_TO_MEDIUM");
            recommendations.put("recommendation", "Use Spring @Service or @Component annotations");
        } else if (!analysis.getEjb2Interfaces().isEmpty()) {
            recommendations.put("migrationPattern", "EJB2_TO_SPRING");
            recommendations.put("complexity", "MEDIUM_TO_HIGH");
            recommendations.put("recommendation", "Refactor to POJO with Spring annotations");
        }

        if (!analysis.getStandardInterfaces().isEmpty()) {
            recommendations.put("interfaceRefactoring", "REMOVE_EJB_INTERFACES");
            recommendations.put("interfaceRecommendation", "Convert to plain Java interfaces or Spring components");
        }

        return recommendations;
    }

    private String createAnalysisJson(Map<String, Object> result) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            json.append("\"").append(entry.getValue().toString()).append("\"");
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Runtime analysis data holder for EJB components.
     */
    private static class EjbRuntimeAnalysis {
        private final Class<?> loadedClass;
        private final List<Annotation> ejbAnnotations = new ArrayList<>();
        private final List<Class<?>> ejb2Interfaces = new ArrayList<>();
        private final List<Class<?>> standardInterfaces = new ArrayList<>();

        public EjbRuntimeAnalysis(Class<?> loadedClass) {
            this.loadedClass = loadedClass;
        }

        public Class<?> getLoadedClass() {
            return loadedClass;
        }

        public List<Annotation> getEjbAnnotations() {
            return ejbAnnotations;
        }

        public List<Class<?>> getEjb2Interfaces() {
            return ejb2Interfaces;
        }

        public List<Class<?>> getStandardInterfaces() {
            return standardInterfaces;
        }

        public void addEjbAnnotation(Annotation annotation) {
            ejbAnnotations.add(annotation);
        }

        public void addEjb2Interface(Class<?> iface) {
            ejb2Interfaces.add(iface);
        }

        public void addStandardInterface(Class<?> iface) {
            standardInterfaces.add(iface);
        }
    }
}
