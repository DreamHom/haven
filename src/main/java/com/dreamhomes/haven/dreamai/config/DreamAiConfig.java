package com.dreamhomes.haven.dreamai.config;

import com.dreamhomes.haven.dreamai.client.AnthropicListingCompareClient;
import com.dreamhomes.haven.dreamai.client.AnthropicListingSearchClient;
import com.dreamhomes.haven.listing.embedding.ListingEmbeddingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({
    DreamAiAnthropicProperties.class,
    ListingEmbeddingProperties.class,
    DreamAiModerationProperties.class,
    DreamAiRateLimitProperties.class
})
public class DreamAiConfig {

    @Bean
    AnthropicListingSearchClient anthropicListingSearchClient(
            DreamAiAnthropicProperties properties,
            ObjectMapper objectMapper) {
        return new AnthropicListingSearchClient(
                buildAnthropicRestClient(properties), properties, objectMapper);
    }

    /**
     * Sister bean to {@link AnthropicListingSearchClient} — reuses the same Anthropic
     * baseUrl + auth headers + timeouts via {@link #buildAnthropicRestClient(DreamAiAnthropicProperties)}.
     * Backs {@link com.dreamhomes.haven.dreamai.DreamAiService#compareListings(String, java.util.List)}.
     */
    @Bean
    AnthropicListingCompareClient anthropicListingCompareClient(
            DreamAiAnthropicProperties properties,
            ObjectMapper objectMapper) {
        return new AnthropicListingCompareClient(
                buildAnthropicRestClient(properties), properties, objectMapper);
    }

    private static RestClient buildAnthropicRestClient(DreamAiAnthropicProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
