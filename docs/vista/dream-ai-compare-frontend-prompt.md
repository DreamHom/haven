# Dream AI compare — frontend integration brief

> Hand this whole document to your frontend agent. It describes the backend
> contract and the user expectations; it deliberately does **not** prescribe
> components, layouts, or visuals — design intuitively for what the data
> represents and what the user is trying to do.

---

## What this is

Dream AI's compare turn used to be a "render these two listing IDs side by
side" stub — no AI reasoning, no recommendation. The backend now actually
analyses the listings, returns per-listing pros/cons + a "best-for" persona
line, and (when one option clearly fits the user's stated context better
than the others) names a recommended listing. The frontend's job is to make
that reasoning legible without burying the existing card layout.

**Two ways the user can trigger it** (the backend handles both — the
frontend doesn't have to choose):

1. Pasting or typing **two-or-more listing URLs / paths** into the prompt
   (`/listings/1`, `https://www.dreamhomes.today/listings/5`, etc.).
2. Asking a **comparison-style follow-up question** in an existing chat that
   just returned listings (e.g. "which is best for me?", "compare these for a
   single mum with two kids", "what would suit a young couple commuting to VI?").
   The orchestrator detects the intent and reuses the listing IDs from the
   prior assistant turn — the frontend just needs to keep passing the
   `chatId` back.

---

## Endpoints (no change)

The compare feature uses the existing Dream AI turn endpoints — same shape,
no new routes.

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `POST` | `/api/dream-ai/suggestions` | **Public** (no JWT required) | Synchronous JSON. Returns the full assistant turn. |
| `POST` | `/api/dream-ai/turns/stream` | **Public** | Server-sent events: `trace` → optional `delta` → `final` (carries the same JSON envelope) or `problem` (RFC 7807) on failure. |
| `GET`  | `/api/dream-ai/chats/{id}` | Authenticated user only | History rehydration; ids in compare blocks are re-filtered to LIVE on read (`meta.staleIdsFiltered: true` if anything dropped). |

For the **conversation-aware trigger** (path 2 above), the user must be
**logged in** so the chat thread exists in `dream_ai_chats` and the
orchestrator can read prior-turn context. Anonymous follow-ups have no chat
state to look back at — they fall through to the URL-trigger path or to the
clarify / rank / no-results paths.

---

## Request shape

```ts
type DreamAiRunTurnRequest = {
  prompt?: string;            // free-text user input
  chatId?: number;            // include to continue an existing thread
  clientMessageId?: string;   // optional idempotency key (UUID v4 recommended)
  userChoice?: {              // alternative to prompt — used after a clarify chip
    chipId: string;
    sendText: string;         // verbatim text to treat as the user's reply
  };
};
```

- `prompt` (or `userChoice.sendText`) is required and non-blank.
- `chatId` is optional but **strongly recommended** for any multi-turn UX —
  it's the only way the conversation-aware compare path can fire.
- `clientMessageId` is the safe-replay key: same id + same `chatId` returns
  the previously-stored turn instead of doing the work again.

---

## Response shape

```ts
type DreamAiRunTurnResponse = {
  chatId: number | null;        // null on anonymous one-shot, otherwise the thread id
  traceId: string;              // UUID, threaded through logs + observability
  listingIds: number[];         // legacy convenience: top-level ids from the turn
  turn: AssistantTurnV1;        // the new authoritative envelope
};

type AssistantTurnV1 = {
  kind: "reply" | "clarify" | "compare" | "no_results" | "error";
  markdown: string | null;      // optional prose (compare: AI summary; clarify/no_results: helper copy)
  blocks: TurnBlock[];          // structured payloads (see below)
  meta: TurnMeta;
};

type TurnBlock = {
  type: "listings" | "compare" | "chips";
  listingIds?: number[];        // when type === "listings": ranked results to render as cards
  compareListingIds?: number[]; // when type === "compare": ids to lay out side-by-side
  options?: ChipOption[];       // when type === "chips": quick-reply buttons (clarify path)
  compareReasoning?: CompareReasoning | null; // ⭐ NEW — only set on compare blocks when AI ran
};

type ChipOption = {
  chipId: string;               // echo this back via userChoice.chipId
  label: string;                // short button copy
  sendText: string;             // sentence to send if user taps the chip
};

type CompareReasoning = {
  recommendedListingId: number | null; // the AI's pick, or null when too even to call
  summary: string;                     // 2-4 sentence markdown explaining the recommendation (or the tradeoffs if no winner)
  perListing: PerListingNote[];        // one note per listing being compared, in input order
};

type PerListingNote = {
  id: number;                          // matches one of the compareListingIds
  headline: string;                    // short character-summary of the listing (≤ 120 chars)
  pros: string[];                      // 1-4 concrete advantages, each ≤ 80 chars
  cons: string[];                      // 1-4 honest drawbacks, each ≤ 80 chars
  bestFor: string;                     // one-sentence persona statement (≤ 240 chars)
};

type TurnMeta = {
  inventoryEmpty: boolean | null;      // true = catalogue itself is empty
  queryTooStrict: boolean | null;      // true = catalogue had matches but none ranked
  degraded: boolean | null;            // true = stub fallback (no Anthropic key, etc.)
  provider: string | null;             // "anthropic" | "stub" | "compare" | "orchestrator"
  traceId: string;                     // mirrors top-level traceId
  moderationBlocked: boolean | null;   // true on 422 fallthrough
  retryable: boolean | null;
  staleIdsFiltered: boolean | null;    // true on history rehydration if any ids were dropped
};
```

### What each `kind` means for rendering decisions

- `reply` — successful search; `blocks` contains a single `listings` block with
  ranked IDs. The user sees their results.
- `clarify` — prompt was too vague; `blocks` contains a single `chips` block.
  The user picks one or types their own answer; the frontend echoes the chip
  via `userChoice` on the next request.
- **`compare` — the new behaviour.** `blocks` contains a single `compare`
  block with `compareListingIds` (always) and `compareReasoning` (when the
  AI ran). `markdown` is the recommendation summary. **The frontend MUST
  branch on whether `compareReasoning` is present**:
  - **Present**: full AI analysis available — show the summary, the pick,
    the per-listing notes alongside the cards.
  - **Absent / null**: the backend ran in stub mode (no Anthropic key) or
    the model failed. The user still expects to see the listings rendered
    side-by-side; just no commentary.
- `no_results` — nothing matched. `markdown` explains why (`inventoryEmpty`
  vs `queryTooStrict` from `meta`). The user expects a graceful nudge to
  loosen filters or try different wording.
- `error` — unrecoverable for this turn. `markdown` is human-friendly.
  Common cause for compare: one or more requested listings is no longer
  LIVE (e.g. the user pasted an old URL, or the listing closed since the
  prior turn surfaced it).

---

## What the user expects (without prescribing the UI)

When the user has just been shown a row of listings and they ask a
comparison question, **they do not want the conversation to re-search**.
They want the AI to look at the listings on screen and say something
substantive about *those*. The contract above gives the frontend everything
it needs to satisfy this:

- A clear **lead-in line** ("Of these three, the Lekki flat fits your
  family-with-school-runs constraint best because…") — that's
  `turn.markdown` and/or `compareReasoning.summary`.
- A way to **mark the recommended option** so the user's eye finds the
  pick without reading every sentence — that's
  `compareReasoning.recommendedListingId`. If it's `null`, the AI
  deliberately declined to choose; the summary will explain why ("all three
  are within budget; pick on personal preference"). The frontend should
  honour the absence of a winner rather than inventing one.
- **Per-listing reasoning attached to the listing it describes** — that's
  `perListing[]`. Each entry has the listing's `id` so the frontend can
  attach pros / cons / best-for to the right card without guessing.
- **Persona language** (`bestFor`) so the user can self-identify
  ("'a young couple commuting to VI' — yes, that's me"). It's a single
  sentence; treat it as a tagline, not a list.
- **Honest tradeoffs** — `cons` is intentionally non-empty even on the
  recommended listing. The user trusts the AI more when they see
  acknowledgment of weaknesses.

The same `compare` shape arrives whether the user typed URLs or asked a
follow-up question — the user doesn't care about the trigger; the frontend
just renders the response.

---

## Anonymous vs. logged-in nuance

| Scenario | What happens |
| --- | --- |
| Anonymous user types a prompt with two URLs | URL-trigger fires; AI compare runs; **`chatId` in response is `null`** (no thread persisted). |
| Anonymous user asks "which is best?" with no URLs | Backend has no chat history to look at; falls through to clarify / rank / no-results. |
| Logged-in user types a prompt with two URLs | URL-trigger fires; turn persists to a chat (response includes `chatId`); `compareReasoning` populated. |
| Logged-in user asks "which is best?" after a `reply` turn | **Conversation-aware path fires** — uses the prior turn's listing ids; AI compare runs with the original prompt joined into the user-intent context. |

If the frontend wants the conversation-aware compare to work, **always send
`chatId` back on every follow-up request in a thread**. The backend cannot
infer it.

---

## Concrete example — happy path, conversation-aware

User session:

1. User (anonymous or logged-in) types `3-bed in Lekki under 4M`.
2. Frontend POSTs `{ prompt: "3-bed in Lekki under 4M" }`.
3. Backend returns:
   ```json
   {
     "chatId": 17,
     "traceId": "9b0c…",
     "listingIds": [1, 5],
     "turn": {
       "kind": "reply",
       "markdown": null,
       "blocks": [{ "type": "listings", "listingIds": [1, 5] }],
       "meta": { "provider": "anthropic", "traceId": "9b0c…", … }
     }
   }
   ```
4. Frontend renders 2 listing cards.
5. User (now in a logged-in session) follows up: `which would suit a single
   mum with two kids in primary school?`.
6. Frontend POSTs `{ prompt: "which would suit a single mum with two kids in primary school?", chatId: 17 }`.
7. Backend detects the comparison intent + the prior turn's `[1, 5]` ids,
   asks Claude with the joined intent, and returns:
   ```json
   {
     "chatId": 17,
     "traceId": "fa14…",
     "listingIds": [],
     "turn": {
       "kind": "compare",
       "markdown": "For a single mum with two school-aged kids, listing #1 (the Lekki Phase 1 sea-view flat) is the better pick — it's in the same school catchment as Greensprings and St Saviour's, and the cross-ventilation and en-suite master suit a family with kids more comfortably than the Hughes Avenue walk-up. The Yaba listing is cheaper but the second-floor stairs and the bus-stop traffic are real friction with school runs.",
       "blocks": [{
         "type": "compare",
         "compareListingIds": [1, 5],
         "compareReasoning": {
           "recommendedListingId": 1,
           "summary": "For a single mum with two school-aged kids, listing #1 fits better — school catchment, en-suite master, cross-ventilation. The Yaba walk-up is cheaper but the stairs and bus traffic add daily friction.",
           "perListing": [
             {
               "id": 1,
               "headline": "Top-floor 3-bed with sea-view balcony, Lekki Phase 1",
               "pros": [
                 "Walking distance to two top primary schools",
                 "En-suite master keeps her own routine separate",
                 "24/7 power + borehole — no school-morning surprises"
               ],
               "cons": [
                 "₦3.5M/yr stretches the 4M budget once service charge is included",
                 "Top-floor: groceries up four flights"
               ],
               "bestFor": "A single parent with school-aged kids who values catchment and predictable utilities."
             },
             {
               "id": 5,
               "headline": "3-bed walk-up two minutes from UNILAG",
               "pros": [
                 "Well under budget at ₦1.8M/yr",
                 "Cross-ventilation; second floor is breezy",
                 "Two-minute walk to the Yaba bus stop"
               ],
               "cons": [
                 "No lift — second floor walk-up with two kids is hard",
                 "Bus-stop traffic noisy on school mornings",
                 "School catchment is weaker than Lekki Phase 1"
               ],
               "bestFor": "A budget-conscious postgrad couple or a single working adult; harder fit for primary-school families."
             }
           ]
         }
       }],
       "meta": { "provider": "compare", "traceId": "fa14…", … }
     }
   }
   ```
8. Frontend re-renders the same 2 cards but now layered with the AI's
   reasoning — summary lead-in, recommendation badge on listing #1,
   pros/cons/best-for surfaced per card.

---

## Edge cases the frontend must handle gracefully

| Situation | Backend response | What to surface to the user |
| --- | --- | --- |
| Anthropic key not configured | `kind=compare`, block has `compareListingIds` but **no `compareReasoning`** (or `null`) | Render the cards side-by-side; suppress the reasoning UI silently. The user still gets value from the layout. |
| One or more listings is no longer LIVE | `kind=error`, friendly markdown about confirming availability | Show the markdown as an inline message; offer to re-run the search to refresh results. |
| Model returned per-listing notes that don't fully cover every requested id | `compareReasoning.perListing` has fewer entries than `compareListingIds` | Render whatever notes exist; cards without matching notes degrade silently to the no-reasoning shape. (The backend already filters out notes with unknown ids — frontend doesn't need to validate.) |
| Conversation follow-up with no prior listings (e.g. user's first prompt was a clarify) | Backend falls through to rank / no-results | Same as a normal first turn; no special handling needed. |
| User pastes only one URL and asks "is this good for me?" | Falls through to clarify / rank — only **two-or-more** URLs trigger compare | The frontend doesn't need to know; render whatever turn comes back. |
| `meta.staleIdsFiltered: true` on a history-rehydrated chat | Some listings the prior turn referenced are no longer LIVE | Add a subtle "some results have changed" note above the rehydrated turn. |
| 429 rate limit | RFC 7807 ProblemDetail body, `Retry-After` header | Surface the wait time; don't silent-fail. |
| 422 moderation block | RFC 7807 ProblemDetail with `type: …/moderation-blocked` | Show the explanation; the prompt was rejected pre-LLM. |

---

## Backwards compatibility

The compare block now ships with an **optional** `compareReasoning` field.
Older frontends that ignore it keep working — they see the same
`compareListingIds` they always saw and render the layout. No action is
required from the frontend until you want to surface the AI commentary.

The legacy "two URLs paste" trigger is unchanged. The conversation-aware
trigger is purely additive — older frontends that don't pass `chatId` simply
never see it fire.

---

## Where to look in the OpenAPI spec

`GET /v3/api-docs` (or the Scalar UI at `/scalar.html`) contains the
authoritative schemas:

- `AssistantTurnV1`
- `TurnBlock` — note the new `compareReasoning` field on the `compare` shape
- `CompareReasoning`, `PerListingNote`
- `DreamAiRunTurnRequest`, `DreamAiRunTurnResponse`

Diff against the prior frozen bundle when re-exporting; the only schema
additions are `CompareReasoning` and `PerListingNote`, plus the optional
`compareReasoning` field on `TurnBlock`. Everything else is wire-stable.

---

## What the frontend agent should NOT do

- Don't re-rank or re-order `perListing[]` on the client — the backend
  already returns it in the same order as `compareListingIds`.
- Don't call any other endpoint to enrich the comparison — the listing's
  full data is fetched separately via `/api/listings/{id}` and `/photos`
  as you do today; the compare reasoning is the AI's reading of that data,
  not a replacement for it.
- Don't try to "explain" a `null` recommendation to the user as
  inconclusive — the `summary` already does that; just present the summary
  honestly and let the user decide.
- Don't hide a recommendation behind a click — the user asked which fits;
  the recommendation is the answer.
- Don't strip the `cons` from the recommended listing because they look
  negative — the model includes them deliberately to build trust.
