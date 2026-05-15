# Vista integration docs (Haven repo)

Material here is for **DreamHomes Vista** engineers consuming the Haven API.

| File | Purpose |
| --- | --- |
| [`integration-log.md`](integration-log.md) | Gap matrix vs Vista’s inventory, changelog per shipped API change, OpenAPI reminders |
| [`openapi-diff-1.0.1-to-1.0.2.md`](openapi-diff-1.0.1-to-1.0.2.md) | **Contract drift** between frozen Vista YAML 1.0.1 and 1.0.2 (new paths + same-path schema changes not always in changelog prose) |
| [`../dream-ai-capabilities.md`](../dream-ai-capabilities.md) | **Dream AI handoff** — what Haven implements vs phase 2; points tests + OpenAPI as sources of truth |
| [`../haven-backend-gaps-and-integration.md`](../haven-backend-gaps-and-integration.md) | **Canonical** Vista ↔ Haven gap inventory + **Appendix A** (route parity matrix) |

Keep a Vista-side re-export if desired, but **`docs/haven-backend-gaps-and-integration.md` in this repo is authoritative** for Haven `main`.
