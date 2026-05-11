package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.inspection.dto.CreateSlotCommand;
import com.dreamhomes.haven.inspection.exception.InvalidSlotWindowException;
import com.dreamhomes.haven.inspection.exception.SlotOverlapException;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.inspection.service.InspectionSlotService;
import com.dreamhomes.haven.inspection.repository.InspectionSlotRepository;

@ExtendWith(MockitoExtension.class)
class InspectionSlotServiceTest {

    @Mock ListingService listingService;
    @Mock InspectionSlotRepository slotRepository;

    InspectionSlotService service;

    @BeforeEach
    void setUp() {
        service = new InspectionSlotService(slotRepository, listingService);
    }

    @Test
    void persistsSlotWhenCallerOwnsTheListing() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(99L));
        when(slotRepository.saveAndFlush(any(InspectionSlot.class))).thenAnswer(inv -> {
            InspectionSlot s = inv.getArgument(0);
            s.setId(123L);
            return s;
        });

        InspectionSlot result = service.create(99L, 7L, new CreateSlotCommand(
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z")));

        ArgumentCaptor<InspectionSlot> captor = ArgumentCaptor.forClass(InspectionSlot.class);
        verify(slotRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getListingId()).isEqualTo(7L);
        // createdAt is populated by JPA auditing on persist (InspectionSlot has @CreatedDate);
        // exercised end-to-end in InspectionSlotRepositoryIT.
        assertThat(result.getId()).isEqualTo(123L);
    }

    @Test
    void rejectsWhenCallerDoesNotOwnTheListing() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(200L));

        assertThatThrownBy(() -> service.create(99L, 7L, new CreateSlotCommand(
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"))))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void rejectsWhenListingNotFound() {
        when(listingService.ownerOf(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, 404L, new CreateSlotCommand(
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"))))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void rejectsSlotWhereEndIsNotAfterStart() {
        // Window check fires before any API lookup — fail fast on garbage input.
        assertThatThrownBy(() -> service.create(99L, 7L, new CreateSlotCommand(
                Instant.parse("2026-06-01T11:00:00Z"),
                Instant.parse("2026-06-01T10:00:00Z"))))
                .isInstanceOf(InvalidSlotWindowException.class);

        verify(slotRepository, never()).save(any());
        verify(listingService, never()).ownerOf(any());
    }

    @Test
    void translatesGistExcludeViolationToSlotOverlapException() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(99L));
        when(slotRepository.saveAndFlush(any(InspectionSlot.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("overlap"));

        assertThatThrownBy(() -> service.create(99L, 7L, new CreateSlotCommand(
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T11:00:00Z"))))
                .isInstanceOf(SlotOverlapException.class);
    }

    @Test
    void listAvailableDelegatesToRepository() {
        when(slotRepository.findAvailableForListing(7L))
                .thenReturn(java.util.List.of(InspectionSlot.builder().id(1L).build()));

        java.util.List<InspectionSlot> result = service.listAvailableForListing(7L);

        assertThat(result).hasSize(1);
        verify(slotRepository).findAvailableForListing(7L);
    }
}
