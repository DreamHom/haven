-- Phase 8 / Item 20 (docs/demo-prep/post-session-tasks.md): automated verification
-- provider results.
--
-- v1 ships a `MockVerificationProvider` that always returns PASSED 0.95 with plausible
-- extracted fields. v2 swaps to Smile ID / Dojah / Sourcefin behind the same
-- `VerificationProvider` interface — no schema change.
--
-- Multiple checks per verification (e.g. an owner submission runs OWNER_IDENTITY +
-- LIVENESS = 2 rows). Admins see these rows alongside the documents in the queue UI
-- so they can spot-check "Mock provider says PASSED 0.95 with extracted NIN — does it
-- match the document the user uploaded?".

CREATE TABLE verification_automation_results (
    id                   BIGSERIAL    PRIMARY KEY,
    verification_id      BIGINT       NOT NULL REFERENCES verifications(id) ON DELETE CASCADE,
    check_type           VARCHAR(64)  NOT NULL,
    -- check_type values: OWNER_IDENTITY, AGENT_CREDENTIALS, APPLICANT_IDENTITY,
    -- PROPERTY_DOCUMENTS. LIVENESS lives in its own table (V44) for now — we may
    -- collapse them in v2 once the real providers settle on a shape.
    provider_name        VARCHAR(64)  NOT NULL,   -- MOCK, SMILE_ID, DOJAH, ...
    status               VARCHAR(32)  NOT NULL,   -- PASSED, FAILED, NEEDS_HUMAN_REVIEW
    score                NUMERIC(4,3),            -- 0.000 - 1.000
    extracted_fields     JSONB,                   -- {nin: "...", nameMatch: 0.98}
    provider_reference   VARCHAR(255),            -- provider's own correlation id
    raw_response         JSONB,                   -- provider's full response (forensics)
    run_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT verification_automation_status_check
        CHECK (status IN ('PASSED', 'FAILED', 'NEEDS_HUMAN_REVIEW'))
);

CREATE INDEX verification_automation_results_verification_idx
    ON verification_automation_results (verification_id);
