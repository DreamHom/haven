-- Per-token JWT blocklist backing `POST /api/auth/logout?scope=device`.
--
-- Persona audit (Amaka, Temi): the current "logout = bump tokenVersion" nukes every
-- device the user has signed in on, which is wrong UX when they only want to sign
-- out the laptop they're standing at. With a per-jti blocklist the auth filter can
-- reject only that one token while siblings stay valid.
--
-- Rows are inserted on POST /api/auth/logout?scope=device (default). The auth filter
-- checks for an existing row before honouring a JWT; if found, 401. A nightly clean
-- can prune rows past their expires_at, but until then the table is bounded by the
-- product of (active users × tokens per user × token TTL).
CREATE TABLE jwt_blocklist (
    jti         UUID        PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Used by the periodic pruning job (when one exists): "delete where expires_at < now()".
CREATE INDEX jwt_blocklist_expires_at_idx ON jwt_blocklist (expires_at);
