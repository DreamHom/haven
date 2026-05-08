-- Phase 7: Agent–listing assignment handshake (PRD §4.1, userflows §3).
--
-- Owner invites a verified-or-unverified agent to manage one of their listings; agent
-- accepts or declines; either party can revoke an ACCEPTED assignment later. Notifications
-- are sync DB rows (PRD §7 keeps Kafka to the two big events only).
--
-- Two partial UQ indexes encode the invariants at the data layer:
--   • At most one outstanding REQUESTED row per listing — owners can't spam invites.
--     To invite a different agent, revoke the pending row first.
--   • At most one ACCEPTED row per listing — single active agent per listing.
-- DECLINED and REVOKED rows are terminal and fall out of both indexes.
CREATE TABLE agent_listings (
    id                       BIGSERIAL    PRIMARY KEY,
    listing_id               BIGINT       NOT NULL REFERENCES listings(id),
    agent_user_id            BIGINT       NOT NULL REFERENCES users(id),
    requested_by_owner_id    BIGINT       NOT NULL REFERENCES users(id),
    status                   VARCHAR(32)  NOT NULL DEFAULT 'REQUESTED',
    decision_reason          TEXT,
    requested_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decided_at               TIMESTAMPTZ,
    -- Optimistic lock: the request → accept race (multiple agents racing on same
    -- assignment row, or owner racing a revoke against an agent's accept) resolves to
    -- one winner with a clean 409.
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT agent_listings_status_check
        CHECK (status IN ('REQUESTED', 'ACCEPTED', 'DECLINED', 'REVOKED')),
    -- Decision integrity: terminal-state rows must carry decided_at.
    CONSTRAINT agent_listings_decision_complete CHECK (
        (status = 'REQUESTED' AND decided_at IS NULL)
        OR (status IN ('ACCEPTED', 'DECLINED', 'REVOKED') AND decided_at IS NOT NULL)
    )
);

-- One outstanding invite per listing — service short-circuits with a clean 409, this
-- index is the safety net for races.
CREATE UNIQUE INDEX agent_listings_one_pending_per_listing
    ON agent_listings (listing_id)
    WHERE status = 'REQUESTED';

-- One active agent per listing.
CREATE UNIQUE INDEX agent_listings_one_active_per_listing
    ON agent_listings (listing_id)
    WHERE status = 'ACCEPTED';

-- Hot read: "agent's pending + active assignments" / "owner's outstanding invites".
CREATE INDEX agent_listings_agent_status_idx
    ON agent_listings (agent_user_id, status, requested_at DESC);

CREATE INDEX agent_listings_owner_status_idx
    ON agent_listings (requested_by_owner_id, status, requested_at DESC);
