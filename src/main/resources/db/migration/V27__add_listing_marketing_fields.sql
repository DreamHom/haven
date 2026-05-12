-- V27: add marketing-copy fields to listings.
--
-- Persona audit (Biodun, Amaka): "no `title` or `description` on the listing
-- itself. Only on the property. So I can't write a marketing headline like
-- 'Move-in ready! Just-painted!' — there's nowhere to put it." Biodun also
-- needs `handoverDate` for off-plan launches.
--
-- All optional — historic listings work without them.

ALTER TABLE listings
    ADD COLUMN title         VARCHAR(255),
    ADD COLUMN description   TEXT,
    ADD COLUMN headline      VARCHAR(255),
    ADD COLUMN handover_date DATE;
