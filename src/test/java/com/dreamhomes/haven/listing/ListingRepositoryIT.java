package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;

@Transactional
class ListingRepositoryIT extends AbstractPostgresIT {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PropertyRepository propertyRepository;

    @Autowired
    ListingRepository listingRepository;

    @Test
    void persistsAllFieldsRoundTripThroughTheSchema() {
        Long ownerId = newOwner("owner-listing-1@example.com").getId();
        Long propertyId = newProperty(ownerId).getId();

        Listing saved = listingRepository.save(Listing.builder()
                .propertyId(propertyId)
                .ownerId(ownerId)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("1500000.00"))
                .currency("NGN")
                .cautionFee(new BigDecimal("3000000.00"))
                .serviceCharge(new BigDecimal("250000.00"))
                .agencyFee(new BigDecimal("150000.00"))
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        Optional<Listing> found = listingRepository.findById(saved.getId());
        assertThat(found).isPresent();
        Listing l = found.get();
        assertThat(l.getPropertyId()).isEqualTo(propertyId);
        assertThat(l.getOwnerId()).isEqualTo(ownerId);
        assertThat(l.getListingType()).isEqualTo(ListingType.RENT);
        assertThat(l.getAskingPrice()).isEqualByComparingTo("1500000.00");
        assertThat(l.getCurrency()).isEqualTo("NGN");
        assertThat(l.getCautionFee()).isEqualByComparingTo("3000000.00");
        assertThat(l.getServiceCharge()).isEqualByComparingTo("250000.00");
        assertThat(l.getAgencyFee()).isEqualByComparingTo("150000.00");
        assertThat(l.getStatus()).isEqualTo(ListingStatus.LIVE);
    }

    @Test
    void findByStatusReturnsOnlyMatchingListings() {
        // We declared findByStatus(status, pageable) — exercising it confirms our
        // method signature wires up correctly to the column.
        Long ownerId = newOwner("owner-listing-status@example.com").getId();
        Long propertyId = newProperty(ownerId).getId();

        listingRepository.save(newListing(propertyId, ownerId, ListingStatus.LIVE));
        listingRepository.save(newListing(propertyId, ownerId, ListingStatus.PAUSED));
        listingRepository.save(newListing(propertyId, ownerId, ListingStatus.LIVE));

        Page<Listing> live = listingRepository.findByStatus(
                ListingStatus.LIVE, PageRequest.of(0, 20, Sort.by("createdAt").descending()));

        assertThat(live.getTotalElements()).isEqualTo(2);
        assertThat(live.getContent()).allMatch(l -> l.getStatus() == ListingStatus.LIVE);
    }

    private User newOwner(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("hash").role(Role.OWNER).fullName("Owner")
                .displayName("Owner")
                .tokenVersion(1).createdAt(Instant.now()).build());
    }

    private Property newProperty(Long ownerId) {
        return propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("Some address").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
    }

    private Listing newListing(Long propertyId, Long ownerId, ListingStatus status) {
        return Listing.builder()
                .propertyId(propertyId).ownerId(ownerId)
                .listingType(ListingType.SALE)
                .askingPrice(new BigDecimal("75000000.00"))
                .currency("NGN")
                .status(status)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }
}
