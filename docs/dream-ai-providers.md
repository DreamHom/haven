# Dream AI providers — swap matrix

Dream AI has two pluggable surfaces — the LLM that ranks + compares listings, and the embedding model that powers pgvector nearest-neighbour candidate selection. Each is selected at boot via a single env var; switching providers requires no code changes anywhere downstream of the abstraction.

This document covers Item 25 of `docs/demo-prep/post-session-tasks.md`. The matching v2 work for KYC providers is Item 20 (`verification/automation/`) — same shape.

---

## Quick reference

| Subsystem | Env var | Default | Other values |
|---|---|---|---|
| LLM (rank + compare) | `HAVEN_DREAM_AI_LLM_PROVIDER` | `anthropic` | `openai`, `gemini` |
| Embedding (NN candidate selection) | `HAVEN_DREAM_AI_EMBEDDING_PROVIDER` | `openai` | `voyage`, `self-hosted` |

The defaults match v1 production (Anthropic + OpenAI). Setting either to an unimplemented value boots cleanly but the provider's methods throw `UnsupportedOperationException` with a TODO pointing at the docs to fill in — useful for testing the swap mechanism, not useful for serving traffic.

---

## LLM providers

The active provider must implement `com.dreamhomes.haven.dreamai.provider.LlmRankingProvider` and report `isAvailable() == true`. When `isAvailable()` is false, `DreamAiService` falls back to the location-substring stub so Dream AI never hard-fails.

### `anthropic` (default — active for v1)

Wraps the existing `AnthropicListingSearchClient` + `AnthropicListingCompareClient` (Claude Haiku via the Anthropic Messages API).

**Pick when:**
- You want the best ranking quality available out-of-the-box for the `kind=reply` rail.
- You want the structured pros/cons + recommendation in the `kind=compare` rail.
- You're comfortable paying ~$0.02 per SMART rank turn.

**Env vars to set on Railway:**
```
HAVEN_DREAM_AI_LLM_PROVIDER=anthropic   # (or just leave unset — it's the default)
HAVEN_ANTHROPIC_API_KEY=sk-ant-...
HAVEN_ANTHROPIC_MODEL=claude-haiku-4-5  # optional
```

`meta.llmProvider` on responses: `"anthropic"`.

### `openai` (scaffolded for v2)

Stub that would integrate OpenAI chat-completion with structured JSON output. Bodies throw `UnsupportedOperationException` until v2 wires the actual HTTP call. See `OpenAiLlmRankingProvider.java` for the per-method TODO checklists.

**Pick when (v2):**
- Anthropic outage and you need to flip over in 5 minutes.
- Cost negotiation leverage — concrete migration path.
- A/B testing rank quality between Claude + GPT.

**Env vars to set on Railway (v2):**
```
HAVEN_DREAM_AI_LLM_PROVIDER=openai
HAVEN_OPENAI_CHAT_API_KEY=sk-...        # separate from embeddings key
HAVEN_OPENAI_CHAT_MODEL=gpt-4o-mini     # default (TBD)
```

`meta.llmProvider` on responses: `"openai"`.

### `gemini` (scaffolded for v2)

Stub that would integrate Google Gemini's `generateContent` endpoint with `responseSchema`. Same TODO shape as the OpenAI stub.

**Pick when (v2):**
- Anthropic + OpenAI both down.
- Google Cloud-native deploy where Vertex AI is the cheapest tier.

**Env vars to set on Railway (v2):**
```
HAVEN_DREAM_AI_LLM_PROVIDER=gemini
HAVEN_GOOGLE_API_KEY=AIza...
HAVEN_GEMINI_MODEL=gemini-1.5-flash     # default (TBD)
```

`meta.llmProvider` on responses: `"gemini"`.

---

## Embedding providers

The active provider must implement `com.dreamhomes.haven.dreamai.provider.EmbeddingProvider`. When `isAvailable()` is false, `ListingSearchEmbeddingService.active()` returns false and Dream AI falls back to the legacy first-page catalogue for candidate selection (no NN, no embedding writes).

### `openai` (default — active for v1)

Wraps the existing `OpenAiEmbeddingsClient` (`text-embedding-3-small`, 1536 dims).

**Pick when:**
- You want a single OpenAI account / quota covering embeddings.
- You're already paying OpenAI for other surfaces.

**Env vars to set on Railway:**
```
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=openai      # (or leave unset)
HAVEN_OPENAI_API_KEY=sk-...
HAVEN_OPENAI_EMBEDDING_MODEL=text-embedding-3-small   # default
HAVEN_OPENAI_EMBEDDING_DIMENSIONS=1536                # default
```

`meta.embeddingProvider` on responses: `"openai"`.

### `voyage` (scaffolded for v2)

Stub that would integrate Voyage AI — Anthropic's recommended embedding partner. Pairing Voyage with Claude keeps the whole pipeline single-vendor with respect to the LLM provider's preferred shape.

