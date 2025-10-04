package com.rules.ejb2spring;

import com.analyzer.core.ClassType;
import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.test.stubs.StubProjectFile;
import com.analyzer.test.stubs.StubResourceLocation;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for IdentifyServletSourceInspector.
 * Tests various servlet patterns:
 * 1. @WebServlet annotation
 * 2. extends HttpServlet
 * 3. implements Servlet interface
 * 4. Non-servlet classes
 */
class IdentifyServletSourceInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private IdentifyServletSourceInspector inspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        inspector = new IdentifyServletSourceInspector(stubResourceResolver);
    }

    @Test
    void getName_ShouldReturnCorrectName() {
        assertEquals("Identify Servlet", inspector.getName());
    }

    @Test
    void getColumnName_ShouldReturnCorrectColumnName() {
        assertEquals("is_servlet", inspector.getColumnName());
    }

    @Test
    void analyze_WebServletAnnotation_ShouldDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                import javax.servlet.annotation.WebServlet;

                @WebServlet("/test")
                public class TestServlet {
                    // servlet implementation
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "TestServlet");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(true, result.getValue());
    }

    @Test
    void analyze_ExtendsHttpServlet_ShouldDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                import javax.servlet.http.HttpServlet;

                public class TestServlet extends HttpServlet {
                    // servlet implementation
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "TestServlet");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(true, result.getValue());
    }

    @Test
    void analyze_ImplementsServlet_ShouldDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                import javax.servlet.Servlet;

                public class TestServlet implements Servlet {
                    // servlet implementation
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "TestServlet");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(true, result.getValue());
    }

    @Test
    void analyze_SimpleWebServletAnnotation_ShouldDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                @WebServlet("/simple")
                public class SimpleServlet {
                    // servlet implementation
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "SimpleServlet");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(true, result.getValue());
    }

    @Test
    void analyze_SimpleHttpServletExtension_ShouldDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                public class SimpleServlet extends HttpServlet {
                    // servlet implementation
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "SimpleServlet");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(true, result.getValue());
    }

    @Test
    void analyze_NonServletClass_ShouldNotDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                public class RegularClass {
                    private String name;

                    public String getName() {
                        return name;
                    }
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "RegularClass");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(false, result.getValue());
    }

    @Test
    void analyze_Interface_ShouldNotDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                public interface TestInterface {
                    void doSomething();
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "TestInterface");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(false, result.getValue());
    }

    @Test
    void analyze_ClassWithOtherAnnotations_ShouldNotDetectServlet() throws IOException {
        String javaCode = """
                package com.example;

                @Component
                @Service
                public class ServiceClass {
                    // not a servlet
                }
                """;

        ProjectFile clazz = setupStubCall(javaCode, "ServiceClass");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(false, result.getValue());
    }

    private StubProjectFile setupStubCall(String sourceCode, String className) throws IOException {
        StubProjectFile clazz = new StubProjectFile(className, "com.example", ClassType.SOURCE_ONLY);
        clazz.setHasSourceCode(true);

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        ResourceLocation actualLocation = new ResourceLocation(clazz.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setFileContent(actualLocation, sourceCode);

        return clazz;
    }
}
