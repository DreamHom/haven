-- Phase 8 / Item 19 (docs/demo-prep/post-session-tasks.md): mocked liveness check.
--
-- v1 ships a stub `LivenessCheckService` that always returns PASSED with score=0.97.
-- v2 swaps in a real biometric provider (Smile ID / Dojah / Sourcefin) without changing
-- this schema — the row shape is provider-agnostic by design.
--
-- The verification submit endpoint optionally references a liveness row by id. When it
-- does, the service validates the row belongs to the caller AND is unconsumed; on
-- success it stamps `consumed_at` so the same liveness check can't be replayed across
-- multiple submissions (one liveness check = one verification submission).

CREATE TABLE liveness_check_results (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    status          VARCHAR(32)  NOT NULL,
    score           NUMERIC(4,3),
    provider_name   VARCHAR(64)  NOT NULL,
    raw_response    JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    consumed_at     TIMESTAMPTZ,
    CONSTRAINT liveness_check_status_check CHECK (status IN ('PASSED', 'FAILED'))
);

-- The verification submit path looks up "this user's latest unconsumed liveness row"
-- by id; the partial index keeps that lookup tight and accidentally skips already-used
-- rows (replay protection at the index level, not just the service).
CREATE INDEX liveness_check_results_user_unconsumed_idx
    ON liveness_check_results (user_id, created_at DESC) WHERE consumed_at IS NULL;
