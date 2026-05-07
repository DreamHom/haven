package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.PropertyType;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceBrowseTest {

    @Mock
    ListingRepository listingRepository;

    @Mock
    PropertyRepository propertyRepository;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, propertyRepository);
    }

    @Test
    void browsePublicAsksRepositoryForOnlyLiveListings() {
        Pageable page = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        when(listingRepository.findByStatus(eq(ListingStatus.LIVE), eq(page)))
                .thenReturn(new PageImpl<>(List.of(liveListingFor(7L)), page, 1));
        when(propertyRepository.findAllById(Set.of(7L))).thenReturn(List.of(propertyAt(7L)));

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
        when(propertyRepository.findAllById(Set.of(7L, 8L)))
                .thenReturn(List.of(propertyAt(7L), propertyAt(8L)));

        Page<ListingWithProperty> result = listingService.browsePublic(page);

        assertThat(result.getContent().get(0).property().getId()).isEqualTo(7L);
        assertThat(result.getContent().get(1).property().getId()).isEqualTo(8L);
    }

    @Test
    void emptyPageDoesNotHitPropertyRepository() {
        Pageable page = PageRequest.of(0, 20);
        when(listingRepository.findByStatus(eq(ListingStatus.LIVE), eq(page)))
                .thenReturn(new PageImpl<>(List.of(), page, 0));

        Page<ListingWithProperty> result = listingService.browsePublic(page);

        assertThat(result.isEmpty()).isTrue();
        verify(propertyRepository, never()).findAllById(any());
    }

    private static Listing liveListingFor(Long propertyId) {
        return Listing.builder()
                .id(propertyId * 10).status(ListingStatus.LIVE)
                .propertyId(propertyId).ownerId(1L)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("100.00")).currency("NGN")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private static Property propertyAt(Long id) {
        return Property.builder()
                .id(id).ownerId(1L).type(PropertyType.HOUSE)
                .address("Address " + id).bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build();
    }
}
