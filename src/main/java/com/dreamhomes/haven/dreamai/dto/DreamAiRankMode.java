package com.dreamhomes.haven.dreamai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Item 23 (post-session-tasks.md) — per-request switch for the Dream AI ranking pipeline.
 *
 * <p>Lets the caller trade quality for cost. The orchestrator picks a default when the
 * client doesn't supply one: anonymous traffic defaults to {@link #FAST} (cost defence
 * against abusive anonymous prompts), authenticated traffic defaults to {@link #SMART}
 * (the logged-in experience pays for itself with engagement). A client may always
 * override the default explicitly.</p>
 */
@Schema(description = """
        Dream AI ranking mode:

        - **FAST** — skip the Claude ranking call entirely; return pgvector nearest-neighbour
          ids in their existing similarity order. Cheaper, faster, weaker on constraint-heavy
          prompts. Anonymous callers default to this mode.

        - **SMART** — run the Claude ranking call over the pgvector candidates. Higher quality
          on prompts with explicit constraints ("under ₦4m AND verified AND pets"). Authenticated
          callers default to this mode.
        """)
public enum DreamAiRankMode {
    /** Skip Claude; return pgvector NN order as-is. */
    FAST,
    /** Use the Claude ranking step over the pgvector candidates. */
    SMART
}
