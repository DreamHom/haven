package com.dreamhomes.haven.notification;

/**
 * The kinds of notifications we deliver. Stored as a string so adding a new kind is a
 * code-only change (no DB migration). The {@code payload} column holds kind-specific
 * JSON — different shapes per kind are by design.
 */
public enum NotificationKind {
    INSPECTION_REQUESTED,
    OFFER_SUBMITTED
}
