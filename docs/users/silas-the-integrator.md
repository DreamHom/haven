# Silas — The Integrator

> *"The endpoint exists, the schema parses, but my screen still can't render. What am I missing?"*

## Profile

| | |
|---|---|
| **Role** | Frontend engineer building the DreamHomes web app |
| **Background** | Lives in `/v3/api-docs`, Scalar, TypeScript codegen, and devtools. Translates every endpoint into a usable screen. |

## The story

Silas is the engineer wiring up *the entire frontend*. Every screen the user
sees — browse, listing detail, inspection booking, offers, comments, profile,
agent directory, admin moderation, settings — has to be assembled out of the
endpoints exposed by Haven. He's not building one page; he's building the
whole product surface.

He doesn't have customer pain. His pain is the gap between *the endpoints
exist* and *the user can actually do the thing*. A REST surface can be
"complete" by the backend's measure and still be unshippable by his: missing
embedded data forces an N+1 fan-out, an enum value lacks a renderable label,
two endpoints disagree on what the same field is called, an error returns a
status the UI can't branch on. He's the person who reads the spec end-to-end
and asks the questions the customer personas never thought to ask, because
none of them ever had to *build the screen*.

The flagship example is the Settings gap from [PR #6](https://github.com/DreamHom/haven/pull/6):
phone, license, and agency were editable but unreadable. None of the six
customer personas opened a Settings page, so the gap shipped to main. Silas
is the canonicalisation of that lens — and broader.

## What he cares about

### Read-for-every-write symmetry
Every editable field needs a readable counterpart for the same user, in the
same shape. If a `PATCH /thing` accepts `agency`, then a `GET` somewhere has
to return `agency` for the authenticated caller, or the form preload is
blank and the user is editing fields they can't see the current value of.

### Field-name consistency across a resource family
The same concept should have the same name everywhere. `userId` on `/me`,
then `id` on `/me/profile`, then `id` again on admin writes is three names
for one thing. TypeScript types get noisy, mappers proliferate, and the
intern who joins next month will reintroduce the bug.

### Response shape that maps to a screen, not a row
Listing detail isn't *just* a listing. It's a card with the agent's name,
the property's photos, the next available inspection slot, recent comments,
and trust signals. If rendering that screen takes 6 sequential GETs the
page is slow and the loading-state UX is awful. Backed-in `assignedAgentId`,
`pendingReportCount`, embedded `PropertySummary` are exactly the kinds of
denorms that turn a 6-call screen into a 1-call screen.

### Errors the UI can branch on
A `400` documented in the spec but returning `401` at runtime means his
`switch (res.status)` never fires the validation branch. RFC 7807
ProblemDetail with stable `type` URIs is the contract — when it works,
the UI can render a real error explainer; when it drifts, the UI just
shows "something went wrong" and he gets a Slack ping at 11pm.

### Discoverability through the spec alone
He doesn't get to ping the backend dev on every endpoint. The OpenAPI spec
+ Scalar must answer: what does this accept? What does it return? What can
go wrong? When is the response cached? Are filters honoured? `@Operation`
descriptions matter; example bodies matter; documented error responses
matter.

### Pagination + sort consistency
Every list endpoint should paginate the same way (`?page=`, `?size=`,
`Page<T>` envelope) and accept the same sort/filter idiom. If `/offers/mine`
takes `?sort=createdAt,desc` but `/notifications/mine` takes `?sort=newest`,
his shared `usePaginatedList` hook can't be shared.

### Navigation he can wire from the data
A listing response should embed enough data that he knows what to link to:
"this listing's owner → `/users/{ownerId}/profile`". If the response only
gives him `ownerId: 7` he can build the link; if it omits `ownerId` he
can't render the "go to owner" button.

### Side effects the user sees should be visible in the API
When the user submits a verification, the in-app notification is what
reassures them it was received. If the backend only writes the notification
asynchronously and his polling misses the first 3 seconds, the user sees
silence after pressing the button. Sync notifications + SSE streams are
how he closes the perception gap.

### Capability gaps before they ship
He'll catch missing functionality the moment he tries to wire a screen.
"How do I display the user's saved listings?" → ah, no `/saves/mine`.
"How does the user un-book an inspection?" → no `DELETE` exists. Those
are caught against the spec, not against running code, because Silas
reads the *whole* surface, not just the path his persona uses.

## How he tests the platform

Silas's audit isn't a flow — it's a **completeness sweep**. For every
endpoint in the spec he asks:

1. **What screen renders this?** If he can't name one, the endpoint is
   ceremony and probably shouldn't ship.
2. **Can the screen render this response *alone*?** Or does it need N+1
   follow-up calls?
3. **Where do its fields come from on the input side?** Is every required
   field something the user can pick from a UI he can build with what
   he already has? (Otherwise: capability gap.)
4. **What does the empty state look like?** What does the loading state
   look like? What does each documented error code look like?
5. **Does it have a corresponding read?** If it's a write, can he preload
   the current value?
6. **Do its enum values translate to renderable labels?** `LIVE`, `CLOSED`,
   `TAKEN_DOWN` are fine for the API; he needs to pick a UI string for
   each. Are they distinct enough for the UI to distinguish?
7. **How does the user *get* here?** What's the previous screen? Does
   that screen's response carry the data he needs to build the link?

## User stories

### Story 1 — Discoverability: the spec is enough to build against ✅ Implemented

**As a** frontend integrator
**I want to** read `/v3/api-docs` and know how to call every endpoint
**without** asking the backend dev
**So that** I can codegen TypeScript clients + wire screens in parallel
with backend development.

**Acceptance criteria**
- [x] Every endpoint has a non-empty `summary` + `description`.
- [x] Every error response references a reusable `#/components/responses/*`
  schema (`Unauthenticated`, `Forbidden`, `NotFound`, `Conflict`,
  `ValidationFailed`, `RateLimited`).
- [x] New enum values appear in the spec the same release they ship in
  code (verified via component-schema audit).

### Story 2 — Read-for-every-write symmetry ✅ Implemented

**As a** frontend integrator wiring an editable field
**I want to** preload the current value before letting the user edit it
**So that** the form isn't a blank slate hiding the existing data.

**Acceptance criteria**
- [x] `PATCH /api/me` editable fields (`email`, `fullName`, `displayName`,
  `phone`) are all readable via `GET /api/me/profile`.
- [x] `PATCH /api/me/agent-profile` (`licenseNumber`, `agency`) — same.
- [x] `PATCH /api/listings/{id}` editable fields (`title`, `description`,
  `headline`, `handoverDate`, etc.) are all on `ListingResponse`.

### Story 3 — Detail responses embed what the screen needs ✅ Implemented

**As a** frontend integrator rendering a listing detail page
**I want to** call one endpoint and get enough to render the card
**without** N+1 fan-out
**So that** the page loads fast and the loading state isn't four
overlapping spinners.

**Acceptance criteria**
- [x] `GET /listings/{id}` embeds `PropertySummary` (so photos, address,
  bedrooms render without a property GET).
- [x] `assignedAgentId` on the same response (link to the agent profile).
- [x] `pendingReportCount` on the same response (trust pill).
- [x] `documentsVerifiedAt` embedded via property summary (trust badge).
- [ ] Future: same denorm shape for offer + inspection detail.

### Story 4 — Field-name consistency inside a resource family ✅ Implemented

**As a** frontend integrator
**I want to** see the same identifier field name on every response within
the same resource family
**So that** my TypeScript types stay sane and my mappers shrink.

**Acceptance criteria**
- [x] `MeResponse.userId` + `PrivateUserProfile.userId` agree (fixed in
  this branch — PR #6 had used `id`).
- [ ] Future: an inventory pass to align `userId` vs `id` across the
  whole surface — tracked in `TRADEOFFS.md` under "userId vs id".

### Story 5 — Errors the UI can branch on ✅ Implemented

**As a** frontend integrator handling failure
**I want** the runtime response status to match what the spec promises
**So that** my `switch (res.status)` actually fires the documented branch.

**Acceptance criteria**
- [x] Validation failures return 400, not 401 (`/error` is `permitAll`
  so the auth filter doesn't rewrite the status on dispatch).
- [x] State-machine violations (e.g. `CLOSED → LIVE`) return 409, not 400.
- [x] Anti-enumeration register returns 202 with a real body explaining
  the next step — not silent.
- [x] Rate-limited requests return 429 with `Retry-After` + a Problem+JSON
  body the UI can render a "try again in 60s" toast from.

### Story 6 — Pagination + filter consistency ✅ Implemented (mostly)

**As a** frontend integrator
**I want to** use one `usePaginatedList(endpoint, filters)` hook for every
list screen
**So that** I'm not maintaining six bespoke paginators.

**Acceptance criteria**
- [x] Every list endpoint accepts `?page=` + `?size=` and returns a
  Spring `Page<T>` envelope.
- [x] Filter param names follow the same shape (`?status=`, `?kind=`,
  `?from=`, `?to=` etc.) — confirmed via the spec audit.
- [ ] Future: standardise sort param syntax (`?sort=createdAt,desc`)
  across every list endpoint.

### Story 7 — Side effects the user sees are surfaced through the API ✅ Implemented

**As a** frontend integrator
**I want** the user's "did the system get my action?" question answered
without a polling loop
**So that** the screen doesn't sit silent after a button press.

**Acceptance criteria**
- [x] Sync notifications fire on every user action (`WELCOME`,
  `VERIFICATION_SUBMITTED`, `INSPECTION_BOOKED`,
  `OFFER_RECEIVED_BY_PLATFORM`).
- [x] `GET /api/notifications/stream` (SSE) pushes new rows the moment
  they commit — no polling required.

### Story 8 — Capability gaps caught against the spec, not at runtime ⬜ Process

**As an** auditor
**I want** a recurring sweep that asks "for every editable field is there
a matching read, and for every renderable screen is there a one-shot read
that fills it"
**So that** the next account-settings-style gap doesn't ship to main
hidden behind a customer-flow audit.

**Acceptance criteria**
- [ ] v2 audit cycle includes an explicit integrator-lens pass alongside
  the six customer personas.
- [ ] The pass treats the OpenAPI spec as the input: walk every endpoint,
  answer the 7 questions in "How he tests the platform" above.

## Why the customer personas couldn't catch this

Grep across all six original persona reviews for `settings`, `edit profile`,
`change password`, `field name`, `error code`, `pagination`, `embed`,
`navigation`, `loading state` returns near-zero hits. The framework walked
**business flows** — register, publish, offer, inspect, moderate — and never
**the integrator's seat**.

Customer personas surface "I want to do X and can't" — capability gaps from
the user's mental model. Silas surfaces "I want to *render* X and can't" —
capability gaps from the screen's data model. They're complementary lenses,
and the platform needs both.
