package com.dreamhomes.haven.dreamai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "haven.dream-ai.rate-limit")
public class DreamAiRateLimitProperties {

    private boolean enabled = true;

    /** Max Dream AI turn POSTs per user per window. */
    private int capacity = 30;

    private long windowSeconds = 60;
}
