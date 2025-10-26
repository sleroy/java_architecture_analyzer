package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.GraphEdge;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractTextFileInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comprehensive inspector that analyzes ejb-jar.xml deployment descriptors.
 * 
 * <p>This inspector performs complete analysis of EJB deployment descriptors including:
 * <ul>
 *   <li>Parsing bean definitions (session, entity, message-driven beans)</li>
 *   <li>Extracting transaction configurations from assembly-descriptor</li>
 *   <li>Creating ClassNodes for beans and their interfaces</li>
 *   <li>Creating graph edges modeling EJB relationships</li>
 *   <li>Attaching deployment descriptor metadata to bean ClassNodes</li>
 *   <li>Storing summary statistics on the XML ProjectFile</li>
 * </ul>
 * 
 * <p>Configuration files are not Java classes, so this inspector stores summary
 * data on ProjectFile while enriching actual bean ClassNodes with detailed metadata
 * and creating proper graph relationships.
 */
@InspectorDependencies(need = {  }, produces = {
        EjbDeploymentDescriptorInspector.TAGS.TAG_EJB_JAR_ANALYSIS,
        EjbMigrationTags.EJB_DECLARATIVE_TRANSACTION,
        EjbMigrationTags.EJB_CONTAINER_MANAGED_TRANSACTION,
        EjbMigrationTags.SPRING_TRANSACTION_CONVERSION
})
public class EjbDeploymentDescriptorInspector extends AbstractTextFileInspector {

    public static final String EJB_JAR_XML = "ejb-jar.xml";
    private static final Logger logger = LoggerFactory.getLogger(EjbDeploymentDescriptorInspector.class);

    private final ClassNodeRepository classNodeRepository;
    private final GraphRepository graphRepository;

    @Inject
    public EjbDeploymentDescriptorInspector(ResourceResolver resourceResolver,
                                           ClassNodeRepository classNodeRepository,
                                           GraphRepository graphRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
        this.graphRepository = graphRepository;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        return super.supports(projectFile) && EJB_JAR_XML.equals(projectFile.getFileName());
    }

    @Override
    public String getName() {
        return "EJB Deployment Descriptor Inspector";
    }

    @Override
    protected void processContent(String content, ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
        try {
            // Phase 1: Parse XML
            Document doc = parseXmlDocument(content);
            
            // Phase 2: Extract bean definitions and transaction configurations
            Map<String, BeanMetadata> beansById = extractBeanDefinitions(doc);
            List<TransactionConfiguration> transactionConfigs = extractTransactionConfigurations(doc);
            
            // Phase 3: Enrich graph with ClassNodes and edges
            int enrichedBeans = enrichGraph(beansById, transactionConfigs);
            
            // Phase 4: Store summary on XML ProjectFile
            storeSummary(projectFile, projectFileDecorator, beansById, transactionConfigs, enrichedBeans);

        } catch (Exception e) {
            String filename = projectFile != null ? projectFile.getRelativePath() : "unknown file";
            String errorMsg = String.format("Error processing ejb-jar.xml '%s': %s", filename, e.getMessage());
            logger.warn(errorMsg, e);
            projectFileDecorator.error(errorMsg);
        }
    }

    private Document parseXmlDocument(String xmlContent) throws Exception {
        String cleanedXml = preprocessXmlContent(xmlContent);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);
        
        // Security: Disable external entity processing
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
            @Override
            public void warning(org.xml.sax.SAXParseException e) {
                logger.warn("XML parsing warning at line {}: {}", e.getLineNumber(), e.getMessage());
            }

