-- Transactional Outbox: domain row + outbox row are written atomically in the same
-- application transaction. A separate scheduled relay polls this table, publishes to
-- Kafka, and marks rows shipped. Solves the dual-write between DB and Kafka per the
-- PRD §7 promise that "missed notification = missed deal."
CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    event_id        UUID         NOT NULL UNIQUE,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    partition_key   VARCHAR(128) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

-- Hot index: the relay's poll filters on published_at IS NULL and orders by created_at.
-- Partial index keeps it tiny (most rows are eventually published, drop out of the index).
CREATE INDEX outbox_unpublished_idx
    ON outbox (created_at)
    WHERE published_at IS NULL;

-- Notifications get the event_id used by the consumer for at-least-once dedup.
-- The UNIQUE keeps duplicate-delivery safe: a second insert for the same event_id
-- raises 23505 and we treat it as a no-op.
ALTER TABLE notifications
    ADD COLUMN event_id UUID UNIQUE,
    ADD COLUMN source   VARCHAR(32) NOT NULL DEFAULT 'ASYNC_KAFKA';

ALTER TABLE notifications
    ADD CONSTRAINT notifications_source_check
    CHECK (source IN ('SYNC', 'ASYNC_KAFKA'));

-- Hot read path: "my unread notifications, newest first" for the future dashboard endpoint.
-- Partial-index alternative considered but the column is nullable on existing data, so a
-- composite index covers both reads (unread filter) and the general "all my notifs" order.
CREATE INDEX notifications_recipient_unread_created_idx
    ON notifications (recipient_id, read_at, created_at DESC);
