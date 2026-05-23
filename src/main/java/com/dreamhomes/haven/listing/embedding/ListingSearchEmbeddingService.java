package com.dreamhomes.haven.listing.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Coordinates OpenAI embedding writes (after successful listing/property commits) and
 * pgvector nearest-neighbour reads for Dream AI candidate selection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListingSearchEmbeddingService {

    private final ListingEmbeddingProperties properties;
    private final ListingEmbeddingWriter listingEmbeddingWriter;
    private final OpenAiEmbeddingsClient openAiEmbeddingsClient;
    private final ListingSearchEmbeddingStore listingSearchEmbeddingStore;

    public boolean active() {
        return properties.active();
    }

    /**
     * Returns up to {@code limit} LIVE listing ids ordered by embedding similarity to the query text.
     * Empty when embeddings are disabled or the index has no rows / OpenAI fails.
     */
    public List<Long> nearestLiveListingIds(String queryText, int limit) {
        if (!properties.active() || limit <= 0) {
            return List.of();
        }
        try {
            float[] q = openAiEmbeddingsClient.embed(queryText);
            return listingSearchEmbeddingStore.nearestLive(q, limit);
        } catch (Exception ex) {
            log.warn("Vector neighbour search skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    public void scheduleRefreshListing(long listingId) {
        if (!properties.active()) {
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
        if (!properties.active()) {
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
