-- Phase 13: counter-offers. The original offer plus every counter forms a chain via
-- {@code parent_offer_id} (self-reference). When one party counters, the parent goes
-- COUNTERED (terminal but tracked) and a new child Offer is inserted with the new
-- amount + alternating proposed_by_user_id.
--
-- Why a chain not in-place edits: history matters for trust + dispute resolution.
-- "Owner counter-offered $5M, applicant counter-offered $4.8M, owner accepted" reads
-- as four immutable rows in the chain — the truth is unambiguous.
ALTER TABLE offers
    ADD COLUMN parent_offer_id    BIGINT REFERENCES offers(id),
    -- Who proposed THIS offer? Originals: applicant. Counter-offers alternate.
    -- The "other party" is the one allowed to act on the row.
    ADD COLUMN proposed_by_user_id BIGINT REFERENCES users(id);

-- Backfill: existing rows are originals, proposed by the applicant.
UPDATE offers SET proposed_by_user_id = applicant_id WHERE proposed_by_user_id IS NULL;

ALTER TABLE offers
    ALTER COLUMN proposed_by_user_id SET NOT NULL;

-- Drop the old status check (PENDING/ACCEPTED/DECLINED only) and re-add with COUNTERED.
ALTER TABLE offers
    DROP CONSTRAINT IF EXISTS offers_status_check;

ALTER TABLE offers
    ADD CONSTRAINT offers_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'COUNTERED'));

-- Tree integrity: a child offer must point to a parent on the same listing.
-- (Not enforced as a multi-column FK because parent_offer_id alone references offers.id;
-- this is the kind of invariant the service maintains and a future trigger could
-- backstop if needed.)

-- Hot read: "the chain rooted at this listing" / "what's the latest in this thread".
CREATE INDEX offers_listing_parent_idx
    ON offers (listing_id, parent_offer_id);
