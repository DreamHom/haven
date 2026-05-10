-- PRD §6 promise: "Inspection conflict prevention must be handled at the data layer —
-- no race conditions." The partial unique index on inspection_requests already prevents
-- two ACTIVE requests on the same slot. This constraint goes further and prevents
-- the OWNER from publishing two slots that overlap in time on the same listing —
-- which would let two applicants book the same physical viewing window through
-- different slot ids.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE inspection_slots
    ADD CONSTRAINT inspection_slots_no_overlap
    EXCLUDE USING gist (
        listing_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    );
