package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractJavaClassInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.*;

/**
 * Inspector that detects DAO (Data Access Object) pattern implementations in
 * Java source code
 * for EJB-to-Spring migration analysis.
 * 
 * <p>
 * The DaoRepositoryInspector (CS-030) identifies classes that implement the DAO
 * pattern,
 * which are candidates for migration to Spring Data repositories or
 * Spring @Repository components.
 * </p>
 * 
 * <p>
 * These classes typically:
 * <ul>
 * <li>Have names ending with "DAO" or "Dao"</li>
 * <li>Contain JDBC API calls</li>
 * <li>Implement data access operations (create, read, update, delete)</li>
 * <li>Abstract database operations from business logic</li>
 * </ul>
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        DaoRepositoryInspector.TAGS.TAG_IS_DAO,
        EjbMigrationTags.JPA_REPOSITORY_CONVERSION,
        EjbMigrationTags.DATA_ACCESS_OBJECT_PATTERN,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM
})
public class DaoRepositoryInspector extends AbstractJavaClassInspector {

    @Inject
    public DaoRepositoryInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "DAO/Repository Pattern Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
            ProjectFileDecorator projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        String className = classDecl.getNameAsString();
        String packageName = projectFile.getPackageName();

        // Initial assessment based on class name
        boolean hasDAOName = className.endsWith("DAO") || className.endsWith("Dao")
                || className.endsWith("Repository") || className.endsWith("Repo");

        // Check package name for data access related terms
        boolean inDataAccessPackage = false;
        if (packageName != null) {
            String lowerPackage = packageName.toLowerCase();
            inDataAccessPackage = lowerPackage.contains("dao") || lowerPackage.contains("data")
                    || lowerPackage.contains("repository") || lowerPackage.contains("persistence");
        }

        // Detailed code analysis
        DAODetector detector = new DAODetector();
        classDecl.accept(detector, null);

        // Decision logic - a class is a DAO if it has a DAO name or is in a DAO package
        // AND
        // has data access characteristics in its code
        boolean isDAO = (hasDAOName || inDataAccessPackage) && detector.isDAO();

        if (isDAO) {
            DAOInfo info = detector.getDAOInfo();
            info.className = className;
            info.packageName = packageName;
            info.hasDAOName = hasDAOName;
            info.inDAOPackage = inDataAccessPackage;

            // Set tags according to the produces contract
            projectFileDecorator.setTag(TAGS.TAG_IS_DAO, true);
            projectFileDecorator.setTag(EjbMigrationTags.DATA_ACCESS_OBJECT_PATTERN, true);
            projectFileDecorator.setTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);

            // Choose between JPA repository or standard @Repository based on JDBC usage
            if (info.jdbcCallCount > 0) {
                projectFileDecorator.setTag(EjbMigrationTags.JPA_REPOSITORY_CONVERSION, true);
                projectFileDecorator.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                projectFileDecorator.setTag("spring.conversion.target", "JpaRepository");
            } else {
                projectFileDecorator.setTag("spring.conversion.target", "@Repository");
                projectFileDecorator.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);
            }

            // Set property on class node for detailed analysis
            classNode.setProperty("dao.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setTag("dao.jdbc_calls", info.jdbcCallCount);
            projectFileDecorator.setTag("dao.crud_methods", info.crudMethodCount);
            projectFileDecorator.setTag("dao.entity_methods", info.entityMethodCount);
        }
    }

    /**
     * Visitor that detects DAO pattern characteristics by analyzing method names,
     * JDBC API calls, and other data access patterns.
     */
    private static class DAODetector extends VoidVisitorAdapter<Void> {
        private final DAOInfo info = new DAOInfo();

        // JDBC API method patterns
        private static final Set<String> JDBC_METHOD_NAMES = Set.of(
                "executeQuery", "executeUpdate", "execute", "prepareStatement", "createStatement",
                "getConnection", "setString", "setInt", "setLong", "setDate", "setTimestamp",
                "getResultSet", "next", "getString", "getInt", "getLong", "getDate", "getTimestamp");

        // CRUD method name patterns
        private static final Set<String> CRUD_METHOD_PREFIXES = Set.of(
                "get", "find", "load", "read", "select", "query", "retrieve",
                "save", "store", "create", "add", "insert",
                "update", "modify", "change",
                "delete", "remove", "destroy");

        // Entity-related method name patterns
        private static final Set<String> ENTITY_METHOD_NAMES = Set.of(
                "findById", "findAll", "findByName", "getById", "getAll", "getByName",
                "saveAll", "saveAndFlush", "saveEntity",
                "updateEntity", "deleteById", "deleteAll");

        public boolean isDAO() {
            // A class is considered a DAO if:
            // 1. It has JDBC API calls or
            // 2. It has CRUD methods and entity operations
            return info.jdbcCallCount > 0 ||
                    (info.crudMethodCount > 0 && info.entityMethodCount > 0);
        }

        public DAOInfo getDAOInfo() {
            return info;
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            String methodName = method.getNameAsString();

            // Check for CRUD method patterns
            for (String prefix : CRUD_METHOD_PREFIXES) {
                if (methodName.startsWith(prefix)) {
                    info.crudMethodCount++;
                    info.crudMethods.add(methodName);
                    break;
                }
            }

            // Check for entity-specific method patterns
            for (String entityMethod : ENTITY_METHOD_NAMES) {
                if (methodName.equals(entityMethod) || methodName.contains(entityMethod)) {
                    info.entityMethodCount++;
                    info.entityMethods.add(methodName);
                    break;
                }
            }
        }

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);

            String methodName = methodCall.getNameAsString();

            // Check for JDBC API calls
            if (JDBC_METHOD_NAMES.contains(methodName)) {
                info.jdbcCallCount++;
                info.jdbcCalls.add(methodName);
            }

            // Check for SQL in strings
            methodCall.getArguments().forEach(arg2 -> {
                if (arg2.isStringLiteralExpr()) {
                    String value = arg2.asStringLiteralExpr().getValue().toUpperCase();
                    if (value.contains("SELECT ") || value.contains(" FROM ") ||
                            value.contains("INSERT INTO") || value.contains("UPDATE ") ||
                            value.contains("DELETE FROM")) {
                        info.sqlStringCount++;
                    }
                }
            });
        }

        @Override
        public void visit(NameExpr nameExpr, Void arg) {
            super.visit(nameExpr, arg);

            // Look for JDBC-related variables
            String name = nameExpr.getNameAsString();
            if (name.equals("Connection") || name.equals("PreparedStatement") ||
                    name.equals("ResultSet") || name.equals("Statement")) {
                info.jdbcTypeReferences++;
            }
        }
    }

    /**
     * Data class to hold DAO pattern analysis information
     */
    public static class DAOInfo {
        public String className;
        public String packageName;
        public boolean hasDAOName = false;
        public boolean inDAOPackage = false;
        public int jdbcCallCount = 0;
        public int sqlStringCount = 0;
        public int crudMethodCount = 0;
        public int entityMethodCount = 0;
        public int jdbcTypeReferences = 0;
        public List<String> jdbcCalls = new ArrayList<>();
        public List<String> crudMethods = new ArrayList<>();
        public List<String> entityMethods = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "DAO{class=%s, package=%s, daoName=%b, daoPackage=%b, jdbcCalls=%d, " +
                            "sqlStrings=%d, crudMethods=%d, entityMethods=%d, jdbcTypes=%d}",
                    className,
                    packageName,
                    hasDAOName,
                    inDAOPackage,
                    jdbcCallCount,
                    sqlStringCount,
                    crudMethodCount,
                    entityMethodCount,
                    jdbcTypeReferences);
        }
    }

    public static class TAGS {
        public static final String TAG_IS_DAO = "dao_repository_inspector.is_dao";
    }
}
