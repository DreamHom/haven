package com.dreamhomes.haven.listing.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI embeddings for Dream AI listing discovery ({@code text-embedding-3-small} → pgvector).
 */
@Data
@ConfigurationProperties(prefix = "haven.dream-ai.embeddings")
public class ListingEmbeddingProperties {

    /**
     * OpenAI API key. When blank, embedding index writes and vector search are disabled
     * (Dream AI still works with first-page catalogue + Claude).
     */
    private String openaiApiKey = "";

    private String model = "text-embedding-3-small";

    private int dimensions = 1536;

    private String openaiBaseUrl = "https://api.openai.com";

    private int connectTimeoutMs = 10_000;

    private int readTimeoutMs = 60_000;

    public boolean active() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }
}
