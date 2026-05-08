-- Phase 6B: public Q&A on listings (PRD §4.9 transparency, lightweight on-platform).
--
-- Soft-delete via deleted_at: when an admin or the listing owner takes a comment down,
-- the row stays for forensic / appeal / undo, but partial indexes hide it from the
-- public list query. Hard-delete would lose the audit trail.
--
-- No Kafka involvement: a comment-posted event fires a sync notification to the listing
-- owner per PRD §7 (only INSPECTION_REQUESTED + OFFER_SUBMITTED ride Kafka).
CREATE TABLE comments (
    id                  BIGSERIAL    PRIMARY KEY,
    listing_id          BIGINT       NOT NULL REFERENCES listings(id),
    author_user_id      BIGINT       NOT NULL REFERENCES users(id),
    body                TEXT         NOT NULL,
    deleted_at          TIMESTAMPTZ,
    deleted_by_user_id  BIGINT       REFERENCES users(id),
    deletion_reason     TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Body length cap at the data layer to keep Postgres rows reasonable; the request
    -- DTO @Size cap matches at the validation layer.
    CONSTRAINT comments_body_length_check CHECK (char_length(body) BETWEEN 1 AND 4000),
    -- Either both delete columns are set or both are null — no half-deletes.
    CONSTRAINT comments_delete_complete CHECK (
        (deleted_at IS NULL AND deleted_by_user_id IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL)
    )
);

-- Hot read path: "comments on this listing, oldest first" with deleted ones filtered.
-- Partial index keeps it lean: deleted rows fall out of the index entirely.
CREATE INDEX comments_active_per_listing_idx
    ON comments (listing_id, created_at)
    WHERE deleted_at IS NULL;

-- Author-side index for "my comments" / moderation lookups by author. Doesn't filter
-- on deleted_at because an author may want to see what got taken down.
CREATE INDEX comments_author_created_idx
    ON comments (author_user_id, created_at DESC);
