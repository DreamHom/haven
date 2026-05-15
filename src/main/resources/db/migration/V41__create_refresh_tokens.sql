-- Refresh tokens — long-lived (default 30 days) credentials the client exchanges
-- for a fresh access JWT via POST /api/auth/refresh. Stored as SHA-256 hashes
-- (never raw) so a DB read on this table never leaks the token; the raw token
-- is shown to the client exactly once, in the login/refresh response.
--
-- Rotation policy: every successful refresh issues a NEW token and marks the
-- old one revoked, with `replaced_by_id` pointing at the successor. If a
-- revoked-and-replaced token is ever presented again, that's a replay attack
-- (someone else has a copy) — the service revokes the entire rotation chain
-- so neither party can keep using it.
--
-- Logout (scope=device): revokes the matching refresh row.
-- Logout (scope=all):    bumps users.token_version (kills access JWTs) +
--                        revokes every active refresh row for the user.

CREATE TABLE refresh_tokens (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- SHA-256 of the raw token, hex-encoded (32 bytes → 64 hex chars).
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    -- Set when the row is invalidated — either by rotation, explicit logout, or
    -- replay-detection chain revoke. Once set, the token is no longer accepted.
    revoked_at      TIMESTAMPTZ,
    -- When set, points at the successor row issued during a rotation. Used by
    -- the chain-revoke path so that detecting reuse of an old token can walk
    -- forward and revoke every descendant in one go.
    replaced_by_id  BIGINT       REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    -- Optional client metadata for "active sessions" UIs.
    user_agent      VARCHAR(255),
    ip_address      VARCHAR(64)
);

-- Lookup paths:
--   * by user (revoke-all on logout=all, list active sessions)
CREATE INDEX refresh_tokens_user_id_idx     ON refresh_tokens (user_id);
--   * by expires_at (nightly pruning of expired or long-revoked rows)
CREATE INDEX refresh_tokens_expires_at_idx  ON refresh_tokens (expires_at);
