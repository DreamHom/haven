-- Phase 12: review takedown — soft-delete to keep audit history while letting public
-- reads filter cleanly. Mirrors the V12 comments soft-delete pattern.
ALTER TABLE listing_reviews
    ADD COLUMN deleted_at         TIMESTAMPTZ,
    ADD COLUMN deleted_by_user_id BIGINT REFERENCES users(id),
    ADD COLUMN deletion_reason    TEXT,
    ADD CONSTRAINT listing_reviews_delete_complete_check CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL)
    );

-- Public-read partial index — same shape as comments_active_per_listing_idx. Existing
-- non-partial indexes still cover admin / forensic reads that include deleted rows.
CREATE INDEX listing_reviews_active_listing_idx
    ON listing_reviews (listing_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX listing_reviews_active_reviewee_idx
    ON listing_reviews (reviewee_user_id, created_at DESC)
    WHERE deleted_at IS NULL;
