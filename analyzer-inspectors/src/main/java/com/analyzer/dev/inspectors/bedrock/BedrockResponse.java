package com.analyzer.dev.inspectors.bedrock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a response from AWS Bedrock API.
 * Supports multiple model response formats including Claude, Titan, and generic
 * models.
 * Uses Jackson annotations for JSON deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BedrockResponse {

    @JsonProperty("completion")
    private String completion;

    @JsonProperty("text")
    private String text;

    @JsonProperty("outputText")
    private String outputText;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("stop")
    private String stop;

    // Raw response for debugging
    private String rawResponse;

    public BedrockResponse() {
    }

    /**
     * Get the response text from various possible fields.
     * Tries different field names used by different models.
     */
    public String getText() {
        if (text != null && !text.trim().isEmpty()) {
            return text.trim();
        }
        if (completion != null && !completion.trim().isEmpty()) {
            return completion.trim();
        }
        if (outputText != null && !outputText.trim().isEmpty()) {
            return outputText.trim();
        }
        return "";
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCompletion() {
        return completion;
    }

    public void setCompletion(String completion) {
        this.completion = completion;
    }

    public String getOutputText() {
        return outputText;
    }

    public void setOutputText(String outputText) {
        this.outputText = outputText;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getStop() {
        return stop;
    }

    public void setStop(String stop) {
        this.stop = stop;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    /**
     * Check if the response contains valid text content.
     */
    public boolean hasValidText() {
        String responseText = getText();
        return responseText != null && !responseText.trim().isEmpty();
    }

    /**
     * Get the effective stop reason from various possible fields.
     */
    public String getEffectiveStopReason() {
        if (stopReason != null && !stopReason.trim().isEmpty()) {
            return stopReason;
        }
        if (stop != null && !stop.trim().isEmpty()) {
            return stop;
        }
        return "unknown";
    }

    @Override
    public String toString() {
        return "BedrockResponse{" +
                "text='" + (getText().length() > 100 ? getText().substring(0, 100) + "..." : getText()) + '\'' +
                ", stopReason='" + getEffectiveStopReason() + '\'' +
                ", hasRawResponse=" + (rawResponse != null) +
                '}';
    }
}
