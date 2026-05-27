# Cursor handoff — Vista task queue instructions

This document is the entry point for any Cursor / Claude Code / dev session picking up frontend work against Haven. **Read this end-to-end before writing any code.**

---

## Context — what Haven is

Haven is the Spring Boot 3.3 / Java 21 backend for DreamHomes (Nigerian real-estate marketplace). Vista is the Next.js frontend that consumes Haven's REST + SSE API.

- Haven repo: `/Users/lukasio/DreamDevs/DreamHomes/haven` (this repo)
- Vista repo: separate (Next.js, deployed to `vista.dreamhomes.today`)
- API contract: `GET /v3/api-docs` (OpenAPI 3) — also rendered at `/scalar.html`
- Backend docs: `docs/demo-prep/` for architecture session notes, `docs/dream-ai-capabilities.md`, `docs/TRADEOFFS.md`

---

## Your job (Cursor)

1. **Open `docs/vista/vista-task-queue.md`** — the master list of frontend work
2. **Pick the highest-priority item that's marked `READY FOR VISTA`** (its backend shipped, API docs are populated)
3. **Read the item's API contract section carefully** — request/response shapes, error codes, edge cases
4. **Implement the Vista side** following the contract exactly
5. **Mark the item as `IN PROGRESS`**, then `DONE` when shipped
6. **Test against the live backend** (`haven.dreamhomes.today`) before marking done

---

## Task entry format

Each entry in `vista-task-queue.md` follows this shape:

```markdown
## VTASK-NNN — Short title

**Status:** ✅ READY FOR VISTA | ⏳ BACKEND IN PROGRESS | 🚧 IN PROGRESS | ✅ DONE
**Backend item:** post-session-tasks.md Item NN
**Backend status:** ✅ shipped at commit <sha> | ⏳ not yet shipped

### Why this matters
One-paragraph framing — what the user gets from this change, which persona it serves.

### API contract
[Filled in when the backend ships]
- Endpoints (method + path)
- Request body schema + example
- Response body schema + example (success + each error code)
- Error responses (status code + ProblemDetail `type` URI + when each fires)

### Vista implementation notes
- Files likely to touch
- New components to create
- State management hooks needed
- Migration path if changing an existing screen
- Copy suggestions

### Test plan (Vista side)
- Manual scenarios to verify against live backend
- Edge cases worth poking
- Visual states (loading / error / empty / success)

### What NOT to do
- Anti-patterns to avoid for this specific change
- Common mistakes the backend contract is opinionated about
```

---

## Rules

1. **Don't invent API shapes.** If the contract section is incomplete or `[Filled in when the backend ships]`, the task is NOT ready — pick a different one or wait.
2. **Don't read source from Haven.** The OpenAPI spec and the task entry are the contract. If they disagree with the source, flag it as a backend bug — don't paper over it on the Vista side.
3. **Test against `haven.dreamhomes.today` (production), not local Haven.** Haven's production deploy is the source of truth for the contract Vista must support.
4. **Update the task status as you go.** A stale `IN PROGRESS` blocks the next person from picking it up.
5. **If an error type isn't documented but the backend returns it, treat it as a backend gap.** Open a comment on the task; don't silently add a `default:` case to your `switch`.

---

## How to find which Haven endpoints exist

- Live: `https://haven.dreamhomes.today/v3/api-docs` (raw JSON) or `/scalar.html` (rendered)
- Repo: search `*.java` files for `@PostMapping`, `@GetMapping`, etc.
- Persona-driven flows: `audit/bruno/` has Bruno HTTP collections for each persona (Amaka, Biodun, Emeka, Ngozi, Dayo, plus a Demo orchestration). Best place to see real request/response examples.

---

## Auth

Haven uses RS256 JWT. Vista should:
- POST `/api/auth/login` → store the JWT + refresh token
- Include `Authorization: Bearer <jwt>` on authenticated requests
- On 401, attempt POST `/api/auth/refresh` once; if that fails, redirect to login
- See `docs/demo-prep/02-auth.md` for the full token lifecycle (rotation, replay detection, jti blocklist, tokenVersion bump)

---

## Errors

Every backend error is RFC 7807 ProblemDetail (`application/problem+json`):

```json
{
  "type": "https://github.com/DreamHom/haven/blob/main/docs/errors/<error-name>",
  "title": "Short human-readable summary",
  "status": 409,
  "detail": "Longer explanation",
  ...optional fields
}
```

Vista should:
- Always check `Content-Type` includes `application/problem+json` before parsing
- Branch on `status` first, then `type` URI suffix for specific user-facing copy
- Never display the raw `detail` to end users — it's for debugging; render product copy based on `type`

---

## Streaming endpoints

Some Haven endpoints stream Server-Sent Events:

- `POST /api/dream-ai/turns/stream` (Dream AI)
- `GET /api/notifications/stream` (live notification push)

For SSE:
- Use the browser's native `EventSource` API
- Listen for named events (e.g. `trace`, `delta`, `final`, `problem`)
- Reconnect on disconnect (EventSource does this automatically)
- HTTP stays 200 once the stream started — branch on the `problem` event for terminal failures, not just `res.status`
