-- Phase 9B: engagement signals — saves + view_count.
--
-- Saves model: a user pins a listing for later. One row per (user, listing); composite
-- PK gives us idempotent UPSERT-style semantics — re-saving the same listing is a no-op.
CREATE TABLE listing_saves (
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    listing_id BIGINT      NOT NULL REFERENCES listings(id),
    saved_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, listing_id)
);

-- Hot read: "all listings I've saved" — most-recent first. user_id is the leading PK
-- column already, but a covering index on saved_at lets the order-by be index-only.
CREATE INDEX listing_saves_user_saved_at_idx
    ON listing_saves (user_id, saved_at DESC);

-- Hot read: "how many people saved this listing" — backs an analytics card on the
-- admin dashboard + a "popular" ranking when relevant.
CREATE INDEX listing_saves_listing_idx
    ON listing_saves (listing_id);

-- View counter: atomic increment on every public listing-detail GET. We deliberately
-- DON'T track per-user views (no FK to users) to keep the increment lock-free and avoid
-- a row-per-anonymous-visitor explosion. Counts are aggregate-only.
ALTER TABLE listings
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
