package com.analyzer.inspectors.core.bedrock;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a request to AWS Bedrock API.
 * Supports multiple model formats including Claude (legacy and Messages API),
 * Titan, and generic models.
 * Uses Jackson annotations for JSON serialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BedrockRequest {

    // Claude Messages API fields (Claude 3, 3.5, 4)
    @JsonProperty("anthropic_version")
    private String anthropicVersion;

    @JsonProperty("messages")
    private List<Message> messages;

    // Claude legacy fields (Claude 2)
    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("max_tokens_to_sample")
    private Integer maxTokensToSample;

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

    // Titan-specific fields
    @JsonProperty("inputText")
    private String inputText;

    @JsonProperty("maxTokenCount")
    private Integer maxTokenCount;

    // Generic/common fields
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("top_k")
    private Integer topK;

    /**
     * Represents a message in the Claude Messages API format.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return "Message{" +
                    "role='" + role + '\'' +
                    ", content='"
                    + (content != null ? content.substring(0, Math.min(50, content.length())) + "..." : null) + '\'' +
                    '}';
        }
    }

    public BedrockRequest() {
        this.stopSequences = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    // Claude Messages API methods
    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    public void setAnthropicVersion(String anthropicVersion) {
        this.anthropicVersion = anthropicVersion;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public void addMessage(String role, String content) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(new Message(role, content));
    }

    public void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    // Prompt-related methods (legacy)
    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    // Claude-specific methods
    public Integer getMaxTokensToSample() {
        return maxTokensToSample;
    }

    public void setMaxTokensToSample(Integer maxTokensToSample) {
        this.maxTokensToSample = maxTokensToSample;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences != null ? stopSequences : new ArrayList<>();
    }

    public void addStopSequence(String stopSequence) {
        if (this.stopSequences == null) {
            this.stopSequences = new ArrayList<>();
        }
        this.stopSequences.add(stopSequence);
    }

    // Titan-specific methods
    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public Integer getMaxTokenCount() {
        return maxTokenCount;
    }

    public void setMaxTokenCount(Integer maxTokenCount) {
        this.maxTokenCount = maxTokenCount;
    }

    // Generic/common methods
    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    @Override
    public String toString() {
        return "BedrockRequest{" +
                "prompt='" + (prompt != null ? prompt.substring(0, Math.min(50, prompt.length())) + "..." : null) + '\''
                +
                ", inputText='"
                + (inputText != null ? inputText.substring(0, Math.min(50, inputText.length())) + "..." : null) + '\'' +
                ", maxTokensToSample=" + maxTokensToSample +
                ", maxTokenCount=" + maxTokenCount +
                ", maxTokens=" + maxTokens +
                ", temperature=" + temperature +
                ", topP=" + topP +
                ", topK=" + topK +
                ", stopSequences=" + stopSequences +
                '}';
    }
}
