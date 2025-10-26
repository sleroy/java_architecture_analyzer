package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractJavaParserInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;

/**
 * Inspector that calculates the cyclomatic complexity of Java source files.
 * This rule helps assess code complexity and maintainability using McCabe's cyclomatic complexity <fmetric.
 * <p>
 * Returns the total cyclomatic complexity score for all methods in the class.
 * <p>
 * Complexity is calculated by counting:
 * - Each method starts with complexity 1
 * - +1 for each if, while, for, do-while, switch statement
 * - +1 for each case in switch statements
 * - +1 for each catch block
 * - +1 for each ternary operator (? :)
 * - +1 for each logical AND (&&) or OR (||) operator
 * <p>
 * Interpretation:
 * - 1-10: Simple, low risk
 * - 11-20: Moderate complexity
 * - 21-50: High complexity, more testing needed
 * - >50: Very high complexity, consider refactoring
 */
@InspectorDependencies(
        requires = { InspectorTags.TAG_JAVA_DETECTED },
        produces = { CyclomaticComplexityInspector.TAG_CYCLOMATIC_COMPLEXITY }
)
public class CyclomaticComplexityInspector extends AbstractJavaParserInspector {

    public static final String TAG_CYCLOMATIC_COMPLEXITY = "metrics.cyclomatic-complexity";

    private final GraphRepository graphRepository;

    @Inject
    public CyclomaticComplexityInspector(ResourceResolver resourceResolver, GraphRepository graphRepository) {
        super(resourceResolver);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "Cyclomatic Complexity";
    }


    public String getColumnName() {
        return TAG_CYCLOMATIC_COMPLEXITY;
    }


    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile clazz, NodeDecorator projectFileDecorator) {
        ComplexityCalculator calculator = new ComplexityCalculator();
        cu.accept(calculator, null);

        int totalComplexity = calculator.getTotalComplexity();
        projectFileDecorator.setProperty(getColumnName(), totalComplexity);
    }

    /**
     * Visitor that calculates cyclomatic complexity by traversing the AST.
     */
    private static class ComplexityCalculator extends VoidVisitorAdapter<Void> {
        private int totalComplexity = 0;

        public int getTotalComplexity() {
            return totalComplexity;
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            // Each method starts with complexity 1
            totalComplexity += 1;

            // Visit the method body to count decision points
            super.visit(method, arg);
        }

        @Override
        public void visit(IfStmt stmt, Void arg) {
            totalComplexity += 1;
            super.visit(stmt, arg);
        }

        @Override
        public void visit(WhileStmt stmt, Void arg) {
            totalComplexity += 1;
            super.visit(stmt, arg);
        }

        @Override
        public void visit(ForStmt stmt, Void arg) {
            totalComplexity += 1;
            super.visit(stmt, arg);
        }

        @Override
        public void visit(ForEachStmt stmt, Void arg) {
            totalComplexity += 1;
            super.visit(stmt, arg);
        }

        @Override
        public void visit(DoStmt stmt, Void arg) {
            totalComplexity += 1;
            super.visit(stmt, arg);
        }

        @Override
        public void visit(SwitchStmt stmt, Void arg) {
            totalComplexity += 1;
            super.visit(stmt, arg);
        }

        @Override
        public void visit(SwitchEntry entry, Void arg) {
            // Each case/default adds complexity
            if (entry.getLabels().isNonEmpty()) {
                totalComplexity += 1;
            }
            super.visit(entry, arg);
        }

        @Override
        public void visit(CatchClause clause, Void arg) {
            totalComplexity += 1;
            super.visit(clause, arg);
        }

        @Override
        public void visit(ConditionalExpr expr, Void arg) {
            // Ternary operator (? :)
            totalComplexity += 1;
            super.visit(expr, arg);
        }

        @Override
        public void visit(BinaryExpr expr, Void arg) {
            // Logical AND (&&) and OR (||) operators
            if (expr.getOperator() == BinaryExpr.Operator.AND ||
                    expr.getOperator() == BinaryExpr.Operator.OR) {
                totalComplexity += 1;
            }
            super.visit(expr, arg);
        }
    }
}
