package com.dreamhomes.haven.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Singleton platform configuration (id always 1).")
public record PlatformSettingsResponse(
        @Schema(description = "JSON object of platform-wide toggles and numbers.")
        Map<String, Object> settings,
        @Schema(description = "Last update time.")
        Instant updatedAt
) {
}
