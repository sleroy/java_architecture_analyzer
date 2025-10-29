package com.analyzer.rules.ejb2spring;

import com.analyzer.api.inspector.Inspector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.dev.inspectors.binary.AbstractASMClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class-centric EJB Create Method Usage Inspector - Phase 3 migration.
 * 
 * <p>
 * Analyzes EJB create method patterns including:
 * </p>
 * <ul>
 * <li>ejbCreate/ejbPostCreate methods in Entity and Session beans</li>
 * <li>create methods in Home interfaces</li>
 * <li>Client code using create methods with JNDI lookups</li>
 * <li>Cross-method dependencies and migration complexity assessment</li>
 * </ul>
 * 
 * <p>
 * <strong>Key Differences from EjbCreateMethodUsageInspector:</strong>
 * </p>
 * <ul>
 * <li>Extends AbstractASMClassInspector (class-centric) instead of AbstractASMInspector (file-centric)</li>
 * <li>Receives JavaClassNode directly instead of creating it</li>
 * <li>Writes all analysis results to JavaClassNode properties</li>
 * <li>Uses NodeDecorator for type-safe property access</li>
 * <li>Simplified constructor with standard injection pattern</li>
 * </ul>
 * 
 * @since Phase 3 - Systematic Inspector Migration
 * @see EjbCreateMethodUsageInspector Original file-centric version
 */
