package com.analyzer.core;

import java.util.Objects;

/**
 * Represents the result of an inspector analysis.
 * Contains the result value and metadata about the analysis.
 */
public class InspectorResult {

    public static final String NOT_APPLICABLE = "N/A";
    public static final String ERROR = "ERROR";

    private final String inspectorName;
    private final Object value;
    private final boolean successful;
    private final String errorMessage;

    /**
     * Creates a successful inspector result.
     * 
     * @param inspectorName the name of the inspector that produced this result
     * @param value         the result value
     */
    public InspectorResult(String inspectorName, Object value) {
        this.inspectorName = Objects.requireNonNull(inspectorName, "Inspector name cannot be null");
        this.value = value;
        this.successful = true;
        this.errorMessage = null;
    }

    /**
     * Creates a failed inspector result.
     * 
     * @param inspectorName the name of the inspector that produced this result
     * @param errorMessage  the error message
     */
    public InspectorResult(String inspectorName, String errorMessage) {
        this.inspectorName = Objects.requireNonNull(inspectorName, "Inspector name cannot be null");
        this.value = ERROR;
        this.successful = false;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a not applicable result (when inspector doesn't support the analyzed
     * object).
     * 
     * @param inspectorName the name of the inspector
     * @return a not applicable result
     */
    public static InspectorResult notApplicable(String inspectorName) {
        return new InspectorResult(inspectorName, (Object) NOT_APPLICABLE);
    }

    /**
     * Creates an error result.
     * 
     * @param inspectorName the name of the inspector
     * @param errorMessage  the error message
     * @return an error result
     */
    public static InspectorResult error(String inspectorName, String errorMessage) {
        return new InspectorResult(inspectorName, errorMessage);
    }

    public String getInspectorName() {
        return inspectorName;
    }

    public Object getValue() {
        return value;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public boolean isNotApplicable() {
        return NOT_APPLICABLE.equals(value);
    }

    public boolean isError() {
        return ERROR.equals(value);
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
                Objects.equals(inspectorName, that.inspectorName) &&
                Objects.equals(value, that.value) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inspectorName, value, successful, errorMessage);
    }

    @Override
    public String toString() {
        return "InspectorResult{" +
                "inspectorName='" + inspectorName + '\'' +
                ", value=" + value +
                ", successful=" + successful +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
