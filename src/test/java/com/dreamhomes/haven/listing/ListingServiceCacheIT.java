package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.common.config.CacheConfig;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.dto.UpdateListingCommand;
import com.dreamhomes.haven.listing.embedding.ListingSearchEmbeddingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.listingreport.ListingReportRepository;
import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the {@code @Cacheable} / {@code @CacheEvict} wiring on {@link ListingService}.
 * Uses a slice context (no full Spring Boot start) wired with the real
 * {@link CacheConfig} so the cache proxy is engaged; collaborators are mocks.
 *
 * <p>Each test clears every cache in {@code @BeforeEach} so they're independent.
 */
@SpringJUnitConfig
class ListingServiceCacheIT {

    @Autowired ListingService listingService;
    @Autowired ListingRepository listingRepository;
    @Autowired PropertyService propertyService;
    @Autowired UserRepository userRepository;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void resetCachesAndMocks() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        reset(listingRepository, propertyService, userRepository);
    }

    @Test
    void findPubliclyVisibleHitsRepositoryOnceAcrossRepeatedCallsForSameListing() {
        Listing live = liveListing(50L, 7L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(live));
        when(propertyService.findSummary(7L)).thenReturn(Optional.of(summary(7L)));
        when(userRepository.findPublicBioByUserId(1L)).thenReturn(Optional.empty());

        for (int i = 0; i < 3; i++) {
            ListingWithProperty lwp = listingService.findPubliclyVisible(50L);
            assertThat(lwp.listing().getId()).isEqualTo(50L);
        }

        verify(listingRepository, times(1)).findById(50L);
        verify(propertyService, times(1)).findSummary(7L);
    }

    @Test
    void recordPublicViewBumpsOnEveryCallEvenWhenDetailIsCached() {
        Listing live = liveListing(50L, 7L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(live));
        when(propertyService.findSummary(7L)).thenReturn(Optional.of(summary(7L)));
        when(userRepository.findPublicBioByUserId(1L)).thenReturn(Optional.empty());

        for (int i = 0; i < 3; i++) {
            listingService.findPubliclyVisible(50L);
            listingService.recordPublicView(50L);
        }

        verify(listingRepository, times(3)).incrementViewCount(50L);
    }

    @Test
    void differentListingIdsCacheIndependently() {
        Listing fifty = liveListing(50L, 7L);
        Listing sixty = liveListing(60L, 8L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(fifty));
        when(listingRepository.findById(60L)).thenReturn(Optional.of(sixty));
        when(propertyService.findSummary(7L)).thenReturn(Optional.of(summary(7L)));
        when(propertyService.findSummary(8L)).thenReturn(Optional.of(summary(8L)));
        when(userRepository.findPublicBioByUserId(1L)).thenReturn(Optional.empty());

        listingService.findPubliclyVisible(50L);
        listingService.findPubliclyVisible(60L);
        listingService.findPubliclyVisible(50L);

        verify(listingRepository, times(1)).findById(50L);
        verify(listingRepository, times(1)).findById(60L);
    }

    @Test
    void notFoundResultsAreNotCachedSoLaterPublishCanBeReadImmediately() {
        when(listingRepository.findById(404L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(liveListing(404L, 7L)));
        when(propertyService.findSummary(7L)).thenReturn(Optional.of(summary(7L)));
        when(userRepository.findPublicBioByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.findPubliclyVisible(404L))
                .isInstanceOf(ListingNotFoundException.class);
        ListingWithProperty lwp = listingService.findPubliclyVisible(404L);
        assertThat(lwp.listing().getId()).isEqualTo(404L);
    }

    @Test
    void browsePublicHitsRepositoryOnceForRepeatedCallsWithSameFiltersAndPageable() {
        Pageable page = PageRequest.of(0, 20);
        when(listingRepository.findByStatus(ListingStatus.LIVE, page))
                .thenReturn(new PageImpl<>(List.of(liveListing(50L, 7L)), page, 1));
        when(propertyService.findSummariesByIds(Set.of(7L)))
                .thenReturn(Map.of(7L, summary(7L)));
        when(userRepository.findPublicBiosByUserIds(anySet())).thenReturn(List.of());

        for (int i = 0; i < 4; i++) {
            listingService.browsePublic(null, null, null, null, null, null, page);
        }

        verify(listingRepository, times(1)).findByStatus(ListingStatus.LIVE, page);
    }

    @Test
    void browsePublicDistinguishesDifferentFilterSetsInTheCache() {
        Pageable page = PageRequest.of(0, 20);
        when(listingRepository.findByStatus(ListingStatus.LIVE, page))
                .thenReturn(new PageImpl<>(List.of(), page, 0));
        when(listingRepository.searchLive(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), page, 0));

        listingService.browsePublic(null, null, null, null, null, null, page);
        listingService.browsePublic(ListingType.RENT, null, null, null, null, null, page);
        listingService.browsePublic(null, null, null, null, null, "Yaba", page);

        verify(listingRepository, times(1)).findByStatus(ListingStatus.LIVE, page);
        verify(listingRepository, times(2))
                .searchLive(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateEvictsTheCachedDetailEntryForThatListing() {
        Listing live = liveListing(50L, 7L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(live));
        when(propertyService.findSummary(7L)).thenReturn(Optional.of(summary(7L)));
        when(userRepository.findPublicBioByUserId(1L)).thenReturn(Optional.empty());
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        listingService.findPubliclyVisible(50L);
        assertThat(cacheManager.getCache(CacheConfig.LISTINGS_DETAIL).get(50L)).isNotNull();

        listingService.update(1L, Role.OWNER, 50L,
                new UpdateListingCommand(null, null, null, null, null, null, null, null, null, null, null));

        assertThat(cacheManager.getCache(CacheConfig.LISTINGS_DETAIL).get(50L)).isNull();
    }

    @Test
    void updateEvictsTheEntireBrowseNamespaceSinceWeDontKnowWhichFilterMatched() {
        Pageable page = PageRequest.of(0, 20);
        when(listingRepository.findByStatus(ListingStatus.LIVE, page))
                .thenReturn(new PageImpl<>(List.of(), page, 0));
        Listing live = liveListing(50L, 7L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(live));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        listingService.browsePublic(null, null, null, null, null, null, page);
        listingService.browsePublic(null, null, null, null, null, null, page);
        verify(listingRepository, times(1)).findByStatus(ListingStatus.LIVE, page);

        listingService.update(1L, Role.OWNER, 50L,
                new UpdateListingCommand(null, null, null, null, null, null, null, null, null, null, null));

        // Post-eviction the same browse args must re-hit the repo.
        listingService.browsePublic(null, null, null, null, null, null, page);
        verify(listingRepository, times(2)).findByStatus(ListingStatus.LIVE, page);
    }

    @Test
    void adminMarkApprovedEvictsTheCachedDetailEntry() {
        Listing live = liveListing(50L, 7L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(live));
        when(propertyService.findSummary(7L)).thenReturn(Optional.of(summary(7L)));
        when(userRepository.findPublicBioByUserId(1L)).thenReturn(Optional.empty());
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        listingService.findPubliclyVisible(50L);
        listingService.markApproved(50L, Instant.now());

        assertThat(cacheManager.getCache(CacheConfig.LISTINGS_DETAIL).get(50L)).isNull();
    }

    @Test
    void adminForceStatusEvictsTheCachedDetailEntry() {
        Listing live = liveListing(50L, 7L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(live));
        when(propertyService.findSummary(7L)).thenReturn(Optional.of(summary(7L)));
        when(userRepository.findPublicBioByUserId(1L)).thenReturn(Optional.empty());
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        listingService.findPubliclyVisible(50L);
        listingService.forceStatus(50L, ListingStatus.TAKEN_DOWN, Instant.now());

        assertThat(cacheManager.getCache(CacheConfig.LISTINGS_DETAIL).get(50L)).isNull();
    }

    @Test
    void browsePublicTreatsDifferentPageNumbersAsDifferentCacheEntries() {
        Pageable page0 = PageRequest.of(0, 20);
        Pageable page1 = PageRequest.of(1, 20);
        when(listingRepository.findByStatus(ListingStatus.LIVE, page0))
                .thenReturn(new PageImpl<>(List.of(), page0, 0));
        when(listingRepository.findByStatus(ListingStatus.LIVE, page1))
                .thenReturn(new PageImpl<>(List.of(), page1, 0));

        listingService.browsePublic(null, null, null, null, null, null, page0);
        listingService.browsePublic(null, null, null, null, null, null, page1);

        verify(listingRepository, times(1)).findByStatus(ListingStatus.LIVE, page0);
        verify(listingRepository, times(1)).findByStatus(ListingStatus.LIVE, page1);
    }

    private static Listing liveListing(Long id, Long propertyId) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(id).propertyId(propertyId).ownerId(1L)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("100.00")).currency("NGN")
                .status(ListingStatus.LIVE).createdAt(now).updatedAt(now)
                .build();
    }

    private static PropertySummary summary(Long id) {
        return new PropertySummary(id, PropertyType.HOUSE, "Address " + id, 3, 2,
                null, null, null, null);
    }

    @Configuration
    @Import(CacheConfig.class)
    static class MockCollaborators {
        @Bean ListingRepository listingRepository() { return mock(ListingRepository.class); }
        @Bean PropertyService propertyService() { return mock(PropertyService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean AgentListingRepository agentListingRepository() { return mock(AgentListingRepository.class); }
        @Bean ListingReportRepository listingReportRepository() { return mock(ListingReportRepository.class); }
        @Bean ListingSearchEmbeddingService listingSearchEmbeddingService() {
            return mock(ListingSearchEmbeddingService.class);
        }
        @Bean ListingMapper listingMapper() { return new ListingMapperImpl(); }

        @Bean ListingService listingService(ListingRepository listingRepository,
                                            PropertyService propertyService,
                                            ListingMapper listingMapper,
                                            AgentListingRepository agentListingRepository,
                                            ListingReportRepository listingReportRepository,
                                            UserRepository userRepository,
                                            ListingSearchEmbeddingService embeddings) {
            return new ListingService(listingRepository, propertyService, listingMapper,
                    agentListingRepository, listingReportRepository, userRepository, embeddings);
        }
    }
}
