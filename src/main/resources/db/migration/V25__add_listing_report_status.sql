-- V25: add a moderation lifecycle to listing_reports.
--
-- The user-facing POST /listings/{id}/report endpoint persists a row; the admin
-- queue (GET /admin/listing-reports) needs to mark items as RESOLVED or
-- DISMISSED so the queue actually drains. PENDING is the default — every prior
-- row is back-filled accordingly.
--
-- Persona audit (Dayo): write-only moderation. Reports were going into a hole.

ALTER TABLE listing_reports
    ADD COLUMN status             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN resolution_note    TEXT,
    ADD COLUMN resolved_by_admin_id BIGINT REFERENCES users(id),
    ADD COLUMN resolved_at        TIMESTAMPTZ,
    ADD CONSTRAINT listing_reports_status_check
        CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED'));

CREATE INDEX listing_reports_pending_idx
    ON listing_reports (status, created_at DESC)
    WHERE status = 'PENDING';
