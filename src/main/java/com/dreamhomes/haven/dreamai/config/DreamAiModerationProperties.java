package com.dreamhomes.haven.dreamai.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "haven.dream-ai.moderation")
public class DreamAiModerationProperties {

    /**
     * Case-insensitive substring block list — empty by default (no-op).
     */
    private List<String> bannedSubstrings = new ArrayList<>();
}
