package com.dreamhomes.haven.engagement;

import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSaveServiceTest {

    @Mock ListingSaveRepository listingSaveRepository;
    @Mock ListingRepository listingRepository;

    ListingSaveService service;

    @BeforeEach
    void setUp() {
        service = new ListingSaveService(listingSaveRepository, listingRepository);
    }

    @Test
    void savingNewListingPersistsRowWithBothIds() {
        when(listingRepository.existsById(7L)).thenReturn(true);
        when(listingSaveRepository.existsByUserIdAndListingId(50L, 7L)).thenReturn(false);

        service.save(50L, 7L);

        ArgumentCaptor<ListingSave> cap = ArgumentCaptor.forClass(ListingSave.class);
        verify(listingSaveRepository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(50L);
        assertThat(cap.getValue().getListingId()).isEqualTo(7L);
        assertThat(cap.getValue().getSavedAt()).isNotNull();
    }

    @Test
    void savingAlreadySavedListingIsIdempotent() {
        when(listingRepository.existsById(7L)).thenReturn(true);
        when(listingSaveRepository.existsByUserIdAndListingId(50L, 7L)).thenReturn(true);

        service.save(50L, 7L);

        // Re-saving is a no-op — composite PK would block the second insert anyway,
        // but we short-circuit so the wire response is success either way.
        verify(listingSaveRepository, never()).save(any());
    }

    @Test
    void savingNonExistentListingThrows404() {
        when(listingRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.save(50L, 404L))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void unsaveDeletesTheCompositeRow() {
        when(listingSaveRepository.existsByUserIdAndListingId(50L, 7L)).thenReturn(true);

        service.unsave(50L, 7L);

        verify(listingSaveRepository).deleteById(any(ListingSaveId.class));
    }

    @Test
    void unsaveOnUnsavedListingIsIdempotent() {
        when(listingSaveRepository.existsByUserIdAndListingId(50L, 7L)).thenReturn(false);

        service.unsave(50L, 7L);

        verify(listingSaveRepository, never()).deleteById(any(ListingSaveId.class));
    }
}
