# Session 3 — Property + Listing Lifecycle

## Property vs Listing

These two sound interchangeable but mean different things in Haven.

A **Property** is the physical asset — the house at 12 Admiralty Way, Lekki, with 4 bedrooms. It exists for decades.

A **Listing** is one offer/availability of that property — "for rent, ₦4m/year". It usually lasts weeks to months until rented or sold.

One property typically has **many listings over time**: rented in 2024, re-listed in 2025, sold in 2026. Each is a separate row pointing back to the same property.

The split lets us preserve history: the property's verification badges, photos, and rental record live forever; each listing represents one transaction in that property's lifetime.

Rule of thumb: if it's still true after the tenant moves out, it belongs on Property. If it only describes this particular offer, it belongs on Listing.

## The listing state machine

A listing is in one of four states at any moment:

- **DRAFT** — owner is editing, not yet public
- **OPEN** — live and accepting offers + inspections
- **CLOSED** — deal closed (rented or sold)
- **TAKEN_DOWN** — admin removed it (fraud, complaint)

Only certain transitions are legal:

- DRAFT → OPEN (owner publishes)
- OPEN → CLOSED (offer accepted, auto-flip)
- OPEN → TAKEN_DOWN (admin moderation)
- TAKEN_DOWN → OPEN (admin reverses decision)

What's not allowed: CLOSED → OPEN (can't un-rent), DRAFT → CLOSED (can't close what was never published). Illegal transitions return 409 Conflict.

Other parts of the system rely on these states — only OPEN listings appear in browse, offers can only be accepted on OPEN listings, reviews only on CLOSED ones. Centralising the rules keeps the whole system honest.

## Optimistic locking with @Version

If Amaka opens her listing for editing at 9:00 and her agent Emeka opens the same listing at 9:01, then they both save — without protection, whoever saves last silently overwrites the other's edit.

We add a `version` column to the listings table. Hibernate increments it on every UPDATE and includes it in the WHERE clause: `WHERE id = ? AND version = ?`.

When Amaka saves first, her version-3 update succeeds and the DB row is now version 4. When Emeka tries to save, his version-3 update matches zero rows. Hibernate throws an exception; we return 409 Conflict with "this was modified by someone else, refresh and retry".

It's called **optimistic** because we assume conflicts are rare. For owner+agent edits, that's true. The alternative (pessimistic locking — lock the row when someone opens it) blocks everyone else from reading. Too wasteful for occasional edits.

## Photos via R2

Listing photos are stored on Cloudflare R2, which is S3-API-compatible but has zero egress fees (vs S3's $0.09/GB). Critical for an image-heavy product.

Upload flow today: the browser POSTs a multipart file to Haven, Haven proxies it to R2 using the AWS SDK's S3 client, the public R2 URL gets stored in the database.

Reads are direct: when anyone views a listing, the browser fetches photos directly from R2's CDN. Haven never touches read bytes.

The proxied-upload approach works fine at our scale (~50 uploads/day) but uses Haven's bandwidth and memory. At >100 concurrent uploads we'd switch to pre-signed PUT URLs — the browser uploads directly to R2 with a short-lived signed URL, and Haven just records metadata after. Already designed and partially scaffolded.

The demo seed uses pre-existing Unsplash URLs directly (no R2 round-trip) for speed.

## Same endpoint, different responses

`GET /api/listings/17` returns different JSON depending on who's asking.

Anonymous browser gets public fields only. Logged-in applicant gets the same plus `savedByMe`. The owner gets owner-only fields like `ownerEmail`, `pendingOfferCount`, and `version` (needed for edits). An admin gets moderation fields too.

Same URL, same row, four different shapes. The service inspects the JWT principal and layers fields onto the response.

We could have split this into separate endpoints (public / owner / admin). We didn't because the frontend has one URL pattern — the page just shows extra controls when it's your own listing. One backend call, one frontend fetch.

For writes, the role check (`@PreAuthorize("hasRole('OWNER')")`) plus a service-level ownership check (`if (!listing.ownerId.equals(me.userId())) throw forbidden`) gives two-tier defence: the role check is general, the ownership check is specific to this listing.

## Accepting an offer cascades

When an owner accepts an offer, three things happen in one transaction:

1. The accepted offer flips to ACCEPTED
2. Every other PENDING offer on that listing auto-declines, and each loser gets a notification
3. The listing itself auto-closes (CLOSED status)

The owner doesn't have to remember to close the listing — that was a persona-audit gap from Biodun. The atomic transaction means you never see a half-state ("accepted but listing still open" can't exist).

That ACCEPTED offer row is the permanent link between applicant and listing. It's never deleted. To answer "who rented this property?" we query offers where `status = ACCEPTED` on any listing belonging to the property.

## How history is tracked

Three layers, each answering a different question.

**Deal history (implicit)** — preserved through the ACCEPTED offer rows. The link between applicant and listing lives forever in the offers table.

**Admin audit log (explicit)** — every admin action (takedown, suspend, approve verification) writes a row to `admin_audit_log`. Append-only, in the same transaction as the action. PRD §4.10: "Full audit trail of all admin actions".

**JPA timestamps (automatic)** — every entity has `createdAt` and `updatedAt` populated by Spring Data's `@CreatedDate` / `@LastModifiedDate`. Tells you *when* a row was touched, but not *what* changed.

What's NOT captured: per-field edit history. If Amaka changed the price three times last week, we only know the current price and the timestamp of the last change. A future audit listener could capture diffs (Hibernate Envers would do it); we decided owner-edit history isn't critical enough to justify the storage cost. Admin actions ARE captured because they have compliance implications.
