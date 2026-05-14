package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.property.model.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;

@ExtendWith(MockitoExtension.class)
class ListingServiceBrowseTest {

    @Mock
    ListingRepository listingRepository;

    @Mock
    PropertyService propertyService;

    @Mock
    com.dreamhomes.haven.user.repository.UserRepository userRepository;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, propertyService, new com.dreamhomes.haven.listing.ListingMapperImpl(), org.mockito.Mockito.mock(com.dreamhomes.haven.agentlisting.AgentListingRepository.class), org.mockito.Mockito.mock(com.dreamhomes.haven.listingreport.ListingReportRepository.class), userRepository);
        lenient().when(userRepository.findPublicBiosByUserIds(any()))
                .thenReturn(List.of());
    }

    @Test
    void browsePublicAsksRepositoryForOnlyLiveListings() {
        Pageable page = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        when(listingRepository.findByStatus(eq(ListingStatus.LIVE), eq(page)))
                .thenReturn(new PageImpl<>(List.of(liveListingFor(7L)), page, 1));
        when(propertyService.findSummariesByIds(Set.of(7L))).thenReturn(Map.of(7L, summaryAt(7L)));

        Page<ListingWithProperty> result = listingService.browsePublic(page);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).listing().getStatus()).isEqualTo(ListingStatus.LIVE);
        verify(listingRepository).findByStatus(ListingStatus.LIVE, page);
    }

    @Test
    void browsePublicAttachesParentPropertyToEachListing() {
        Pageable page = PageRequest.of(0, 20);
        when(listingRepository.findByStatus(eq(ListingStatus.LIVE), eq(page)))
                .thenReturn(new PageImpl<>(List.of(liveListingFor(7L), liveListingFor(8L)), page, 2));
        when(propertyService.findSummariesByIds(Set.of(7L, 8L)))
                .thenReturn(Map.of(7L, summaryAt(7L), 8L, summaryAt(8L)));

        Page<ListingWithProperty> result = listingService.browsePublic(page);

        assertThat(result.getContent().get(0).property().id()).isEqualTo(7L);
        assertThat(result.getContent().get(1).property().id()).isEqualTo(8L);
    }

    @Test
    void emptyPageDoesNotHitPropertyService() {
        Pageable page = PageRequest.of(0, 20);
        when(listingRepository.findByStatus(eq(ListingStatus.LIVE), eq(page)))
                .thenReturn(new PageImpl<>(List.of(), page, 0));

        Page<ListingWithProperty> result = listingService.browsePublic(page);

        assertThat(result.isEmpty()).isTrue();
        verify(propertyService, never()).findSummariesByIds(any());
    }

    private static Listing liveListingFor(Long propertyId) {
        return Listing.builder()
                .id(propertyId * 10).status(ListingStatus.LIVE)
                .propertyId(propertyId).ownerId(1L)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("100.00")).currency("NGN")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private static PropertySummary summaryAt(Long id) {
        return new PropertySummary(id, PropertyType.HOUSE, "Address " + id, 3, 2, null, null, null, null);
    }
}
