-- Item 2 — pre-signed R2 upload URLs. Each call to POST /api/listings/{id}/photos/upload-url
-- mints a single-use intent row carrying:
--   * the listing it belongs to
--   * the caller who's allowed to confirm it
--   * the R2 object key the pre-signed URL targets
--   * the content type + size bounds we issued for
--   * the URL's expiry (10 min from issue today)
--   * confirmed_at — null until the matching /confirm call succeeds
-- HEAD on R2 + this row are the two checks /confirm runs before writing a listings_photos
-- row. After ~24h a scheduled cleanup purges expired/orphan rows.

CREATE TABLE photo_upload_intent (
    id                  BIGSERIAL    PRIMARY KEY,
    listing_id          BIGINT       NOT NULL REFERENCES listings(id),
    requested_by        BIGINT       NOT NULL REFERENCES users(id),
    file_key            VARCHAR(512) NOT NULL,
    content_type        VARCHAR(64)  NOT NULL,
    max_size_bytes      BIGINT       NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    confirmed_at        TIMESTAMPTZ,
    confirmed_photo_id  BIGINT       REFERENCES listing_photos(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT photo_upload_intent_file_key_unique UNIQUE (file_key)
);

-- Lookup paths used by ListingPhotoUploadIntentService.confirm():
--   * by file_key (the caller supplies it)
CREATE INDEX photo_upload_intent_listing_idx ON photo_upload_intent (listing_id);

-- Cleanup scan: find expired/old rows older than 24h.
CREATE INDEX photo_upload_intent_expires_at_idx ON photo_upload_intent (expires_at);
