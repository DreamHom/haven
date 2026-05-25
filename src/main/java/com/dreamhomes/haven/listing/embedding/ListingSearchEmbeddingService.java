package com.dreamhomes.haven.listing.embedding;

import com.dreamhomes.haven.dreamai.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Coordinates embedding writes (after successful listing/property commits) and pgvector
 * nearest-neighbour reads for Dream AI candidate selection.
 *
 * <p>The actual embedding call goes through the {@link EmbeddingProvider} abstraction
 * (Item 25) so swapping vendors (OpenAI → Voyage → self-hosted) is one env var with no
 * code changes here. The {@link ListingEmbeddingProperties#active()} short-circuit is
 * still consulted because the legacy callers (the {@code listing_embedding_writer}
 * background job + the catalog backfill) read the OpenAI-specific properties for
 * model / dimensions / base URL — those are an OpenAI-shaped knob and would migrate to
 * a provider-agnostic shape on the v2 swap.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListingSearchEmbeddingService {

    private final ListingEmbeddingProperties properties;
    private final ListingEmbeddingWriter listingEmbeddingWriter;
    private final EmbeddingProvider embeddingProvider;
    private final ListingSearchEmbeddingStore listingSearchEmbeddingStore;

    public boolean active() {
        return properties.active() && embeddingProvider.isAvailable();
    }

    /**
     * Returns up to {@code limit} LIVE listing ids ordered by embedding similarity to the query text.
     * Empty when embeddings are disabled or the index has no rows / the provider fails.
     */
    public List<Long> nearestLiveListingIds(String queryText, int limit) {
        if (!active() || limit <= 0) {
            return List.of();
        }
        try {
            float[] q = embeddingProvider.embed(queryText);
            return listingSearchEmbeddingStore.nearestLive(q, limit);
        } catch (Exception ex) {
            log.warn("Vector neighbour search skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Item 22 — distance-bounded variant. Drops candidates whose cosine distance to the
     * query is at or above {@code maxDistance} so the orchestrator can early-bail on junk
     * prompts and avoid the Claude call. Same fail-soft semantics as the unbounded overload.
     */
    public List<Long> nearestLiveListingIds(String queryText, int limit, double maxDistance) {
        if (!active() || limit <= 0) {
            return List.of();
        }
        try {
            float[] q = embeddingProvider.embed(queryText);
            return listingSearchEmbeddingStore.nearestLive(q, limit, maxDistance);
        } catch (Exception ex) {
            log.warn("Vector neighbour search skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    public void scheduleRefreshListing(long listingId) {
        if (!active()) {
            return;
        }
        Runnable job = () -> {
            try {
                listingEmbeddingWriter.refresh(listingId);
            } catch (Exception ex) {
                log.warn("listing_search_embeddings refresh failed for listingId={}", listingId, ex);
            }
        };
        runAfterCommitOrNow(job);
    }

    public void scheduleDeleteListing(long listingId) {
        if (!active()) {
            return;
        }
        Runnable job = () -> {
            try {
                listingEmbeddingWriter.delete(listingId);
            } catch (Exception ex) {
                log.warn("listing_search_embeddings delete failed for listingId={}", listingId, ex);
            }
        };
        runAfterCommitOrNow(job);
    }

    /**
     * The active embedding provider — exposed so callers that want to stamp
     * {@code meta.embeddingProvider} on responses can see which vendor served the query.
     */
    public EmbeddingProvider provider() {
        return embeddingProvider;
    }

    private static void runAfterCommitOrNow(Runnable job) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }
}
