package com.dreamhomes.haven.notification;

import java.util.Map;
import java.util.UUID;

/**
 * Public contract for recording in-app notifications. Cross-feature consumers wire this
 * interface — they never see the {@code Notification} entity, the repository, or any
 * other implementation detail.
 *
 * <p>Two flavours:
 * <ul>
 *   <li>{@link #recordSync} — caller is acting in the same DB transaction as the source
 *       event (e.g. owner takes down a listing → notify the listing owner). Synchronous,
 *       failure-coupled with the originating action.</li>
 *   <li>{@link #recordAsync} — caller is processing a Kafka event from elsewhere.
 *       {@code eventId} provides idempotency: the second delivery of the same event is
 *       a no-op. Failure here does NOT roll back the producing action.</li>
 * </ul>
 *
 * <p>The implementation is {@code com.dreamhomes.haven.notification.NotificationService}
 * in the {@code feature-notification-impl} module. Callers only need this interface on
 * their classpath, which keeps them honest about the contract.
 */
public interface NotificationApi {

    /**
     * Persist a synchronous notification triggered by a same-transaction action.
     *
     * @param kind            classification — drives client-side rendering
     * @param recipientUserId user the notification belongs to
     * @param payload         arbitrary JSON-serialisable map; rendered by the client
     */
    void recordSync(NotificationKind kind, Long recipientUserId, Map<String, Object> payload);

    /**
     * Persist an asynchronous notification triggered by a Kafka event. Idempotent on
     * {@code eventId} — duplicate deliveries are dropped.
     *
     * @param eventId         per-event identifier (already on the consumed event)
     * @param kind            classification
     * @param recipientUserId user the notification belongs to
     * @param payload         the event itself, serialised for the client
     */
    void recordAsync(UUID eventId, NotificationKind kind, Long recipientUserId, Object payload);
}
