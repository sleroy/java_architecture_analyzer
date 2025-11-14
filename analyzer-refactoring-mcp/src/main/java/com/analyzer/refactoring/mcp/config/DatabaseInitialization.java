package com.analyzer.refactoring.mcp.config;

import com.analyzer.core.serialization.JsonSerializationService;
import com.analyzer.refactoring.mcp.security.PathSecurityValidator;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DatabaseInitialization {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitialization.class);
    private final PathSecurityValidator pathSecurityValidator;
    private final GraphDatabaseService graphDatabaseService;
    private final JsonSerializationService jsonSerializationService;


    @Value("${project.path:${PROJECT_PATH:${user.dir}}}")
    private String projectPathString;


    @Autowired
    public DatabaseInitialization(final PathSecurityValidator pathSecurityValidator,
                                  final GraphDatabaseService graphDatabaseService,
                                  final JsonSerializationService jsonSerializationService) {
        this.pathSecurityValidator = pathSecurityValidator;
        this.graphDatabaseService = graphDatabaseService;
        this.jsonSerializationService = jsonSerializationService;
    }

    /**
     * Initialize services after the application is fully started.
     * This ensures all Spring beans are ready before initialization.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeServices(final ApplicationEvent event) {
        try {
            // Parse and validate project root path
            final Path projectRoot = Paths.get(projectPathString).toAbsolutePath().normalize();
            logger.info("===========================================");
            logger.info("Initializing MCP Server");
            logger.info("Project root: {}", projectRoot);
            logger.info("===========================================");

            // Initialize security validator
            pathSecurityValidator.initialize(projectRoot);
            logger.info("✓ Security validator initialized");

            // Initialize database service
            final boolean dbLoaded = graphDatabaseService.initialize(projectRoot, jsonSerializationService);

            if (dbLoaded) {
                final GraphDatabaseService.GraphStatistics stats = graphDatabaseService.getStatistics();
                logger.info("✓ Graph database loaded: {}", stats);
                logger.info("  - Total nodes: {}", stats.getTotalNodes());
                logger.info("  - Total edges: {}", stats.getTotalEdges());
                logger.info("  - Class nodes: {}", stats.getClassNodes());
                logger.info("  - File nodes: {}", stats.getFileNodes());
            } else {
                logger.info("✗ Graph database not found - operating in AST-only mode");
                logger.info("  To enable graph features, run the analyzer application first:");
                logger.info("  cd {} && java -jar analyzer-app.jar", projectRoot);
            }

            logger.info("===========================================");
            logger.info("MCP Server ready");
            logger.info("Security: File operations restricted to: {}", projectRoot);
            logger.info("Database: {}", dbLoaded ? "Loaded" : "Not available");
            logger.info("===========================================");

        } catch (final Exception e) {
            logger.error("Failed to initialize services", e);
            logger.error("MCP Server will continue but some features may be unavailable");
        }
    }
}
