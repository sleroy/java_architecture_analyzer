package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for moving static members between classes.
 * Maps to JDT processor: org.eclipse.jdt.ui.moveStaticMembersProcessor
 */

public class MoveStaticMembersTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public MoveStaticMembersTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Move static members (fields, methods, nested types) from one class to another and update all references. " +
                        "This refactoring relocates static members to a more appropriate class, automatically updating all references " +
                        "including static imports and qualified references. Useful for organizing utility methods, " +
                        "consolidating related constants, or refactoring class responsibilities following SOLID principles.")
    public String moveStaticMembers(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the source file containing the static members to move") 
            String sourceFile,
            
            @ToolParam(description = "List of static member names to move (field names, method names, or nested type names)") 
            List<String> memberNames,
            
            @ToolParam(description = "Fully qualified name of the destination class where members should be moved (e.g., com.example.TargetClass)") 
            String destinationClass) {
        try {
            var result = refactoringService.moveStaticMembers(projectPath, sourceFile, memberNames, destinationClass);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in move_static_members tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
