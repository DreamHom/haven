package com.dreamhomes.haven.engagement;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.engagement.model.ListingSave;
import com.dreamhomes.haven.engagement.model.ListingSaveId;

/**
 * Save / unsave a listing for later. Both operations are idempotent — re-saving an
 * already-saved listing or unsaving a never-saved one are no-ops with a 200 response.
 * Idempotency makes the frontend's "toggle saved" UI race-free.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ListingSaveService {

    private final ListingSaveRepository listingSaveRepository;
    private final ListingService listingService;

    @Transactional
    public void save(Long userId, Long listingId) {
        if (!listingService.exists(listingId)) {
            throw new ListingNotFoundException(listingId);
        }
        if (listingSaveRepository.existsByUserIdAndListingId(userId, listingId)) {
            return; // already saved → no-op, 200 OK
        }
        listingSaveRepository.save(ListingSave.builder()
                .userId(userId)
                .listingId(listingId)
                .savedAt(Instant.now())
                .build());
        log.info("User {} saved listing {}", userId, listingId);
    }

    @Transactional
    public void unsave(Long userId, Long listingId) {
        if (!listingSaveRepository.existsByUserIdAndListingId(userId, listingId)) {
            return; // already not saved → no-op
        }
        ListingSaveId pk = new ListingSaveId(userId, listingId);
        listingSaveRepository.deleteById(pk);
        log.info("User {} unsaved listing {}", userId, listingId);
    }

    @Transactional(readOnly = true)
    public Page<ListingSave> listMine(Long userId, Pageable pageable) {
        return listingSaveRepository.findByUserIdOrderBySavedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long countSavers(Long listingId) {
        return listingSaveRepository.countByListingId(listingId);
    }
}
