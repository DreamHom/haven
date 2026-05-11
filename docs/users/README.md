# DreamHomes Personas

The six humans the platform exists for. Each `.md` file in this folder is the
canonical answer to "who is this for, what do they need to do, what does
success look like, and what should we test."

## Why this folder exists

These personas are the bridge between the [PRD](../dreamhomes-prd.md) and the
endpoints in the [Scalar UI](http://localhost:8080/scalar.html). When you're
about to add a feature, ask: *which persona's pain does this solve?* If you
can't answer that, the feature probably doesn't belong on the platform.

When a feature ships, the persona doc that covers it should be updated:
- Add the new user story to that persona's section.
- Mark its **Status** as Implemented.
- Add the endpoint to the **Journey through the platform** section.
- Add any new error states the persona might encounter.

## The cast

| Persona | Role | Lives in | Needs from us |
|---|---|---|---|
| [Amaka](amaka-the-lagos-landlord.md) | Owner | Abuja (manages property in Lagos) | Self-serve listing management without an agent in the middle. |
| [Emeka](emeka-the-hustling-agent.md) | Agent | Port Harcourt | A profile that signals legitimacy when he can't afford an office. |
| [Temi](temi-the-first-timer.md) | Applicant | Lagos (Ikorodu → VI) | Education, hand-holding, fair pricing signal — she's never rented before. |
| [Biodun](biodun-the-developer.md) | Owner with assigned agent | Ibadan (developing in Ojodu) | Delegation: list 12 units, hand them to an agent, approve from anywhere. |
| [Ngozi](ngozi-the-skeptic.md) | Applicant (rent-to-buy) | Lagos | Trust signals so visible she'll engage despite past burns. |
| [Dayo](dayo-the-platform-guardian.md) | Admin | Internal — DreamHomes trust & safety | Tooling sharp enough to make every approved badge mean something. |

## How each persona doc is structured

Every file follows the same shape so writing tests, designing UI, or scoping a
new feature against a persona is consistent:

1. **Profile** — role, age, location, one-line background.
2. **The story** — the original narrative. The "why" of every decision in
   the user-stories section.
3. **What they care about** — bullet-form goals, pain points, success
   conditions. Use this when prioritising features.
4. **User stories** — the standard "As a / I want to / so that" format,
   each with acceptance criteria, the endpoints it touches, and an
   **implementation status** (Implemented / Partial / Future).
5. **Journey through the platform** — the chronological path through
   our endpoints from sign-up to outcome. Useful for IT writers and
   designers walking through the flow.
6. **Possible errors** — every HTTP error this persona might hit
   in normal use, with the error code, our response shape, and what
   the UI should do.
7. **Test scenarios** — outline of the integration tests that cover
   this persona's golden path + edge cases.

## Status legend (used in user stories)

- ✅ **Implemented** — endpoint exists, tested, works against this persona's
  expected flow.
- 🟡 **Partial** — endpoint exists but doesn't fully cover the story
  (e.g. missing field, no notification fanout yet).
- ⬜ **Future** — story is real, no endpoint exists yet. Track in the
  backlog.

## Cross-cutting reads

- [`dreamhomes-prd.md`](../dreamhomes-prd.md) — the brief these personas
  satisfy.
- [`dreamhomes-userflows.md`](../dreamhomes-userflows.md) — the journeys
  composed across personas (e.g. owner ↔ agent assignment).
- [`STATE-OF-THE-SYSTEM.md`](../STATE-OF-THE-SYSTEM.md) — what's actually
  shipped today, by phase.
- [`TRADEOFFS.md`](../TRADEOFFS.md) — every "we chose X over Y" with the
  revisit triggers.
