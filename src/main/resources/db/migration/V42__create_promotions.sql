CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(32) NOT NULL,
    listing_id BIGINT REFERENCES listings(id),
    agent_user_id BIGINT REFERENCES users(id),
    placement VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    approved_by_admin_id BIGINT REFERENCES users(id),
    approved_at TIMESTAMPTZ,
    decision_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT promotions_window_valid CHECK (ends_at > starts_at),
    CONSTRAINT promotions_status_valid CHECK (status IN ('PENDING','ACTIVE','PAUSED','REJECTED','REVOKED','EXPIRED')),
    CONSTRAINT promotions_target_type_valid CHECK (target_type IN ('LISTING','AGENT')),
    CONSTRAINT promotions_target_consistent CHECK (
        (target_type = 'LISTING' AND listing_id IS NOT NULL AND agent_user_id IS NULL)
        OR (target_type = 'AGENT' AND agent_user_id IS NOT NULL AND listing_id IS NULL)
    ),
    CONSTRAINT promotions_placement_valid CHECK (placement IN ('HOMEPAGE_FEATURED','LISTING_SEARCH_TOP','AGENT_DIRECTORY_TOP')),
    CONSTRAINT promotions_decision_complete CHECK (
        (status IN ('PENDING','ACTIVE','PAUSED','EXPIRED') AND (status = 'PENDING' OR approved_by_admin_id IS NOT NULL))
        OR (status IN ('REJECTED','REVOKED') AND decision_reason IS NOT NULL)
    )
);

CREATE INDEX promotions_public_lookup_idx
    ON promotions (placement, status, starts_at, ends_at, priority DESC, created_at DESC);

CREATE INDEX promotions_owner_lookup_idx
    ON promotions (created_by_user_id, created_at DESC);

CREATE INDEX promotions_listing_target_lookup_idx
    ON promotions (listing_id, status)
    WHERE listing_id IS NOT NULL;

CREATE INDEX promotions_agent_target_lookup_idx
    ON promotions (agent_user_id, status)
    WHERE agent_user_id IS NOT NULL;

CREATE TABLE promotion_impressions (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    viewer_user_id BIGINT REFERENCES users(id),
    placement VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT promotion_impressions_placement_valid CHECK (placement IN ('HOMEPAGE_FEATURED','LISTING_SEARCH_TOP','AGENT_DIRECTORY_TOP'))
);

CREATE INDEX promotion_impressions_promotion_time_idx
    ON promotion_impressions (promotion_id, occurred_at DESC);

CREATE TABLE promotion_clicks (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    viewer_user_id BIGINT REFERENCES users(id),
    placement VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT promotion_clicks_placement_valid CHECK (placement IN ('HOMEPAGE_FEATURED','LISTING_SEARCH_TOP','AGENT_DIRECTORY_TOP'))
);

CREATE INDEX promotion_clicks_promotion_time_idx
    ON promotion_clicks (promotion_id, occurred_at DESC);