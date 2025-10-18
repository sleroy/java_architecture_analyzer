package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.detection.SourceFileTagDetector;
import com.analyzer.inspectors.core.source.AbstractTextFileInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that analyzes ejb-jar.xml deployment descriptors to identify EJB
 * components
 * and their characteristics for Spring Boot migration planning.
 */
@InspectorDependencies(need = { SourceFileTagDetector.class }, produces = {
        EjbDeploymenDescriptorAnalyzerInspector.TAGS.TAG_EJB_JAR_ANALYSIS })
public class EjbDeploymenDescriptorAnalyzerInspector extends AbstractTextFileInspector {

    private static final Logger logger = LoggerFactory.getLogger(EjbDeploymenDescriptorAnalyzerInspector.class);

    public static class TAGS {
        public static final String TAG_EJB_JAR_ANALYSIS = "ejb_jar_analysis";
        public static final String TAG_SESSION_BEANS_COUNT = "ejb.session_beans_count";
        public static final String TAG_ENTITY_BEANS_COUNT = "ejb.entity_beans_count";
        public static final String TAG_MESSAGE_DRIVEN_BEANS_COUNT = "ejb.message_driven_beans_count";
    }

    private final ClassNodeRepository classNodeRepository;

    public EjbDeploymenDescriptorAnalyzerInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // Only process XML files with ejb-jar.xml filename
        return super.supports(projectFile)
                && "ejb-jar.xml".equals(projectFile.getFileName());
    }

    @Override
    public String getName() {
        return "EJB Deployment Descriptor Analyzer Inspector";
    }

    @Override
    protected void processContent(String content, ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        classNodeRepository.getOrCreateClassNodeByFqn(projectFile.getFullyQualifiedName()).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            try {
                EjbJarAnalysis analysis = parseEjbJarXml(content);

                // Set analysis results using own TAGS constants
                classNode.setProperty(TAGS.TAG_EJB_JAR_ANALYSIS, analysis.toJson());
                classNode.setProperty(TAGS.TAG_SESSION_BEANS_COUNT, analysis.getSessionBeansCount());
                classNode.setProperty(TAGS.TAG_ENTITY_BEANS_COUNT, analysis.getEntityBeansCount());
                classNode.setProperty(TAGS.TAG_MESSAGE_DRIVEN_BEANS_COUNT, analysis.getMessageDrivenBeansCount());

            } catch (Exception e) {
                String filename = projectFile != null ? projectFile.getRelativePath() : "unknown file";
                String errorMsg = String.format("Error parsing XML file '%s': %s", filename, e.getMessage());
                logger.warn(errorMsg);
                projectFileDecorator.error(errorMsg);
            }
        });
    }

    private EjbJarAnalysis parseEjbJarXml(String xmlContent) throws Exception {
        // Pre-process XML to handle common issues
        String cleanedXml = preprocessXmlContent(xmlContent);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        // Make parser more tolerant of formatting issues
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);

        DocumentBuilder builder = factory.newDocumentBuilder();

        // Set custom error handler for better error reporting
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

        Document doc = builder.parse(new ByteArrayInputStream(cleanedXml.getBytes("UTF-8")));

        EjbJarAnalysis analysis = new EjbJarAnalysis();

        // Parse enterprise beans
        NodeList sessionBeans = doc.getElementsByTagName("session");
        for (int i = 0; i < sessionBeans.getLength(); i++) {
            Element sessionBean = (Element) sessionBeans.item(i);
            analysis.addSessionBean(parseSessionBean(sessionBean));
        }

        NodeList entityBeans = doc.getElementsByTagName("entity");
        for (int i = 0; i < entityBeans.getLength(); i++) {
            Element entityBean = (Element) entityBeans.item(i);
            analysis.addEntityBean(parseEntityBean(entityBean));
        }

        NodeList messageDrivenBeans = doc.getElementsByTagName("message-driven");
        for (int i = 0; i < messageDrivenBeans.getLength(); i++) {
            Element mdb = (Element) messageDrivenBeans.item(i);
            analysis.addMessageDrivenBean(parseMessageDrivenBean(mdb));
        }

        return analysis;
    }

    /**
     * Pre-processes XML content to handle common formatting issues that cause
     * parsing failures.
     * Specifically handles cases where comments appear before the XML declaration.
     */
    private String preprocessXmlContent(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return xmlContent;
        }

        // Find the XML declaration
        int xmlDeclStart = xmlContent.indexOf("<?xml");
        if (xmlDeclStart == -1) {
            // No XML declaration found, return as-is
            return xmlContent;
        }

        // If XML declaration is not at the start, there might be comments before it
        if (xmlDeclStart > 0) {
            String beforeDecl = xmlContent.substring(0, xmlDeclStart).trim();
            // Check if there are only comments and whitespace before XML declaration
            if (beforeDecl.startsWith("<!--") && beforeDecl.endsWith("-->")) {
                logger.debug("Found comment before XML declaration, relocating it");
                // Move the comment after the XML declaration
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

    private SessionBeanInfo parseSessionBean(Element sessionBean) {
        SessionBeanInfo info = new SessionBeanInfo();
        info.ejbName = getTextContent(sessionBean, "ejb-name");
        info.ejbClass = getTextContent(sessionBean, "ejb-class");
        info.homeInterface = getTextContent(sessionBean, "home");
        info.remoteInterface = getTextContent(sessionBean, "remote");
        info.localHomeInterface = getTextContent(sessionBean, "local-home");
        info.localInterface = getTextContent(sessionBean, "local");
        info.sessionType = getTextContent(sessionBean, "session-type");
        info.transactionType = getTextContent(sessionBean, "transaction-type");
        return info;
    }

    private EntityBeanInfo parseEntityBean(Element entityBean) {
        EntityBeanInfo info = new EntityBeanInfo();
        info.ejbName = getTextContent(entityBean, "ejb-name");
        info.ejbClass = getTextContent(entityBean, "ejb-class");
        info.homeInterface = getTextContent(entityBean, "home");
        info.remoteInterface = getTextContent(entityBean, "remote");
        info.localHomeInterface = getTextContent(entityBean, "local-home");
        info.localInterface = getTextContent(entityBean, "local");
        info.persistenceType = getTextContent(entityBean, "persistence-type");
        info.primaryKeyClass = getTextContent(entityBean, "prim-key-class");
        return info;
    }

    private MessageDrivenBeanInfo parseMessageDrivenBean(Element mdb) {
        MessageDrivenBeanInfo info = new MessageDrivenBeanInfo();
        info.ejbName = getTextContent(mdb, "ejb-name");
        info.ejbClass = getTextContent(mdb, "ejb-class");
        info.transactionType = getTextContent(mdb, "transaction-type");
        info.messageDestinationType = getTextContent(mdb, "message-destination-type");
        return info;
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }

    // Data classes for EJB analysis
    public static class EjbJarAnalysis {
        private List<SessionBeanInfo> sessionBeans = new ArrayList<>();
        private List<EntityBeanInfo> entityBeans = new ArrayList<>();
        private List<MessageDrivenBeanInfo> messageDrivenBeans = new ArrayList<>();

        public void addSessionBean(SessionBeanInfo bean) {
            sessionBeans.add(bean);
        }

        public void addEntityBean(EntityBeanInfo bean) {
            entityBeans.add(bean);
        }

        public void addMessageDrivenBean(MessageDrivenBeanInfo bean) {
            messageDrivenBeans.add(bean);
        }

        public int getSessionBeansCount() {
            return sessionBeans.size();
        }

        public int getEntityBeansCount() {
            return entityBeans.size();
        }

        public int getMessageDrivenBeansCount() {
            return messageDrivenBeans.size();
        }

        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"sessionBeans\":").append(sessionBeans.size()).append(",");
            json.append("\"entityBeans\":").append(entityBeans.size()).append(",");
            json.append("\"messageDrivenBeans\":").append(messageDrivenBeans.size());
            json.append("}");
            return json.toString();
        }
    }

    public static class SessionBeanInfo {
        public String ejbName;
        public String ejbClass;
        public String homeInterface;
        public String remoteInterface;
        public String localHomeInterface;
        public String localInterface;
        public String sessionType;
        public String transactionType;
    }

    public static class EntityBeanInfo {
        public String ejbName;
        public String ejbClass;
        public String homeInterface;
        public String remoteInterface;
        public String localHomeInterface;
        public String localInterface;
        public String persistenceType;
        public String primaryKeyClass;
    }

    public static class MessageDrivenBeanInfo {
        public String ejbName;
        public String ejbClass;
        public String transactionType;
        public String messageDestinationType;
    }
}
