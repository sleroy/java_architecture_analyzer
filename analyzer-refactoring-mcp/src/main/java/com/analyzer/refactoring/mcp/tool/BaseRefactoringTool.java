package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.security.PathSecurityValidator;
import com.analyzer.refactoring.mcp.service.JdtRefactoringService.RefactoringResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base class for refactoring tools providing common utility methods.
 * 
 * This base class provides:
 * - JSON serialization helpers
 * - Security validation for file operations
 * - Common error handling patterns
 */
public abstract class BaseRefactoringTool {

    protected static final Logger logger = LoggerFactory.getLogger(BaseRefactoringTool.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    protected PathSecurityValidator pathSecurityValidator;

    /**
     * Convert RefactoringResult to JSON string response.
     */
    protected String toJsonResponse(RefactoringResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error serializing result to JSON", e);
            return "{\"success\":false,\"messages\":[\"Error serializing result: " + e.getMessage() + "\"]}";
        }
    }

    /**
     * Convert any object to JSON string response.
     */
    protected String toJsonResponse(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error serializing result to JSON", e);
            return "{\"success\":false,\"error\":\"Error serializing result: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Validate that a file path is within the allowed project root.
     * 
     * @param projectPath  The project root path
     * @param relativePath The relative file path
     * @throws SecurityException if the path is outside the project root
     */
    protected void validatePath(String projectPath, String relativePath) throws SecurityException {
        if (pathSecurityValidator != null && pathSecurityValidator.isInitialized()) {
            pathSecurityValidator.validateProjectPath(projectPath, relativePath);
        } else {
            logger.warn("PathSecurityValidator not available - skipping path validation");
        }
    }

    /**
     * Create a JSON error response for security violations.
     * 
     * @param message The error message
     * @return JSON error response
     */
    protected String securityErrorResponse(String message) {
        return String.format("{\"success\":false,\"error\":\"Security violation: %s\"}",
                message.replace("\"", "\\\""));
    }
}
