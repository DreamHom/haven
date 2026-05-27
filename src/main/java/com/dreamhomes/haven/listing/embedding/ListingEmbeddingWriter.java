package com.dreamhomes.haven.listing.embedding;

import com.dreamhomes.haven.dreamai.provider.EmbeddingProvider;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.property.model.Property;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingEmbeddingWriter {

    private final ListingEmbeddingProperties properties;
    private final ListingRepository listingRepository;
    private final PropertyRepository propertyRepository;
    private final EmbeddingProvider embeddingProvider;
    private final ListingSearchEmbeddingStore listingSearchEmbeddingStore;

    @Transactional
    public void refresh(long listingId) {
        if (!properties.active() || !embeddingProvider.isAvailable()) {
            return;
        }
        Listing listing = listingRepository.findById(listingId).orElse(null);
        if (listing == null) {
            listingSearchEmbeddingStore.delete(listingId);
            return;
        }
        if (listing.getStatus() != ListingStatus.LIVE) {
            listingSearchEmbeddingStore.delete(listingId);
            return;
        }
        PropertySummary property = propertyRepository.findById(listing.getPropertyId())
                .map(ListingEmbeddingWriter::toSummary)
                .orElse(null);
        String text = ListingEmbeddingText.format(listing, property);
        float[] vector = embeddingProvider.embed(text);
        listingSearchEmbeddingStore.upsert(listingId, vector, properties.getModel());
        log.debug("Updated listing_search_embeddings for listingId={}", listingId);
    }

    private static PropertySummary toSummary(Property p) {
        return new PropertySummary(
                p.getId(),
                p.getType(),
                p.getAddress(),
                p.getBedrooms(),
                p.getBathrooms(),
                p.getSizeSqm(),
                p.getDocumentsVerifiedAt(),
                p.getLatitude(),
                p.getLongitude());
    }

    @Transactional
    public void delete(long listingId) {
        listingSearchEmbeddingStore.delete(listingId);
    }
}
