package com.dreamhomes.haven.notification.model;

/**
 * Where a notification came from. Useful for ops debugging — distinguishing event-
 * driven (Kafka) notifications from synchronously-written ones (verification approvals,
 * listing decisions, etc., once those land in Phase 5).
 */
public enum NotificationSource {
    SYNC,
    ASYNC_KAFKA
}
