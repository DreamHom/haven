package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.AbstractPostgresIT;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.PropertyType;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OfferRepositoryIT extends AbstractPostgresIT {

    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired OfferRepository offerRepository;

    @Test
    void persistsOfferRoundTripThroughTheSchema() {
        User owner = newUser(Role.OWNER);
        User applicant = newUser(Role.APPLICANT);
        Listing listing = newListingFor(owner.getId());

        Offer saved = offerRepository.save(Offer.builder()
                .listingId(listing.getId())
                .applicantId(applicant.getId())
                .ownerId(owner.getId())
                .proposedByUserId(applicant.getId())
                .amount(new BigDecimal("75000000.00"))
                .currency("NGN")
                .message("Cash buyer, can close in 30 days")
                .status(OfferStatus.PENDING)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        Offer found = offerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getListingId()).isEqualTo(listing.getId());
        assertThat(found.getApplicantId()).isEqualTo(applicant.getId());
        assertThat(found.getOwnerId()).isEqualTo(owner.getId());
        assertThat(found.getAmount()).isEqualByComparingTo("75000000.00");
        assertThat(found.getCurrency()).isEqualTo("NGN");
        assertThat(found.getMessage()).isEqualTo("Cash buyer, can close in 30 days");
        assertThat(found.getStatus()).isEqualTo(OfferStatus.PENDING);
    }

    private User newUser(Role role) {
        return userRepository.save(User.builder()
                .email("offer-" + role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(role).fullName("User")
                .tokenVersion(1).createdAt(Instant.now()).build());
    }

    private Listing newListingFor(Long ownerId) {
        Property property = propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("Address").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        return listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }
}
