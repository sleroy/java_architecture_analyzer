package com.analyzer.rules.ejb2spring;

import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.api.graph.JavaClassNode;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that detects exception handling antipatterns.
 * Identifies generic exception throws and catches, and empty catch blocks.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        EjbMigrationTags.ANTIPATTERN_EXCEPTION_GENERIC })
public class ExceptionAntipatternInspector extends AbstractJavaClassInspector {

    @Inject
    public ExceptionAntipatternInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository,
            LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
            NodeDecorator<ProjectFile> projectFileDecorator) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl) {
            ExceptionAntipatternDetector detector = new ExceptionAntipatternDetector();
            classDecl.accept(detector, null);

            if (detector.hasExceptionAntipattern()) {
                ExceptionAntipatternInfo info = detector.getExceptionAntipatternInfo();

                // Tag ProjectFile and JavaClassNode
                projectFileDecorator.enableTag(EjbMigrationTags.ANTIPATTERN_EXCEPTION_GENERIC);
                classNode.enableTag(EjbMigrationTags.ANTIPATTERN_EXCEPTION_GENERIC);

                // Store analysis data
                classNode.setProperty("antipattern.exception.info", info);

                // Calculate refactoring complexity
                double complexity = calculateComplexity(info);
                classNode.setProperty(EjbMigrationTags.METRIC_MIGRATION_COMPLEXITY, complexity);
            }
        }
    }

    private double calculateComplexity(ExceptionAntipatternInfo info) {
        // Base complexity
        double complexity = EjbMigrationTags.COMPLEXITY_LOW;

        // Increase based on violation count
        int totalViolations = info.genericThrows.size() +
                info.genericCatches.size() +
                info.emptyCatchBlocks.size();

        if (totalViolations > 5) {
            complexity = EjbMigrationTags.COMPLEXITY_MEDIUM;
        }
        if (totalViolations > 10) {
            complexity = EjbMigrationTags.COMPLEXITY_HIGH;
        }

        return complexity;
    }

    @Override
    public String getName() {
        return "Exception Antipattern Inspector";
    }

    /**
     * Visitor that detects exception handling antipatterns.
     */
    private static class ExceptionAntipatternDetector extends VoidVisitorAdapter<Void> {
        private final ExceptionAntipatternInfo antipatternInfo = new ExceptionAntipatternInfo();
        private boolean hasAntipattern = false;

        private static final java.util.Set<String> GENERIC_EXCEPTIONS = java.util.Set.of(
                "Exception", "Throwable", "java.lang.Exception", "java.lang.Throwable");

        public boolean hasExceptionAntipattern() {
            return hasAntipattern;
        }

        public ExceptionAntipatternInfo getExceptionAntipatternInfo() {
            return antipatternInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            antipatternInfo.className = classDecl.getNameAsString();
            super.visit(classDecl, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            String methodName = method.getNameAsString();

            // Check for generic throws
            for (ReferenceType thrownType : method.getThrownExceptions()) {
                String exceptionType = thrownType.asString();
                if (GENERIC_EXCEPTIONS.contains(exceptionType)) {
                    antipatternInfo.genericThrows.add(methodName + " throws " + exceptionType);
                    hasAntipattern = true;
                }
            }

            super.visit(method, arg);
        }

        @Override
        public void visit(CatchClause catchClause, Void arg) {
            // Check for generic exception catch
            String exceptionType = catchClause.getParameter().getTypeAsString();
            if (GENERIC_EXCEPTIONS.contains(exceptionType)) {
                antipatternInfo.genericCatches.add("catch(" + exceptionType + ")");
                hasAntipattern = true;
            }

            // Check for empty catch block
            if (catchClause.getBody().getStatements().isEmpty()) {
                antipatternInfo.emptyCatchBlocks.add("Empty catch block for " + exceptionType);
                hasAntipattern = true;
            }

            super.visit(catchClause, arg);
        }
    }

    /**
     * Data class to hold exception antipattern analysis information.
     */
    public static class ExceptionAntipatternInfo {
        public String className;
        public List<String> genericThrows = new ArrayList<>();
        public List<String> genericCatches = new ArrayList<>();
        public List<String> emptyCatchBlocks = new ArrayList<>();
        public String migrationTarget = "Specific exception types and proper handling";

        public int getTotalViolations() {
            return genericThrows.size() + genericCatches.size() + emptyCatchBlocks.size();
        }
    }
}
