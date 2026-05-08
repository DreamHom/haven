-- Phase 5: Verification + Admin moderation infrastructure (PRD §4.8, §4.10).
--
-- Two new tables and a handful of badge columns. PRD §6 says document references are
-- metadata only — no raw files in DB. We store JSONB pointers (e.g. {"kind":"NIN","ref":"..."})
-- and let the storage subsystem live somewhere else.
--
-- Listing approval is non-blocking per PRD §4.1: listings go LIVE on creation and
-- admin "approval" stamps approved_at as a verified-listing badge. The PRD also
-- explicitly says listing approvals + verification updates are sync DB notifications,
-- NOT Kafka — so the third sequence diagram (03c) is superseded for capstone scope.

CREATE TABLE verifications (
    id                  BIGSERIAL    PRIMARY KEY,
    type                VARCHAR(32)  NOT NULL,
    submitter_user_id   BIGINT       NOT NULL REFERENCES users(id),
    -- Exactly one of target_user_id / target_property_id is set, gated by the type
    -- check below. Self-verifications point at the submitter; property docs point
    -- at a property the submitter owns.
    target_user_id      BIGINT       REFERENCES users(id),
    target_property_id  BIGINT       REFERENCES properties(id),
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    -- Document metadata: free-shape JSON so adding a new doc kind doesn't need a migration.
    document_refs       JSONB        NOT NULL,
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decided_at          TIMESTAMPTZ,
    decided_by_admin_id BIGINT       REFERENCES users(id),
    decision_reason     TEXT,
    -- Optimistic lock: two admins racing to decide the same row resolves to one winner.
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT verifications_type_check
        CHECK (type IN ('OWNER_IDENTITY', 'PROPERTY_DOCUMENTS',
                        'AGENT_CREDENTIALS', 'APPLICANT_IDENTITY')),
    CONSTRAINT verifications_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- Decision integrity: a non-PENDING row must carry decided_at + decided_by;
    -- a PENDING row must not. Prevents half-written decisions slipping through.
    CONSTRAINT verifications_decision_complete
        CHECK (
            (status = 'PENDING' AND decided_at IS NULL AND decided_by_admin_id IS NULL)
            OR (status IN ('APPROVED', 'REJECTED')
                AND decided_at IS NOT NULL
                AND decided_by_admin_id IS NOT NULL)
        ),
    -- Target consistency: PROPERTY_DOCUMENTS targets a property; the others target a user.
    CONSTRAINT verifications_target_consistent
        CHECK (
            (type = 'PROPERTY_DOCUMENTS'
                AND target_property_id IS NOT NULL
                AND target_user_id IS NULL)
            OR (type IN ('OWNER_IDENTITY', 'AGENT_CREDENTIALS', 'APPLICANT_IDENTITY')
                AND target_user_id IS NOT NULL
                AND target_property_id IS NULL)
        )
);

-- One pending submission per (type, target) — re-submitting after rejection works
-- because the rejected row is no longer PENDING and falls out of the partial index.
-- Two separate partial indexes because target_user_id and target_property_id are
-- mutually exclusive (gated by the consistency check above).
CREATE UNIQUE INDEX verifications_one_pending_per_user
    ON verifications (type, target_user_id)
    WHERE status = 'PENDING' AND target_user_id IS NOT NULL;

CREATE UNIQUE INDEX verifications_one_pending_per_property
    ON verifications (type, target_property_id)
    WHERE status = 'PENDING' AND target_property_id IS NOT NULL;

-- Hot read: the admin queue page filters by type + status, ordered oldest first
-- (FIFO fairness). Composite index covers both the filter and the sort.
CREATE INDEX verifications_queue_idx
    ON verifications (type, status, submitted_at);

-- Admin audit log: every admin write recorded once with target metadata for forensics.
-- target_type lives as a string so adding a new moderation target is code-only.
CREATE TABLE admin_audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    admin_id     BIGINT       NOT NULL REFERENCES users(id),
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(32)  NOT NULL,
    target_id    BIGINT       NOT NULL,
    metadata     JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Two read paths: "what has this admin done lately" and "what's the history on this
-- target". Both ordered newest-first.
CREATE INDEX admin_audit_log_admin_created_idx
    ON admin_audit_log (admin_id, created_at DESC);

CREATE INDEX admin_audit_log_target_idx
    ON admin_audit_log (target_type, target_id, created_at DESC);

-- Verified-badge columns: a non-null timestamp = verified, null = unverified.
-- Storing the moment of decision (not just a boolean) so the audit story is complete
-- without joining admin_audit_log on every profile read.
ALTER TABLE users
    ADD COLUMN identity_verified_at TIMESTAMPTZ,
    ADD COLUMN suspended_at         TIMESTAMPTZ;

ALTER TABLE agent_profiles
    ADD COLUMN credential_verified_at TIMESTAMPTZ;

ALTER TABLE properties
    ADD COLUMN documents_verified_at TIMESTAMPTZ;

ALTER TABLE listings
    ADD COLUMN approved_at TIMESTAMPTZ;
