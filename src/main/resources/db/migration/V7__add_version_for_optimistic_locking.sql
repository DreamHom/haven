-- Optimistic locking on aggregates that legitimately have concurrent writers:
-- listings (owner edits, future admin overrides) and offers (owner accepts/declines
-- while a future auto-decline-of-competing-offers job runs).
-- JPA's @Version increments on every save; a stale write throws
-- ObjectOptimisticLockingFailureException, which we map to 409 in the global handler.
ALTER TABLE listings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE offers   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
