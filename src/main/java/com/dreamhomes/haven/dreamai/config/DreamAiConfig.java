package com.dreamhomes.haven.dreamai.config;

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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
        return new AnthropicListingSearchClient(restClient, properties, objectMapper);
    }
}
