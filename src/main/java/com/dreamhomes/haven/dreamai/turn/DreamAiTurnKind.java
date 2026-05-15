package com.dreamhomes.haven.dreamai.turn;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Assistant turn discriminator — drives UI layout for Dream AI.")
public enum DreamAiTurnKind {
    /** Ranked listings and optional rationale (MVP). */
    reply,
    /** Missing slots — use chips block. */
    clarify,
    /** Zero matches — copy from meta flags (inventory vs strict). */
    no_results,
    /** Side-by-side compare projection. */
    compare,
    /** Terminal failure for the turn (upstream, parse, policy). */
    error
}
