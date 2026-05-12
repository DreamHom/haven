package com.dreamhomes.haven.common.outbox;

/**
 * Internal Spring application event fired after a service writes a new outbox row and
 * its transaction commits. Allows the {@link OutboxRelay} to drain the row immediately
 * instead of waiting for the next scheduled poll — cuts happy-path Kafka latency from
 * up to a second down to tens of milliseconds.
 *
 * <p>The scheduled poll stays as a safety net: if the application crashes between the
 * commit and the listener invocation, the row sits unpublished until the next tick.
 */
public final class OutboxRowReadyEvent {

    public static final OutboxRowReadyEvent INSTANCE = new OutboxRowReadyEvent();

    private OutboxRowReadyEvent() {
    }
}
