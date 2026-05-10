package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.verification.VerificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-action counters for admin moderation. Lets ops chart approval / rejection volume
 * by verification type and listing actions over time — useful for spotting abuse waves
 * and admin-coverage gaps.
 *
 * <p>Counters are pre-registered for every (action, type) pair we expect, so the first
 * increment doesn't pay the registration cost on a hot path. Cardinality stays bounded:
 * decision actions × {@link VerificationType} count is fixed at 8.
 */
@Component
@RequiredArgsConstructor
public class AdminMetrics {

    static final String VERIFICATION_DECISIONS = "haven.verification.decisions";
    static final String LISTING_ACTIONS = "haven.listing.admin_actions";
    static final String USER_MODERATIONS = "haven.user.moderations";

    private final MeterRegistry meterRegistry;
    private final Map<VerificationType, Counter> verificationApprovals = new EnumMap<>(VerificationType.class);
    private final Map<VerificationType, Counter> verificationRejections = new EnumMap<>(VerificationType.class);

    public void recordVerificationDecision(VerificationType type, boolean approved) {
        Map<VerificationType, Counter> map = approved ? verificationApprovals : verificationRejections;
        map.computeIfAbsent(type, t -> Counter.builder(VERIFICATION_DECISIONS)
                .tag("type", t.name())
                .tag("decision", approved ? "approved" : "rejected")
                .description("Admin verification decisions, partitioned by verification type")
                .register(meterRegistry)).increment();
    }

    public void recordListingAction(AdminAction action) {
        Counter.builder(LISTING_ACTIONS)
                .tag("action", action.name())
                .description("Admin listing actions (approval, takedown)")
                .register(meterRegistry)
                .increment();
    }

    public void recordUserModeration(AdminAction action) {
        Counter.builder(USER_MODERATIONS)
                .tag("action", action.name())
                .description("Admin user moderation actions (suspend, reactivate)")
                .register(meterRegistry)
                .increment();
    }
}
