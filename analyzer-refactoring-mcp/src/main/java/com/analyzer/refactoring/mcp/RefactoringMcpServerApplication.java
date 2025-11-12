package com.analyzer.refactoring.mcp;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import com.analyzer.refactoring.mcp.tool.RenameCompilationUnitTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

/**
 * Spring Boot MCP Server application for Java refactoring operations.
 * 
 * This application provides a Model Context Protocol (MCP) server that exposes
 * Eclipse JDT refactoring capabilities as tools that can be invoked by AI assistants.
 * 
 * The server uses STDIO transport for communication and implements all major
 * JDT refactoring processors including rename, move, copy, and delete operations.
 * 
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/mcp/">Spring AI MCP Documentation</a>
 * @see <a href="https://www.eclipse.org/jdt/">Eclipse JDT</a>
 */
@SpringBootApplication
@AutoConfiguration
@ComponentScan({"com.analyzer.refactoring.mcp", "com.analyzer.refactoring.mcp.tool"}) // Include your tool pa
public class RefactoringMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RefactoringMcpServerApplication.class, args);
    }

}
