package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
class InspectionSlotServiceTest {

    @Mock ListingRepository listingRepository;
    @Mock InspectionSlotRepository slotRepository;

    InspectionSlotService service;

    @BeforeEach
    void setUp() {
        service = new InspectionSlotService(slotRepository, listingRepository);
    }

    @Test
    void persistsSlotWhenCallerOwnsTheListing() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listingOwnedBy(99L)));
        when(slotRepository.save(any(InspectionSlot.class))).thenAnswer(inv -> {
            InspectionSlot s = inv.getArgument(0);
            s.setId(123L);
            return s;
        });

        InspectionSlot result = service.create(99L, 7L, new CreateSlotCommand(
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z")));

        ArgumentCaptor<InspectionSlot> captor = ArgumentCaptor.forClass(InspectionSlot.class);
        verify(slotRepository).save(captor.capture());
        assertThat(captor.getValue().getListingId()).isEqualTo(7L);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(result.getId()).isEqualTo(123L);
    }

    @Test
    void rejectsWhenCallerDoesNotOwnTheListing() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listingOwnedBy(200L)));

        assertThatThrownBy(() -> service.create(99L, 7L, new CreateSlotCommand(
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"))))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void rejectsWhenListingNotFound() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, 404L, new CreateSlotCommand(
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"))))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void rejectsSlotWhereEndIsNotAfterStart() {
        // Window check fires before any DB lookup — fail fast on garbage input.
        assertThatThrownBy(() -> service.create(99L, 7L, new CreateSlotCommand(
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.parse("2026-06-01T10:00:00Z"))))
                .isInstanceOf(InvalidSlotWindowException.class);

        verify(slotRepository, never()).save(any());
        verify(listingRepository, never()).findById(any());
    }

    @Test
    void listAvailableDelegatesToRepository() {
        when(slotRepository.findAvailableForListing(7L))
                .thenReturn(java.util.List.of(InspectionSlot.builder().id(1L).build()));

        java.util.List<InspectionSlot> result = service.listAvailableForListing(7L);

        assertThat(result).hasSize(1);
        verify(slotRepository).findAvailableForListing(7L);
    }

    private static Listing listingOwnedBy(Long ownerId) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(7L).propertyId(1L).ownerId(ownerId)
                .listingType(ListingType.RENT).askingPrice(new BigDecimal("100.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(now).updatedAt(now).build();
    }
}