            @Override
            public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                logger.error("XML parsing error at line {}: {}", e.getLineNumber(), e.getMessage());
                throw e;
            }

            @Override
            public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                logger.error("XML fatal error at line {}: {}", e.getLineNumber(), e.getMessage());
                throw e;
            }
        });

        return builder.parse(new ByteArrayInputStream(cleanedXml.getBytes("UTF-8")));
    }

    private String preprocessXmlContent(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return xmlContent;
        }

        int xmlDeclStart = xmlContent.indexOf("<?xml");
        if (xmlDeclStart == -1) {
            return xmlContent;
        }

        if (xmlDeclStart > 0) {
            String beforeDecl = xmlContent.substring(0, xmlDeclStart).trim();
            if (beforeDecl.startsWith("<!--") && beforeDecl.endsWith("-->")) {
                logger.debug("Found comment before XML declaration, relocating it");
                int xmlDeclEnd = xmlContent.indexOf("?>", xmlDeclStart);
                if (xmlDeclEnd != -1) {
                    String xmlDecl = xmlContent.substring(xmlDeclStart, xmlDeclEnd + 2);
                    String restOfDocument = xmlContent.substring(xmlDeclEnd + 2);
                    return xmlDecl + "\n" + beforeDecl + "\n" + restOfDocument;
                }
            }
        }

        return xmlContent;
    }

    private Map<String, BeanMetadata> extractBeanDefinitions(Document doc) {
        Map<String, BeanMetadata> beans = new HashMap<>();

        // Parse session beans
        NodeList sessionBeans = doc.getElementsByTagName("session");
        for (int i = 0; i < sessionBeans.getLength(); i++) {
            BeanMetadata bean = parseSessionBean((Element) sessionBeans.item(i));
            if (bean.ejbName != null) {
                beans.put(bean.ejbName, bean);
            }
        }

        // Parse entity beans
        NodeList entityBeans = doc.getElementsByTagName("entity");
        for (int i = 0; i < entityBeans.getLength(); i++) {
            BeanMetadata bean = parseEntityBean((Element) entityBeans.item(i));
            if (bean.ejbName != null) {
                beans.put(bean.ejbName, bean);
            }
        }

        // Parse message-driven beans
        NodeList messageDrivenBeans = doc.getElementsByTagName("message-driven");
        for (int i = 0; i < messageDrivenBeans.getLength(); i++) {
            BeanMetadata bean = parseMessageDrivenBean((Element) messageDrivenBeans.item(i));
            if (bean.ejbName != null) {
                beans.put(bean.ejbName, bean);
            }
        }

        return beans;
    }

    private BeanMetadata parseSessionBean(Element element) {
        BeanMetadata bean = new BeanMetadata();
        bean.beanType = "session";
        bean.ejbName = getTextContent(element, "ejb-name");
        bean.ejbClass = getTextContent(element, "ejb-class");
        bean.homeInterface = getTextContent(element, "home");
        bean.remoteInterface = getTextContent(element, "remote");
        bean.localHomeInterface = getTextContent(element, "local-home");
        bean.localInterface = getTextContent(element, "local");
        bean.sessionType = getTextContent(element, "session-type");
        bean.transactionType = getTextContent(element, "transaction-type");
        return bean;
    }

    private BeanMetadata parseEntityBean(Element element) {
        BeanMetadata bean = new BeanMetadata();
        bean.beanType = "entity";
        bean.ejbName = getTextContent(element, "ejb-name");
        bean.ejbClass = getTextContent(element, "ejb-class");
        bean.homeInterface = getTextContent(element, "home");
        bean.remoteInterface = getTextContent(element, "remote");
        bean.localHomeInterface = getTextContent(element, "local-home");
        bean.localInterface = getTextContent(element, "local");
        bean.persistenceType = getTextContent(element, "persistence-type");
        bean.primaryKeyClass = getTextContent(element, "prim-key-class");
        bean.transactionType = getTextContent(element, "transaction-type");
        return bean;
    }

    private BeanMetadata parseMessageDrivenBean(Element element) {
        BeanMetadata bean = new BeanMetadata();
        bean.beanType = "message-driven";
        bean.ejbName = getTextContent(element, "ejb-name");
        bean.ejbClass = getTextContent(element, "ejb-class");
        bean.transactionType = getTextContent(element, "transaction-type");
        bean.messageDestinationType = getTextContent(element, "message-destination-type");
        return bean;
    }

    private List<TransactionConfiguration> extractTransactionConfigurations(Document doc) {
        List<TransactionConfiguration> configs = new ArrayList<>();

        NodeList containerTransactions = doc.getElementsByTagName("container-transaction");
        for (int i = 0; i < containerTransactions.getLength(); i++) {
            Element containerTx = (Element) containerTransactions.item(i);
            configs.addAll(parseContainerTransaction(containerTx));
        }

        return configs;
    }

    private List<TransactionConfiguration> parseContainerTransaction(Element containerTx) {
        List<TransactionConfiguration> configs = new ArrayList<>();

        String transAttr = getTextContent(containerTx, "trans-attribute");
        if (transAttr == null || transAttr.trim().isEmpty()) {
            return configs;
        }

        EJBTransactionAttribute ejbAttr;
        try {
            String normalizedAttr = normalizeTransactionAttribute(transAttr);
            ejbAttr = EJBTransactionAttribute.valueOf(normalizedAttr);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown transaction attribute: {}", transAttr);
            return configs;
        }

        NodeList methods = containerTx.getElementsByTagName("method");
        for (int i = 0; i < methods.getLength(); i++) {
            Element method = (Element) methods.item(i);

            String beanName = getTextContent(method, "ejb-name");
            String methodName = getTextContent(method, "method-name");

            if (beanName == null || methodName == null) {
                continue;
            }

            List<String> methodParams = parseMethodParams(method);

            TransactionConfiguration config = new TransactionConfiguration();
            config.beanName = beanName;
            config.methodName = methodName;
            config.methodParams = methodParams;
            config.ejbAttribute = ejbAttr;
            config.springPropagation = mapToSpringPropagation(ejbAttr);
            config.readOnly = isReadOnlyMethod(methodName);

            configs.add(config);
        }

        return configs;
    }

    private String normalizeTransactionAttribute(String xmlAttr) {
        if (xmlAttr == null) return null;

        return switch (xmlAttr) {
            case "Required" -> "REQUIRED";
            case "RequiresNew" -> "REQUIRES_NEW";
            case "Supports" -> "SUPPORTS";
            case "NotSupported" -> "NOT_SUPPORTED";
            case "Mandatory" -> "MANDATORY";
            case "Never" -> "NEVER";
            default -> xmlAttr.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
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
        return methodName.startsWith("find") || methodName.startsWith("get") ||
               methodName.startsWith("is") || methodName.startsWith("has") ||
               methodName.startsWith("count") || methodName.startsWith("list") ||
               methodName.startsWith("search");
    }

    private List<String> parseMethodParams(Element method) {
        List<String> params = new ArrayList<>();
        NodeList paramElements = method.getElementsByTagName("method-param");
        for (int i = 0; i < paramElements.getLength(); i++) {
            params.add(paramElements.item(i).getTextContent().trim());
        }
        return params;
    }

    private int enrichGraph(Map<String, BeanMetadata> beansById, List<TransactionConfiguration> transactionConfigs) {
        int enrichedCount = 0;

        for (BeanMetadata bean : beansById.values()) {
            if (bean.ejbClass == null || bean.ejbClass.trim().isEmpty()) {
                logger.warn("Skipping bean {} - no ejb-class defined", bean.ejbName);
                continue;
            }

            try {
                // Create/get bean ClassNode
                JavaClassNode beanNode = classNodeRepository.getOrCreateByFqn(bean.ejbClass);

                // Create interface ClassNodes and edges
                createInterfaceNodesAndEdges(beanNode, bean);

                // Attach bean metadata
                attachBeanMetadata(beanNode, bean, transactionConfigs);

                enrichedCount++;

            } catch (Exception e) {
                logger.error("Error enriching bean {}: {}", bean.ejbName, e.getMessage(), e);
            }
        }

        return enrichedCount;
    }

    private void createInterfaceNodesAndEdges(JavaClassNode beanNode, BeanMetadata bean) {
        // Home interface
        if (bean.homeInterface != null && !bean.homeInterface.trim().isEmpty()) {
            JavaClassNode homeNode = classNodeRepository.getOrCreateByFqn(bean.homeInterface);
            GraphEdge edge = graphRepository.getOrCreateEdge(beanNode, homeNode, "ejb_home_interface");
            edge.setProperty("interface_type", "home");
            edge.setProperty("ejb_version", "2.x");
        }

        // Remote interface
        if (bean.remoteInterface != null && !bean.remoteInterface.trim().isEmpty()) {
            JavaClassNode remoteNode = classNodeRepository.getOrCreateByFqn(bean.remoteInterface);
            GraphEdge edge = graphRepository.getOrCreateEdge(beanNode, remoteNode, "ejb_remote_interface");
            edge.setProperty("interface_type", "remote");
        }

        // Local home interface
        if (bean.localHomeInterface != null && !bean.localHomeInterface.trim().isEmpty()) {
            JavaClassNode localHomeNode = classNodeRepository.getOrCreateByFqn(bean.localHomeInterface);
            GraphEdge edge = graphRepository.getOrCreateEdge(beanNode, localHomeNode, "ejb_local_home_interface");
            edge.setProperty("interface_type", "local_home");
            edge.setProperty("ejb_version", "2.x");
        }

        // Local interface
        if (bean.localInterface != null && !bean.localInterface.trim().isEmpty()) {
            JavaClassNode localNode = classNodeRepository.getOrCreateByFqn(bean.localInterface);
            GraphEdge edge = graphRepository.getOrCreateEdge(beanNode, localNode, "ejb_local_interface");
            edge.setProperty("interface_type", "local");
        }
    }

    private void attachBeanMetadata(JavaClassNode beanNode, BeanMetadata bean, 
                                   List<TransactionConfiguration> allConfigs) {
        // Deployment descriptor metadata
        beanNode.setProperty("ejb.deployment.ejb_name", bean.ejbName);
        beanNode.setProperty("ejb.deployment.bean_type", bean.beanType);
        
        if (bean.homeInterface != null) {
            beanNode.setProperty("ejb.deployment.home_interface", bean.homeInterface);
        }
        if (bean.remoteInterface != null) {
            beanNode.setProperty("ejb.deployment.remote_interface", bean.remoteInterface);
        }
        if (bean.localHomeInterface != null) {
            beanNode.setProperty("ejb.deployment.local_home_interface", bean.localHomeInterface);
        }
        if (bean.localInterface != null) {
            beanNode.setProperty("ejb.deployment.local_interface", bean.localInterface);
        }
        if (bean.sessionType != null) {
            beanNode.setProperty("ejb.deployment.session_type", bean.sessionType);
        }
        if (bean.persistenceType != null) {
            beanNode.setProperty("ejb.deployment.persistence_type", bean.persistenceType);
        }
        if (bean.primaryKeyClass != null) {
            beanNode.setProperty("ejb.deployment.primary_key_class", bean.primaryKeyClass);
        }
        if (bean.transactionType != null) {
            beanNode.setProperty("ejb.deployment.transaction_type", bean.transactionType);
        }
        if (bean.messageDestinationType != null) {
            beanNode.setProperty("ejb.deployment.message_destination_type", bean.messageDestinationType);
        }

        // Transaction configurations for this bean
        List<TransactionConfiguration> beanConfigs = allConfigs.stream()
                .filter(c -> c.beanName.equals(bean.ejbName))
                .collect(Collectors.toList());

        if (!beanConfigs.isEmpty()) {
            for (TransactionConfiguration config : beanConfigs) {
                String configKey = "ejb.transaction." + config.methodName;
                Map<String, Object> configValue = new HashMap<>();
                configValue.put("ejbAttr", config.ejbAttribute.name());
                configValue.put("springProp", config.springPropagation.name());
                configValue.put("readOnly", config.readOnly);
                beanNode.setProperty(configKey, configValue);
            }
        }
    }

    private void storeSummary(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator,
                             Map<String, BeanMetadata> beans, 
                             List<TransactionConfiguration> transactionConfigs,
                             int enrichedBeans) {
        // Count beans by type
        long sessionBeans = beans.values().stream().filter(b -> "session".equals(b.beanType)).count();
        long entityBeans = beans.values().stream().filter(b -> "entity".equals(b.beanType)).count();
        long messageDrivenBeans = beans.values().stream().filter(b -> "message-driven".equals(b.beanType)).count();

        // Store counts
        decorator.setProperty(TAGS.TAG_SESSION_BEANS_COUNT, (int) sessionBeans);
        decorator.setProperty(TAGS.TAG_ENTITY_BEANS_COUNT, (int) entityBeans);
        decorator.setProperty(TAGS.TAG_MESSAGE_DRIVEN_BEANS_COUNT, (int) messageDrivenBeans);

        // Store analysis summary as a plain object
        Map<String, Object> summary = new HashMap<>();
        summary.put("sessionBeans", sessionBeans);
        summary.put("entityBeans", entityBeans);
        summary.put("messageDrivenBeans", messageDrivenBeans);
        summary.put("totalTransactionConfigs", transactionConfigs.size());
        summary.put("enrichedBeans", enrichedBeans);
        decorator.setProperty(TAGS.TAG_EJB_JAR_ANALYSIS, summary);

        // Set transaction-related tags on ProjectFile
        if (!transactionConfigs.isEmpty()) {
            projectFile.setProperty(EjbMigrationTags.EJB_DECLARATIVE_TRANSACTION, true);
            projectFile.setProperty(EjbMigrationTags.EJB_CONTAINER_MANAGED_TRANSACTION, true);
            projectFile.setProperty(EjbMigrationTags.SPRING_TRANSACTION_CONVERSION, true);
        }
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }

    public static class TAGS {
        public static final String TAG_EJB_JAR_ANALYSIS = "ejb_jar_analysis";
        public static final String TAG_SESSION_BEANS_COUNT = "ejb.session_beans_count";
        public static final String TAG_ENTITY_BEANS_COUNT = "ejb.entity_beans_count";
        public static final String TAG_MESSAGE_DRIVEN_BEANS_COUNT = "ejb.message_driven_beans_count";
    }

    // Data classes
    private static class BeanMetadata {
        String beanType;
        String ejbName;
        String ejbClass;
        String homeInterface;
        String remoteInterface;
        String localHomeInterface;
        String localInterface;
        String sessionType;
        String persistenceType;
        String primaryKeyClass;
        String transactionType;
        String messageDestinationType;
    }

    private static class TransactionConfiguration {
        String beanName;
        String methodName;
        List<String> methodParams = new ArrayList<>();
        EJBTransactionAttribute ejbAttribute;
        SpringPropagation springPropagation;
        boolean readOnly;
    }

    private enum EJBTransactionAttribute {
        REQUIRED, REQUIRES_NEW, SUPPORTS, NOT_SUPPORTED, MANDATORY, NEVER
    }

    private enum SpringPropagation {
        REQUIRED, REQUIRES_NEW, SUPPORTS, NOT_SUPPORTED, MANDATORY, NEVER
    }
}
