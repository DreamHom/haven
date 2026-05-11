package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Photo metadata management for listings. Owners can add and delete; the public can
 * read. The {@code url} is a pointer to external object storage — Haven never stores
 * raw bytes (PRD §6).
 *
 * <p>{@code displayOrder} is server-assigned (max+1) on each insert so concurrent
 * uploads on the same listing don't collide on a manually-supplied position.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ListingPhotoService {

    private final ListingPhotoRepository photoRepository;
    private final ListingService listingService;

    @Transactional
    public ListingPhoto add(Long callerId, Long listingId, String url, String caption) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Photo url cannot be empty");
        }
        Long ownerId = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (!ownerId.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        Integer currentMax = photoRepository.findMaxDisplayOrderForListing(listingId);
        int next = currentMax == null ? 1 : currentMax + 1;

        ListingPhoto saved = photoRepository.save(ListingPhoto.builder()
                .listingId(listingId)
                .url(url.trim())
                .displayOrder(next)
                .caption(caption)
                .uploadedAt(Instant.now())
                .build());
        log.info("Owner {} added photoId={} listingId={} order={}",
                callerId, saved.getId(), listingId, next);
        return saved;
    }

    @Transactional
    public void delete(Long callerId, Long photoId) {
        ListingPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ListingPhotoNotFoundException(photoId));
        Long ownerId = listingService.ownerOf(photo.getListingId())
                .orElseThrow(() -> new ListingNotFoundException(photo.getListingId()));
        if (!ownerId.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }
        photoRepository.delete(photo);
        log.info("Owner {} deleted photoId={} from listingId={}",
                callerId, photoId, photo.getListingId());
    }

    @Transactional(readOnly = true)
    public List<ListingPhoto> list(Long listingId) {
        return photoRepository.findByListingIdOrderByDisplayOrderAscIdAsc(listingId);
    }
}
