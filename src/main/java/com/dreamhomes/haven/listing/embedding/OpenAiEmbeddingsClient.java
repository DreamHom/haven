package com.dreamhomes.haven.listing.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal OpenAI {@code /v1/embeddings} client (single input string per call).
 */
@Component
@Slf4j
public class OpenAiEmbeddingsClient {

    private final ListingEmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiEmbeddingsClient(ListingEmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        rf.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getOpenaiBaseUrl())
                .requestFactory(rf)
                .defaultHeader("Authorization", "Bearer " + (properties.getOpenaiApiKey() != null
                        ? properties.getOpenaiApiKey() : ""))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public float[] embed(String input) {
        if (!properties.active()) {
            throw new IllegalStateException("OpenAI embeddings are not configured");
        }
        String trimmed = input.length() > 12000 ? input.substring(0, 12000) : input;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("input", trimmed);
        body.put("dimensions", properties.getDimensions());

        String raw;
        try {
            raw = restClient.post()
                    .uri("/v1/embeddings")
                    .body(body.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("OpenAI embeddings HTTP " + res.getStatusCode().value());
                    })
                    .body(String.class);
        } catch (RestClientException ex) {
            log.warn("OpenAI embeddings request failed: {}", ex.toString());
            throw new IllegalStateException("OpenAI embeddings unavailable", ex);
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("OpenAI embeddings response missing data[]");
            }
            JsonNode emb = data.get(0).path("embedding");
            if (!emb.isArray()) {
                throw new IllegalStateException("OpenAI embeddings response missing embedding array");
            }
            List<Float> floats = new ArrayList<>(emb.size());
            for (JsonNode n : emb) {
                floats.add((float) n.asDouble());
            }
            float[] out = new float[floats.size()];
            for (int i = 0; i < floats.size(); i++) {
                out[i] = floats.get(i);
            }
            if (out.length != properties.getDimensions()) {
                log.warn("Embedding length {} != configured dimensions {}", out.length, properties.getDimensions());
            }
            return out;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Failed to parse OpenAI embeddings JSON", ex);
            throw new IllegalStateException("OpenAI embeddings response unreadable", ex);
        }
    }
}
