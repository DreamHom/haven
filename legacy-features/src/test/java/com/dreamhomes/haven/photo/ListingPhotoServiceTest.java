package com.dreamhomes.haven.photo;

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
class ListingPhotoServiceTest {

    @Mock ListingPhotoRepository photoRepository;
    @Mock ListingRepository listingRepository;

    ListingPhotoService service;

    @BeforeEach
    void setUp() {
        service = new ListingPhotoService(photoRepository, listingRepository);
    }

    @Test
    void ownerAddingFirstPhotoStartsDisplayOrderAtOne() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listing(7L, /*ownerId=*/50L)));
        when(photoRepository.findMaxDisplayOrderForListing(7L)).thenReturn(null);
        when(photoRepository.save(any(ListingPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

        ListingPhoto saved = service.add(/*callerId=*/50L, 7L, "https://cdn.example.com/a.jpg", "Front view");

        ArgumentCaptor<ListingPhoto> cap = ArgumentCaptor.forClass(ListingPhoto.class);
        verify(photoRepository).save(cap.capture());
        assertThat(cap.getValue().getListingId()).isEqualTo(7L);
        assertThat(cap.getValue().getUrl()).isEqualTo("https://cdn.example.com/a.jpg");
        assertThat(cap.getValue().getCaption()).isEqualTo("Front view");
        assertThat(cap.getValue().getDisplayOrder()).isEqualTo(1);
        assertThat(saved).isSameAs(cap.getValue());
    }

    @Test
    void subsequentPhotoIncrementsDisplayOrder() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listing(7L, 50L)));
        when(photoRepository.findMaxDisplayOrderForListing(7L)).thenReturn(4);
        when(photoRepository.save(any(ListingPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

        service.add(50L, 7L, "https://cdn.example.com/b.jpg", null);

        ArgumentCaptor<ListingPhoto> cap = ArgumentCaptor.forClass(ListingPhoto.class);
        verify(photoRepository).save(cap.capture());
        assertThat(cap.getValue().getDisplayOrder()).isEqualTo(5);
    }

    @Test
    void nonOwnerCannotAddPhoto() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listing(7L, /*ownerId=*/99L)));

        assertThatThrownBy(() -> service.add(/*callerId=*/50L, 7L, "https://cdn/x.jpg", null))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(photoRepository, never()).save(any());
    }

    @Test
    void addRejectsNonExistentListing() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(50L, 404L, "https://cdn/x.jpg", null))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void addRejectsBlankUrl() {
        assertThatThrownBy(() -> service.add(50L, 7L, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(listingRepository, never()).findById(any());
    }

    @Test
    void ownerCanDeleteTheirPhoto() {
        ListingPhoto photo = ListingPhoto.builder()
                .id(123L).listingId(7L).url("u").displayOrder(1)
                .uploadedAt(Instant.now()).build();
        when(photoRepository.findById(123L)).thenReturn(Optional.of(photo));
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listing(7L, /*ownerId=*/50L)));

        service.delete(/*callerId=*/50L, 123L);

        verify(photoRepository).delete(photo);
    }

    @Test
    void nonOwnerCannotDeletePhoto() {
        ListingPhoto photo = ListingPhoto.builder()
                .id(123L).listingId(7L).url("u").displayOrder(1)
                .uploadedAt(Instant.now()).build();
        when(photoRepository.findById(123L)).thenReturn(Optional.of(photo));
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listing(7L, /*ownerId=*/99L)));

        assertThatThrownBy(() -> service.delete(/*callerId=*/50L, 123L))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(photoRepository, never()).delete(any(ListingPhoto.class));
    }

    @Test
    void deleteRejectsNonExistentPhoto() {
        when(photoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(50L, 404L))
                .isInstanceOf(ListingPhotoNotFoundException.class);
    }

    @Test
    void listForListingReturnsOrderedSequence() {
        ListingPhoto a = ListingPhoto.builder().id(1L).listingId(7L).displayOrder(1).build();
        ListingPhoto b = ListingPhoto.builder().id(2L).listingId(7L).displayOrder(2).build();
        when(photoRepository.findByListingIdOrderByDisplayOrderAscIdAsc(7L))
                .thenReturn(java.util.List.of(a, b));

        var result = service.list(7L);

        assertThat(result).extracting(ListingPhoto::getId).containsExactly(1L, 2L);
    }

    private static Listing listing(Long id, Long ownerId) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(id).propertyId(1L).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(now).updatedAt(now).version(0L).build();
    }
}
