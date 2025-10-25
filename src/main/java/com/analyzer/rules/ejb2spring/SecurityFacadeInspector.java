package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inspector that detects Security Facade patterns in Java source code
 * for EJB-to-Spring migration analysis.
 * 
 * <p>
 * The SecurityFacadeInspector (CS-050) identifies classes that handle security
 * concerns
 * using EJB security mechanisms such as SessionContext.getCallerPrincipal() or
 * SessionContext.isCallerInRole(). These are candidates for migration to Spring
 * Security.
 * </p>
 * 
 * <p>
 * Security Facade characteristics detected include:
 * <ul>
 * <li>Usage of SessionContext.getCallerPrincipal()</li>
 * <li>Usage of SessionContext.isCallerInRole()</li>
 * <li>Security-related naming patterns</li>
 * <li>Role-based access control patterns</li>
 * <li>Security-related annotations</li>
 * </ul>
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        SecurityFacadeInspector.TAGS.TAG_IS_SECURITY_FACADE,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM
})
public class SecurityFacadeInspector extends AbstractJavaClassInspector {

    @Inject
    public SecurityFacadeInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Security Facade Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        String className = classDecl.getNameAsString();

        // Initial assessment based on class name
        boolean hasSecurityName = className.contains("Security") ||
                className.contains("Auth") ||
                className.contains("Permission") ||
                className.contains("Access") ||
                className.contains("Principal") ||
                className.contains("Role") ||
                className.contains("User");

        // Detailed code analysis
        SecurityDetector detector = new SecurityDetector();
        classDecl.accept(detector, null);

        // Decision logic - a class is a security facade if it uses security APIs or has
        // security methods
        boolean isSecurityFacade = detector.usesSecurityAPIs() ||
                (hasSecurityName && detector.hasSecurityCharacteristics());

