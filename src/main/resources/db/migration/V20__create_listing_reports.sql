-- Listing reports — any authenticated user can flag a listing they believe violates
-- the rules (scam, off-platform fee solicitation, stale, inappropriate). Admins read
-- the queue from /api/admin/listing-reports (separate ticket); this migration only
-- creates the storage + the single uniqueness rule we want enforced at the DB.
--
-- Why a unique (listing_id, reporter_user_id): one user → one report per listing.
-- Without this, a single user could spam the queue and drown out signal. The 409 the
-- service raises on duplicate is enforced by the database, not by application logic.

CREATE TABLE listing_reports (
    id                  BIGSERIAL PRIMARY KEY,
    listing_id          BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    reporter_user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason              VARCHAR(32) NOT NULL,
    details             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT listing_reports_one_per_user_per_listing
        UNIQUE (listing_id, reporter_user_id)
);

-- Admin queue queries by listing_id (group reports per listing) and by created_at
-- (recent first). Cover both with a single composite index.
CREATE INDEX listing_reports_listing_recent_idx
    ON listing_reports (listing_id, created_at DESC);
