package com.dreamhomes.haven.domain.listing.service;

import com.dreamhomes.haven.domain.listing.dto.CreateListingRequest;
import com.dreamhomes.haven.domain.listing.dto.UpdateListingRequest;
import com.dreamhomes.haven.domain.listing.model.Listing;
import com.dreamhomes.haven.domain.listing.repository.ListingRepository;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;

    @Transactional
    public Listing create(CreateListingRequest req) {
        var l = new Listing();
        l.setPropertyId(req.propertyId());
        l.setType(req.type());
        l.setPrice(req.price());
        l.setTitle(req.title());
        return listingRepository.save(l);
    }

    @Transactional(readOnly = true)
    public Listing get(Long id) {
        return listingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
    }

    @Transactional
    public Listing update(Long id, UpdateListingRequest req) {
        var l = get(id);

        if (req.title() != null) {
            l.setTitle(req.title());
        }

        if (req.price() != null) {
            l.setPrice(req.price());
        }

        if (req.status() != null) {
            l.setStatus(req.status());
        }
        
        return listingRepository.save(l);
    }
}

