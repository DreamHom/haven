-- Token revocation: every JWT carries the user's token_version. Bumping it (e.g. on
-- logout or admin suspend) invalidates every previously-issued token for that user.
ALTER TABLE users
    ADD COLUMN token_version INT NOT NULL DEFAULT 1;

-- Drop the speculative index from V1 — no query filters by role yet, and unused
-- indexes still cost on every INSERT/UPDATE. Re-add when a real query needs it.
DROP INDEX IF EXISTS users_role_idx;

-- Per-role profile table (Option C: shared identity + per-role profile). Lives
-- separate from users so role-specific columns don't pollute the identity table
-- with NULLs for non-applicable rows. Cascade delete: a user wipe takes the
-- profile with it.
CREATE TABLE agent_profiles (
    user_id                  BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    license_number           VARCHAR(64)  NOT NULL,
    cac_registration_number  VARCHAR(64),
    bio                      TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT agent_profiles_license_number_unique UNIQUE (license_number)
);
