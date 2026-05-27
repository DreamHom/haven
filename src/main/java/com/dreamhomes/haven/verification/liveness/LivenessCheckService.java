package com.dreamhomes.haven.verification.liveness;

import com.dreamhomes.haven.verification.exception.LivenessCheckAlreadyConsumedException;
import com.dreamhomes.haven.verification.exception.LivenessCheckNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Liveness check orchestration (Item 19 in {@code docs/demo-prep/post-session-tasks.md}).
 *
 * <h2>MOCKED v1</h2>
 * {@link #runMockedCheck(Long)} always persists a PASSED row with {@code score = 0.97}
 * and {@code provider_name = "MOCK"}. There is no real biometric provider in v1; the
 * point is that the integration boundary (this service + the {@code liveness_check_results}
 * table + the {@code livenessCheckId} field on submit) is real, so v2 can swap in
 * Smile ID / Dojah / Sourcefin without touching any caller code.
 *
 * <h2>v2 swap path</h2>
 * <ol>
 *   <li>Extract a {@code LivenessProvider} interface (mirror of the
 *       {@code VerificationProvider} pattern from Item 20).</li>
 *   <li>Add a {@code MockLivenessProvider} and a real {@code SmileIdLivenessProvider}
 *       gated by {@code @ConditionalOnProperty(name = "haven.verification.liveness-provider")}.</li>
 *   <li>{@code runMockedCheck} becomes {@code runCheck(userId, providerPayload)} —
 *       dispatch to the active provider, persist its real response in {@code raw_response}.</li>
 * </ol>
 *
 * <p>The {@link #consume(Long, Long)} contract stays unchanged across v1/v2: the
 * verification submit endpoint validates ownership + unconsumed-ness, stamps
 * {@code consumed_at}, and returns the row.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LivenessCheckService {

    private static final String MOCK_PROVIDER_NAME = "MOCK";
    private static final BigDecimal MOCK_SCORE = new BigDecimal("0.97");
    private static final String MOCK_RAW_RESPONSE =
            "{\"provider\":\"MOCK\",\"status\":\"PASSED\",\"score\":0.97,\"note\":\"v1 mock — always passes\"}";

    private final LivenessCheckResultRepository repository;

    /**
     * MOCKED v1 — always returns PASSED with score=0.97. v2 swaps in a real biometric
     * provider (Smile ID / Dojah / Sourcefin); see class-level Javadoc for the swap path.
     *
     * @return the persisted row; caller forwards {@code id} to the verification submit.
     */
    @Transactional
    public LivenessCheckResult runMockedCheck(Long userId) {
        LivenessCheckResult saved = repository.save(LivenessCheckResult.builder()
                .userId(userId)
                .status("PASSED")
                .score(MOCK_SCORE)
                .providerName(MOCK_PROVIDER_NAME)
                .rawResponse(MOCK_RAW_RESPONSE)
                .createdAt(Instant.now())
                .build());
        log.info("Mocked liveness check id={} userId={} status=PASSED score={}",
                saved.getId(), userId, saved.getScore());
        return saved;
    }

    /**
     * Validates the liveness row belongs to {@code callerId} and is unconsumed, then
     * stamps {@code consumed_at} so it cannot be replayed across multiple verification
     * submissions. Mismatched owner collapses to {@link LivenessCheckNotFoundException}
     * (403) to avoid leaking existence; already-consumed rows surface as
     * {@link LivenessCheckAlreadyConsumedException} (409).
     */
    @Transactional
    public LivenessCheckResult consume(Long callerId, Long livenessCheckId) {
        LivenessCheckResult row = repository.findById(livenessCheckId)
                .orElseThrow(() -> new LivenessCheckNotFoundException(livenessCheckId));
        if (!row.getUserId().equals(callerId)) {
            throw new LivenessCheckNotFoundException(livenessCheckId);
        }
        if (row.getConsumedAt() != null) {
            throw new LivenessCheckAlreadyConsumedException(livenessCheckId);
        }
        row.setConsumedAt(Instant.now());
        return repository.save(row);
    }
}
