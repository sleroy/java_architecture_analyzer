package com.analyzer.dev.inspectors.bedrock;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.bedrock.BedrockApiClient;
import com.analyzer.core.bedrock.BedrockApiException;
import com.analyzer.core.bedrock.BedrockConfig;
import com.analyzer.core.bedrock.BedrockConfigurationException;
import com.analyzer.core.bedrock.BedrockResponse;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.dev.inspectors.core.AbstractJavaClassInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract base class for inspectors that use AWS Bedrock AI models to analyze
 * JavaClassNode.
 * This class is specifically designed for class-centric analysis where tags and
 * metrics
 * are attached directly to JavaClassNode objects rather than ProjectFile
 * objects.
 * <p>
 * Key differences from AbstractBedrockInspectorAbstract:
 * - Extends AbstractJavaClassInspector (works on JavaClassNode)
 * - Filters for SOURCE classes only (via isSourceFilePresent())
 * - Tags/metrics are set on JavaClassNode in the graph
 * - Uses ProjectFileRepository to access source content
 * <p>
 * Subclasses must implement:
 * - getName() from Inspector interface
 * - buildPrompt() to create model-specific prompts
 * - parseResponse() to process AI responses and set tags/metrics on
 * JavaClassNode
 *
 * @author Java Architecture Analyzer
 */
public abstract class AbstractBedrockJavaClassInspector extends AbstractJavaClassInspector {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractBedrockJavaClassInspector.class);

    protected final ResourceResolver resourceResolver;
    private final BedrockConfig config;
    private BedrockApiClient apiClient;
    private boolean initializedSuccessfully = false;

    /**
     * Constructor with ResourceResolver, ProjectFileRepository, and LocalCache
     *
     * @param resourceResolver      resolver for accessing file content
     * @param projectFileRepository repository for looking up ProjectFile instances
     */
    @Inject
    protected AbstractBedrockJavaClassInspector(ResourceResolver resourceResolver,
            ProjectFileRepository projectFileRepository) {
        this(resourceResolver, projectFileRepository, BedrockConfig.load());
    }

    /**
     * Constructor with custom Bedrock configuration
     *
     * @param resourceResolver      resolver for accessing file content
     * @param projectFileRepository repository for looking up ProjectFile instances
     * @param config                Bedrock configuration
     */
    protected AbstractBedrockJavaClassInspector(ResourceResolver resourceResolver,
            ProjectFileRepository projectFileRepository,
            BedrockConfig config) {
        super(projectFileRepository);
        this.resourceResolver = resourceResolver;
        this.config = config;

        try {
            // Validate configuration
            config.validate();

            // Initialize API client
            this.apiClient = new BedrockApiClient(config);

            logger.info("Initialized Bedrock JavaClassNode inspector: {} with model: {}",
                    getName(), config.getModelId());
            this.initializedSuccessfully = true;

        } catch (BedrockConfigurationException e) {
            logger.warn("Invalid Bedrock configuration for inspector {}: {}. The inspector will be disabled.",
                    getName(), e.getMessage());
            this.initializedSuccessfully = false;
        }
    }

    @Override
    public final boolean supports(JavaClassNode classNode) {
        // Only process SOURCE classes with source files present
        return classNode != null && classNode.isSourceFilePresent();
    }

    @Override
    protected final void analyzeClass(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
        if (!initializedSuccessfully) {
            logger.debug("Bedrock inspector {} is not initialized due to configuration issues.", getName());
            return;
        }

        // Double-check source file is present
        if (!classNode.isSourceFilePresent()) {
            logger.debug("Skipping class {} - no source file present", classNode.getFullyQualifiedName());
            return;
        }

        // Check if Bedrock integration is enabled
        if (!config.isEnabled()) {
            logger.debug("Bedrock integration disabled for inspector: {}", getName());
            return;
        }

        try {
            // Get source content from ProjectFile
            String content = getSourceContent(classNode);

            if (content == null || content.trim().isEmpty()) {
                decorator.error("No source content available for AI analysis");
                return;
            }

            if (content.length() < 10) {
                logger.debug("Content too short for meaningful AI analysis: {} characters", content.length());
                return;
            }

            // Build prompt for AI model
            String prompt = buildPrompt(content, classNode);
            if (prompt == null || prompt.trim().isEmpty()) {
                decorator.error("Failed to build prompt for AI analysis");
                return;
            }

            logger.debug("Invoking Bedrock model for class: {} with prompt length: {}",
                    classNode.getFullyQualifiedName(), prompt.length());

            // Call Bedrock API
            BedrockResponse response = apiClient.invokeModel(prompt);

            // Validate response
            if (!response.hasValidText()) {
                decorator.error("Bedrock API returned empty or invalid response");
                return;
            }

            logger.debug("Received Bedrock response for class: {} with {} characters",
                    classNode.getFullyQualifiedName(), response.getText().length());

            // Parse and set tags/metrics on JavaClassNode
            parseResponse(response.getText(), classNode, decorator);

        } catch (BedrockApiException e) {
            logger.warn("Bedrock API call failed for class {}: {}",
                    classNode.getFullyQualifiedName(), e.getMessage());
            decorator.error("Bedrock API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during Bedrock analysis for class {}: {}",
                    classNode.getFullyQualifiedName(), e.getMessage(), e);
            decorator.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Get source content for a JavaClassNode by finding its ProjectFile
     *
     * @param classNode the class node to get content for
     * @return source code content, or null if not available
     */
    protected String getSourceContent(JavaClassNode classNode) {
        try {
            // Find the ProjectFile for this class
            Optional<ProjectFile> projectFile = findProjectFile(classNode);
            if (projectFile.isEmpty()) {
                logger.warn("Could not find ProjectFile for class: {}", classNode.getFullyQualifiedName());
                return null;
            }

            ProjectFile file = projectFile.get();

            // Create ResourceLocation from file path
            ResourceLocation location = ResourceLocation.file(file.getFilePath().toString());

            // Read content using ResourceResolver
            try (InputStream inputStream = resourceResolver.openStream(location)) {
                if (inputStream == null) {
                    logger.warn("Could not open input stream for: {}", file.getRelativePath());
                    return null;
                }

                // Read all content
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            }

        } catch (Exception e) {
            logger.error("Error reading source content for {}: {}",
                    classNode.getFullyQualifiedName(), e.getMessage());
            return null;
        }
    }

    /**
     * Build a prompt for the AI model based on the source code content and class
     * information.
     * This method should create a clear, specific prompt that will guide the AI to
     * produce
     * the desired analysis result.
     *
     * @param content   the complete source code content
     * @param classNode the JavaClassNode being analyzed
     * @return the prompt string for the AI model
     */
    protected abstract String buildPrompt(String content, JavaClassNode classNode);

    /**
     * Parse the response from the AI model and extract the analysis result.
     * This method should interpret the AI's response and set appropriate
     * tags and metrics on the JavaClassNode via the decorator.
     *
     * @param response  the text response from the AI model
     * @param classNode the JavaClassNode being analyzed
     * @param decorator decorator for setting tags/metrics on the JavaClassNode
     */
    protected abstract void parseResponse(String response, JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator);

    /**
     * Get the Bedrock configuration used by this inspector.
     *
     * @return the current Bedrock configuration
     */
    protected BedrockConfig getConfig() {
        return config;
    }

    /**
     * Get the API client used by this inspector.
     *
     * @return the current API client
     */
    protected BedrockApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Cleanup resources when the inspector is no longer needed.
     */
    public void close() {
        if (apiClient != null) {
            apiClient.close();
        }
    }
}
