-- Semantic listing discovery for Dream AI: OpenAI text-embedding-3-small vectors (1536 dims) + pgvector ANN index.
-- Requires CREATE privilege on the database (local Docker: superuser OK). Managed Postgres may need
-- a DBA to enable the extension once before this migration runs.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE listing_search_embeddings (
    listing_id BIGINT PRIMARY KEY REFERENCES listings(id) ON DELETE CASCADE,
    embedding  vector(1536) NOT NULL,
    model      VARCHAR(64)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Cosine distance operator class for `<=>` with HNSW (pgvector >= 0.5).
CREATE INDEX listing_search_embeddings_hnsw
    ON listing_search_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
