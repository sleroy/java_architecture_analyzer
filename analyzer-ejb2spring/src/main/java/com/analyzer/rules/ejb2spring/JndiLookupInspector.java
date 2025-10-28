package com.analyzer.rules.ejb2spring;

import ch.qos.logback.classic.Logger;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.dev.inspectors.source.AbstractSourceFileInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inspector that detects JNDI lookup patterns for EJB-to-Spring migration
 * analysis.
 * Identifies JNDI usage that needs to be replaced with Spring dependency
 * injection.
 */
@InspectorDependencies(requires = {InspectorTags.TAG_JAVA_DETECTED}, produces = {
        JndiLookupInspector.TAGS.TAG_USES_JNDI,
        JndiLookupInspector.TAGS.TAG_JNDI_COMPLEXITY})
public class JndiLookupInspector extends AbstractSourceFileInspector {

    private static final Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(JndiLookupInspector.class);
    private final ClassNodeRepository classNodeRepository;

    public JndiLookupInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    // Removed supports() method - trust @InspectorDependencies completely

    @Override
    protected void analyzeSourceFile(ProjectFile projectFile, ResourceLocation sourceLocation,
                                     NodeDecorator<ProjectFile> projectFileDecorator) throws java.io.IOException {
        String content = readFileContent(sourceLocation);
        if (content == null || content.trim().isEmpty()) {
            logger.error("Empty source file: {}", sourceLocation);
            return;
        }

        // Parse Java content using JavaParser
        try {
            com.github.javaparser.JavaParser parser = new com.github.javaparser.JavaParser();
            com.github.javaparser.ParseResult<CompilationUnit> parseResult = parser.parse(content);

            if (!parseResult.isSuccessful()) {
                projectFileDecorator.error("Failed to parse Java file");
                return;
            }

            CompilationUnit cu = parseResult.getResult().get();
            analyzeCompilationUnit(cu, projectFile, projectFileDecorator);
        } catch (Exception e) {
            projectFileDecorator.error("Error parsing Java file: " + e.getMessage());
        }
    }

