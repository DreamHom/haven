-- Item 8 (post-session-tasks.md): comment threading. Add an optional parent_comment_id
-- so replies can attach to a top-level comment. The service-layer validation ensures the
-- parent exists, is non-deleted, and belongs to the same listing — see CommentService.
--
-- We keep the parent reference soft (no ON DELETE cascade): when an admin or owner takes
-- a parent down via soft-delete, replies remain in the database for the audit trail and
-- the public list query filters them out via the existing deleted_at IS NULL guard.
ALTER TABLE comments
    ADD COLUMN parent_comment_id BIGINT REFERENCES comments(id);

-- Partial index supports a future "load all replies for parent X" query without bloating
-- the index with the (much larger) population of top-level comments.
CREATE INDEX comments_parent_idx
    ON comments (parent_comment_id)
    WHERE parent_comment_id IS NOT NULL;
