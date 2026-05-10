package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.support.AbstractPostgresIT;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class InspectionRequestRepositoryIT extends AbstractPostgresIT {

    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired InspectionSlotRepository slotRepository;
    @Autowired InspectionRequestRepository requestRepository;

    @Test
    void persistsRequestRoundTripThroughTheSchema() {
        Long applicantId = newApplicant().getId();
        Long slotId = newSlot().getId();

        InspectionRequest saved = requestRepository.save(request(slotId, applicantId, InspectionRequestStatus.PENDING));

        InspectionRequest found = requestRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSlotId()).isEqualTo(slotId);
        assertThat(found.getApplicantId()).isEqualTo(applicantId);
        assertThat(found.getStatus()).isEqualTo(InspectionRequestStatus.PENDING);
    }

    @Test
    void partialUniqueIndexRejectsSecondActiveRequestForSameSlot() {
        // The whole point of the partial index: at most one PENDING-or-APPROVED request
        // per slot, enforced at the DB. This test proves the index is in place — without
        // it, two concurrent claims would both succeed and we'd have a double-booking bug.
        Long slotId = newSlot().getId();
        Long applicantA = newApplicant().getId();
        Long applicantB = newApplicant().getId();

        requestRepository.saveAndFlush(request(slotId, applicantA, InspectionRequestStatus.PENDING));

        assertThatThrownBy(() -> requestRepository.saveAndFlush(
                request(slotId, applicantB, InspectionRequestStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void declinedRequestFreesTheSlotForAnotherApplicant() {
        Long slotId = newSlot().getId();
        Long applicantA = newApplicant().getId();
        Long applicantB = newApplicant().getId();

        // First request gets declined.
        requestRepository.saveAndFlush(request(slotId, applicantA, InspectionRequestStatus.DECLINED));

        // Second request can now claim the slot — partial index excludes DECLINED rows.
        InspectionRequest second = requestRepository.saveAndFlush(
                request(slotId, applicantB, InspectionRequestStatus.PENDING));

        assertThat(second.getId()).isNotNull();
    }

    private InspectionRequest request(Long slotId, Long applicantId, InspectionRequestStatus status) {
        Instant now = Instant.now();
        return InspectionRequest.builder()
                .slotId(slotId).applicantId(applicantId).status(status)
                .createdAt(now).updatedAt(now).build();
    }

    private InspectionSlot newSlot() {
        Long listingId = newLiveListing().getId();
        return slotRepository.save(InspectionSlot.builder()
                .listingId(listingId)
                .startsAt(Instant.parse("2026-06-01T10:00:00Z"))
                .endsAt(Instant.parse("2026-06-01T11:00:00Z"))
                .createdAt(Instant.now()).build());
    }

    private Listing newLiveListing() {
        User owner = userRepository.save(User.builder()
                .email("owner-reqrepo-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.OWNER).fullName("Owner")
                .tokenVersion(1).createdAt(Instant.now()).build());
        Property property = propertyRepository.save(Property.builder()
                .ownerId(owner.getId()).type(PropertyType.HOUSE)
                .address("Address").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        return listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(owner.getId())
                .listingType(ListingType.RENT).askingPrice(new BigDecimal("100.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    private User newApplicant() {
        return userRepository.save(User.builder()
                .email("applicant-reqrepo-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.APPLICANT).fullName("Applicant")
                .tokenVersion(1).createdAt(Instant.now()).build());
    }
}
