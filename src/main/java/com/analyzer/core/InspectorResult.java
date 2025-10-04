package com.analyzer.core;

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
    public InspectorResult(String tagName, Object value) {
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
    public InspectorResult(String tagName, String errorMessage) {
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
    public InspectorResult(String tagName, Object value, boolean successful, String errorMessage) {
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
     * @param tagName the tag name
     * @param errorMessage  the error message
     * @return an error result
     */
    public static InspectorResult error(String tagName, String errorMessage) {
        return new InspectorResult(tagName, errorMessage);
    }

    public static InspectorResult notApplicable(String tagName, Throwable e) {
        return new InspectorResult(tagName, ERROR, false, e.getMessage());
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