    private void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
                                        NodeDecorator<ProjectFile> nodeDecorator) {
        classNodeRepository.getOrCreateClassNode(cu).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            JndiLookupDetector detector = new JndiLookupDetector();
            cu.accept(detector, null);

            if (detector.hasJndiLookups()) {
                JndiLookupInfo info = detector.getJndiLookupInfo();

                // Honor produces contract - set tags on ProjectFile (dependency chain)
                setProducedTags(nodeDecorator, info);

                // Store detailed analysis data as consolidated POJO on ClassNode (no toJson() -
                // direct POJO storage)
                classNode.setProperty("jndi_lookup_analysis", info);
                return;
            }

        });
    }

    @Override
    public String getName() {
        return "JNDI Lookup Pattern Detector";
    }

    /**
     * Honor produces contract - set tags on ProjectFile as required
     * by @InspectorDependencies
     */
    private void setProducedTags(NodeDecorator<ProjectFile> projectFileDecorator, JndiLookupInfo info) {
        // Set the main produced tags on ProjectFile (dependency chain)
        projectFileDecorator.setProperty(TAGS.TAG_USES_JNDI, true);
        projectFileDecorator.setProperty(TAGS.TAG_JNDI_COMPLEXITY, info.getMigrationComplexity());
    }

    public static class TAGS {
        public static final String TAG_USES_JNDI = "jndi_lookup_inspector.uses_jndi";
        public static final String TAG_JNDI_COMPLEXITY = "jndi_lookup_inspector.complexity";
    }

    /**
     * Visitor that detects JNDI lookup patterns by analyzing the AST.
     * Detects JNDI usage through:
     * 1. InitialContext creation and lookup calls
     * 2. JNDI name patterns (java:comp/env, ejb/, etc.)
     * 3. Service Locator patterns
     */
    private static class JndiLookupDetector extends VoidVisitorAdapter<Void> {
        // Predefined collections to replace long equals chains
        private static final Set<String> EJB_PREFIXES = Set.of("java:comp/env/ejb/");
        private static final Set<String> JDBC_PREFIXES = Set.of("java:comp/env/jdbc/");
        private static final Set<String> JMS_PREFIXES = Set.of("java:comp/env/jms/");
        private static final Set<String> ENV_PREFIXES = Set.of("java:comp/env/");
        private static final Set<String> EJB_PATTERNS = Set.of("ejb/");
        private static final Set<String> JDBC_PATTERNS = Set.of("jdbc/");
        private static final Set<String> JMS_PATTERNS = Set.of("jms/");
        private boolean hasJndiLookups = false;
        private JndiLookupInfo jndiInfo = new JndiLookupInfo();

        public boolean hasJndiLookups() {
            return hasJndiLookups;
        }

        public JndiLookupInfo getJndiLookupInfo() {
            return jndiInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            jndiInfo.className = classDecl.getNameAsString();
            super.visit(classDecl, arg);
        }

        @Override
        public void visit(ObjectCreationExpr objectCreation, Void arg) {
            String typeName = objectCreation.getType().getNameAsString();
            if ("InitialContext".equals(typeName) ||
                    "javax.naming.InitialContext".equals(typeName)) {
                hasJndiLookups = true;
                jndiInfo.hasInitialContext = true;
                jndiInfo.initialContextCount++;
            }
            super.visit(objectCreation, arg);
        }

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            String methodName = methodCall.getNameAsString();

            if ("lookup".equals(methodName)) {
                hasJndiLookups = true;
                jndiInfo.lookupCallCount++;

                // Extract JNDI name if it's a string literal
                if (methodCall.getArguments().size() > 0 &&
                        methodCall.getArguments().get(0) instanceof StringLiteralExpr) {
                    StringLiteralExpr jndiName = (StringLiteralExpr) methodCall.getArguments().get(0);
                    String name = jndiName.getValue();
                    jndiInfo.jndiNames.add(name);

                    // Categorize JNDI names
                    categorizeJndiName(name);
                }
            } else if ("bind".equals(methodName) || "rebind".equals(methodName)) {
                hasJndiLookups = true;
                jndiInfo.bindCallCount++;
            } else if ("unbind".equals(methodName)) {
                hasJndiLookups = true;
                jndiInfo.unbindCallCount++;
            }

            super.visit(methodCall, arg);
        }

        private void categorizeJndiName(String jndiName) {
            if (EJB_PREFIXES.stream().anyMatch(jndiName::startsWith)) {
                jndiInfo.ejbReferences.add(jndiName);
            } else if (JDBC_PREFIXES.stream().anyMatch(jndiName::startsWith)) {
                jndiInfo.dataSourceReferences.add(jndiName);
            } else if (JMS_PREFIXES.stream().anyMatch(jndiName::startsWith)) {
                jndiInfo.jmsReferences.add(jndiName);
            } else if (ENV_PREFIXES.stream().anyMatch(jndiName::startsWith)) {
                jndiInfo.environmentReferences.add(jndiName);
            } else if (EJB_PATTERNS.stream().anyMatch(jndiName::contains)) {
                jndiInfo.ejbReferences.add(jndiName);
            } else if (JDBC_PATTERNS.stream().anyMatch(jndiName::contains)) {
                jndiInfo.dataSourceReferences.add(jndiName);
            } else if (JMS_PATTERNS.stream().anyMatch(jndiName::contains)) {
                jndiInfo.jmsReferences.add(jndiName);
            } else {
                jndiInfo.otherReferences.add(jndiName);
            }
        }
    }

    /**
     * Data class to hold JNDI lookup analysis information
     */
    public static class JndiLookupInfo {
        public String className;
        public boolean hasInitialContext;
        public int initialContextCount;
        public int lookupCallCount;
        public int bindCallCount;
        public int unbindCallCount;
        public Set<String> jndiNames = new HashSet<>();
        public List<String> ejbReferences = new ArrayList<>();
        public List<String> dataSourceReferences = new ArrayList<>();
        public List<String> jmsReferences = new ArrayList<>();
        public List<String> environmentReferences = new ArrayList<>();
        public List<String> otherReferences = new ArrayList<>();

        public String getMigrationComplexity() {
            int totalReferences = ejbReferences.size() + dataSourceReferences.size() +
                    jmsReferences.size() + environmentReferences.size() +
                    otherReferences.size();
            if (totalReferences > 10) {
                return "HIGH";
            } else if (totalReferences > 5) {
                return "MEDIUM";
            } else {
                return "LOW";
            }
        }

        // Removed toJson() method - storing POJO directly instead of JSON serialization
    }

}
