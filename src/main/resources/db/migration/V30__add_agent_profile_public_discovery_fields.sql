-- V30: Surface public discovery fields on the agent profile so owners shopping for an
-- agent can filter / read the trust signals PRD §4.2 promises ("fees, ratings, deals
-- closed, specializations, locations covered, response rate"). The frontend's agent-
-- profile page previously had nothing to render in this section and rendered a
-- placeholder ("this profile intentionally focuses on verified trust signals and live
-- listings") — these columns let it carry real data.
--
-- Shape choices:
--  * Three flat lists (service_areas, languages, specialization_tags) → TEXT[].
--    Postgres array types are the natural fit; no need for join tables this early —
--    no query reads them by value yet, just renders them as a whole. Default '{}'
--    keeps existing rows valid without a backfill and avoids NULL-handling in the DTO.
--  * fee_schedule → TEXT, nullable. Agents express fees in free-form ("5% on sale,
--    1 month rent commission" / "fixed ₦200k consultation"), so structured JSONB
--    would be premature. If structured filters become useful, migrate to JSONB
--    later; the migration is forward-compatible (TEXT casts to JSONB cleanly).
ALTER TABLE agent_profiles
    ADD COLUMN service_areas        TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN languages            TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN specialization_tags  TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN fee_schedule         TEXT;
