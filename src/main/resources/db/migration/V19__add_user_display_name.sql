-- Adds a separate `display_name` to users so the UI can render a short, mobile-
-- friendly handle alongside the full legal name.
--
-- Backfill rule: existing rows get the first whitespace-delimited token of
-- `full_name` (so "Amaka Chinwe Okafor" → "Amaka") which is a sensible default
-- for the Nigerian-name shapes we see most. Users override at registration time
-- (new optional `displayName` field on RegisterRequest) or via a future profile
-- update — defaults stay predictable instead of arbitrary.
--
-- Column is NOT NULL once backfilled so callers can rely on its presence.

ALTER TABLE users
    ADD COLUMN display_name VARCHAR(255);

UPDATE users
   SET display_name = SPLIT_PART(full_name, ' ', 1)
 WHERE display_name IS NULL;

ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL;
