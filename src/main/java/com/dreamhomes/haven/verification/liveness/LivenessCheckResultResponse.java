package com.dreamhomes.haven.verification.liveness;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wire shape returned by {@code POST /api/verifications/liveness-check}. {@code mocked}
 * is serialised as {@code _mocked} per the Item 19 spec — the underscore prefix flags
 * the field as a developer-mode hint rather than a domain attribute, so frontend
 * integrators and judges immediately know what they're looking at.
 *
 * <p>v2 will keep the same shape but populate {@code provider} with the real provider's
 * name (smile-id / dojah / sourcefin) and set {@code mocked = false}.
 */
public record LivenessCheckResultResponse(
        @Schema(description = "Id of the persisted liveness check row — pass to verification submit.",
                example = "42")
        Long id,
        @Schema(description = "Outcome of the check. v1 mock always returns PASSED.",
                example = "PASSED")
        String status,
        @Schema(description = "Confidence score in [0,1]. v1 mock always returns 0.97.",
                example = "0.97")
        BigDecimal score,
        @Schema(description = "Provider that produced this check. v1 always \"MOCK\".",
                example = "MOCK")
        String provider,
        @Schema(description = "When the check ran (server time).",
                example = "2026-05-24T08:30:00Z")
        Instant checkedAt,
        @JsonProperty("_mocked")
        @Schema(description = "True when the result came from the mocked provider. v1 always true.",
                example = "true")
        boolean mocked
) {
}
