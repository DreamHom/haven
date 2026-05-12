package com.dreamhomes.haven.common.outbox;

public final class OutboxRowReadyEvent {

    public static final OutboxRowReadyEvent INSTANCE = new OutboxRowReadyEvent();

    private OutboxRowReadyEvent() {
    }
}
