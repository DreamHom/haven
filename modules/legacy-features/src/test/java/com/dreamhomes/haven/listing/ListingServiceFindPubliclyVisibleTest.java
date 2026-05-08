package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertyApi;
import com.dreamhomes.haven.property.PropertySummary;
import com.dreamhomes.haven.property.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers the public-visibility rule we wrote in {@code findPubliclyVisible}:
 * paused or closed listings are 404 to anonymous callers (we don't want to leak that
 * a listing exists or used to exist), and live listings come back with their property
 * attached so the public detail page can render in one round trip.
 */
@ExtendWith(MockitoExtension.class)
class ListingServiceFindPubliclyVisibleTest {

    @Mock
    ListingRepository listingRepository;

    @Mock
    PropertyApi propertyApi;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, propertyApi);
    }

    @Test
    void liveListingIsReturnedWithItsProperty() {
        Listing live = listing(50L, 7L, ListingStatus.LIVE);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(live));
        when(propertyApi.findSummary(7L)).thenReturn(Optional.of(summary(7L)));

        ListingWithProperty result = listingService.findPubliclyVisible(50L);

        assertThat(result.listing().getId()).isEqualTo(50L);
        assertThat(result.property().id()).isEqualTo(7L);
    }

    @Test
    void pausedListingIs404ToHidePauseFromPublic() {
        when(listingRepository.findById(50L))
                .thenReturn(Optional.of(listing(50L, 7L, ListingStatus.PAUSED)));

        assertThatThrownBy(() -> listingService.findPubliclyVisible(50L))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void closedListingIs404ToAvoidLeakingThatItEverExisted() {
        when(listingRepository.findById(50L))
                .thenReturn(Optional.of(listing(50L, 7L, ListingStatus.CLOSED)));

        assertThatThrownBy(() -> listingService.findPubliclyVisible(50L))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void missingListingIs404() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.findPubliclyVisible(404L))
                .isInstanceOf(ListingNotFoundException.class);
    }

    private static Listing listing(Long id, Long propertyId, ListingStatus status) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(id).propertyId(propertyId).ownerId(1L)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("100.00")).currency("NGN")
                .status(status).createdAt(now).updatedAt(now)
                .build();
    }

    private static PropertySummary summary(Long id) {
        return new PropertySummary(id, PropertyType.HOUSE, "Address", 3, 2, null, null);
    }
}
