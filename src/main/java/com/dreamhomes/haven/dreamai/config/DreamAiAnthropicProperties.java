package com.dreamhomes.haven.dreamai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic Messages API settings for natural-language listing discovery.
 * When {@link #getApiKey()} is blank, {@link com.dreamhomes.haven.dreamai.DreamAiService}
 * falls back to the legacy location-substring stub.
 */
@Data
@ConfigurationProperties(prefix = "haven.dream-ai.anthropic")
public class DreamAiAnthropicProperties {

    /**
     * Secret key from the Anthropic console. Bind via {@code HAVEN_ANTHROPIC_API_KEY}.
     */
    private String apiKey = "";

    /**
     * Claude model id (default: Claude 3.5 Haiku).
     */
    private String model = "claude-3-5-haiku-20241022";

    private String baseUrl = "https://api.anthropic.com";

    /**
     * How many LIVE listings to load from the catalogue and show the model (first page, default sort).
     */
    private int maxCandidates = 80;

    private int maxOutputTokens = 512;

    private int connectTimeoutMs = 10_000;

    private int readTimeoutMs = 60_000;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
