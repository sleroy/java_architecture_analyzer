package com.analyzer.core.inspector;
import com.analyzer.core.inspector.InspectorDependencies;

import java.util.Objects;

/**
 * Represents the result of an inspector analysis.
 * Contains the result value and metadata about the analysis.
 */
public class InspectorResult {

    public static final String NOT_APPLICABLE = "N/A";
    public static final String ERROR = "ERROR";

    private final String tagName;
    private final Object value;
    private final boolean successful;
    private final String errorMessage;

    /**
     * Creates a successful inspector result.
     *
     * @param tagName the name of the inspector that produced this result
     * @param value   the result value
     */
    private InspectorResult(String tagName, Object value) {
        this.tagName = Objects.requireNonNull(tagName, "Inspector name cannot be null");
        this.value = value;
        this.successful = true;
        this.errorMessage = null;
    }

    /**
     * Creates a failed inspector result.
     *
     * @param tagName      the name of the inspector that produced this result
     * @param errorMessage the error message
     */
    private InspectorResult(String tagName, String errorMessage) {
        this.tagName = Objects.requireNonNull(tagName, "Inspector name cannot be null");
        this.value = ERROR;
        this.successful = false;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates an inspector result.
     *
     * @param tagName      the tag name
     * @param value        the value
     * @param successful   what the result successful
     * @param errorMessage optional error message
     */
    private InspectorResult(String tagName, Object value, boolean successful, String errorMessage) {
        this.tagName = tagName;
        this.value = value;
        this.successful = successful;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a not applicable result (when inspector doesn't support the analyzed
     * object).
     *
     * @param tagName the tag name
     * @return a not applicable result
     */
    public static InspectorResult notApplicable(String tagName) {
        return new InspectorResult(tagName, (Object) NOT_APPLICABLE, false, "");
    }

    /**
     * Creates an error result.
     *
     * @param tagName      the tag name
     * @param errorMessage the error message
     * @return an error result
     */
    public static InspectorResult error(String tagName, String errorMessage) {
        return new InspectorResult(tagName, errorMessage);
    }

    public static InspectorResult notApplicable(String tagName, Throwable e) {
        return new InspectorResult(tagName, ERROR, false, e.getMessage());
    }

    // ==================== ADDITIONAL STATIC FACTORY METHODS ====================

    /**
     * Creates a successful inspector result with the given value.
     *
     * @param tagName the name of the inspector that produced this result
     * @param value   the result value
     * @return a successful inspector result
     */
    public static InspectorResult success(String tagName, Object value) {
        return new InspectorResult(tagName, value);
    }

    /**
     * Creates a successful inspector result with a string value.
     *
     * @param tagName the name of the inspector that produced this result
     * @param value   the string result value
     * @return a successful inspector result
     */
    public static InspectorResult success(String tagName, String value) {
        return new InspectorResult(tagName, (Object) value);
    }

    /**
     * Creates a successful inspector result with an integer value.
     *
     * @param tagName the name of the inspector that produced this result
     * @param value   the integer result value
     * @return a successful inspector result
     */
    public static InspectorResult success(String tagName, int value) {
        return new InspectorResult(tagName, value);
    }

    /**
     * Creates a successful inspector result with a long value.
     *
     * @param tagName the name of the inspector that produced this result
     * @param value   the long result value
     * @return a successful inspector result
     */
    public static InspectorResult success(String tagName, long value) {
        return new InspectorResult(tagName, value);
    }

    /**
     * Creates a successful inspector result with a double value.
     *
     * @param tagName the name of the inspector that produced this result
     * @param value   the double result value
     * @return a successful inspector result
     */
    public static InspectorResult success(String tagName, double value) {
        return new InspectorResult(tagName, value);
    }

    /**
     * Creates a successful inspector result with a boolean value.
     *
     * @param tagName the name of the inspector that produced this result
     * @param value   the boolean result value
     * @return a successful inspector result
     */
    public static InspectorResult success(String tagName, boolean value) {
        return new InspectorResult(tagName, value);
    }

    /**
     * Creates an error result from an exception.
     *
     * @param tagName   the name of the inspector that produced this result
     * @param exception the exception that caused the error
     * @return an error inspector result
     */
    public static InspectorResult fromException(String tagName, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        return new InspectorResult(tagName, message);
    }

    /**
     * Creates an error result from a throwable.
     *
     * @param tagName   the name of the inspector that produced this result
     * @param throwable the throwable that caused the error
     * @return an error inspector result
     */
    public static InspectorResult fromThrowable(String tagName, Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = throwable.getClass().getSimpleName();
        }
        return new InspectorResult(tagName, message);
    }

    /**
     * Creates a result indicating that processing was skipped.
     *
     * @param tagName the name of the inspector
     * @param reason  the reason why processing was skipped
     * @return a skipped inspector result
     */
    public static InspectorResult skipped(String tagName, String reason) {
        return new InspectorResult(tagName, "SKIPPED: " + reason, false, reason);
    }

    /**
     * Creates a result indicating that processing was skipped due to unsupported
     * file type.
     *
     * @param tagName the name of the inspector
     * @return a skipped inspector result
     */
    public static InspectorResult unsupportedFileType(String tagName) {
        return skipped(tagName, "Unsupported file type");
    }

    /**
     * Creates a result with a warning message but still considered successful.
     *
     * @param tagName the name of the inspector
     * @param value   the result value
     * @param warning the warning message
     * @return a successful inspector result with a warning
     */
    public static InspectorResult successWithWarning(String tagName, Object value, String warning) {
        return new InspectorResult(tagName, value, true, "WARNING: " + warning);
    }

    /**
     * Creates a result indicating partial success.
     *
     * @param tagName the name of the inspector
     * @param value   the partial result value
     * @param message additional message about the partial result
     * @return a partial success inspector result
     */
    public static InspectorResult partialSuccess(String tagName, Object value, String message) {
        return new InspectorResult(tagName, value, true, "PARTIAL: " + message);
    }

    /**
     * Creates a result for when a file is empty.
     *
     * @param tagName the name of the inspector
     * @return an empty file result
     */
    public static InspectorResult emptyFile(String tagName) {
        return notApplicable(tagName);
    }

    /**
     * Creates a result for when a file cannot be read.
     *
     * @param tagName the name of the inspector
     * @param reason  the reason the file cannot be read
     * @return a file not readable result
     */
    public static InspectorResult fileNotReadable(String tagName, String reason) {
        return error(tagName, "File not readable: " + reason);
    }

    public String getTagName() {
        return tagName;
    }

    public Object getValue() {
        return value;
    }

    public boolean isSuccessful() {
        return successful && !(ERROR.equals(value));
    }

    public boolean isNotApplicable() {
        return NOT_APPLICABLE.equals(value);
    }

    public boolean isError() {
        return !isSuccessful();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the string representation of the value for CSV output.
     *
     * @return string representation of the value
     */
    public String getStringValue() {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InspectorResult that = (InspectorResult) o;
        return successful == that.successful &&
                Objects.equals(tagName, that.tagName) &&
                Objects.equals(value, that.value) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagName, value, successful, errorMessage);
    }

    @Override
    public String toString() {
        return "InspectorResult{" +
                "inspectorName='" + tagName + '\'' +
                ", value=" + value +
                ", successful=" + successful +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
