package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractTextFileInspector;
import com.analyzer.api.resource.ResourceResolver;
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
 * <p>
 * This inspector performs complete analysis of EJB deployment descriptors
 * including:
 * <ul>
 * <li>Parsing bean definitions (session, entity, message-driven beans)</li>
 * <li>Extracting transaction configurations from assembly-descriptor</li>
 * <li>Creating ClassNodes for beans and their interfaces</li>
 * <li>Creating graph edges modeling EJB relationships</li>
 * <li>Attaching deployment descriptor metadata to bean ClassNodes</li>
 * <li>Storing summary statistics on the XML ProjectFile</li>
 * </ul>
 *
 * <p>
 * Configuration files are not Java classes, so this inspector stores summary
 * data on ProjectFile while enriching actual bean ClassNodes with detailed
 * metadata
 * and creating proper graph relationships.
 */
@InspectorDependencies(requires = {
        InspectorTags.TAG_SOURCE_FILE
}, produces = {
        EjbDeploymentDescriptorInspector.TAGS.TAG_EJB_JAR_ANALYSIS,
        com.analyzer.rules.ejb2spring.EjbMigrationTags.TAG_EJB_DECLARATIVE_TRANSACTION,
        com.analyzer.rules.ejb2spring.EjbMigrationTags.TAG_EJB_CONTAINER_MANAGED_TRANSACTION,
        com.analyzer.rules.ejb2spring.EjbMigrationTags.TAG_SPRING_TRANSACTION_CONVERSION
})
public class EjbDeploymentDescriptorInspector extends AbstractTextFileInspector {

    public static final String EJB_JAR_XML = "ejb-jar.xml";
    private static final Logger logger = LoggerFactory.getLogger(EjbDeploymentDescriptorInspector.class);

    private final ClassNodeRepository classNodeRepository;
    private final GraphRepository graphRepository;

