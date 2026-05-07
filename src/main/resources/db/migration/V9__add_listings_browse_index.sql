-- Public browse hits this query for every paginated request:
--   SELECT * FROM listings WHERE status = 'LIVE' ORDER BY created_at DESC LIMIT N
-- A composite index on (status, created_at DESC) lets PG seek directly without
-- scanning the table. With status filtered, Postgres can also use this for the
-- "newest first" sort cheaply.
CREATE INDEX listings_status_created_at_idx ON listings (status, created_at DESC);
