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

@Service
@Slf4j
@RequiredArgsConstructor
public class ListingVideoService {

    private final ListingVideoRepository videoRepository;
    private final ListingService listingService;

    @Transactional
    public ListingVideo add(Long callerId, Long listingId, String url, String caption) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Video url cannot be empty");
        }
        Long ownerId = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (!ownerId.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        Integer currentMax = videoRepository.findMaxDisplayOrderForListing(listingId);
        int next = currentMax == null ? 1 : currentMax + 1;

        ListingVideo saved = videoRepository.save(ListingVideo.builder()
                .listingId(listingId)
                .url(url.trim())
                .displayOrder(next)
                .caption(caption)
                .uploadedAt(Instant.now())
                .build());
        log.info("Owner {} added listingVideoId={} listingId={} order={}",
                callerId, saved.getId(), listingId, next);
        return saved;
    }

    @Transactional
    public void delete(Long callerId, Long videoId) {
        ListingVideo video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ListingVideoNotFoundException(videoId));
        Long ownerId = listingService.ownerOf(video.getListingId())
                .orElseThrow(() -> new ListingNotFoundException(video.getListingId()));
        if (!ownerId.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }
        videoRepository.delete(video);
        log.info("Owner {} deleted listingVideoId={} from listingId={}",
                callerId, videoId, video.getListingId());
    }

    @Transactional(readOnly = true)
    public List<ListingVideo> list(Long listingId) {
        if (!listingService.exists(listingId)) {
            throw new ListingNotFoundException(listingId);
        }
        return videoRepository.findByListingIdOrderByDisplayOrderAscIdAsc(listingId);
    }
}
