package com.dreamhomes.haven.dreamai.moderation;

import com.dreamhomes.haven.dreamai.config.DreamAiModerationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * MVP moderation gate — extend with external classifier later.
 */
@Service
@RequiredArgsConstructor
public class DreamAiModerationService {

    private final DreamAiModerationProperties properties;

    public void assertAllowed(String prompt) {
        if (prompt == null) {
            return;
        }
        String lower = prompt.toLowerCase();
        for (String banned : properties.getBannedSubstrings()) {
            if (banned != null && !banned.isBlank() && lower.contains(banned.toLowerCase().trim())) {
                throw new DreamAiModerationBlockedException("That message cannot be processed here.");
            }
        }
    }
}
