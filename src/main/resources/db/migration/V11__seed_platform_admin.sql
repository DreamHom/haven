-- Seeded platform admin (PRD §4.10: "Seeded admin account — no self-registration").
-- Idempotent on email — running this migration on a database that already has the
-- admin row is a no-op, so the same migration set is safe across dev/staging/prod
-- where the admin email may be pre-provisioned.
--
-- Placeholders come from spring.flyway.placeholders.* — the dev defaults live in
-- application.yml; prod must override via ADMIN_EMAIL / ADMIN_PASSWORD_HASH env
-- vars. We never store a raw password in source.
INSERT INTO users (email, password_hash, role, full_name, token_version, created_at)
VALUES (
    '${admin_email}',
    '${admin_password_hash}',
    'ADMIN',
    '${admin_full_name}',
    1,
    NOW()
)
ON CONFLICT (email) DO NOTHING;
