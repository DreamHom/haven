-- V21: extend the property type enum to cover Lagos starter-unit vocabulary.
--
-- Persona audit (Temi) flagged that the existing four values (APARTMENT, HOUSE,
-- LAND, COMMERCIAL) miss the actual vocabulary first-time renters use:
-- self-cons, mini-flats, studios, and room-and-parlours all currently get
-- filed under APARTMENT, making the catalogue impossible to filter.
--
-- The CHECK constraint is replaced atomically; the Java enum and the
-- application-side `requiresRoomCounts()` are updated in lockstep.

ALTER TABLE properties DROP CONSTRAINT properties_type_check;
ALTER TABLE properties ADD CONSTRAINT properties_type_check
    CHECK (type IN ('APARTMENT', 'HOUSE', 'LAND', 'COMMERCIAL',
                    'SELF_CONTAIN', 'MINI_FLAT', 'STUDIO', 'ROOM_AND_PARLOUR'));
