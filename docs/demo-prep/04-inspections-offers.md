# Session 4 — Inspections + Offers

## What inspection slots are

A slot is a time window when the owner (or their assigned agent) is available to show the property. The owner publishes slots ahead of time:

```
inspection_slots:
  listing=17, 2026-06-15 10:00–11:00
  listing=17, 2026-06-15 11:00–12:00
  listing=17, 2026-06-15 14:00–15:00
```

Applicants browse the listing's available slots and pick one to **request**. They don't free-form text "can I come Saturday?" — the slot is the unit of booking. Scheduling becomes machine-readable and atomically bookable, no email-tag.

Either the owner or their assigned active agent (e.g. Emeka if Amaka delegated) can publish slots — both go through `POST /api/listings/{listingId}/slots`.

## Problem 1: stop the owner from publishing overlapping slots

If Amaka publishes 10:00–11:00 and accidentally also 10:30–11:30, two applicants could each book one and both show up at 10:45. We need the database to reject Slot B at insert time.

The fix is Postgres' `EXCLUDE USING GIST` constraint:

```sql
ALTER TABLE inspection_slots
    ADD CONSTRAINT inspection_slots_no_overlap
    EXCLUDE USING gist (
        listing_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    );
```

In English: no two rows are allowed where listing_id is the same AND the time ranges overlap.

The `'[)'` is a half-open interval — includes the start, excludes the end. This makes 10:00–11:00 and 11:00–12:00 NOT overlap (the first ends right before the second begins), so back-to-back slots are allowed.

Couldn't be done correctly in application code without race conditions. Postgres serialises the check at the storage layer — there's no window where another thread can sneak in.

## Problem 2: stop two applicants racing to book one slot

Slot #101 is published. At the same instant, Temi and Ngozi both click "Book this slot". Two inserts hit the database simultaneously. Without protection, both succeed and the slot is double-booked.

A naive `UNIQUE(slot_id)` would prevent this but it burns the slot forever — once Ngozi books and gets declined, the old DECLINED row would block anyone else from ever booking.

The fix is a **partial unique index** — uniqueness enforced only on rows matching a condition:

```sql
CREATE UNIQUE INDEX inspection_requests_active_slot_unique
    ON inspection_requests (slot_id)
    WHERE status IN ('PENDING', 'APPROVED');
```

- Active rows (PENDING / APPROVED) → max one per slot
- Inactive rows (DECLINED / CANCELLED) → unlimited, don't count

When Temi and Ngozi both insert: Postgres processes them one at a time at the index layer. One wins, the other gets a duplicate-key error. Our service catches it and returns 409 Conflict to the loser.

## The slot self-heals

When a booking goes inactive (DECLINED or CANCELLED), it drops out of the index → the slot is free again for the next applicant.

```
T+0:   Ngozi books → PENDING (occupies index)
T+1:   Owner declines → DECLINED (drops out of index)
T+2:   Temi can now book the same slot → PENDING ✓
```

The same slot can cycle through many bookings over its lifetime. The partial index just enforces "at any single moment, max one active request".

## The inspection request lifecycle

After an applicant books a slot, the request goes through statuses:

```
PENDING ──► APPROVED  (owner / agent confirms)
       └──► DECLINED  (owner / agent rejects)
       └──► CANCELLED (applicant withdraws — only from PENDING)
```

A fresh booking starts as `PENDING`. The owner sees it on their dashboard and decides — approve or decline. The applicant can cancel while still PENDING. After APPROVED, post-inspection statuses exist (`COMPLETED`, `NO_SHOW`) but no cancel path.

## What we found during the audit

Three honest gaps that the code intends to handle but doesn't:

- **Booking doesn't notify the assigned agent**, only the owner. `InspectionRequestedListener` reads only `event.ownerId()` and writes a single notification row. Comments mention "fanout to owner + agent" — implementation is missing the agent half.
- **Approve/decline don't notify the applicant.** `transitionFromPending()` flips the status and saves; no outbox event, no notification. The applicant has to refresh to find out.
- **No cancel path after APPROVED.** Once approved, both parties are locked in. Applicant emergency = no-show on record. Owner emergency = ghost. Persona-audit Temi flagged the original lock-in but the fix was scoped to PENDING only.

All three are tracked in `post-session-tasks.md` (item 7).

## Counter-offers form a chain

When you counter an offer, the system doesn't modify the original — it creates a new row pointing back to the original via `parent_offer_id`.

```
Temi offers ₦7m         → Offer #42 (status=PENDING)
Amaka counters ₦8m      → Offer #43 (parent=42, status=PENDING)
                          Offer #42 flips to COUNTERED (terminal)
Temi counters ₦7.5m     → Offer #44 (parent=43, status=PENDING)
                          Offer #43 flips to COUNTERED
Amaka accepts           → Offer #44 status=ACCEPTED ✓
```

Each row stays in the DB. The chain reads like a negotiation transcript — you can walk from any offer back to its parent to see the full history.

## Turn-taking — you can't act on your own offer

Each offer row records `proposedByUserId`. The rule is brutally simple:

> *"You can't accept, decline, or counter an offer that YOU made."*

That single check produces alternating turns automatically — whoever just spoke can't respond to themselves; the other party has to act. No state machine for "whose turn is it" needed.

If Temi tries to accept her own offer, the server throws `CannotActOnOwnOfferException` → 403 Forbidden.

## Accepting an offer auto-closes the listing

Three things happen in one transaction when an owner accepts:

1. The accepted offer flips to ACCEPTED
2. Every other PENDING offer on that listing auto-declines, with notification to each loser ("ANOTHER_OFFER_ACCEPTED")
3. The listing itself auto-closes (CLOSED status, no further offers, no edits)

The owner doesn't have to remember to close the listing — auto-close was a persona-audit catch from Biodun. Single transaction means no half-state.

## When an agent is in the mix

Amaka can assign Emeka as her agent. Once Emeka accepts the assignment, he can act on offers as if he were Amaka — accept, decline, counter, see all offers.

The check (`canNegotiateOffer()`) returns true for: owner, applicant, OR agent with `status=ACCEPTED` on the listing.

If Amaka revokes Emeka's assignment, his ability to act on her offers disappears the moment the revoke commits. No grandfathered access.
