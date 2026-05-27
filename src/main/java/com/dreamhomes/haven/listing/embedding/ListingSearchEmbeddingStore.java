package com.dreamhomes.haven.listing.embedding;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ListingSearchEmbeddingStore {

    private final JdbcTemplate jdbcTemplate;

    public void upsert(long listingId, float[] embedding, String model) {
        PGvector vec = new PGvector(embedding);
        jdbcTemplate.update("""
                        INSERT INTO listing_search_embeddings (listing_id, embedding, model, updated_at)
                        VALUES (?, ?, ?, NOW())
                        ON CONFLICT (listing_id) DO UPDATE SET
                            embedding = EXCLUDED.embedding,
                            model = EXCLUDED.model,
                            updated_at = NOW()
                        """,
                listingId, vec, model);
    }

    public void delete(long listingId) {
        jdbcTemplate.update("DELETE FROM listing_search_embeddings WHERE listing_id = ?", listingId);
    }

    /**
     * Cosine distance ({@code <=>}) — smallest distance = best match. Only LIVE listings.
     */
    public List<Long> nearestLive(float[] query, int limit) {
        PGvector qv = new PGvector(query);
        return jdbcTemplate.query("""
                        SELECT e.listing_id
                        FROM listing_search_embeddings e
                        JOIN listings l ON l.id = e.listing_id
                        WHERE l.status = 'LIVE'
                        ORDER BY e.embedding <=> ?
                        LIMIT ?
                        """,
                (rs, rowNum) -> rs.getLong(1),
                qv, limit);
    }

    /**
     * Item 22 — cost-defence variant of {@link #nearestLive(float[], int)} that also enforces
     * a cosine-distance cutoff. Rows whose distance to {@code query} is at or above
     * {@code maxDistance} are dropped, so junk prompts (e.g. "purple elephant tap dance")
     * resolve to an empty result and the orchestrator can skip the downstream Claude call.
     *
     * <p>Distance semantics: pgvector's {@code <=>} is cosine distance ∈ [0, 2] — 0 is
     * identical direction, 1 is orthogonal, 2 is opposite. 0.5 is a reasonable starting
     * threshold for English real-estate prompts; tune per corpus.
     */
    public List<Long> nearestLive(float[] query, int limit, double maxDistance) {
        PGvector qv = new PGvector(query);
        return jdbcTemplate.query("""
                        SELECT e.listing_id
                        FROM listing_search_embeddings e
                        JOIN listings l ON l.id = e.listing_id
                        WHERE l.status = 'LIVE'
                          AND (e.embedding <=> ?) < ?
                        ORDER BY e.embedding <=> ?
                        LIMIT ?
                        """,
                (rs, rowNum) -> rs.getLong(1),
                qv, maxDistance, qv, limit);
    }
}
