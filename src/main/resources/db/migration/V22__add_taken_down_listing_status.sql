-- V22: add TAKEN_DOWN as a distinct listing status.
--
-- Dayo's audit (persona Story 6) flagged that takedown currently collapses to
-- CLOSED in the DB, which loses the moderation distinction "admin took this
-- down" vs "owner closed the deal normally". Forensic queries can't separate
-- the two. Adding TAKEN_DOWN restores that distinction.
--
-- Re-publishing a TAKEN_DOWN listing transitions it back to LIVE.

ALTER TABLE listings DROP CONSTRAINT listings_status_check;
ALTER TABLE listings ADD CONSTRAINT listings_status_check
    CHECK (status IN ('LIVE', 'PAUSED', 'CLOSED', 'TAKEN_DOWN'));
