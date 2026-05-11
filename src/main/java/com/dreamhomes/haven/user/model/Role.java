package com.dreamhomes.haven.user.model;

/**
 * The four actor types on the platform. Stored as a string in the {@code users.role} column,
 * with a CHECK constraint mirroring this set so DB rejects unknown values.
 */
public enum Role {
    OWNER,
    AGENT,
    APPLICANT,
    ADMIN
}
