-- Vista gap batch: password reset, user prefs + soft-delete, inspection lifecycle,
-- listing pets/utilities, comment moderation flags, platform settings, minimal ads.

-- 1) Password reset tokens (hashed at rest; raw token only returned once from API flow).
CREATE TABLE password_reset_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    token_hash   VARCHAR(64)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens(user_id);
CREATE INDEX password_reset_tokens_active_hash_idx ON password_reset_tokens(token_hash)
    WHERE used_at IS NULL;

-- 2) User notification prefs (JSON text), soft-delete timestamp, public profile image URL.
ALTER TABLE users
    ADD COLUMN notification_preferences TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN account_deleted_at TIMESTAMPTZ,
    ADD COLUMN profile_image_url VARCHAR(2048);

-- 3) Inspection request statuses: owner no-show + completed (agent closed loop).
ALTER TABLE inspection_requests DROP CONSTRAINT inspection_requests_status_check;
ALTER TABLE inspection_requests ADD CONSTRAINT inspection_requests_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'CANCELLED', 'NO_SHOW', 'COMPLETED'));

-- 4) Listing discovery fields (Vista map/compare copy).
ALTER TABLE listings
    ADD COLUMN pets_allowed VARCHAR(128),
    ADD COLUMN utilities_note TEXT;

-- 5) Public comment moderation queue.
CREATE TABLE comment_flags (
    id                 BIGSERIAL PRIMARY KEY,
    comment_id         BIGINT       NOT NULL REFERENCES comments(id),
    reporter_user_id   BIGINT       NOT NULL REFERENCES users(id),
    reason             VARCHAR(512),
    status             VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT comment_flags_status_check CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);
CREATE INDEX comment_flags_open_listing_idx ON comment_flags (status, created_at DESC);
CREATE UNIQUE INDEX comment_flags_one_open_per_comment_reporter
    ON comment_flags (comment_id, reporter_user_id)
    WHERE status = 'OPEN';

-- 6) Singleton platform configuration (commissions, SLAs, toggles — JSON blob).
CREATE TABLE platform_settings (
    id         SMALLINT PRIMARY KEY,
    settings   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
INSERT INTO platform_settings(id, settings) VALUES (1, '{}'::jsonb)
    ON CONFLICT (id) DO NOTHING;

-- 7) Minimal ads vertical (no payment processor — lifecycle + budget for Vista wiring).
CREATE TABLE ad_campaigns (
    id             BIGSERIAL PRIMARY KEY,
    sponsor_user_id BIGINT    NOT NULL REFERENCES users(id),
    title          VARCHAR(255) NOT NULL,
    body           TEXT,
    status         VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    budget_cents   BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ad_campaigns_status_check CHECK (status IN (
        'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'ACTIVE', 'PAUSED', 'ENDED'))
);
CREATE INDEX ad_campaigns_sponsor_idx ON ad_campaigns(sponsor_user_id, created_at DESC);
