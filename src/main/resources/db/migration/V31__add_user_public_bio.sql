-- V31: optional public bio for trust pages (Vista §12 — public owner profile richness).
-- Nullable; surfaced on GET /api/users/{id}/profile and editable via PATCH /api/me.

ALTER TABLE users
    ADD COLUMN public_bio TEXT;
