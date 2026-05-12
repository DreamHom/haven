package com.dreamhomes.haven.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional reason for a reactivate action. Audit log captures it. Persona
 * audit (Dayo): "reactivate should symmetrically carry a justification —
 * 'user produced exonerating evidence' / 'ticket #4421 closed'. Six months
 * later somebody asks 'who un-suspended this scammer?' and nobody knows."
 */
public record ReactivateUserRequest(
        @Size(max = 1000) String reason
) {
}