@InspectorDependencies(requires = { InspectorTags.TAG_APPLICATION_CLASS }, produces = {
        EjbMigrationTags.EJB_CREATE_METHOD,
        EjbMigrationTags.EJB_CREATE_METHOD_USAGE,
        EjbMigrationTags.EJB_HOME_INTERFACE,
        EjbMigrationTags.EJB_CLIENT_CODE,
        EjbMigrationTags.EJB_PARAMETERIZED_CREATE,
        EjbMigrationTags.EJB_COMPLEX_INITIALIZATION,
        EjbMigrationTags.EJB_DEPENDENCY_INJECTION_CANDIDATE,
        EjbMigrationTags.EJB_JNDI_LOOKUP,
        EjbMigrationTags.EJB_MIGRATION_SIMPLE,
        EjbMigrationTags.EJB_MIGRATION_MEDIUM,
        EjbMigrationTags.EJB_MIGRATION_COMPLEX
})
public class EjbCreateMethodUsageInspector extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(EjbCreateMethodUsageInspector.class);

    private static final String EJB_CREATE_METHOD_PREFIX = "ejbCreate";
    private static final String EJB_POST_CREATE_METHOD_PREFIX = "ejbPostCreate";
    private static final String HOME_CREATE_METHOD_PREFIX = "create";

    private static final Set<String> ENTITY_BEAN_INTERFACES = Set.of(
            "javax/ejb/EntityBean");

    private static final Set<String> SESSION_BEAN_INTERFACES = Set.of(
            "javax/ejb/SessionBean");

    private static final Set<String> EJB_HOME_INTERFACES = Set.of(
            "javax/ejb/EJBHome",
            "javax/ejb/EJBLocalHome");

    @Inject
    public EjbCreateMethodUsageInspector(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "EJB Create Method Usage Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        return new EjbCreateMethodVisitor(classNode, decorator);
    }

    /**
     * ASM visitor that analyzes EJB create method patterns using class-centric
     * architecture.
     * Analyzes beans, home interfaces, and client code for create method usage.
     */
    private static class EjbCreateMethodVisitor extends ASMClassNodeVisitor {

        private final ClassNode classNode;
        private CreateMethodMetadata beanMetadata;
        private HomeInterfaceMetadata homeMetadata;
        private CreateMethodUsageMetadata usageMetadata;

        protected EjbCreateMethodVisitor(JavaClassNode graphNode, NodeDecorator<JavaClassNode> decorator) {
            super(graphNode, decorator);
            this.classNode = new ClassNode();
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classNode.visit(version, access, name, signature, superName, interfaces);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            // Analyze EJB bean classes for ejbCreate methods
            if (isEjbBeanClass(classNode)) {
                beanMetadata = analyzeEjbBeanCreateMethods(classNode);
                if (beanMetadata.hasCreateMethods()) {
                    addBeanCreateMethodResults();
                }
            }

            // Analyze Home interfaces for create methods
            if (isEjbHomeInterface(classNode)) {
                homeMetadata = analyzeHomeInterfaceCreateMethods(classNode);
                if (homeMetadata.hasCreateMethods()) {
                    addHomeCreateMethodResults();
                }
            }

            // Analyze client classes for create method usage
            usageMetadata = analyzeCreateMethodUsage(classNode);
            if (usageMetadata.hasCreateMethodCalls()) {
                addCreateMethodUsageResults();
            }

            super.visitEnd();
        }

        private boolean isEjbBeanClass(ClassNode classNode) {
            if (classNode.interfaces == null)
                return false;

            return classNode.interfaces.stream()
                    .anyMatch(
                            iface -> ENTITY_BEAN_INTERFACES.contains(iface) || SESSION_BEAN_INTERFACES.contains(iface));
        }

        private boolean isEjbHomeInterface(ClassNode classNode) {
            if (classNode.interfaces == null)
                return false;

            return classNode.interfaces.stream()
                    .anyMatch(iface -> EJB_HOME_INTERFACES.contains(iface));
        }

        private CreateMethodMetadata analyzeEjbBeanCreateMethods(ClassNode classNode) {
            CreateMethodMetadata metadata = new CreateMethodMetadata(classNode.name, determineEjbBeanType(classNode));

            // Analyze all methods for create patterns
            if (classNode.methods != null) {
                for (MethodNode method : classNode.methods) {
                    if (isEjbCreateMethod(method)) {
                        EjbCreateMethodInfo createMethod = analyzeEjbCreateMethod(method, classNode);
                        metadata.addEjbCreateMethod(createMethod);
                    } else if (isEjbPostCreateMethod(method)) {
                        EjbPostCreateMethodInfo postCreateMethod = analyzeEjbPostCreateMethod(method, classNode);
                        metadata.addEjbPostCreateMethod(postCreateMethod);
                    }
                }
            }

            return metadata;
        }

        private boolean isEjbCreateMethod(MethodNode method) {
            return method.name.startsWith(EJB_CREATE_METHOD_PREFIX) &&
                    !method.name.equals(EJB_CREATE_METHOD_PREFIX) &&
                    isPublicMethod(method);
        }

        private boolean isEjbPostCreateMethod(MethodNode method) {
            return method.name.startsWith(EJB_POST_CREATE_METHOD_PREFIX) &&
                    !method.name.equals(EJB_POST_CREATE_METHOD_PREFIX) &&
                    isPublicMethod(method);
        }

        private boolean isPublicMethod(MethodNode method) {
            return (method.access & Opcodes.ACC_PUBLIC) != 0;
        }

        private EjbBeanType determineEjbBeanType(ClassNode classNode) {
            if (classNode.interfaces == null)
                return EjbBeanType.UNKNOWN;

            for (String iface : classNode.interfaces) {
                if (ENTITY_BEAN_INTERFACES.contains(iface)) {
                    return EjbBeanType.ENTITY_BEAN;
                } else if (SESSION_BEAN_INTERFACES.contains(iface)) {
                    return EjbBeanType.SESSION_BEAN;
                }
            }
            return EjbBeanType.UNKNOWN;
        }

        private EjbCreateMethodInfo analyzeEjbCreateMethod(MethodNode method, ClassNode classNode) {
            String createMethodSuffix = extractCreateMethodSuffix(method.name);
            String returnType = extractReturnType(method.desc);
            List<String> parameters = extractMethodParameters(method.desc);

            // Analyze method body for initialization patterns
            CreateMethodAnalysis analysis = analyzeCreateMethodBody(method);

            // Determine create method type
            CreateMethodType createType = determineCreateMethodType(method);

            return new EjbCreateMethodInfo(method.name, method.desc, createMethodSuffix,
                    returnType, parameters, createType, analysis);
        }

        private String extractCreateMethodSuffix(String methodName) {
            if (methodName.startsWith(EJB_CREATE_METHOD_PREFIX)) {
                return methodName.substring(EJB_CREATE_METHOD_PREFIX.length());
            }
            return "";
        }

        private String extractReturnType(String methodDescriptor) {
            int returnTypeStart = methodDescriptor.lastIndexOf(')') + 1;
            return methodDescriptor.substring(returnTypeStart);
        }

        private List<String> extractMethodParameters(String methodDescriptor) {
            List<String> params = new ArrayList<>();
            String paramPart = methodDescriptor.substring(1, methodDescriptor.lastIndexOf(')'));

            if (!paramPart.isEmpty()) {
                for (int i = 0; i < paramPart.length(); i++) {
                    char c = paramPart.charAt(i);
                    if (c == 'L') {
                        // Object parameter
                        int semicolon = paramPart.indexOf(';', i);
                        if (semicolon != -1) {
                            params.add(paramPart.substring(i, semicolon + 1));
                            i = semicolon;
                        }
                    } else if ("BCDFIJSZ".indexOf(c) >= 0) {
                        // Primitive parameter
                        params.add(String.valueOf(c));
                    }
                }
            }

            return params;
        }

        private CreateMethodType determineCreateMethodType(MethodNode method) {
            String suffix = extractCreateMethodSuffix(method.name);
            List<String> params = extractMethodParameters(method.desc);

            if (suffix.isEmpty() && params.isEmpty()) {
                return CreateMethodType.DEFAULT_CREATE;
            } else if (!params.isEmpty()) {
                return CreateMethodType.PARAMETERIZED;
            } else {
                return CreateMethodType.NAMED_CREATE;
            }
        }

        private CreateMethodAnalysis analyzeCreateMethodBody(MethodNode method) {
            CreateMethodAnalysis analysis = new CreateMethodAnalysis();

            if (method.instructions != null) {
                InsnList instructions = method.instructions;
                for (AbstractInsnNode instruction : instructions) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode fieldInsn = (FieldInsnNode) instruction;
                        if (fieldInsn.getOpcode() == Opcodes.PUTFIELD) {
                            analysis.addFieldAssignment(fieldInsn.name);
                        }
                    } else if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode methodInsn = (MethodInsnNode) instruction;

                        // Detect JNDI lookups
                        if (isJndiLookupCall(methodInsn)) {
                            analysis.addDependencyLookup(extractJndiName(methodInsn));
                        }

                        // Detect database operations
                        if (isDatabaseOperationCall(methodInsn)) {
                            analysis.addDatabaseOperation(methodInsn.name);
                        }

                        // Detect initialization patterns
                        if (isInitializationCall(methodInsn)) {
                            analysis.addInitializationPattern(methodInsn.name);
                        }
                    }
                }
            }

            return analysis;
        }

        private boolean isJndiLookupCall(MethodInsnNode methodInsn) {
            return "lookup".equals(methodInsn.name) &&
                    (methodInsn.owner.contains("Context") || methodInsn.owner.contains("InitialContext"));
        }

        private String extractJndiName(MethodInsnNode methodInsn) {
            return "jndi://" + methodInsn.owner + "/" + methodInsn.name;
        }

        private boolean isDatabaseOperationCall(MethodInsnNode methodInsn) {
            return methodInsn.owner.contains("Connection") ||
                    methodInsn.owner.contains("PreparedStatement") ||
                    methodInsn.owner.contains("ResultSet");
        }

        private boolean isInitializationCall(MethodInsnNode methodInsn) {
            return methodInsn.name.startsWith("set") ||
                    methodInsn.name.startsWith("init") ||
                    methodInsn.name.equals("<init>");
        }

        private EjbPostCreateMethodInfo analyzeEjbPostCreateMethod(MethodNode method, ClassNode classNode) {
            String postCreateMethodSuffix = extractCreateMethodSuffix(method.name);
            List<String> parameters = extractMethodParameters(method.desc);
            return new EjbPostCreateMethodInfo(method.name, method.desc, postCreateMethodSuffix, parameters);
        }

        private HomeInterfaceMetadata analyzeHomeInterfaceCreateMethods(ClassNode classNode) {
            HomeInterfaceMetadata metadata = new HomeInterfaceMetadata(classNode.name,
                    isRemoteHomeInterface(classNode),
                    isLocalHomeInterface(classNode));

            // Analyze create methods in Home interface
            if (classNode.methods != null) {
                for (MethodNode method : classNode.methods) {
                    if (isHomeCreateMethod(method)) {
                        HomeCreateMethodInfo createMethod = analyzeHomeCreateMethod(method, classNode);
                        metadata.addCreateMethod(createMethod);
                    }
                }
            }

            return metadata;
        }

        private boolean isHomeCreateMethod(MethodNode method) {
            return method.name.startsWith(HOME_CREATE_METHOD_PREFIX) &&
                    (method.access & Opcodes.ACC_ABSTRACT) != 0 && // Abstract method in interface
                    isPublicMethod(method);
        }

        private boolean isRemoteHomeInterface(ClassNode classNode) {
            return implementsInterface(classNode, "javax/ejb/EJBHome");
        }

        private boolean isLocalHomeInterface(ClassNode classNode) {
            return implementsInterface(classNode, "javax/ejb/EJBLocalHome");
        }

        private boolean implementsInterface(ClassNode classNode, String interfaceName) {
            if (classNode.interfaces == null)
                return false;
            return classNode.interfaces.contains(interfaceName);
        }

        private HomeCreateMethodInfo analyzeHomeCreateMethod(MethodNode method, ClassNode classNode) {
            String createMethodSuffix = extractCreateMethodSuffix(method.name);
            String returnType = extractReturnType(method.desc);
            List<String> parameters = extractMethodParameters(method.desc);
            boolean isRemoteMethod = isRemoteHomeInterface(classNode);
            boolean isLocalMethod = isLocalHomeInterface(classNode);

            // Extract exception declarations
            List<String> exceptions = new ArrayList<>();
            if (method.exceptions != null) {
                exceptions = method.exceptions.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            }

            return new HomeCreateMethodInfo(method.name, method.desc, createMethodSuffix,
                    returnType, parameters, isRemoteMethod, isLocalMethod, exceptions);
        }

        private CreateMethodUsageMetadata analyzeCreateMethodUsage(ClassNode classNode) {
            CreateMethodUsageMetadata metadata = new CreateMethodUsageMetadata(classNode.name);

            // Analyze all methods for create method calls
            if (classNode.methods != null) {
                for (MethodNode method : classNode.methods) {
                    if (method.instructions == null)
                        continue;

                    List<CreateMethodCall> createCalls = findCreateMethodCalls(method, classNode);
                    if (!createCalls.isEmpty()) {
                        CreateMethodCallContext callContext = new CreateMethodCallContext(method.name,
                                classNode.name, createCalls);
                        metadata.addCallContext(callContext);
                    }
                }
            }

            return metadata;
        }

        private List<CreateMethodCall> findCreateMethodCalls(MethodNode method, ClassNode classNode) {
            List<CreateMethodCall> createCalls = new ArrayList<>();

            InsnList instructions = method.instructions;
            for (AbstractInsnNode instruction : instructions) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode methodInsn = (MethodInsnNode) instruction;

                    if (isCreateMethodCall(methodInsn)) {
                        CreateMethodCall createCall = analyzeCreateMethodCall(methodInsn, method);
                        createCalls.add(createCall);
                    }
                }
            }

            return createCalls;
        }

        private boolean isCreateMethodCall(MethodInsnNode methodInsn) {
            // Check if method name starts with "create"
            if (!methodInsn.name.startsWith("create")) {
                return false;
            }

            // Check if owner looks like an EJB Home interface
            return isLikelyEjbHomeInterface(methodInsn.owner);
        }

        private boolean isLikelyEjbHomeInterface(String className) {
            return className.endsWith("Home") ||
                    className.endsWith("LocalHome") ||
                    className.contains("Home");
        }

        private CreateMethodCall analyzeCreateMethodCall(MethodInsnNode methodInsn, MethodNode callerMethod) {
            List<String> parameters = extractMethodParameters(methodInsn.desc);

            // Analyze context around the call
            CreateCallContext context = analyzeCreateCallContext(methodInsn, callerMethod);

            return new CreateMethodCall(methodInsn.owner, methodInsn.name, methodInsn.desc,
                    callerMethod.name, parameters, context);
        }

        private CreateCallContext analyzeCreateCallContext(MethodInsnNode createCall, MethodNode callerMethod) {
            boolean hasJndiLookup = false;
            String jndiName = null;

            // Look for JNDI lookup pattern before create call
            AbstractInsnNode current = createCall.getPrevious();
            int lookback = 0;
            while (current != null && lookback < 10) {
                if (current instanceof MethodInsnNode) {
                    MethodInsnNode prevCall = (MethodInsnNode) current;
                    if (isJndiLookupCall(prevCall)) {
                        hasJndiLookup = true;
                        jndiName = extractJndiName(prevCall);
                        break;
                    }
                }
                current = current.getPrevious();
                lookback++;
            }

            // Look for exception handling around create call
            boolean hasExceptionHandling = hasExceptionHandlingAround(createCall, callerMethod);

            return new CreateCallContext(hasJndiLookup, jndiName, hasExceptionHandling);
        }

        private boolean hasExceptionHandlingAround(MethodInsnNode createCall, MethodNode callerMethod) {
            // Simplified check - would need more sophisticated try-catch analysis
            return callerMethod.tryCatchBlocks != null && !callerMethod.tryCatchBlocks.isEmpty();
        }

        private void addBeanCreateMethodResults() {
            // Write all results to JavaClassNode properties (class-centric)
            enableTag(EjbMigrationTags.EJB_CREATE_METHOD);

            if (beanMetadata.getBeanType() == EjbBeanType.ENTITY_BEAN) {
                enableTag(EjbMigrationTags.EJB_ENTITY_BEAN);
            } else if (beanMetadata.getBeanType() == EjbBeanType.SESSION_BEAN) {
                enableTag(EjbMigrationTags.EJB_SESSION_BEAN);
            }

            // Add complexity tags
            int totalCreateMethods = beanMetadata.getEjbCreateMethods().size();
            if (totalCreateMethods > 3) {
                enableTag(EjbMigrationTags.EJB_MIGRATION_COMPLEX);
            } else if (totalCreateMethods > 1) {
                enableTag(EjbMigrationTags.EJB_MIGRATION_MEDIUM);
            } else {
                enableTag(EjbMigrationTags.EJB_MIGRATION_SIMPLE);
            }

            // Add create method specific tags
            for (EjbCreateMethodInfo createMethod : beanMetadata.getEjbCreateMethods()) {
                if (createMethod.getCreateMethodType() == CreateMethodType.PARAMETERIZED) {
                    enableTag(EjbMigrationTags.EJB_PARAMETERIZED_CREATE);
                }
                if (!createMethod.getAnalysis().getInitializationPatterns().isEmpty()) {
                    enableTag(EjbMigrationTags.EJB_COMPLEX_INITIALIZATION);
                }
                if (!createMethod.getAnalysis().getDependencyLookups().isEmpty()) {
                    enableTag(EjbMigrationTags.EJB_DEPENDENCY_INJECTION_CANDIDATE);
                }
            }

            // Consolidate analysis data as single property
            setProperty("ejb.create.method.analysis", new CreateMethodAnalysisResult(
                    totalCreateMethods,
                    beanMetadata.getEjbPostCreateMethods().size(),
                    beanMetadata.getBeanType().toString(),
                    assessCreateMethodMigrationComplexity(beanMetadata),
                    beanMetadata));

            logger.debug("Detected {} ejbCreate methods in {}", totalCreateMethods, classNode.name);
        }

        private void addHomeCreateMethodResults() {
            // Write all results to JavaClassNode properties (class-centric)
            enableTag(EjbMigrationTags.EJB_HOME_INTERFACE);
            enableTag(EjbMigrationTags.EJB_CREATE_METHOD);

            if (homeMetadata.isRemoteHome()) {
                enableTag(EjbMigrationTags.EJB_REMOTE_INTERFACE);
            }
            if (homeMetadata.isLocalHome()) {
                enableTag(EjbMigrationTags.EJB_LOCAL_INTERFACE);
            }

            // Consolidate analysis data as single property
            setProperty("ejb.home.interface.analysis", new HomeInterfaceAnalysisResult(
                    homeMetadata.getCreateMethods().size(),
                    homeMetadata.isRemoteHome(),
                    homeMetadata.isLocalHome(),
                    homeMetadata));

            logger.debug("Detected Home interface with {} create methods: {}",
                    homeMetadata.getCreateMethods().size(), classNode.name);
        }

        private void addCreateMethodUsageResults() {
            // Write all results to JavaClassNode properties (class-centric)
            enableTag(EjbMigrationTags.EJB_CLIENT_CODE);
            enableTag(EjbMigrationTags.EJB_CREATE_METHOD_USAGE);

            int totalCreateCalls = usageMetadata.getCallContexts().stream()
                    .mapToInt(context -> context.getCreateMethodCalls().size())
                    .sum();

            boolean hasJndiLookups = usageMetadata.getCallContexts().stream()
                    .flatMap(context -> context.getCreateMethodCalls().stream())
                    .anyMatch(call -> call.getCallContext().hasJndiLookup());

            if (hasJndiLookups) {
                enableTag(EjbMigrationTags.EJB_JNDI_LOOKUP);
                enableTag(EjbMigrationTags.EJB_DEPENDENCY_INJECTION_CANDIDATE);
            }

            // Consolidate analysis data as single property
            setProperty("ejb.create.method.usage.analysis", new CreateMethodUsageAnalysisResult(
                    totalCreateCalls,
                    countReferencedHomeInterfaces(usageMetadata),
                    hasJndiLookups,
                    usageMetadata));

            logger.debug("Detected {} create method calls in client class: {}", totalCreateCalls, classNode.name);
        }

        private int countReferencedHomeInterfaces(CreateMethodUsageMetadata usageMetadata) {
            return (int) usageMetadata.getCallContexts().stream()
                    .flatMap(context -> context.getCreateMethodCalls().stream())
                    .map(CreateMethodCall::getTargetClass)
                    .distinct()
                    .count();
        }

        private String assessCreateMethodMigrationComplexity(CreateMethodMetadata metadata) {
            int complexityScore = 0;

            // Base complexity for having create methods
            complexityScore += 2;

            // Add complexity for each create method
            complexityScore += metadata.getEjbCreateMethods().size();

            // Add complexity for initialization patterns
            for (EjbCreateMethodInfo createMethod : metadata.getEjbCreateMethods()) {
                complexityScore += createMethod.getAnalysis().getInitializationPatterns().size();
                complexityScore += createMethod.getAnalysis().getDependencyLookups().size();
                if (!createMethod.getAnalysis().getDatabaseOperations().isEmpty()) {
                    complexityScore += 2;
                }
            }

            // Unmatched ejbPostCreate methods add complexity
            int unmatchedPostCreateMethods = metadata.getEjbPostCreateMethods().size() -
                    Math.min(metadata.getEjbCreateMethods().size(), metadata.getEjbPostCreateMethods().size());
            complexityScore += unmatchedPostCreateMethods * 2;

            // Classify complexity
            if (complexityScore <= 5) {
                return "LOW";
            } else if (complexityScore <= 12) {
                return "MEDIUM";
            } else {
                return "HIGH";
            }
        }
    }

    // Supporting enums and data classes (unchanged from original)
    public enum EjbBeanType {
        ENTITY_BEAN, SESSION_BEAN, UNKNOWN
    }

    public enum CreateMethodType {
        DEFAULT_CREATE, // ejbCreate() with no parameters
        PARAMETERIZED, // ejbCreate(...) with parameters
        NAMED_CREATE // ejbCreateBySomething(...)
    }

    /**
     * Consolidated analysis result for EJB create method data
     */
    public static class CreateMethodAnalysisResult {
        private final int createMethodCount;
        private final int postCreateMethodCount;
        private final String beanType;
        private final String migrationComplexity;
        private final CreateMethodMetadata metadata;

        public CreateMethodAnalysisResult(int createMethodCount, int postCreateMethodCount,
                String beanType, String migrationComplexity,
                CreateMethodMetadata metadata) {
            this.createMethodCount = createMethodCount;
            this.postCreateMethodCount = postCreateMethodCount;
            this.beanType = beanType;
            this.migrationComplexity = migrationComplexity;
            this.metadata = metadata;
        }
    }

    /**
     * Consolidated analysis result for EJB home interface data
     */
    public static class HomeInterfaceAnalysisResult {
        private final int createMethodCount;
        private final boolean isRemoteHome;
        private final boolean isLocalHome;
        private final HomeInterfaceMetadata metadata;

        public HomeInterfaceAnalysisResult(int createMethodCount, boolean isRemoteHome,
                boolean isLocalHome, HomeInterfaceMetadata metadata) {
            this.createMethodCount = createMethodCount;
            this.isRemoteHome = isRemoteHome;
            this.isLocalHome = isLocalHome;
            this.metadata = metadata;
        }
}

    /**
     * Consolidated analysis result for EJB create method usage data
     */
    public static class CreateMethodUsageAnalysisResult {
        private final int createMethodCallCount;
        private final int referencedHomeInterfaceCount;
        private final boolean hasJndiLookups;
        private final CreateMethodUsageMetadata metadata;

        public CreateMethodUsageAnalysisResult(int createMethodCallCount, int referencedHomeInterfaceCount,
                boolean hasJndiLookups, CreateMethodUsageMetadata metadata) {
            this.createMethodCallCount = createMethodCallCount;
            this.referencedHomeInterfaceCount = referencedHomeInterfaceCount;
            this.hasJndiLookups = hasJndiLookups;
            this.metadata = metadata;
        }
    }

    // Data classes
    public static class CreateMethodMetadata {
        private final String className;
        private final EjbBeanType beanType;
        private final List<EjbCreateMethodInfo> ejbCreateMethods = new ArrayList<>();
        private final List<EjbPostCreateMethodInfo> ejbPostCreateMethods = new ArrayList<>();

        public CreateMethodMetadata(String className, EjbBeanType beanType) {
            this.className = className;
            this.beanType = beanType;
        }

        public boolean hasCreateMethods() {
            return !ejbCreateMethods.isEmpty();
        }

        public void addEjbCreateMethod(EjbCreateMethodInfo method) {
            ejbCreateMethods.add(method);
        }

        public void addEjbPostCreateMethod(EjbPostCreateMethodInfo method) {
            ejbPostCreateMethods.add(method);
        }

        public String getClassName() {
            return className;
        }

        public EjbBeanType getBeanType() {
            return beanType;
        }

        public List<EjbCreateMethodInfo> getEjbCreateMethods() {
            return ejbCreateMethods;
        }

        public List<EjbPostCreateMethodInfo> getEjbPostCreateMethods() {
            return ejbPostCreateMethods;
        }
    }

    public static class EjbCreateMethodInfo {
        private final String methodName;
        private final String methodDescriptor;
        private final String createMethodSuffix;
        private final String returnType;
        private final List<String> parameters;
        private final CreateMethodType createMethodType;
        private final CreateMethodAnalysis analysis;

        public EjbCreateMethodInfo(String methodName, String methodDescriptor, String createMethodSuffix,
                String returnType, List<String> parameters, CreateMethodType createMethodType,
                CreateMethodAnalysis analysis) {
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.createMethodSuffix = createMethodSuffix;
            this.returnType = returnType;
            this.parameters = List.copyOf(parameters);
            this.createMethodType = createMethodType;
            this.analysis = analysis;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getMethodDescriptor() {
            return methodDescriptor;
        }

        public String getCreateMethodSuffix() {
            return createMethodSuffix;
        }

        public String getReturnType() {
            return returnType;
        }

        public List<String> getParameters() {
            return parameters;
        }

        public CreateMethodType getCreateMethodType() {
            return createMethodType;
        }

        public CreateMethodAnalysis getAnalysis() {
            return analysis;
        }
    }

    public static class EjbPostCreateMethodInfo {
        private final String methodName;
        private final String methodDescriptor;
        private final String postCreateMethodSuffix;
        private final List<String> parameters;

        public EjbPostCreateMethodInfo(String methodName, String methodDescriptor,
                String postCreateMethodSuffix, List<String> parameters) {
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.postCreateMethodSuffix = postCreateMethodSuffix;
            this.parameters = List.copyOf(parameters);
        }

        public String getMethodName() {
            return methodName;
        }

        public String getMethodDescriptor() {
            return methodDescriptor;
        }

        public String getPostCreateMethodSuffix() {
            return postCreateMethodSuffix;
        }

        public List<String> getParameters() {
            return parameters;
        }
    }

    public static class CreateMethodAnalysis {
        private final List<String> initializationPatterns = new ArrayList<>();
        private final List<String> fieldAssignments = new ArrayList<>();
        private final List<String> dependencyLookups = new ArrayList<>();
        private final List<String> databaseOperations = new ArrayList<>();

        public void addInitializationPattern(String pattern) {
            initializationPatterns.add(pattern);
        }

        public void addFieldAssignment(String fieldName) {
            fieldAssignments.add(fieldName);
        }

        public void addDependencyLookup(String jndiName) {
            dependencyLookups.add(jndiName);
        }

        public void addDatabaseOperation(String operation) {
            databaseOperations.add(operation);
        }

        public List<String> getInitializationPatterns() {
            return initializationPatterns;
        }

        public List<String> getFieldAssignments() {
            return fieldAssignments;
        }

        public List<String> getDependencyLookups() {
            return dependencyLookups;
        }

        public List<String> getDatabaseOperations() {
            return databaseOperations;
        }
    }

    public static class HomeInterfaceMetadata {
        private final String interfaceName;
        private final boolean isRemoteHome;
        private final boolean isLocalHome;
        private final List<HomeCreateMethodInfo> createMethods = new ArrayList<>();

        public HomeInterfaceMetadata(String interfaceName, boolean isRemoteHome, boolean isLocalHome) {
            this.interfaceName = interfaceName;
            this.isRemoteHome = isRemoteHome;
            this.isLocalHome = isLocalHome;
        }

        public boolean hasCreateMethods() {
            return !createMethods.isEmpty();
        }

        public void addCreateMethod(HomeCreateMethodInfo method) {
            createMethods.add(method);
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public boolean isRemoteHome() {
            return isRemoteHome;
        }

        public boolean isLocalHome() {
            return isLocalHome;
        }

        public List<HomeCreateMethodInfo> getCreateMethods() {
            return createMethods;
        }
    }

    public static class HomeCreateMethodInfo {
        private final String methodName;
        private final String methodDescriptor;
        private final String createMethodSuffix;
        private final String returnType;
        private final List<String> parameters;
        private final boolean isRemoteMethod;
        private final boolean isLocalMethod;
        private final List<String> declaredExceptions;

        public HomeCreateMethodInfo(String methodName, String methodDescriptor, String createMethodSuffix,
                String returnType, List<String> parameters, boolean isRemoteMethod,
                boolean isLocalMethod, List<String> declaredExceptions) {
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.createMethodSuffix = createMethodSuffix;
            this.returnType = returnType;
            this.parameters = List.copyOf(parameters);
            this.isRemoteMethod = isRemoteMethod;
            this.isLocalMethod = isLocalMethod;
            this.declaredExceptions = List.copyOf(declaredExceptions);
        }

        public String getMethodName() {
            return methodName;
        }

        public String getMethodDescriptor() {
            return methodDescriptor;
        }

        public String getCreateMethodSuffix() {
            return createMethodSuffix;
        }

        public String getReturnType() {
            return returnType;
        }

        public List<String> getParameters() {
            return parameters;
        }

        public boolean isRemoteMethod() {
            return isRemoteMethod;
        }

        public boolean isLocalMethod() {
            return isLocalMethod;
        }

        public List<String> getDeclaredExceptions() {
            return declaredExceptions;
        }
    }

    public static class CreateMethodUsageMetadata {
        private final String className;
        private final List<CreateMethodCallContext> callContexts = new ArrayList<>();

        public CreateMethodUsageMetadata(String className) {
            this.className = className;
        }

        public boolean hasCreateMethodCalls() {
            return !callContexts.isEmpty();
        }

        public void addCallContext(CreateMethodCallContext context) {
            callContexts.add(context);
        }

        public String getClassName() {
            return className;
        }

        public List<CreateMethodCallContext> getCallContexts() {
            return callContexts;
        }
    }

    public static class CreateMethodCallContext {
        private final String callerMethod;
        private final String callerClass;
        private final List<CreateMethodCall> createMethodCalls;

        public CreateMethodCallContext(String callerMethod, String callerClass,
                List<CreateMethodCall> createMethodCalls) {
            this.callerMethod = callerMethod;
            this.callerClass = callerClass;
            this.createMethodCalls = List.copyOf(createMethodCalls);
        }

        public String getCallerMethod() {
            return callerMethod;
        }

        public String getCallerClass() {
            return callerClass;
        }

        public List<CreateMethodCall> getCreateMethodCalls() {
            return createMethodCalls;
        }
    }

    public static class CreateMethodCall {
        private final String targetClass;
        private final String methodName;
        private final String methodDescriptor;
        private final String callerMethod;
        private final List<String> parameters;
        private final CreateCallContext callContext;

        public CreateMethodCall(String targetClass, String methodName, String methodDescriptor,
                String callerMethod, List<String> parameters, CreateCallContext callContext) {
            this.targetClass = targetClass;
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.callerMethod = callerMethod;
            this.parameters = List.copyOf(parameters);
            this.callContext = callContext;
        }

        public String getTargetClass() {
            return targetClass;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getMethodDescriptor() {
            return methodDescriptor;
        }

        public String getCallerMethod() {
            return callerMethod;
        }

        public List<String> getParameters() {
            return parameters;
        }

        public CreateCallContext getCallContext() {
            return callContext;
        }
    }

    public static class CreateCallContext {
        private final boolean hasJndiLookup;
        private final String jndiName;
        private final boolean hasExceptionHandling;

        public CreateCallContext(boolean hasJndiLookup, String jndiName, boolean hasExceptionHandling) {
            this.hasJndiLookup = hasJndiLookup;
            this.jndiName = jndiName;
            this.hasExceptionHandling = hasExceptionHandling;
        }

        public boolean hasJndiLookup() {
            return hasJndiLookup;
        }

        public String getJndiName() {
            return jndiName;
        }

        public boolean hasExceptionHandling() {
            return hasExceptionHandling;
        }
    }
}
