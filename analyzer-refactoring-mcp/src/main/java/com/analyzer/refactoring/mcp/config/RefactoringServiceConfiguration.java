package com.analyzer.refactoring.mcp.config;

import com.analyzer.ejb2spring.analysis.EjbAntiPatternDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class RefactoringServiceConfiguration {

    @Bean
    public EjbAntiPatternDetector antiPatternDetector() {
        return new EjbAntiPatternDetector();
    }
}
