package com.analyzer.inspectors.core.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for AWS Bedrock API integration.
 * Handles authentication, rate limiting, request/response serialization,
 * and error handling for Bedrock model invocations.
 */
public class BedrockApiClient {

    private static final Logger logger = LoggerFactory.getLogger(BedrockApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final BedrockConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore rateLimiter;
    private volatile long lastRequestTime = 0;

    /**
     * Creates a new BedrockApiClient with the specified configuration.
     *
     * @param config the Bedrock configuration
     */
    public BedrockApiClient(BedrockConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.rateLimiter = new Semaphore(config.getRateLimitRpm());

        // Build HTTP client with timeout settings
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .retryOnConnectionFailure(true)
                .build();

        logger.info("Initialized Bedrock API client for model: {}", config.getModelId());
    }

    /**
     * Invoke a Bedrock model with the given prompt.
     *
     * @param prompt the input prompt for the model
     * @return the response from the model
     * @throws BedrockApiException if the API call fails
     */
    public BedrockResponse invokeModel(String prompt) throws BedrockApiException {
        return invokeModelWithRetry(prompt, config.getRetryAttempts());
    }

    private BedrockResponse invokeModelWithRetry(String prompt, int attemptsRemaining) throws BedrockApiException {
        try {
            // Apply rate limiting
            applyRateLimit();

            // Build request
            BedrockRequest request = buildRequest(prompt);
            String requestJson = objectMapper.writeValueAsString(request);

            if (config.isLogRequests()) {
                logger.debug("Bedrock request: {}", requestJson);
            }

            // Make HTTP call
            String responseJson = makeHttpRequest(requestJson);

            if (config.isLogResponses()) {
                logger.debug("Bedrock response: {}", responseJson);
            }

            // Parse response
            return parseResponse(responseJson);

        } catch (BedrockApiException e) {
            if (attemptsRemaining > 1 && isRetryableError(e)) {
                logger.warn("Bedrock API call failed, retrying. Attempts remaining: {}. Error: {}",
                        attemptsRemaining - 1, e.getMessage());
                try {
                    Thread.sleep(1000); // Simple backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BedrockApiException("Request interrupted during retry", ie);
                }
                return invokeModelWithRetry(prompt, attemptsRemaining - 1);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new BedrockApiException("Unexpected error during Bedrock API call", e);
        }
    }

    private void applyRateLimit() throws BedrockApiException {
        try {
            // Simple rate limiting based on requests per minute
            long currentTime = System.currentTimeMillis();
            long timeSinceLastRequest = currentTime - lastRequestTime;
            long minInterval = 60000 / config.getRateLimitRpm(); // ms between requests

            if (timeSinceLastRequest < minInterval) {
                long sleepTime = minInterval - timeSinceLastRequest;
                logger.debug("Rate limiting: sleeping for {} ms", sleepTime);
                Thread.sleep(sleepTime);
            }

            if (!rateLimiter.tryAcquire(config.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                throw new BedrockApiException("Rate limit exceeded, request timed out");
            }

            lastRequestTime = System.currentTimeMillis();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BedrockApiException("Request interrupted during rate limiting", e);
        }
    }

    private BedrockRequest buildRequest(String prompt) {
        BedrockRequest request = new BedrockRequest();

        // Build request based on model type
        if (isClaudeModel(config.getModelId())) {
            if (useMessagesApi(config.getModelId())) {
                request = buildClaudeMessagesRequest(prompt);
            } else {
                request = buildClaudeLegacyRequest(prompt);
            }
        } else if (config.getModelId().contains("amazon.titan")) {
            request = buildTitanRequest(prompt);
        } else {
            // Default/generic request format
            request = buildGenericRequest(prompt);
        }

        return request;
    }

    /**
     * Determines if the model ID represents a Claude model.
     */
    private boolean isClaudeModel(String modelId) {
        return modelId.contains("anthropic.claude") ||
                modelId.contains("us.anthropic.claude");
    }

    /**
     * Determines if the Claude model uses the Messages API format.
     * Claude 3, 3.5, and 4 use Messages API; Claude 2 uses legacy format.
     */
    private boolean useMessagesApi(String modelId) {
        // Claude 3, 3.5, and 4 models use Messages API
        return modelId.contains("claude-3") ||
                modelId.contains("claude-sonnet-4") ||
                modelId.contains("us.anthropic.claude-3") ||
                modelId.contains("us.anthropic.claude-sonnet-4");
    }

    /**
     * Build request for Claude Messages API (Claude 3, 3.5, 4).
     * Uses the new Messages API format as per AWS documentation.
     */
    private BedrockRequest buildClaudeMessagesRequest(String prompt) {
        BedrockRequest request = new BedrockRequest();
        request.setAnthropicVersion("bedrock-2023-05-31");
        request.setMaxTokens(config.getMaxTokens());
        request.setTemperature(config.getTemperature());
        request.setTopP(config.getTopP());

        // Add user message
        request.addMessage("user", prompt);

        return request;
    }

    /**
     * Build request for Claude legacy format (Claude 2).
     * Uses the original prompt-based format.
     */
    private BedrockRequest buildClaudeLegacyRequest(String prompt) {
        BedrockRequest request = new BedrockRequest();
        request.setPrompt("\n\nHuman: " + prompt + "\n\nAssistant:");
        request.setMaxTokensToSample(config.getMaxTokens());
        request.setTemperature(config.getTemperature());
        request.setTopP(config.getTopP());
        request.addStopSequence("\n\nHuman:");
        return request;
    }

    private BedrockRequest buildTitanRequest(String prompt) {
        BedrockRequest request = new BedrockRequest();
        request.setInputText(prompt);
        request.setMaxTokenCount(config.getMaxTokens());
        request.setTemperature(config.getTemperature());
        request.setTopP(config.getTopP());
        return request;
    }

    private BedrockRequest buildGenericRequest(String prompt) {
        BedrockRequest request = new BedrockRequest();
        request.setPrompt(prompt);
        request.setMaxTokens(config.getMaxTokens());
        request.setTemperature(config.getTemperature());
        request.setTopP(config.getTopP());
        return request;
    }

    private String makeHttpRequest(String requestJson) throws BedrockApiException {
        try {
            // Build URL for model invocation
            String url = config.getBaseUrl() + "/model/" + config.getModelId() + "/invoke";

            RequestBody body = RequestBody.create(requestJson, JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + config.getApiToken())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    String errorMsg = String.format("Bedrock API call failed with status %d: %s",
                            response.code(), responseBody);
                    throw new BedrockApiException(errorMsg);
                }

                return responseBody;
            }

        } catch (IOException e) {
            throw new BedrockApiException("HTTP request failed", e);
        } finally {
            // Release rate limiter permit
            rateLimiter.release();
        }
    }

    private BedrockResponse parseResponse(String responseJson) throws BedrockApiException {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseJson);
            BedrockResponse response = new BedrockResponse();

            // Parse response based on model type
            if (config.getModelId().contains("anthropic.claude")) {
                response = parseClaudeResponse(jsonNode);
            } else if (config.getModelId().contains("amazon.titan")) {
                response = parseTitanResponse(jsonNode);
            } else {
                response = parseGenericResponse(jsonNode);
            }

            response.setRawResponse(responseJson);
            return response;

        } catch (Exception e) {
            throw new BedrockApiException("Failed to parse Bedrock response", e);
        }
    }

    private BedrockResponse parseClaudeResponse(JsonNode jsonNode) {
        BedrockResponse response = new BedrockResponse();

        // Messages API format (Claude 3, 3.5, 4)
        if (jsonNode.has("content") && jsonNode.get("content").isArray() &&
                jsonNode.get("content").size() > 0) {
            JsonNode contentArray = jsonNode.get("content");
            JsonNode firstContent = contentArray.get(0);
            if (firstContent.has("text")) {
                response.setText(firstContent.get("text").asText().trim());
            }
        }
        // Legacy format (Claude 2)
        else if (jsonNode.has("completion")) {
            response.setText(jsonNode.get("completion").asText().trim());
        }

        // Handle stop reason (both formats)
        if (jsonNode.has("stop_reason")) {
            response.setStopReason(jsonNode.get("stop_reason").asText());
        }

        return response;
    }

    private BedrockResponse parseTitanResponse(JsonNode jsonNode) {
        BedrockResponse response = new BedrockResponse();

        if (jsonNode.has("results") && jsonNode.get("results").isArray() &&
                jsonNode.get("results").size() > 0) {
            JsonNode firstResult = jsonNode.get("results").get(0);
            if (firstResult.has("outputText")) {
                response.setText(firstResult.get("outputText").asText().trim());
            }
        }

        return response;
    }

    private BedrockResponse parseGenericResponse(JsonNode jsonNode) {
        BedrockResponse response = new BedrockResponse();

        // Try common response field names
        if (jsonNode.has("text")) {
            response.setText(jsonNode.get("text").asText().trim());
        } else if (jsonNode.has("completion")) {
            response.setText(jsonNode.get("completion").asText().trim());
        } else if (jsonNode.has("response")) {
            response.setText(jsonNode.get("response").asText().trim());
        } else {
            // Fallback: use entire response as text
            response.setText(jsonNode.toString());
        }

        return response;
    }

    private boolean isRetryableError(BedrockApiException e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("503") ||
                message.contains("502") ||
                message.contains("rate limit");
    }

    /**
     * Close the HTTP client and release resources.
     */
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    /**
     * Get configuration information for debugging.
     */
    public String getConfigSummary() {
        return String.format("BedrockApiClient{model=%s, baseUrl=%s, rateLimitRpm=%d, timeoutSeconds=%d}",
                config.getModelId(), config.getBaseUrl(), config.getRateLimitRpm(), config.getTimeoutSeconds());
    }
}
