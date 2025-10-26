package com.analyzer.core;

import com.analyzer.core.inspector.InspectorResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for InspectorResult static factory methods
 */
public class InspectorResultTest {

    @Test
    public void testSuccessFactoryMethods() {
        // Test generic success method

        InspectorResult result = InspectorResult.success("test", "value");
        assertTrue(result.isSuccessful());
        assertFalse(result.isError());
        assertEquals("test", result.getTagName());
        assertEquals("value", result.getValue());
        assertNull(result.getErrorMessage());

        // Test string success method
        InspectorResult stringResult = InspectorResult.success("test", "hello");
        assertTrue(stringResult.isSuccessful());
        assertEquals("hello", stringResult.getValue());

        // Test integer success method
        InspectorResult intResult = InspectorResult.success("test", 42);
        assertTrue(intResult.isSuccessful());
        assertEquals(42, intResult.getValue());

        // Test long success method
        InspectorResult longResult = InspectorResult.success("test", 123L);
        assertTrue(longResult.isSuccessful());
        assertEquals(123L, longResult.getValue());

        // Test double success method
        InspectorResult doubleResult = InspectorResult.success("test", 3.14);
        assertTrue(doubleResult.isSuccessful());
        assertEquals(3.14, doubleResult.getValue());

        // Test boolean success method
        InspectorResult boolResult = InspectorResult.success("test", true);
        assertTrue(boolResult.isSuccessful());
        assertEquals(true, boolResult.getValue());
    }

    @Test
    public void testErrorFactoryMethods() {
        // Test basic error method
        InspectorResult errorResult = InspectorResult.error("test", "Something went wrong");
        assertTrue(errorResult.isError());
        assertFalse(errorResult.isSuccessful());
        assertEquals("test", errorResult.getTagName());
        assertEquals("Something went wrong", errorResult.getErrorMessage());

        // Test fromException method
        Exception ex = new RuntimeException("Test exception");
        InspectorResult exceptionResult = InspectorResult.fromException("test", ex);
        assertTrue(exceptionResult.isError());
        assertEquals("Test exception", exceptionResult.getErrorMessage());

        // Test fromException with null message
        Exception exWithNullMessage = new RuntimeException();
        InspectorResult nullMessageResult = InspectorResult.fromException("test", exWithNullMessage);
        assertTrue(nullMessageResult.isError());
        assertEquals("RuntimeException", nullMessageResult.getErrorMessage());

        // Test fromThrowable method
        Throwable throwable = new IllegalArgumentException("Invalid argument");
        InspectorResult throwableResult = InspectorResult.fromThrowable("test", throwable);
        assertTrue(throwableResult.isError());
        assertEquals("Invalid argument", throwableResult.getErrorMessage());
    }

    @Test
    public void testNotApplicableFactoryMethods() {
        // Test basic notApplicable method
        InspectorResult naResult = InspectorResult.notApplicable("test");
        assertTrue(naResult.isNotApplicable());
        assertFalse(naResult.isSuccessful());
        assertEquals("test", naResult.getTagName());
        assertEquals(InspectorResult.NOT_APPLICABLE, naResult.getValue());

        // Test notApplicable with throwable
        Throwable t = new UnsupportedOperationException("Not supported");
        InspectorResult naWithThrowable = InspectorResult.notApplicable("test", t);
        assertFalse(naWithThrowable.isSuccessful());
        assertEquals("Not supported", naWithThrowable.getErrorMessage());
    }

    @Test
    public void testSkippedFactoryMethods() {
        // Test skipped method
        InspectorResult skippedResult = InspectorResult.skipped("test", "No data available");
        assertFalse(skippedResult.isSuccessful());
        assertEquals("test", skippedResult.getTagName());
        assertEquals("SKIPPED: No data available", skippedResult.getValue());
        assertEquals("No data available", skippedResult.getErrorMessage());

        // Test unsupportedFileType method
        InspectorResult unsupportedResult = InspectorResult.unsupportedFileType("test");
        assertFalse(unsupportedResult.isSuccessful());
        assertEquals("SKIPPED: Unsupported file type", unsupportedResult.getValue());
        assertEquals("Unsupported file type", unsupportedResult.getErrorMessage());
    }

    @Test
    public void testWarningAndPartialSuccessFactoryMethods() {
        // Test successWithWarning method
        InspectorResult warningResult = InspectorResult.successWithWarning("test", "result", "This is a warning");
        assertTrue(warningResult.isSuccessful());
        assertEquals("result", warningResult.getValue());
        assertEquals("WARNING: This is a warning", warningResult.getErrorMessage());

        // Test partialSuccess method
        InspectorResult partialResult = InspectorResult.partialSuccess("test", "partial_result", "Some data missing");
        assertTrue(partialResult.isSuccessful());
        assertEquals("partial_result", partialResult.getValue());
        assertEquals("PARTIAL: Some data missing", partialResult.getErrorMessage());
    }

    @Test
    public void testFileSpecificFactoryMethods() {
        // Test emptyFile method
        InspectorResult emptyResult = InspectorResult.emptyFile("test");
        assertTrue(emptyResult.isNotApplicable());
        assertEquals(InspectorResult.NOT_APPLICABLE, emptyResult.getValue());

        // Test fileNotReadable method
        InspectorResult notReadableResult = InspectorResult.fileNotReadable("test", "Permission denied");
        assertTrue(notReadableResult.isError());
        assertEquals("File not readable: Permission denied", notReadableResult.getErrorMessage());
    }

    @Test
    public void testFactoryMethodsWithNullTagName() {
        // Test that factory methods handle null tag names properly (should throw NPE
        // for consistency)
        assertThrows(NullPointerException.class, () -> {
            InspectorResult.success(null, "value");
        });

        assertThrows(NullPointerException.class, () -> {
            InspectorResult.error(null, "error");
        });
    }

    @Test
    public void testStringValueMethod() {
        // Test getStringValue with different types
        InspectorResult stringResult = InspectorResult.success("test", "hello");
        assertEquals("hello", stringResult.getStringValue());

        InspectorResult intResult = InspectorResult.success("test", 42);
        assertEquals("42", intResult.getStringValue());

        InspectorResult boolResult = InspectorResult.success("test", true);
        assertEquals("true", boolResult.getStringValue());

        InspectorResult nullResult = InspectorResult.success("test", null);
        assertEquals("", nullResult.getStringValue());
    }

    @Test
    public void testEqualsAndHashCode() {
        // Test that results created with factory methods have proper equals/hashCode
        InspectorResult result1 = InspectorResult.success("test", "value");
        InspectorResult result2 = InspectorResult.success("test", "value");
        InspectorResult result3 = InspectorResult.success("test", "different");

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
        assertNotEquals(result1.hashCode(), result3.hashCode());
    }

    @Test
    public void testToString() {
        // Test that toString works properly with factory methods
        InspectorResult result = InspectorResult.success("test", "value");
        String toString = result.toString();
        assertTrue(toString.contains("test"));
        assertTrue(toString.contains("value"));
        assertTrue(toString.contains("successful=true"));
    }
}
