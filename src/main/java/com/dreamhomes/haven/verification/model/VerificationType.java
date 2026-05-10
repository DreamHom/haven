package com.dreamhomes.haven.verification.model;

/**
 * The four verification tracks (PRD §4.8). Targets differ:
 * <ul>
 *   <li>{@link #OWNER_IDENTITY}, {@link #AGENT_CREDENTIALS}, {@link #APPLICANT_IDENTITY}
 *       — target is a {@code users.id}.</li>
 *   <li>{@link #PROPERTY_DOCUMENTS} — target is a {@code properties.id}.</li>
 * </ul>
 * The {@code verifications_target_consistent} CHECK constraint enforces this at the
 * database layer; the service enforces it at write time.
 */
public enum VerificationType {
    OWNER_IDENTITY,
    PROPERTY_DOCUMENTS,
    AGENT_CREDENTIALS,
    APPLICANT_IDENTITY
}
