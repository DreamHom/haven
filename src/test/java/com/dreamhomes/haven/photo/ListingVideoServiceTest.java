package com.dreamhomes.haven.photo;

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

@ExtendWith(MockitoExtension.class)
class ListingVideoServiceTest {

    @Mock
    ListingVideoRepository videoRepository;
    @Mock
    ListingService listingService;

    ListingVideoService service;

    @BeforeEach
    void setUp() {
        service = new ListingVideoService(videoRepository, listingService);
    }

    @Test
    void ownerAddingFirstVideoStartsDisplayOrderAtOne() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));
        when(videoRepository.findMaxDisplayOrderForListing(7L)).thenReturn(null);
        when(videoRepository.save(any(ListingVideo.class))).thenAnswer(inv -> inv.getArgument(0));

        ListingVideo saved = service.add(50L, 7L, "https://youtu.be/abc123", "Walk-through");

        ArgumentCaptor<ListingVideo> cap = ArgumentCaptor.forClass(ListingVideo.class);
        verify(videoRepository).save(cap.capture());
        assertThat(cap.getValue().getListingId()).isEqualTo(7L);
        assertThat(cap.getValue().getUrl()).isEqualTo("https://youtu.be/abc123");
        assertThat(cap.getValue().getCaption()).isEqualTo("Walk-through");
        assertThat(cap.getValue().getDisplayOrder()).isEqualTo(1);
        assertThat(saved).isSameAs(cap.getValue());
    }

    @Test
    void subsequentVideoIncrementsDisplayOrder() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));
        when(videoRepository.findMaxDisplayOrderForListing(7L)).thenReturn(2);
        when(videoRepository.save(any(ListingVideo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.add(50L, 7L, "https://vimeo.com/x", null);

        ArgumentCaptor<ListingVideo> cap = ArgumentCaptor.forClass(ListingVideo.class);
        verify(videoRepository).save(cap.capture());
        assertThat(cap.getValue().getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void trimsUrlWhitespace() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));
        when(videoRepository.findMaxDisplayOrderForListing(7L)).thenReturn(null);
        when(videoRepository.save(any(ListingVideo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.add(50L, 7L, "  https://example.com/v  ", null);

        ArgumentCaptor<ListingVideo> cap = ArgumentCaptor.forClass(ListingVideo.class);
        verify(videoRepository).save(cap.capture());
        assertThat(cap.getValue().getUrl()).isEqualTo("https://example.com/v");
    }

    @Test
    void nonOwnerCannotAddVideo() {
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(99L));

        assertThatThrownBy(() -> service.add(50L, 7L, "https://youtu.be/x", null))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(videoRepository, never()).save(any());
    }

    @Test
    void addRejectsNonExistentListing() {
        when(listingService.ownerOf(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(50L, 404L, "https://youtu.be/x", null))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void addRejectsBlankUrl() {
        assertThatThrownBy(() -> service.add(50L, 7L, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(listingService, never()).ownerOf(any());
    }

    @Test
    void ownerCanDeleteTheirVideo() {
        ListingVideo row = ListingVideo.builder()
                .id(88L).listingId(7L).url("u").displayOrder(1)
                .uploadedAt(Instant.now()).build();
        when(videoRepository.findById(88L)).thenReturn(Optional.of(row));
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(50L));

        service.delete(50L, 88L);

        verify(videoRepository).delete(row);
    }

    @Test
    void nonOwnerCannotDeleteVideo() {
        ListingVideo row = ListingVideo.builder()
                .id(88L).listingId(7L).url("u").displayOrder(1)
                .uploadedAt(Instant.now()).build();
        when(videoRepository.findById(88L)).thenReturn(Optional.of(row));
        when(listingService.ownerOf(7L)).thenReturn(Optional.of(99L));

        assertThatThrownBy(() -> service.delete(50L, 88L))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(videoRepository, never()).delete(any(ListingVideo.class));
    }

    @Test
    void deleteRejectsNonExistentVideo() {
        when(videoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(50L, 404L))
                .isInstanceOf(ListingVideoNotFoundException.class);
    }

    @Test
    void listReturnsOrderedRows() {
        ListingVideo a = ListingVideo.builder().id(1L).listingId(7L).displayOrder(1).build();
        ListingVideo b = ListingVideo.builder().id(2L).listingId(7L).displayOrder(2).build();
        when(listingService.exists(7L)).thenReturn(true);
        when(videoRepository.findByListingIdOrderByDisplayOrderAscIdAsc(7L))
                .thenReturn(java.util.List.of(a, b));

        var result = service.list(7L);

        assertThat(result).extracting(ListingVideo::getId).containsExactly(1L, 2L);
    }

    @Test
    void listRejectsMissingListing() {
        when(listingService.exists(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.list(404L))
                .isInstanceOf(ListingNotFoundException.class);
    }
}
