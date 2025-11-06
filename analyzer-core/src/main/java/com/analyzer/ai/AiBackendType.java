package com.analyzer.ai;

/**
 * Enumeration of supported AI backend providers.
 */
public enum AiBackendType {
    /**
     * Amazon Q CLI backend.
     * Uses the 'q chat' command.
     */
    AMAZON_Q("amazonq", "q"),

    /**
     * Google Gemini CLI backend.
     * Uses the 'gemini' command.
     */
    GEMINI("gemini", "gemini");

    private final String name;
    private final String cliCommand;

    AiBackendType(String name, String cliCommand) {
        this.name = name;
        this.cliCommand = cliCommand;
    }

    public String getName() {
        return name;
    }

    public String getCliCommand() {
        return cliCommand;
    }

    /**
     * Parse a string to an AiBackendType.
     * 
     * @param provider Provider name (case-insensitive)
     * @return Corresponding AiBackendType
     * @throws IllegalArgumentException if provider is unknown
     */
    public static AiBackendType fromString(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("AI provider cannot be null or empty");
        }

        String normalized = provider.toLowerCase().trim();

        return switch (normalized) {
            case "amazonq", "amazon-q", "q", "amazon_q" -> AMAZON_Q;
            case "gemini", "google" -> GEMINI;
            default -> throw new IllegalArgumentException(
                    "Unknown AI provider: " + provider + ". Supported providers: amazonq, gemini");
        };
    }
}
