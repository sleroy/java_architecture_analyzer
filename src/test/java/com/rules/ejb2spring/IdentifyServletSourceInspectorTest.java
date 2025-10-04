package com.rules.ejb2spring;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.test.stubs.StubClazz;
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

        Clazz clazz = setupStubCall(javaCode, "TestServlet");
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

        Clazz clazz = setupStubCall(javaCode, "TestServlet");
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

        Clazz clazz = setupStubCall(javaCode, "TestServlet");
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

        Clazz clazz = setupStubCall(javaCode, "SimpleServlet");
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

        Clazz clazz = setupStubCall(javaCode, "SimpleServlet");
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

        Clazz clazz = setupStubCall(javaCode, "RegularClass");
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

        Clazz clazz = setupStubCall(javaCode, "TestInterface");
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

        Clazz clazz = setupStubCall(javaCode, "ServiceClass");
        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(false, result.getValue());
    }

    private StubClazz setupStubCall(String sourceCode, String className) throws IOException {
        ResourceLocation sourceLocation = new StubResourceLocation("/test/" + className + ".java");
        StubClazz clazz = new StubClazz(className, "com.example", Clazz.ClassType.SOURCE_ONLY, sourceLocation, null);

        stubResourceResolver.setFileExists(sourceLocation, true);
        stubResourceResolver.setFileContent(sourceLocation, sourceCode);

        return clazz;
    }
}
