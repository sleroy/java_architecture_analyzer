package com.analyzer.refactoring.mcp.config;

import com.analyzer.core.serialization.JsonSerializationService;
import com.analyzer.refactoring.mcp.security.PathSecurityValidator;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for database and security services.
 * <p>
 * This configuration initializes:
 * - PathSecurityValidator for file system security
 * - JsonSerializationService for graph node deserialization
 * - GraphDatabaseService for H2 database access
 * <p>
 * The project root path is configured via:
 * 1. Command-line argument: --project-path=/path/to/project
 * 2. Environment variable: PROJECT_PATH=/path/to/project
 * 3. System property: -Dproject.path=/path/to/project
 * 4. Default: current working directory
 */
@Configuration
public class DatabaseConfiguration {


    @Value("${project.path:${PROJECT_PATH:${user.dir}}}")
    private String projectPathString;



    /**
     * Creates the JsonSerializationService bean.
     * This service is used by H2GraphDatabase to deserialize graph nodes.
     */
    @Bean
    public JsonSerializationService jsonSerializationService() {
        return new JsonSerializationService();
    }

}