**Pick when (v2):**
- You're running Claude on the LLM side and want consistent vendor recommendations.
- You want lower latency than OpenAI's embeddings endpoint (Voyage publishes lower p99s on small inputs).

**Env vars to set on Railway (v2):**
```
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=voyage
HAVEN_VOYAGE_API_KEY=pa-...
HAVEN_VOYAGE_MODEL=voyage-3                # or voyage-3-lite for cost
```

**Caveat:** Voyage's model dimensions differ from OpenAI's. The corpus would need re-vectorising (or `ListingEmbeddingProperties.dimensions` adjusted) before the swap takes effect.

`meta.embeddingProvider` on responses: `"voyage"`.

### `self-hosted` (scaffolded for v2)

Stub that would call a Hugging Face TEI container (or any HTTP-shaped embedding service) you operate yourself. Typical deploy: a small CPU or GPU VM running TEI with `sentence-transformers/all-MiniLM-L6-v2` (384-dim) or `BAAI/bge-base-en-v1.5` (768-dim).

**Pick when (v2):**
- Cost predictability is critical — a fixed VM bill vs per-call API charges.
- Data residency / privacy requires the embedding never leave your infra.
- You want to fine-tune the model on Nigerian real-estate phrasing.

**Env vars to set on Railway (v2):**
```
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=self-hosted
HAVEN_SELF_HOSTED_EMBEDDING_URL=https://embed.internal.dreamhomes.com
```

**Caveat:** The chosen model's output width must match `ListingEmbeddingProperties.dimensions` (1536). If you pick a smaller / larger model you must re-vectorise the corpus.

`meta.embeddingProvider` on responses: `"self-hosted"`.

---

## How the swap mechanism works

Every provider implementation is a Spring `@Component` carrying a `@ConditionalOnProperty` that pins it to a specific value of the env var:

```java
@Component
@ConditionalOnProperty(name = "haven.dream-ai.llm-provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicLlmRankingProvider implements LlmRankingProvider { ... }
```

At boot Spring resolves exactly one bean implementing each interface. `DreamAiService` and `ListingSearchEmbeddingService` depend on the interface, not the impl, so they never need to change when the env var flips.

The swap is testable end-to-end via `DreamAiProviderSwapTest` — even the scaffolded providers (which throw on method bodies) are picked up by the conditional, proving v2 is a body-fill + env-var change, not a refactor.

---

## Provider availability + fallback to stub

```
isAvailable() == true   → service uses the provider (rank / embed)
isAvailable() == false  → service falls back:
   LLM stub → location-substring browse
   Embedding stub → first-page catalogue (no NN)
```

This lets a deploy ship with the env vars unset and still serve traffic. The `meta.degraded=true` + `meta.provider="stub"` fields surface this to the UI so Vista can render a "quick search (smart search unavailable)" indicator if it chooses.

---

## Per-call provider stamps (Item 25 additive fields)

Two purely-additive fields on `turn.meta`:

- `meta.llmProvider` — name of the LLM provider that actually ran (`"anthropic"` / `"openai"` / `"gemini"`). Null when the LLM wasn't called on this turn — stub fallback, FAST rankMode (Item 23), clarify / no_results / inventory-empty branches.
- `meta.embeddingProvider` — name of the embedding provider that actually ran (`"openai"` / `"voyage"` / `"self-hosted"`). Null when embeddings weren't consulted (substring stub, pure browse-only path, embedding subsystem dark).

The existing `meta.provider` field keeps its high-level semantics (`anthropic` / `stub` / `embeddings-only` / `compare`) for backwards compatibility. The new fields are a finer-grained debug surface — useful when running an A/B test or post-mortem-ing a quality regression.

---

## Sample env-var sets

### "All Anthropic" (v2 — Claude + Voyage)

```
HAVEN_DREAM_AI_LLM_PROVIDER=anthropic
HAVEN_ANTHROPIC_API_KEY=sk-ant-...
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=voyage
HAVEN_VOYAGE_API_KEY=pa-...
```

### "Voyage + Gemini" (v2 — mixed-vendor cost optimisation)

```
HAVEN_DREAM_AI_LLM_PROVIDER=gemini
HAVEN_GOOGLE_API_KEY=AIza...
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=voyage
HAVEN_VOYAGE_API_KEY=pa-...
```

### "Fully mocked / stub" (CI / local with no keys)

```
HAVEN_DREAM_AI_LLM_PROVIDER=anthropic
# HAVEN_ANTHROPIC_API_KEY left unset → isAvailable=false → location stub
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=openai
# HAVEN_OPENAI_API_KEY left unset → isAvailable=false → first-page catalogue
```

Dream AI still serves turns — just with `meta.provider="stub"` + `meta.degraded=true` so Vista knows to soften copy.
