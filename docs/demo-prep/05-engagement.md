# Session 5 — Engagement (Saves, Comments, Reviews)

## The three surfaces

Anyone browsing a listing can interact with it in three ways:

- **Saves** — bookmark a listing for later. Private (only you see your own saves).
- **Comments** — public Q&A on the listing. "Is it pet-friendly?" / "Yes, small dogs welcome."
- **Reviews** — post-deal feedback (1–5 stars + body). Only people who actually transacted can write one.

Each one has a different design wrinkle: saves → idempotency, comments → soft delete, reviews → eligibility gate.

## Saves are idempotent

Hitting "save this listing" multiple times = same outcome as hitting it once. Hitting "unsave" when you never saved = silent success. No errors either way.

The service checks first:

```java
if (listingSaveRepository.existsByUserIdAndListingId(userId, listingId)) {
    return; // already saved → no-op
}
```

Same for unsave — if not saved, just return without complaining.

Three reasons this matters:

- Frontend doesn't need to track prior state — just call `POST /save` when the heart is tapped
- Network retries don't fail (flaky connection → tap → timeout → retry → still succeeds)
- Double-click protection — second tap is a no-op, not an error

The principle: state-setting operations should describe the desired state ("save this"), not the delta ("add one to save count"). The first is idempotent by design.

## Comments are flat and soft-deleted

Comments today have **no threading** — every comment is a top-level reply to the listing. There's no `parent_comment_id` column on the `comments` table. (Threading is on the post-session task list — Vista wants it but the backend hasn't built it yet.)

When a comment is deleted (by the author, the listing owner, or an admin), the row stays in the DB. The service sets three columns together:

```java
deleted_at
deleted_by_user_id
deletion_reason
```

A `comments_delete_complete` CHECK constraint enforces all three or none — you can't have a half-deleted row.

The public read query filters `WHERE deleted_at IS NULL`, so deleted comments disappear from the UI. The row stays for forensic + appeal purposes — if the author thinks the takedown was unfair, an admin can see exactly what was said and who removed it.

Note: comment **flagging** (users reporting abusive comments) already exists in the backend — `CommentFlagService` + `CommentFlagRepository`. Vista just hasn't wired up the UI for it yet.

## Reviews: the eligibility gate

The interesting design here is who's allowed to review whom on a CLOSED listing.

`ReviewService.post()` enforces strict bidirectional eligibility:

- The **listing owner** can review the **buyer/renter** (whoever had the accepted offer)
- The **buyer/renter** can review the **listing owner**

That's it. The check uses `offerService.hadAcceptedOffer(listingId, userId)` to verify the deal actually happened, and `revieweeId == listing.ownerId()` to enforce direction.

Other hard rules in code:

- Listing must be in CLOSED status (409 `ListingNotClosedException` otherwise)
- Can't review yourself (`InvalidRevieweeException`)
- Rating must be 1–5
- Body can't be blank
- One review per (listing, reviewer, reviewee) — DB unique constraint

## The agent review gap

Emeka the agent does all the work on Amaka's listing — showings, negotiation, key handover. Then the deal closes and… he can't be reviewed.

The eligibility check requires `revieweeId == listing.ownerId()`. So the applicant can only review the owner; the agent is invisible to the review system.

This is a real backend gap — flagged in the post-session task list. Would need extending the eligibility logic to accept "agent with ACCEPTED `agent_listings` row" as a valid reviewee.

## Soft delete + the star aggregate

Reviews use the same soft-delete pattern as comments — `deletedAt` + `deletedByUserId` + `deletionReason` set together.

The aggregate query that computes a user's average rating filters on `deletedAt IS NULL`:

```sql
SELECT AVG(rating), COUNT(*)
FROM listing_reviews
WHERE reviewee_user_id = ? AND deleted_at IS NULL
```

So when a review is soft-deleted, the next call to `aggregateForUser()` naturally excludes it. The star rating drops on the next page load. No background recompute job, no cached "stale rating" to invalidate.

That's the soft-delete payoff: auditable removal without recomputation overhead.

## Who can delete a review

Two paths in `ReviewService.delete()`:

- The **author** can delete their own review (no reason required)
- An **admin** can delete any review (reason required for the audit log)

Anyone else → 403 `NotAuthorisedToDeleteReviewException`. Admin deletes also write an `admin_audit_log` row (action = `REVIEW_TAKEDOWN`).
