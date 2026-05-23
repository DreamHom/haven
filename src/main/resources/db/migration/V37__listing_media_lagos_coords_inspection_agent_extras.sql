-- Listing media beyond virtual tour: optional floor-plan URL + ordered video pointers (URLs only).
ALTER TABLE listings
    ADD COLUMN floor_plan_url VARCHAR(2048);

CREATE TABLE listing_videos (
    id            BIGSERIAL    PRIMARY KEY,
    listing_id    BIGINT       NOT NULL REFERENCES listings(id),
    url           VARCHAR(512) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    caption       VARCHAR(255),
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT listing_videos_url_length_check CHECK (char_length(url) BETWEEN 1 AND 512)
);

CREATE INDEX listing_videos_listing_order_idx
    ON listing_videos (listing_id, display_order, id);

-- Legacy properties with no map pin: default WGS-84 to central Lagos (matches public API examples).
-- Product default for NGN-first catalogue; override coordinates on PATCH when the real pin is known.
UPDATE properties
SET latitude = 6.4541,
    longitude = 3.3947
WHERE latitude IS NULL
  AND longitude IS NULL;

-- Assigned agent notes for the viewing (keys, gate, parking) — visible in inspection API payloads.
ALTER TABLE inspection_requests
    ADD COLUMN agent_extras TEXT;
