-- Phase 11: per-listing photo metadata. PRD §6 forbids raw file storage in DB — the
-- url column is a pointer to external object storage. Object-storage layer itself is
-- out of capstone scope (PRD §9); for the demo, vista posts an externally-hosted URL.
CREATE TABLE listing_photos (
    id            BIGSERIAL    PRIMARY KEY,
    listing_id    BIGINT       NOT NULL REFERENCES listings(id),
    url           VARCHAR(512) NOT NULL,  -- pointer; CDN / object storage URL
    display_order INT          NOT NULL DEFAULT 0,
    caption       VARCHAR(255),
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Body length sanity at the data layer.
    CONSTRAINT listing_photos_url_length_check CHECK (char_length(url) BETWEEN 1 AND 512)
);

-- Hot read: "all photos for this listing, in display order." Composite index gets us
-- the order-by for free. Ties on display_order resolve by id (insertion order).
CREATE INDEX listing_photos_listing_order_idx
    ON listing_photos (listing_id, display_order, id);