        if (isSecurityFacade) {
            SecurityInfo info = detector.getSecurityInfo();
            info.className = className;
            info.hasSecurityName = hasSecurityName;

            // Set tags according to the produces contract
            projectFileDecorator.enableTag(TAGS.TAG_IS_SECURITY_FACADE);
            projectFileDecorator.enableTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION);
            projectFileDecorator.enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);

            // Set property on class node for detailed analysis
            classNode.setProperty("security.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setProperty("security.principal_calls", info.principalCalls.size());
            projectFileDecorator.setProperty("security.role_checks", info.roleCheckCalls.size());
            projectFileDecorator.setProperty("security.security_method_count", info.securityMethodCount);

            // Set Spring Boot migration target
            projectFileDecorator.setProperty("spring.conversion.target", "@Service+Spring Security");

            // If custom security implementations are used, migration is more complex
            if (info.hasCustomSecurityLogic) {
                projectFileDecorator.enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                // Override medium complexity
                projectFileDecorator.enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
            }
        }
    }

    /**
     * Visitor that detects Security Facade characteristics by analyzing
     * methods and security API calls.
     */
    private static class SecurityDetector extends VoidVisitorAdapter<Void> {
        private final SecurityInfo info = new SecurityInfo();

        // Security-related types
        private static final Set<String> SECURITY_TYPES = Set.of(
                "SecurityContext", "java.security.Principal", "javax.security.auth.Subject",
                "SessionContext", "javax.ejb.SessionContext", "jakarta.ejb.SessionContext",
                "EJBContext", "javax.ejb.EJBContext", "jakarta.ejb.EJBContext",
                "UserPrincipal", "Authentication", "Authorization",
                "Identity", "IdentityStore", "CredentialValidationResult");

        // Security-related method names
        private static final Set<String> SECURITY_METHODS = Set.of(
                "getCallerPrincipal", "isCallerInRole", "getUserPrincipal", "isUserInRole",
                "checkPermission", "hasPermission", "authenticate", "login", "logout",
                "authorize", "hasRole", "hasAccess", "isAuthenticated", "isAuthorized");

        // Security annotations
        private static final Set<String> SECURITY_ANNOTATIONS = Set.of(
                "RolesAllowed", "DeclareRoles", "RunAs", "PermitAll", "DenyAll",
                "Secured", "PreAuthorize", "PostAuthorize", "RolesRequired");

        public boolean usesSecurityAPIs() {
            return !info.principalCalls.isEmpty() || !info.roleCheckCalls.isEmpty() || info.securityTypeReferences > 0;
        }

        public boolean hasSecurityCharacteristics() {
            return info.securityMethodCount > 0 || info.securityAnnotationCount > 0;
        }

        public SecurityInfo getSecurityInfo() {
            return info;
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            String methodName = method.getNameAsString().toLowerCase();

            // Check for security-related method names
            if (methodName.contains("security") ||
                    methodName.contains("auth") ||
                    methodName.contains("permission") ||
                    methodName.contains("role") ||
                    methodName.contains("access") ||
                    methodName.contains("principal") ||
                    methodName.contains("login") ||
                    methodName.contains("logout") ||
                    methodName.contains("credential")) {

                info.securityMethodCount++;
                info.securityMethods.add(method.getNameAsString());
            }

            // Check for security annotations
            method.getAnnotations().forEach(annotation -> {
                String annotName = annotation.getNameAsString();
                if (SECURITY_ANNOTATIONS.contains(annotName)) {
                    info.securityAnnotationCount++;
                    info.securityAnnotations.add(annotName);
                }
            });

            // Check if the method contains security-related strings in its body
            if (method.getBody().isPresent()) {
                String body = method.getBody().get().toString().toLowerCase();
                if (body.contains("security") ||
                        body.contains("auth") ||
                        body.contains("role") ||
                        body.contains("permission") ||
                        body.contains("principal") ||
                        body.contains("credential")) {

                    info.hasSecurityRelatedStrings = true;

                    // If the method contains complex logic (conditions, loops)
                    // and security-related code, mark as custom security logic
                    if ((body.contains("if ") || body.contains("for ") || body.contains("while ")) &&
                            method.getBody().get().getChildNodes().size() > 5) {
                        info.hasCustomSecurityLogic = true;
                    }
                }
            }
        }

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);

            String methodName = methodCall.getNameAsString();

            // Check for getCallerPrincipal() calls
            if (methodName.equals("getCallerPrincipal") || methodName.equals("getUserPrincipal")) {
                info.principalCalls.add(methodCall.toString());

                // If we see a SessionContext.getCallerPrincipal call directly
                if (methodCall.getScope().isPresent() &&
                        methodCall.getScope().get().toString().contains("SessionContext")) {
                    info.usesSessionContextSecurity = true;
                }
            }

            // Check for isCallerInRole() calls
            else if (methodName.equals("isCallerInRole") || methodName.equals("isUserInRole")) {
                info.roleCheckCalls.add(methodCall.toString());

                // If we see a SessionContext.isCallerInRole call directly
                if (methodCall.getScope().isPresent() &&
                        methodCall.getScope().get().toString().contains("SessionContext")) {
                    info.usesSessionContextSecurity = true;
                }
            }

            // Check for other security-related methods
            else if (SECURITY_METHODS.contains(methodName)) {
                info.securityMethodCalls.add(methodCall.toString());
            }
        }

        @Override
        public void visit(NameExpr nameExpr, Void arg) {
            super.visit(nameExpr, arg);

            // Look for security-related types
            String name = nameExpr.getNameAsString();
            if (SECURITY_TYPES.contains(name)) {
                info.securityTypeReferences++;
                info.securityTypes.add(name);
            }
        }
    }

    /**
     * Data class to hold Security Facade analysis information
     */
    public static class SecurityInfo {
        public String className;
        public boolean hasSecurityName = false;
        public boolean usesSessionContextSecurity = false;
        public boolean hasSecurityRelatedStrings = false;
        public boolean hasCustomSecurityLogic = false;
        public int securityTypeReferences = 0;
        public int securityMethodCount = 0;
        public int securityAnnotationCount = 0;
        public List<String> principalCalls = new ArrayList<>();
        public List<String> roleCheckCalls = new ArrayList<>();
        public List<String> securityMethodCalls = new ArrayList<>();
        public List<String> securityMethods = new ArrayList<>();
        public List<String> securityAnnotations = new ArrayList<>();
        public Set<String> securityTypes = new HashSet<>();

        @Override
        public String toString() {
            return String.format(
                    "SecurityFacade{class=%s, securityName=%b, sessionContextSecurity=%b, " +
                            "securityStrings=%b, customLogic=%b, typeRefs=%d, " +
                            "securityMethods=%d, securityAnnotations=%d, " +
                            "principalCalls=%d, roleChecks=%d, methodCalls=%d}",
                    className,
                    hasSecurityName,
                    usesSessionContextSecurity,
                    hasSecurityRelatedStrings,
                    hasCustomSecurityLogic,
                    securityTypeReferences,
                    securityMethodCount,
                    securityAnnotationCount,
                    principalCalls.size(),
                    roleCheckCalls.size(),
                    securityMethodCalls.size());
        }
    }

    public static class TAGS {
        public static final String TAG_IS_SECURITY_FACADE = "security_facade_inspector.is_security_facade";
    }
}
