package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractSourceFileInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspector I-0805: Declarative Transaction Inspector
 * 
 * Analyzes declarative transaction attributes defined in EJB deployment
 * descriptors
 * (ejb-jar.xml) and maps them to equivalent Spring @Transactional annotations.
 * 
 * Detects EJB transaction attributes (Required, RequiresNew, Supports, etc.)
 * and
 * provides Spring migration recommendations with appropriate propagation
 * settings.
 */
@InspectorDependencies(requires = { EjbMigrationTags.EJB_DEPLOYMENT_DESCRIPTOR }, produces = {
        EjbMigrationTags.EJB_DECLARATIVE_TRANSACTION,
        EjbMigrationTags.EJB_CONTAINER_MANAGED_TRANSACTION,
        EjbMigrationTags.SPRING_TRANSACTION_CONVERSION
})
public class DeclarativeTransactionJavaSourceInspector extends AbstractSourceFileInspector {

    private final ClassNodeRepository classNodeRepository;

    @Inject
    public DeclarativeTransactionJavaSourceInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    

    @Override
    public String getName() {
        return "I-0805 Declarative Transaction Inspector";
    }

    @Override
    public boolean supports(ProjectFile file) {
        return super.supports(file);
    }

    @Override
    protected void analyzeSourceFile(ProjectFile projectFile, ResourceLocation sourceLocation,
            ProjectFileDecorator projectFileDecorator) throws IOException {
        classNodeRepository.getOrCreateClassNodeByFqn(projectFile.getFullyQualifiedName()).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            try {
                String content = readFileContent(sourceLocation);
                if (content == null || content.trim().isEmpty()) {
                    projectFileDecorator.notApplicable();
                    return;
                }

                Document doc = parseXmlDocument(content);
                List<TransactionConfiguration> configs = extractTransactionConfigurations(doc);

                if (configs.isEmpty()) {
                    projectFileDecorator.notApplicable();
                    return;
                }

                // Honor produces contract: set tags on ProjectFile
                projectFile.setTag(EjbMigrationTags.EJB_DECLARATIVE_TRANSACTION, true);
                projectFile.setTag(EjbMigrationTags.EJB_CONTAINER_MANAGED_TRANSACTION, true);
                projectFile.setTag(EjbMigrationTags.SPRING_TRANSACTION_CONVERSION, true);

                // Store consolidated analysis data as single property (guideline #5)
                int totalConfigs = configs.size();
                DeclarativeTransactionAnalysis analysis = new DeclarativeTransactionAnalysis(
                    totalConfigs,
                    assessOverallComplexity(configs),
                    generateMigrationRecommendations(configs),
                    configs
                );
                classNode.setProperty("declarative_transaction.analysis", analysis.toJson());

                projectFileDecorator.setTag("declarative_transaction.analysis",
                        "Found " + totalConfigs + " declarative transaction configurations");

            } catch (Exception e) {
                projectFileDecorator.error("Failed to parse ejb-jar.xml: " + e.getMessage());
            }
        });
    }

    private Document parseXmlDocument(String content) throws ParserConfigurationException,
            SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        // Disable external entity processing for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(content.getBytes()));
    }

    private List<TransactionConfiguration> extractTransactionConfigurations(Document doc) {
        List<TransactionConfiguration> configs = new ArrayList<>();

        // Parse container-transaction elements
        NodeList containerTransactions = doc.getElementsByTagName("container-transaction");

        for (int i = 0; i < containerTransactions.getLength(); i++) {
            Element containerTx = (Element) containerTransactions.item(i);
            configs.addAll(parseContainerTransaction(containerTx));
        }

        return configs;
    }

    private List<TransactionConfiguration> parseContainerTransaction(Element containerTx) {
        List<TransactionConfiguration> configs = new ArrayList<>();

        // Get transaction attribute
        String transAttr = getElementText(containerTx, "trans-attribute");
        if (transAttr == null || transAttr.trim().isEmpty()) {
            return configs;
        }

        EJBTransactionAttribute ejbAttr;
        try {
            // Convert camelCase XML values to UPPER_SNAKE_CASE enum values
            String normalizedAttr = normalizeTransactionAttribute(transAttr);
            ejbAttr = EJBTransactionAttribute.valueOf(normalizedAttr);
        } catch (IllegalArgumentException e) {
            return configs; // Skip unknown transaction attributes
        }

        // Get method elements
        NodeList methods = containerTx.getElementsByTagName("method");

        for (int i = 0; i < methods.getLength(); i++) {
            Element method = (Element) methods.item(i);

            String beanName = getElementText(method, "ejb-name");
            String methodName = getElementText(method, "method-name");

            if (beanName == null || methodName == null) {
                continue;
            }

            // Handle method parameters for overloaded methods
            List<String> methodParams = parseMethodParams(method);

            TransactionConfiguration config = new TransactionConfiguration();
            config.setBeanName(beanName);
            config.setMethodName(methodName);
            config.setMethodParams(methodParams);
            config.setEjbAttribute(ejbAttr);
            config.setSpringPropagation(mapToSpringPropagation(ejbAttr));
            config.setReadOnly(isReadOnlyMethod(methodName));

            configs.add(config);
        }

        return configs;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList elements = parent.getElementsByTagName(tagName);
        if (elements.getLength() > 0) {
            return elements.item(0).getTextContent().trim();
        }
        return null;
    }

    private List<String> parseMethodParams(Element method) {
        List<String> params = new ArrayList<>();
        NodeList paramElements = method.getElementsByTagName("method-param");

        for (int i = 0; i < paramElements.getLength(); i++) {
            params.add(paramElements.item(i).getTextContent().trim());
        }

        return params;
    }

    /**
     * Normalizes XML transaction attribute names to match enum values.
     * Converts camelCase to UPPER_SNAKE_CASE.
     */
    private String normalizeTransactionAttribute(String xmlAttr) {
        if (xmlAttr == null) {
            return null;
        }
        
        // Handle common XML transaction attribute mappings
        return switch (xmlAttr) {
            case "Required" -> "REQUIRED";
            case "RequiresNew" -> "REQUIRES_NEW";
            case "Supports" -> "SUPPORTS";
            case "NotSupported" -> "NOT_SUPPORTED";
            case "Mandatory" -> "MANDATORY";
            case "Never" -> "NEVER";
            default ->
                // Fallback: convert to uppercase and handle camelCase
                    xmlAttr.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        };
    }

    private SpringPropagation mapToSpringPropagation(EJBTransactionAttribute ejbAttr) {
        return switch (ejbAttr) {
            case REQUIRED -> SpringPropagation.REQUIRED;
            case REQUIRES_NEW -> SpringPropagation.REQUIRES_NEW;
            case SUPPORTS -> SpringPropagation.SUPPORTS;
            case NOT_SUPPORTED -> SpringPropagation.NOT_SUPPORTED;
            case MANDATORY -> SpringPropagation.MANDATORY;
            case NEVER -> SpringPropagation.NEVER;
        };
    }

    private boolean isReadOnlyMethod(String methodName) {
        // Heuristic for read-only methods
        return methodName.startsWith("find") ||
                methodName.startsWith("get") ||
                methodName.startsWith("is") ||
                methodName.startsWith("has") ||
                methodName.startsWith("count") ||
                methodName.startsWith("list") ||
                methodName.startsWith("search");
    }

    private long calculateComplexityScore(TransactionConfiguration config) {
        long score = 0;

        // Complex transaction attributes add to complexity
        switch (config.getEjbAttribute()) {
            case NOT_SUPPORTED:
            case NEVER:
                score += 2;
                break;
            case MANDATORY:
            case SUPPORTS:
                score += 1;
                break;
            default:
                score += 0;
        }

        // Method parameters add complexity
        if (!config.getMethodParams().isEmpty()) {
            score += 1;
        }

        return score;
    }

    private String assessOverallComplexity(List<TransactionConfiguration> configs) {
        if (configs.isEmpty()) {
            return "NONE";
        }

        long totalComplexity = configs.stream()
                .mapToLong(this::calculateComplexityScore)
                .sum();

        double avgComplexity = (double) totalComplexity / configs.size();

        if (avgComplexity >= 2.0) {
            return "HIGH";
        } else if (avgComplexity >= 1.0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private List<String> generateMigrationRecommendations(List<TransactionConfiguration> configs) {
        List<String> recommendations = new ArrayList<>();

        Map<EJBTransactionAttribute, Long> attrCounts = new HashMap<>();
        for (TransactionConfiguration config : configs) {
            attrCounts.merge(config.getEjbAttribute(), 1L, Long::sum);
        }

        for (Map.Entry<EJBTransactionAttribute, Long> entry : attrCounts.entrySet()) {
            EJBTransactionAttribute attr = entry.getKey();
            Long count = entry.getValue();

            switch (attr) {
                case REQUIRED:
                    recommendations.add("Convert " + count + " REQUIRED transactions to Spring @Transactional default");
                    break;
                case REQUIRES_NEW:
                    recommendations
                            .add("Convert " + count + " REQUIRES_NEW to @Transactional(propagation = REQUIRES_NEW)");
                    break;
                case NOT_SUPPORTED:
                    recommendations.add("Review " + count
                            + " NOT_SUPPORTED methods - use @Transactional(propagation = NOT_SUPPORTED)");
                    break;
                case NEVER:
                    recommendations
                            .add("Validate " + count + " NEVER constraints - use @Transactional(propagation = NEVER)");
                    break;
                case SUPPORTS:
                    recommendations.add("Convert " + count + " SUPPORTS to @Transactional(propagation = SUPPORTS)");
                    break;
                case MANDATORY:
                    recommendations
                            .add("Ensure " + count + " MANDATORY methods use @Transactional(propagation = MANDATORY)");
                    break;
            }
        }

        long readOnlyCount = configs.stream()
                .mapToLong(config -> config.isReadOnly() ? 1 : 0)
                .sum();

        if (readOnlyCount > 0) {
            recommendations.add("Add readOnly = true to " + readOnlyCount + " query methods for optimization");
        }

        return recommendations;
    }

    /**
     * Consolidated analysis result for declarative transaction data (guideline #5)
     */
    public static class DeclarativeTransactionAnalysis {
        private final int configCount;
        private final String migrationComplexity;
        private final List<String> recommendations;
        private final List<TransactionConfiguration> configurations;

        public DeclarativeTransactionAnalysis(int configCount, String migrationComplexity, 
                                            List<String> recommendations, 
                                            List<TransactionConfiguration> configurations) {
            this.configCount = configCount;
            this.migrationComplexity = migrationComplexity;
            this.recommendations = recommendations;
            this.configurations = configurations;
        }

        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"configCount\":").append(configCount).append(",");
            json.append("\"migrationComplexity\":\"").append(migrationComplexity).append("\",");
            json.append("\"recommendations\":[");
            for (int i = 0; i < recommendations.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(recommendations.get(i)).append("\"");
            }
            json.append("],");
            json.append("\"configurations\":[");

            for (int i = 0; i < configurations.size(); i++) {
                TransactionConfiguration config = configurations.get(i);
                if (i > 0) json.append(",");

                json.append("{");
                json.append("\"beanName\":\"").append(config.getBeanName()).append("\",");
                json.append("\"methodName\":\"").append(config.getMethodName()).append("\",");
                json.append("\"ejbAttribute\":\"").append(config.getEjbAttribute().name()).append("\",");
                json.append("\"springPropagation\":\"").append(config.getSpringPropagation().name()).append("\",");
                json.append("\"readOnly\":").append(config.isReadOnly());

                if (!config.getMethodParams().isEmpty()) {
                    json.append(",\"methodParams\":[");
                    for (int j = 0; j < config.getMethodParams().size(); j++) {
                        if (j > 0) json.append(",");
                        json.append("\"").append(config.getMethodParams().get(j)).append("\"");
                    }
                    json.append("]");
                }
                json.append("}");
            }

            json.append("]}");
            return json.toString();
        }
    }

    /**
     * Data class representing a declarative transaction configuration
     */
    public static class TransactionConfiguration {
        private String beanName;
        private String methodName;
        private List<String> methodParams = new ArrayList<>();
        private EJBTransactionAttribute ejbAttribute;
        private SpringPropagation springPropagation;
        private boolean readOnly;

        public String getBeanName() {
            return beanName;
        }

        public void setBeanName(String beanName) {
            this.beanName = beanName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public List<String> getMethodParams() {
            return methodParams;
        }

        public void setMethodParams(List<String> methodParams) {
            this.methodParams = methodParams;
        }

        public EJBTransactionAttribute getEjbAttribute() {
            return ejbAttribute;
        }

        public void setEjbAttribute(EJBTransactionAttribute ejbAttribute) {
            this.ejbAttribute = ejbAttribute;
        }

        public SpringPropagation getSpringPropagation() {
            return springPropagation;
        }

        public void setSpringPropagation(SpringPropagation springPropagation) {
            this.springPropagation = springPropagation;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }
    }

    /**
     * EJB transaction attribute types as defined in EJB specification
     */
    public enum EJBTransactionAttribute {
        REQUIRED,
        REQUIRES_NEW,
        SUPPORTS,
        NOT_SUPPORTED,
        MANDATORY,
        NEVER
    }

    /**
     * Spring transaction propagation types for migration mapping
     */
    public enum SpringPropagation {
        REQUIRED,
        REQUIRES_NEW,
        SUPPORTS,
        NOT_SUPPORTED,
        MANDATORY,
        NEVER
    }
}
