package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService.RefactoringResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

/**
 * Base class for refactoring tools providing common utility methods.
 */
public abstract class BaseRefactoringTool {
    
    protected static final Logger logger = LoggerFactory.getLogger(BaseRefactoringTool.class);
    protected static final ObjectMapper objectMapper = new ObjectMapper();
    
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
}
