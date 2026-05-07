package com.dreamhomes.haven.listing;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceBrowseTest {

    @Mock
    ListingRepository listingRepository;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, /* unused */ null);
    }

    @Test
    void browsePublicAsksRepositoryForOnlyLiveListingsWithCallerPaging() {
        Pageable page = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Listing live = Listing.builder()
                .id(1L).status(ListingStatus.LIVE)
                .propertyId(1L).ownerId(1L)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("100.00")).currency("NGN")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(listingRepository.findByStatus(eq(ListingStatus.LIVE), eq(page)))
                .thenReturn(new PageImpl<>(List.of(live), page, 1));

        Page<Listing> result = listingService.browsePublic(page);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(ListingStatus.LIVE);
        verify(listingRepository).findByStatus(ListingStatus.LIVE, page);
    }
}
