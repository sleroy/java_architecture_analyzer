package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Inspector that detects Entity Bean implementations for EJB-to-Spring JPA
 * migration analysis.
 * Identifies EJB 2.x Entity Beans (both CMP and BMP) that need migration to
 * JPA @Entity classes.
 * <p>
 * DEPENDENCIES:
 * - Requires JAVA tag and JAVA_SOURCE for Java file parsing
 * - Produces Entity Bean detection tags for downstream EJB analysis
 */
@InspectorDependencies(requires = {InspectorTags.TAG_JAVA_IS_SOURCE}, produces = {
        EntityBeanJavaSourceInspector.TAGS.TAG_IS_ENTITY_BEAN,
        EntityBeanJavaSourceInspector.TAGS.TAG_BEAN_PERSISTENCE_TYPE
})
public class EntityBeanJavaSourceInspector extends AbstractJavaClassInspector {

    @Inject
    public EntityBeanJavaSourceInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator projectFileDecorator) {
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
            EntityBeanDetector detector = new EntityBeanDetector();
            classDecl.accept(detector, null);

            if (detector.isEntityBean()) {
                EntityBeanInfo info = detector.getEntityBeanInfo();

                // Honor produces contract: set tags on ProjectFile using ProjectFileDecorator
                // for dependency chains
                projectFileDecorator.setProperty(TAGS.TAG_IS_ENTITY_BEAN, true);
                projectFileDecorator.setProperty(TAGS.TAG_BEAN_PERSISTENCE_TYPE,
                        Objects.requireNonNullElse(info.persistenceType, "UNKNOWN"));

                // Set single consolidated analysis property on ClassNode
                classNode.setProperty(PROPERTIES.ENTITY_BEAN_ANALYSIS, info);
            }
        }
    }

    @Override
    public String getName() {
        return "Entity Bean Detector";
    }

    public static class TAGS {
        public static final String TAG_IS_ENTITY_BEAN = "entity_bean_inspector.is_entity_bean";
        public static final String TAG_BEAN_PERSISTENCE_TYPE = "entity_bean_inspector.bean_persistence_type";
    }

    public static class PROPERTIES {
        public static final String ENTITY_BEAN_ANALYSIS = "entity_bean.analysis";
    }

    /**
     * Visitor that detects Entity Bean characteristics by analyzing the AST.
     * Detects Entity Beans through:
     * 1. implements javax.ejb.EntityBean (EJB 2.x)
     * 2. Analysis of ejbCreate/ejbFind methods to determine CMP vs BMP
     */
    private static class EntityBeanDetector extends VoidVisitorAdapter<Void> {
        private final EntityBeanInfo entityBeanInfo = new EntityBeanInfo();
        private boolean isEntityBean = false;

        public boolean isEntityBean() {
            return isEntityBean;
        }

        public EntityBeanInfo getEntityBeanInfo() {
            return entityBeanInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                return;
            }

            entityBeanInfo.className = classDecl.getNameAsString();

            // Check for EntityBean interface implementation
            if (classDecl.getImplementedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                    String typeName = implementedType.getNameAsString();
                    if ("EntityBean".equals(typeName) ||
                            "javax.ejb.EntityBean".equals(typeName)) {
                        isEntityBean = true;
                        entityBeanInfo.ejbVersion = "2.x";
                        analyzePersistenceType(classDecl);
                        return;
                    }
                }
            }

            super.visit(classDecl, arg);
        }

        private void analyzePersistenceType(ClassOrInterfaceDeclaration classDecl) {
            List<String> ejbMethods = new ArrayList<>();

            // Analyze methods to determine CMP vs BMP
            for (MethodDeclaration method : classDecl.getMethods()) {
                String methodName = method.getNameAsString();
                ejbMethods.add(methodName);

                if ("ejbLoad".equals(methodName)) {
                    // Check if method has implementation (BMP) or is abstract/empty (CMP)
                    if (method.getBody().isPresent() && !method.getBody().get().getStatements().isEmpty()) {
                        entityBeanInfo.hasCustomPersistenceLogic = true;
                    }
                } else if ("ejbStore".equals(methodName)) {
                    if (method.getBody().isPresent() && !method.getBody().get().getStatements().isEmpty()) {
                        entityBeanInfo.hasCustomPersistenceLogic = true;
                    }
                } else if (methodName.startsWith("ejbFind")) {
                    entityBeanInfo.finderMethods.add(methodName);
                    if ("ejbFindByPrimaryKey".equals(methodName)) {
                        if (method.getBody().isPresent() && !method.getBody().get().getStatements().isEmpty()) {
                            entityBeanInfo.hasCustomPersistenceLogic = true;
                        }
                    }
                } else if (methodName.startsWith("ejbSelect")) {
                    entityBeanInfo.selectorMethods.add(methodName);
                } else if (methodName.startsWith("ejbCreate")) {
                    entityBeanInfo.createMethods.add(methodName);
                }
            }

            // Determine persistence type based on method implementation analysis
            if (entityBeanInfo.hasCustomPersistenceLogic) {
                entityBeanInfo.persistenceType = "BMP"; // Bean Managed Persistence
                entityBeanInfo.migrationComplexity = "HIGH";
            } else {
                entityBeanInfo.persistenceType = "CMP"; // Container Managed Persistence
                entityBeanInfo.migrationComplexity = "MEDIUM";
            }

            entityBeanInfo.ejbMethods = ejbMethods;
        }
    }

    /**
     * Data class to hold Entity Bean analysis information
     */
    public static class EntityBeanInfo {
        public String className;
        public String persistenceType; // CMP, BMP
        public String ejbVersion; // 2.x
        public String migrationComplexity; // MEDIUM, HIGH
        public boolean hasCustomPersistenceLogic;
        public List<String> ejbMethods = new ArrayList<>();
        public List<String> finderMethods = new ArrayList<>();
        public List<String> selectorMethods = new ArrayList<>();
        public List<String> createMethods = new ArrayList<>();

    }
}