    @Inject
    public EjbDeploymentDescriptorInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository,
            GraphRepository graphRepository, LocalCache localCache) {
        super(resourceResolver, localCache);
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
    protected void processContent(String content, ProjectFile projectFile,
            NodeDecorator<ProjectFile> projectFileDecorator) {
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
        bean.beanType = Constants.BEAN_TYPE_SESSION;
        bean.ejbName = getTextContent(element, "ejb-name");
        bean.ejbClass = getTextContent(element, "ejb-class");
        bean.homeInterface = getTextContent(element, "home");
        bean.remoteInterface = getTextContent(element, "remote");
        bean.localHomeInterface = getTextContent(element, "local-home");
        bean.localInterface = getTextContent(element, "local");
        bean.sessionType = getTextContent(element, "session-type");
        bean.transactionType = getTextContent(element, "transaction-type");
        bean.transactionManagement = bean.transactionType; // Container or Bean
        bean.envEntries = parseEnvEntries(element);
        return bean;
    }

    private BeanMetadata parseEntityBean(Element element) {
        BeanMetadata bean = new BeanMetadata();
        bean.beanType = Constants.BEAN_TYPE_ENTITY;
        bean.ejbName = getTextContent(element, "ejb-name");
        bean.ejbClass = getTextContent(element, "ejb-class");
        bean.homeInterface = getTextContent(element, "home");
        bean.remoteInterface = getTextContent(element, "remote");
        bean.localHomeInterface = getTextContent(element, "local-home");
        bean.localInterface = getTextContent(element, "local");
        bean.persistenceType = getTextContent(element, "persistence-type");
        bean.primaryKeyClass = getTextContent(element, "prim-key-class");
        bean.transactionType = getTextContent(element, "transaction-type");
        bean.transactionManagement = bean.transactionType; // Container or Bean
        bean.envEntries = parseEnvEntries(element);
        return bean;
    }

    private BeanMetadata parseMessageDrivenBean(Element element) {
        BeanMetadata bean = new BeanMetadata();
        bean.beanType = Constants.BEAN_TYPE_MESSAGE_DRIVEN;
        bean.ejbName = getTextContent(element, "ejb-name");
        bean.ejbClass = getTextContent(element, "ejb-class");
        bean.transactionType = getTextContent(element, "transaction-type");
        bean.transactionManagement = bean.transactionType; // Container or Bean
        bean.messageDestinationType = getTextContent(element, "message-destination-type");
        bean.envEntries = parseEnvEntries(element);
        return bean;
    }

    /**
     * Parse environment entries from a bean element.
     *
     * @param beanElement The bean element containing env-entry elements
     * @return List of parsed environment entries
     */
    private List<EnvEntry> parseEnvEntries(Element beanElement) {
        List<EnvEntry> entries = new ArrayList<>();
        NodeList envEntryNodes = beanElement.getElementsByTagName(Constants.XML_ENV_ENTRY);

        for (int i = 0; i < envEntryNodes.getLength(); i++) {
            Element envEntryElement = (Element) envEntryNodes.item(i);

            EnvEntry entry = new EnvEntry();
            entry.name = getTextContent(envEntryElement, Constants.XML_ENV_ENTRY_NAME);
            entry.type = getTextContent(envEntryElement, Constants.XML_ENV_ENTRY_TYPE);
            entry.value = getTextContent(envEntryElement, Constants.XML_ENV_ENTRY_VALUE);

            // Only add if we have at least a name
            if (entry.name != null && !entry.name.trim().isEmpty()) {
                entries.add(entry);
                logger.debug("Parsed env-entry: {}", entry);
            }
        }

        return entries;
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

        NodeList methods = containerTx.getElementsByTagName(Constants.XML_METHOD);
        for (int i = 0; i < methods.getLength(); i++) {
            Element method = (Element) methods.item(i);

            String beanName = getTextContent(method, "ejb-name");
            String methodName = getTextContent(method, "method-name");

            if (beanName == null || methodName == null) {
                continue;
            }

            String methodIntf = getTextContent(method, Constants.XML_METHOD_INTF);
            List<String> methodParams = parseMethodParams(method);

            TransactionConfiguration config = new TransactionConfiguration();
            config.beanName = beanName;
            config.methodName = methodName;
            config.methodInterface = methodIntf; // "Remote" or "Local"
            config.methodParams = methodParams;
            config.ejbAttribute = ejbAttr;
            config.springPropagation = mapToSpringPropagation(ejbAttr);
            config.readOnly = isReadOnlyMethod(methodName);

            configs.add(config);
        }

        return configs;
    }

    private String normalizeTransactionAttribute(String xmlAttr) {
        if (xmlAttr == null)
            return null;

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

    /**
     * Create interface ClassNodes and graph edges for bean interfaces.
     * Creates both EJB-specific edges and standard "implements" edges for
     * architectural analysis.
     *
     * @param beanNode The bean's JavaClassNode
     * @param bean     The bean metadata containing interface information
     */
    private void createInterfaceNodesAndEdges(JavaClassNode beanNode, BeanMetadata bean) {
        // Home interface
        if (bean.homeInterface != null && !bean.homeInterface.trim().isEmpty()) {
            JavaClassNode homeNode = classNodeRepository.getOrCreateByFqn(bean.homeInterface);

            // EJB-specific edge with metadata
            GraphEdge ejbEdge = graphRepository.getOrCreateEdge(beanNode, homeNode, Constants.EDGE_EJB_HOME);
            ejbEdge.setProperty(Constants.EDGE_PROP_INTERFACE_TYPE, Constants.INTF_TYPE_HOME);
            ejbEdge.setProperty(Constants.EDGE_PROP_EJB_VERSION, "2.x");

            // Standard "implements" edge for general architectural queries
            graphRepository.getOrCreateEdge(beanNode, homeNode, Constants.EDGE_IMPLEMENTS);
        }

        // Remote interface
        if (bean.remoteInterface != null && !bean.remoteInterface.trim().isEmpty()) {
            JavaClassNode remoteNode = classNodeRepository.getOrCreateByFqn(bean.remoteInterface);

            // EJB-specific edge with metadata
            GraphEdge ejbEdge = graphRepository.getOrCreateEdge(beanNode, remoteNode, Constants.EDGE_EJB_REMOTE);
            ejbEdge.setProperty(Constants.EDGE_PROP_INTERFACE_TYPE, Constants.INTF_TYPE_REMOTE);

            // Standard "implements" edge for general architectural queries
            graphRepository.getOrCreateEdge(beanNode, remoteNode, Constants.EDGE_IMPLEMENTS);
        }

        // Local home interface
        if (bean.localHomeInterface != null && !bean.localHomeInterface.trim().isEmpty()) {
            JavaClassNode localHomeNode = classNodeRepository.getOrCreateByFqn(bean.localHomeInterface);

            // EJB-specific edge with metadata
            GraphEdge ejbEdge = graphRepository.getOrCreateEdge(beanNode, localHomeNode,
                    Constants.EDGE_EJB_LOCAL_HOME);
            ejbEdge.setProperty(Constants.EDGE_PROP_INTERFACE_TYPE, Constants.INTF_TYPE_LOCAL_HOME);
            ejbEdge.setProperty(Constants.EDGE_PROP_EJB_VERSION, "2.x");

            // Standard "implements" edge for general architectural queries
            graphRepository.getOrCreateEdge(beanNode, localHomeNode, Constants.EDGE_IMPLEMENTS);
        }

        // Local interface
        if (bean.localInterface != null && !bean.localInterface.trim().isEmpty()) {
            JavaClassNode localNode = classNodeRepository.getOrCreateByFqn(bean.localInterface);

            // EJB-specific edge with metadata
            GraphEdge ejbEdge = graphRepository.getOrCreateEdge(beanNode, localNode, Constants.EDGE_EJB_LOCAL);
            ejbEdge.setProperty(Constants.EDGE_PROP_INTERFACE_TYPE, Constants.INTF_TYPE_LOCAL);

            // Standard "implements" edge for general architectural queries
            graphRepository.getOrCreateEdge(beanNode, localNode, Constants.EDGE_IMPLEMENTS);
        }
    }

    private void attachBeanMetadata(JavaClassNode beanNode, BeanMetadata bean,
            List<TransactionConfiguration> allConfigs) {
        // Deployment descriptor metadata using constants
        beanNode.setProperty(Constants.PROP_EJB_NAME, bean.ejbName);
        beanNode.setProperty(Constants.PROP_BEAN_TYPE, bean.beanType);

        if (bean.homeInterface != null) {
            beanNode.setProperty(Constants.PROP_HOME_INTERFACE, bean.homeInterface);
        }
        if (bean.remoteInterface != null) {
            beanNode.setProperty(Constants.PROP_REMOTE_INTERFACE, bean.remoteInterface);
        }
        if (bean.localHomeInterface != null) {
            beanNode.setProperty(Constants.PROP_LOCAL_HOME_INTERFACE, bean.localHomeInterface);
        }
        if (bean.localInterface != null) {
            beanNode.setProperty(Constants.PROP_LOCAL_INTERFACE, bean.localInterface);
        }
        if (bean.sessionType != null) {
            beanNode.setProperty(Constants.PROP_SESSION_TYPE, bean.sessionType);
        }
        if (bean.persistenceType != null) {
            beanNode.setProperty(Constants.PROP_PERSISTENCE_TYPE, bean.persistenceType);
        }
        if (bean.primaryKeyClass != null) {
            beanNode.setProperty(Constants.PROP_PRIMARY_KEY_CLASS, bean.primaryKeyClass);
        }
        if (bean.transactionType != null) {
            beanNode.setProperty(Constants.PROP_TRANSACTION_TYPE, bean.transactionType);
        }
        if (bean.transactionManagement != null) {
            beanNode.setProperty(Constants.PROP_TRANSACTION_MANAGEMENT, bean.transactionManagement);

            // Set tag for transaction management type
            if (Constants.TX_MANAGEMENT_BEAN.equals(bean.transactionManagement)) {
                beanNode.setProperty(Constants.TAG_BEAN_MANAGED_TX, true);
            } else if (Constants.TX_MANAGEMENT_CONTAINER.equals(bean.transactionManagement)) {
                beanNode.setProperty(Constants.TAG_CONTAINER_MANAGED_TX, true);
            }
        }
        if (bean.messageDestinationType != null) {
            beanNode.setProperty(Constants.PROP_MESSAGE_DESTINATION_TYPE, bean.messageDestinationType);
        }

        // Environment entries
        if (!bean.envEntries.isEmpty()) {
            List<Map<String, String>> envEntriesList = new ArrayList<>();
            for (EnvEntry entry : bean.envEntries) {
                Map<String, String> entryMap = new HashMap<>();
                entryMap.put("name", entry.name);
                entryMap.put("type", entry.type);
                entryMap.put("value", entry.value);
                envEntriesList.add(entryMap);
            }
            beanNode.setProperty(Constants.PROP_ENV_ENTRIES, envEntriesList);
            beanNode.setProperty(Constants.TAG_HAS_ENV_ENTRIES, true);
        }

        // Transaction configurations for this bean
        List<TransactionConfiguration> beanConfigs = allConfigs.stream()
                .filter(c -> c.beanName.equals(bean.ejbName))
                .toList();

        if (!beanConfigs.isEmpty()) {
            List<Map<String, Object>> transactionConfigsList = new ArrayList<>();
            for (TransactionConfiguration config : beanConfigs) {
                Map<String, Object> configValue = new HashMap<>();
                configValue.put("methodName", config.methodName);
                configValue.put("methodInterface", config.methodInterface);
                configValue.put("ejbAttr", config.ejbAttribute.name());
                configValue.put("springProp", config.springPropagation.name());
                configValue.put("readOnly", config.readOnly);
                transactionConfigsList.add(configValue);
            }
            beanNode.setProperty(Constants.PROP_TRANSACTION_CONFIGS, transactionConfigsList);
        }
    }

    private void storeSummary(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator,
            Map<String, BeanMetadata> beans,
            List<TransactionConfiguration> transactionConfigs,
            int enrichedBeans) {
        // Count beans by type
        long sessionBeans = beans.values().stream().filter(b -> Constants.BEAN_TYPE_SESSION.equals(b.beanType))
                .count();
        long entityBeans = beans.values().stream().filter(b -> Constants.BEAN_TYPE_ENTITY.equals(b.beanType)).count();
        long messageDrivenBeans = beans.values().stream()
                .filter(b -> Constants.BEAN_TYPE_MESSAGE_DRIVEN.equals(b.beanType)).count();

        // Count transaction management types
        long bmtBeans = beans.values().stream()
                .filter(b -> Constants.TX_MANAGEMENT_BEAN.equals(b.transactionManagement)).count();
        long cmtBeans = beans.values().stream()
                .filter(b -> Constants.TX_MANAGEMENT_CONTAINER.equals(b.transactionManagement)).count();

        // Count beans with environment entries
        long beansWithEnvEntries = beans.values().stream().filter(b -> !b.envEntries.isEmpty()).count();
        long totalEnvEntries = beans.values().stream().mapToLong(b -> b.envEntries.size()).sum();

        // Analyze shared resources
        Map<String, List<String>> classToBeansMap = analyzeSharedClasses(beans);
        Map<String, List<String>> interfaceToBeansMap = analyzeSharedInterfaces(beans);

        // Store basic counts
        decorator.setMetric(Constants.METRIC_SESSION_BEANS_COUNT, (int) sessionBeans);
        decorator.setMetric(Constants.METRIC_ENTITY_BEANS_COUNT, (int) entityBeans);
        decorator.setMetric(Constants.METRIC_MESSAGE_DRIVEN_BEANS_COUNT, (int) messageDrivenBeans);
        decorator.setMetric(Constants.METRIC_BMT_BEANS_COUNT, (int) bmtBeans);
        decorator.setMetric(Constants.METRIC_CMT_BEANS_COUNT, (int) cmtBeans);
        decorator.setMetric(Constants.METRIC_BEANS_WITH_ENV_ENTRIES, (int) beansWithEnvEntries);
        decorator.setMetric(Constants.METRIC_TOTAL_ENV_ENTRIES, (int) totalEnvEntries);
        decorator.setMetric(Constants.METRIC_SHARED_CLASSES_COUNT, classToBeansMap.size());
        decorator.setMetric(Constants.METRIC_SHARED_INTERFACES_COUNT, interfaceToBeansMap.size());

        // Store analysis summary as a plain object
        Map<String, Object> summary = new HashMap<>();
        summary.put("sessionBeans", sessionBeans);
        summary.put("entityBeans", entityBeans);
        summary.put("messageDrivenBeans", messageDrivenBeans);
        summary.put("bmtBeans", bmtBeans);
        summary.put("cmtBeans", cmtBeans);
        summary.put("beansWithEnvEntries", beansWithEnvEntries);
        summary.put("totalEnvEntries", totalEnvEntries);
        summary.put("totalTransactionConfigs", transactionConfigs.size());
        summary.put("enrichedBeans", enrichedBeans);
        summary.put("sharedClassesCount", classToBeansMap.size());
        summary.put("sharedInterfacesCount", interfaceToBeansMap.size());

        // Add details about shared resources if any exist
        if (!classToBeansMap.isEmpty()) {
            summary.put("sharedClasses", classToBeansMap);
        }
        if (!interfaceToBeansMap.isEmpty()) {
            summary.put("sharedInterfaces", interfaceToBeansMap);
        }

        decorator.setProperty(Constants.TAG_EJB_JAR_ANALYSIS, summary);

        // Set transaction-related tags on ProjectFile
        if (!transactionConfigs.isEmpty()) {
            projectFile.setProperty(com.analyzer.rules.ejb2spring.EjbMigrationTags.TAG_EJB_DECLARATIVE_TRANSACTION,
                    true);
            projectFile.setProperty(
                    com.analyzer.rules.ejb2spring.EjbMigrationTags.TAG_EJB_CONTAINER_MANAGED_TRANSACTION,
                    true);
            projectFile.setProperty(com.analyzer.rules.ejb2spring.EjbMigrationTags.TAG_SPRING_TRANSACTION_CONVERSION,
                    true);
        }

        // Set BMT tag if any BMT beans exist
        if (bmtBeans > 0) {
            projectFile.setProperty(Constants.TAG_BEAN_MANAGED_TX, true);
        }

        // Set environment entries tag if any beans have env entries
        if (beansWithEnvEntries > 0) {
            projectFile.setProperty(Constants.TAG_HAS_ENV_ENTRIES, true);
        }
    }

    /**
     * Analyze which EJB implementation classes are shared across multiple bean
     * definitions.
     *
     * @param beans Map of bean metadata by bean name
     * @return Map of ejb-class to list of bean names using that class
     */
    private Map<String, List<String>> analyzeSharedClasses(Map<String, BeanMetadata> beans) {
        Map<String, List<String>> classToBeansMap = new HashMap<>();

        for (BeanMetadata bean : beans.values()) {
            if (bean.ejbClass != null && !bean.ejbClass.trim().isEmpty()) {
                classToBeansMap.computeIfAbsent(bean.ejbClass, k -> new ArrayList<>()).add(bean.ejbName);
            }
        }

        // Keep only classes that are shared (used by more than one bean)
        classToBeansMap.entrySet().removeIf(entry -> entry.getValue().size() <= 1);

        return classToBeansMap;
    }

    /**
     * Analyze which interfaces are shared across multiple bean definitions.
     *
     * @param beans Map of bean metadata by bean name
     * @return Map of interface FQN to list of bean names using that interface
     */
    private Map<String, List<String>> analyzeSharedInterfaces(Map<String, BeanMetadata> beans) {
        Map<String, List<String>> interfaceToBeansMap = new HashMap<>();

        for (BeanMetadata bean : beans.values()) {
            // Track all interface types
            if (bean.homeInterface != null && !bean.homeInterface.trim().isEmpty()) {
                interfaceToBeansMap.computeIfAbsent(bean.homeInterface, k -> new ArrayList<>()).add(bean.ejbName);
            }
            if (bean.remoteInterface != null && !bean.remoteInterface.trim().isEmpty()) {
                interfaceToBeansMap.computeIfAbsent(bean.remoteInterface, k -> new ArrayList<>()).add(bean.ejbName);
            }
            if (bean.localHomeInterface != null && !bean.localHomeInterface.trim().isEmpty()) {
                interfaceToBeansMap.computeIfAbsent(bean.localHomeInterface, k -> new ArrayList<>())
                        .add(bean.ejbName);
            }
            if (bean.localInterface != null && !bean.localInterface.trim().isEmpty()) {
                interfaceToBeansMap.computeIfAbsent(bean.localInterface, k -> new ArrayList<>()).add(bean.ejbName);
            }
        }

        // Keep only interfaces that are shared (used by more than one bean)
        interfaceToBeansMap.entrySet().removeIf(entry -> entry.getValue().size() <= 1);

        return interfaceToBeansMap;
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }

    /**
     * Constants for XML elements, property keys, metrics, tags, and edge types.
     * All string literals are centralized here for maintainability and consistency.
     */
    public static class Constants {
        // XML Elements
        public static final String XML_SESSION = "session";
        public static final String XML_ENTITY = "entity";
        public static final String XML_MESSAGE_DRIVEN = "message-driven";
        public static final String XML_ENV_ENTRY = "env-entry";
        public static final String XML_ENV_ENTRY_NAME = "env-entry-name";
        public static final String XML_ENV_ENTRY_TYPE = "env-entry-type";
        public static final String XML_ENV_ENTRY_VALUE = "env-entry-value";
        public static final String XML_METHOD_INTF = "method-intf";
        public static final String XML_CONTAINER_TRANSACTION = "container-transaction";
        public static final String XML_METHOD = "method";
        public static final String XML_TRANS_ATTRIBUTE = "trans-attribute";

        // Property Keys - Bean Metadata
        public static final String PROP_EJB_NAME = "ejb.deployment.ejb_name";
        public static final String PROP_BEAN_TYPE = "ejb.deployment.bean_type";
        public static final String PROP_HOME_INTERFACE = "ejb.deployment.home_interface";
        public static final String PROP_REMOTE_INTERFACE = "ejb.deployment.remote_interface";
        public static final String PROP_LOCAL_HOME_INTERFACE = "ejb.deployment.local_home_interface";
        public static final String PROP_LOCAL_INTERFACE = "ejb.deployment.local_interface";
        public static final String PROP_SESSION_TYPE = "ejb.deployment.session_type";
        public static final String PROP_PERSISTENCE_TYPE = "ejb.deployment.persistence_type";
        public static final String PROP_PRIMARY_KEY_CLASS = "ejb.deployment.primary_key_class";
        public static final String PROP_TRANSACTION_TYPE = "ejb.deployment.transaction_type";
        public static final String PROP_MESSAGE_DESTINATION_TYPE = "ejb.deployment.message_destination_type";
        public static final String PROP_ENV_ENTRIES = "ejb.deployment.env_entries";
        public static final String PROP_TRANSACTION_MANAGEMENT = "ejb.deployment.transaction_management";
        public static final String PROP_TRANSACTION_CONFIGS = "ejb.transaction.configurations";

        // Property Keys - Shared Resources
        public static final String PROP_SHARED_EJB_CLASS = "ejb.deployment.shared_class";
        public static final String PROP_SHARED_INTERFACE = "ejb.deployment.shared_interface";
        public static final String PROP_CLASS_REUSE_COUNT = "ejb.deployment.class_reuse_count";
        public static final String PROP_INTERFACE_REUSE_COUNT = "ejb.deployment.interface_reuse_count";

        // Metrics
        public static final String METRIC_SESSION_BEANS_COUNT = "ejb.session_beans_count";
        public static final String METRIC_ENTITY_BEANS_COUNT = "ejb.entity_beans_count";
        public static final String METRIC_MESSAGE_DRIVEN_BEANS_COUNT = "ejb.message_driven_beans_count";
        public static final String METRIC_BEANS_WITH_ENV_ENTRIES = "ejb.beans_with_env_entries";
        public static final String METRIC_BMT_BEANS_COUNT = "ejb.bmt_beans_count";
        public static final String METRIC_CMT_BEANS_COUNT = "ejb.cmt_beans_count";
        public static final String METRIC_SHARED_CLASSES_COUNT = "ejb.shared_classes_count";
        public static final String METRIC_SHARED_INTERFACES_COUNT = "ejb.shared_interfaces_count";
        public static final String METRIC_TOTAL_ENV_ENTRIES = "ejb.total_env_entries";

        // Tags
        public static final String TAG_EJB_JAR_ANALYSIS = "ejb_jar_analysis";
        public static final String TAG_HAS_ENV_ENTRIES = "ejb_has_env_entries";
        public static final String TAG_BEAN_MANAGED_TX = "ejb_bean_managed_transaction";
        public static final String TAG_CONTAINER_MANAGED_TX = "ejb_container_managed_transaction";
        public static final String TAG_SHARED_EJB_CLASS = "ejb_shared_class_instance";
        public static final String TAG_SHARED_INTERFACE = "ejb_shared_interface_instance";

        // Edge Types - EJB Specific
        public static final String EDGE_EJB_HOME = "ejb_home_interface";
        public static final String EDGE_EJB_REMOTE = "ejb_remote_interface";
        public static final String EDGE_EJB_LOCAL_HOME = "ejb_local_home_interface";
        public static final String EDGE_EJB_LOCAL = "ejb_local_interface";
        public static final String EDGE_SHARES_CLASS = "ejb_shares_implementation";
        public static final String EDGE_SHARES_INTERFACE = "ejb_shares_interface";

        // Edge Types - Standard (from BinaryClassCouplingGraphInspector)
        public static final String EDGE_IMPLEMENTS = "implements";
        public static final String EDGE_EXTENDS = "extends";
        public static final String EDGE_USES = "uses";

        // Edge Properties
        public static final String EDGE_PROP_INTERFACE_TYPE = "interface_type";
        public static final String EDGE_PROP_EJB_VERSION = "ejb_version";
        public static final String EDGE_PROP_REUSE_COUNT = "reuse_count";

        // Bean Types
        public static final String BEAN_TYPE_SESSION = "session";
        public static final String BEAN_TYPE_ENTITY = "entity";
        public static final String BEAN_TYPE_MESSAGE_DRIVEN = "message-driven";

        // Transaction Management Types
        public static final String TX_MANAGEMENT_CONTAINER = "Container";
        public static final String TX_MANAGEMENT_BEAN = "Bean";

        // Interface Types
        public static final String INTF_TYPE_HOME = "home";
        public static final String INTF_TYPE_REMOTE = "remote";
        public static final String INTF_TYPE_LOCAL_HOME = "local_home";
        public static final String INTF_TYPE_LOCAL = "local";
    }

    @Deprecated
    public static class TAGS {
        public static final String TAG_EJB_JAR_ANALYSIS = Constants.TAG_EJB_JAR_ANALYSIS;
        public static final String METRIC_SESSION_BEANS_COUNT = Constants.METRIC_SESSION_BEANS_COUNT;
        public static final String METRIC_ENTITY_BEANS_COUNT = Constants.METRIC_ENTITY_BEANS_COUNT;
        public static final String METRIC_MESSAGE_DRIVEN_BEANS_COUNT = Constants.METRIC_MESSAGE_DRIVEN_BEANS_COUNT;
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
        List<EnvEntry> envEntries = new ArrayList<>();
        String transactionManagement; // "Container" or "Bean"
    }

    private static class EnvEntry {
        String name;
        String type;
        String value;

        @Override
        public String toString() {
            return String.format("EnvEntry{name='%s', type='%s', value='%s'}", name, type, value);
        }
    }

    private static class TransactionConfiguration {
        String beanName;
        String methodName;
        List<String> methodParams = new ArrayList<>();
        String methodInterface; // "Remote" or "Local"
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
