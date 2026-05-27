-- Item 12 — enforce "at most one LIVE listing per (property, listing_type)" at the
-- database tier as the race-safety net behind ListingService's pre-check. A partial
-- unique index excludes non-LIVE rows (PAUSED / CLOSED / TAKEN_DOWN) so the same
-- property can re-list through its lifetime — only concurrent LIVE rows of the same
-- listing_type are refused.
--
-- The design intent (V3) is "simultaneous rent + sale" is fine; what's not fine is two
-- LIVE RENT (or two LIVE SALE) on the same property. Without this, a duplicate-publish
-- bug or a malicious double-submit could lead to two applicants winning offers on
-- different listing ids for the same physical home, with only one move-in slot.
--
-- ListingService.create()/update() pre-check this with a friendly 409. The DB index
-- exists for the race window where two concurrent transactions both miss the
-- pre-check; the loser surfaces as DataIntegrityViolationException, which the service
-- translates to the same friendly 409 so callers see one story.

CREATE UNIQUE INDEX IF NOT EXISTS listings_one_open_per_type_per_property
    ON listings (property_id, listing_type)
    WHERE status = 'LIVE';
