-- Inspection slots: time windows the owner of a listing publishes as "I'm available".
-- A slot has no explicit AVAILABLE/CLAIMED status — it's "available" if no active
-- inspection_request points at it. This avoids a stale denormalised flag.
CREATE TABLE inspection_slots (
    id          BIGSERIAL    PRIMARY KEY,
    listing_id  BIGINT       NOT NULL REFERENCES listings(id),
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT inspection_slots_window_check CHECK (ends_at > starts_at)
);

-- Inspection requests: an applicant claiming a slot. status defaults to PENDING.
-- Conflict prevention is enforced by the partial unique index below — at most one
-- active (non-DECLINED) request per slot. A DECLINED request frees the slot for
-- another applicant to try.
CREATE TABLE inspection_requests (
    id            BIGSERIAL    PRIMARY KEY,
    slot_id       BIGINT       NOT NULL REFERENCES inspection_slots(id),
    applicant_id  BIGINT       NOT NULL REFERENCES users(id),
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT inspection_requests_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED'))
);

CREATE UNIQUE INDEX inspection_requests_active_slot_unique
    ON inspection_requests (slot_id)
    WHERE status IN ('PENDING', 'APPROVED');

-- Notifications: persisted record of every notification we deliver. Kafka events are
-- the cross-service trigger; this table is the readable artifact for a future
-- "my notifications" API. payload is JSON-shaped text so the row stays self-describing
-- without forcing a JSONB column or polymorphic table per kind.
CREATE TABLE notifications (
    id            BIGSERIAL    PRIMARY KEY,
    recipient_id  BIGINT       NOT NULL REFERENCES users(id),
    kind          VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
