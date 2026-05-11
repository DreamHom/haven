package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertyService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.listing.dto.UpdateListingCommand;
import com.dreamhomes.haven.listing.exception.InvalidListingTransitionException;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;

@ExtendWith(MockitoExtension.class)
class ListingServiceUpdateTest {

    @Mock
    ListingRepository listingRepository;

    @Mock
    PropertyService propertyService;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        // propertyService is unused by update() — passing a mock instead of null so
        // the dependency injection looks honest and a future call would surface, not NPE.
        listingService = new ListingService(listingRepository, propertyService, new com.dreamhomes.haven.listing.ListingMapperImpl());
    }

    @Test
    void updatesAskingPriceAndStatusForOwner() {
        Listing existing = liveListingOwnedBy(99L);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        Listing result = listingService.update(99L, 50L, new UpdateListingCommand(
                new BigDecimal("2000000.00"), ListingStatus.PAUSED));

        assertThat(result.getAskingPrice()).isEqualByComparingTo("2000000.00");
        assertThat(result.getStatus()).isEqualTo(ListingStatus.PAUSED);
        // updatedAt is bumped by JPA auditing on save (Listing has @LastModifiedDate);
        // not the service's responsibility. ListingFlowEndToEndIT exercises the persisted bump.
    }

    @Test
    void rejectsUpdateByNonOwner() {
        when(listingRepository.findById(50L)).thenReturn(Optional.of(liveListingOwnedBy(200L)));

        assertThatThrownBy(() -> listingService.update(99L, 50L,
                new UpdateListingCommand(new BigDecimal("2000000.00"), null)))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void throwsWhenListingDoesNotExist() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.update(99L, 404L,
                new UpdateListingCommand(new BigDecimal("1.00"), null)))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void rejectsReopeningAClosedListing() {
        Listing closed = liveListingOwnedBy(99L);
        closed.setStatus(ListingStatus.CLOSED);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> listingService.update(99L, 50L,
                new UpdateListingCommand(null, ListingStatus.LIVE)))
                .isInstanceOf(InvalidListingTransitionException.class);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void allowsClosingAPausedListing() {
        Listing paused = liveListingOwnedBy(99L);
        paused.setStatus(ListingStatus.PAUSED);
        when(listingRepository.findById(50L)).thenReturn(Optional.of(paused));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        Listing result = listingService.update(99L, 50L,
                new UpdateListingCommand(null, ListingStatus.CLOSED));

        assertThat(result.getStatus()).isEqualTo(ListingStatus.CLOSED);
    }

    private static Listing liveListingOwnedBy(Long ownerId) {
        Instant now = Instant.now().minusSeconds(60);
        return Listing.builder()
                .id(50L).propertyId(7L).ownerId(ownerId)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("1500000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(now).updatedAt(now)
                .build();
    }
}
