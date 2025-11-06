package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.*;

/**
 * Inspector that detects Transaction Script patterns in Java source code
 * for EJB-to-Spring migration analysis.
 * 
 * <p>
 * The TransactionScriptInspector (CS-032) identifies classes that use manual
 * transaction
 * management through UserTransaction or SessionContext.getUserTransaction().
 * These classes are candidates for migration to Spring @Service
 * with @Transactional
 * annotations.
 * </p>
 * 
 * <p>
 * Manual transaction management patterns detected include:
 * <ul>
 * <li>Direct UserTransaction usage</li>
 * <li>SessionContext.getUserTransaction() calls</li>
 * <li>Transaction lifecycle methods (begin, commit, rollback)</li>
 * <li>Transaction boundary patterns with try-catch blocks</li>
 * </ul>
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        TransactionScriptInspector.TAGS.TAG_IS_TRANSACTION_SCRIPT,
        EjbMigrationTags.TAG_EJB_PROGRAMMATIC_TRANSACTION,
        EjbMigrationTags.TAG_EJB_BEAN_MANAGED_TRANSACTION,
        EjbMigrationTags.TAG_SPRING_TRANSACTION_CONVERSION,
        EjbMigrationTags.TAG_SPRING_SERVICE_CONVERSION,
})
public class TransactionScriptInspector extends AbstractJavaClassInspector {

    @Inject
    public TransactionScriptInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository, LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    public String getName() {
        return "Transaction Script Pattern Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        String className = classDecl.getNameAsString();

        // Detailed analysis of transaction management patterns
        TransactionDetector detector = new TransactionDetector();
        classDecl.accept(detector, null);

        if (detector.hasTransactionManagement()) {
            TransactionInfo info = detector.getTransactionInfo();
            info.className = className;

            // Set tags according to the produces contract
            projectFileDecorator.enableTag(TAGS.TAG_IS_TRANSACTION_SCRIPT);
            projectFileDecorator.enableTag(EjbMigrationTags.TAG_EJB_PROGRAMMATIC_TRANSACTION);
            projectFileDecorator.enableTag(EjbMigrationTags.TAG_EJB_BEAN_MANAGED_TRANSACTION);
            projectFileDecorator.enableTag(EjbMigrationTags.TAG_SPRING_TRANSACTION_CONVERSION);
            projectFileDecorator.enableTag(EjbMigrationTags.TAG_SPRING_SERVICE_CONVERSION);
            projectFileDecorator.getMetrics().setMaxMetric(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY, EjbMigrationTags.COMPLEXITY_MEDIUM);

            // Set property on class node for detailed analysis
            classNode.setProperty("transaction.analysis", info);

            // Set analysis statistics
            projectFileDecorator.setMetric("transaction.tx_begin_count", info.beginCount);
            projectFileDecorator.setMetric("transaction.tx_commit_count", info.commitCount);
            projectFileDecorator.setMetric("transaction.tx_rollback_count", info.rollbackCount);
            projectFileDecorator.setMetric("transaction.tx_boundary_methods", info.transactionMethods.size());

            // Set Spring Boot migration target
            projectFileDecorator.setProperty("spring.conversion.target", "@Service+@Transactional");

            // Set migration complexity based on transaction patterns
            if (info.hasNestedTransactions || info.hasMultipleTransactionScopes) {
                projectFileDecorator.getMetrics().setMaxMetric(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY, EjbMigrationTags.COMPLEXITY_HIGH);
            }
        }
    }

    /**
     * Visitor that detects transaction management patterns by analyzing method
     * calls
     * and transaction-related structures.
     */
    private static class TransactionDetector extends VoidVisitorAdapter<Void> {
        private final TransactionInfo info = new TransactionInfo();
        private int currentNestingLevel = 0;
        private final Set<String> currentMethodTransactions = new HashSet<>();

        // Transaction management method patterns
        private static final Set<String> TX_BEGIN_METHODS = Set.of(
                "begin", "beginTransaction", "startTransaction", "startTx", "beginTx");

        private static final Set<String> TX_COMMIT_METHODS = Set.of(
                "commit", "commitTransaction", "endTransaction", "commitTx", "endTx");

        private static final Set<String> TX_ROLLBACK_METHODS = Set.of(
                "rollback", "abort", "rollbackTransaction", "abortTransaction", "rollbackTx");

        // Transaction-related types
        private static final Set<String> TX_TYPES = Set.of(
                "UserTransaction", "javax.transaction.UserTransaction", "jakarta.transaction.UserTransaction",
                "SessionContext", "javax.ejb.SessionContext", "jakarta.ejb.SessionContext",
                "EJBContext", "javax.ejb.EJBContext", "jakarta.ejb.EJBContext",
                "TransactionManager", "javax.transaction.TransactionManager", "jakarta.transaction.TransactionManager");

        public boolean hasTransactionManagement() {
            return info.beginCount > 0 || info.commitCount > 0 || info.rollbackCount > 0 ||
                    info.getUserTransactionCount > 0;
        }

        public TransactionInfo getTransactionInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Reset state for this class
            currentNestingLevel = 0;
            currentMethodTransactions.clear();
            super.visit(classDecl, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            // Track the current method for associating transactions with methods
            String methodName = method.getNameAsString();
            currentMethodTransactions.clear();

            super.visit(method, arg);

            // After visiting the method body, check if it contained transaction management
            if (!currentMethodTransactions.isEmpty()) {
                info.transactionMethods.add(methodName);

                // Check method body for try-catch patterns around transactions
                method.findAll(TryStmt.class).forEach(tryStmt -> {
                    // Use atomic variables to allow modification in lambda expressions
                    final boolean[] hasTxInTry = { false };
                    final boolean[] hasRollbackInCatch = { false };

                    // Check try block for transaction operations
                    tryStmt.getTryBlock().findAll(MethodCallExpr.class).forEach(call -> {
                        String callName = call.getNameAsString();
                        if (TX_BEGIN_METHODS.contains(callName) || TX_COMMIT_METHODS.contains(callName)) {
                            hasTxInTry[0] = true;
                        }
                    });

                    // Check catch blocks for rollback operations
                    tryStmt.getCatchClauses().forEach(catchClause -> {
                        catchClause.getBody().findAll(MethodCallExpr.class).forEach(call -> {
                            if (TX_ROLLBACK_METHODS.contains(call.getNameAsString())) {
                                hasRollbackInCatch[0] = true;
                            }
                        });
                    });

                    // If we have a try with transaction and catch with rollback, it's a transaction
                    // boundary
                    if (hasTxInTry[0] && hasRollbackInCatch[0]) {
                        info.hasTransactionBoundaries = true;
                    }
                });
            }
        }

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            String methodName = methodCall.getNameAsString();
            Expression scope = methodCall.getScope().orElse(null);
            NodeList<Expression> args = methodCall.getArguments();

            // Check for getUserTransaction() calls
            if (methodName.equals("getUserTransaction") && scope != null) {
                info.getUserTransactionCount++;
                info.getUserTransactionCalls.add(methodCall.toString());
                currentMethodTransactions.add("getUserTransaction");
            }

            // Check for transaction lifecycle methods with scope
            if (scope != null) {
                String scopeStr = scope.toString();
                if (scopeStr.contains("Transaction") || scopeStr.contains("tx") || scopeStr.contains("Tx")) {

                    // Track transaction begin calls
                    if (TX_BEGIN_METHODS.contains(methodName)) {
                        info.beginCount++;
                        info.txBeginCalls.add(methodCall.toString());
                        currentMethodTransactions.add("begin");

                        // Track nesting level for detecting nested transactions
                        currentNestingLevel++;
                        if (currentNestingLevel > 1) {
                            info.hasNestedTransactions = true;
                        }
                    }

                    // Track transaction commit calls
                    else if (TX_COMMIT_METHODS.contains(methodName)) {
                        info.commitCount++;
                        info.txCommitCalls.add(methodCall.toString());
                        currentMethodTransactions.add("commit");

                        // Decrement nesting level
                        if (currentNestingLevel > 0) {
                            currentNestingLevel--;
                        }
                    }

                    // Track transaction rollback calls
                    else if (TX_ROLLBACK_METHODS.contains(methodName)) {
                        info.rollbackCount++;
                        info.txRollbackCalls.add(methodCall.toString());
                        currentMethodTransactions.add("rollback");

                        // Decrement nesting level on rollback
                        if (currentNestingLevel > 0) {
                            currentNestingLevel--;
                        }
                    }
                }
            }
        }

        @Override
        public void visit(NameExpr nameExpr, Void arg) {
            super.visit(nameExpr, arg);

            // Look for transaction-related types
            String name = nameExpr.getNameAsString();
            if (TX_TYPES.contains(name)) {
                info.txTypeReferences++;
                info.txTypes.add(name);
            }
        }
    }

    /**
     * Data class to hold Transaction Script pattern analysis information
     */
    public static class TransactionInfo {
        public String className;
        public int beginCount = 0;
        public int commitCount = 0;
        public int rollbackCount = 0;
        public int getUserTransactionCount = 0;
        public int txTypeReferences = 0;
        public boolean hasNestedTransactions = false;
        public boolean hasTransactionBoundaries = false;
        public boolean hasMultipleTransactionScopes = false;
        public List<String> txBeginCalls = new ArrayList<>();
        public List<String> txCommitCalls = new ArrayList<>();
        public List<String> txRollbackCalls = new ArrayList<>();
        public List<String> getUserTransactionCalls = new ArrayList<>();
        public Set<String> transactionMethods = new HashSet<>();
        public Set<String> txTypes = new HashSet<>();

        @Override
        public String toString() {
            return String.format(
                    "TransactionScript{class=%s, begin=%d, commit=%d, rollback=%d, getUserTx=%d, " +
                            "nestedTx=%b, txBoundaries=%b, multipleTxScopes=%b, txMethods=%d, txTypes=%d}",
                    className,
                    beginCount,
                    commitCount,
                    rollbackCount,
                    getUserTransactionCount,
                    hasNestedTransactions,
                    hasTransactionBoundaries,
                    hasMultipleTransactionScopes,
                    transactionMethods.size(),
                    txTypeReferences);
        }
    }

    public static class TAGS {
        public static final String TAG_IS_TRANSACTION_SCRIPT = "transaction_script_inspector.is_transaction_script";
    }
}
