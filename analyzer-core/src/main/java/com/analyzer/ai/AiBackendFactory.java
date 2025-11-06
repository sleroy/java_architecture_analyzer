package com.analyzer.ai;

/**
 * Factory for creating AI backend instances.
 */
public class AiBackendFactory {

    /**
     * Create an AI backend instance for the specified type.
     *
     * @param type The backend type to create
     * @return An instance of the requested backend
     * @throws IllegalArgumentException if the type is null
     */
    public static AiBackend create(AiBackendType type) {
        if (type == null) {
            throw new IllegalArgumentException("Backend type cannot be null");
        }

        return switch (type) {
            case AMAZON_Q -> new AmazonQBackend();
            case GEMINI -> new GeminiBackend();
        };
    }

    /**
     * Create an AI backend from a provider string.
     *
     * @param provider Provider name (e.g., "amazonq", "gemini")
     * @return An instance of the requested backend
     * @throws IllegalArgumentException if the provider is unknown
     */
    public static AiBackend createFromString(String provider) {
        AiBackendType type = AiBackendType.fromString(provider);
        return create(type);
    }

    /**
     * Get the default AI backend (Amazon Q for backward compatibility).
     *
     * @return Default backend instance
     */
    public static AiBackend createDefault() {
        return create(AiBackendType.AMAZON_Q);
    }
}
