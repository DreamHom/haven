package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.inspection.repository.InspectionRequestRepository;
import com.dreamhomes.haven.inspection.repository.InspectionSlotRepository;

@Transactional
class InspectionSlotRepositoryIT extends AbstractPostgresIT {

    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired InspectionSlotRepository slotRepository;
    @Autowired InspectionRequestRepository requestRepository;

    @Test
    void persistsSlotRoundTripThroughTheSchema() {
        Long listingId = newLiveListing().getId();
        Instant starts = Instant.parse("2026-06-01T10:00:00Z");
        Instant ends = Instant.parse("2026-06-01T11:00:00Z");

        InspectionSlot saved = slotRepository.save(InspectionSlot.builder()
                .listingId(listingId).startsAt(starts).endsAt(ends)
                .createdAt(Instant.now()).build());

        Optional<InspectionSlot> found = slotRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getListingId()).isEqualTo(listingId);
        assertThat(found.get().getStartsAt()).isEqualTo(starts);
        assertThat(found.get().getEndsAt()).isEqualTo(ends);
    }

    @Test
    void rejectsTwoOverlappingSlotsForTheSameListing() {
        // PRD §6: data-layer conflict prevention. Without the GiST EXCLUDE constraint,
        // an owner could publish overlapping slots and let two applicants book the
        // same physical viewing window through different slot ids.
        Long listingId = newLiveListing().getId();
        slotRepository.saveAndFlush(slot(listingId, "2026-06-01T10:00:00Z", "2026-06-01T11:00:00Z"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                slotRepository.saveAndFlush(slot(listingId, "2026-06-01T10:30:00Z", "2026-06-01T11:30:00Z")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameTimeWindowOnDifferentListings() {
        // The constraint is scoped per-listing; two different listings can offer slots
        // at the same wall-clock time.
        Long listingA = newLiveListing().getId();
        Long listingB = newLiveListing().getId();

        slotRepository.saveAndFlush(slot(listingA, "2026-06-01T10:00:00Z", "2026-06-01T11:00:00Z"));
        slotRepository.saveAndFlush(slot(listingB, "2026-06-01T10:00:00Z", "2026-06-01T11:00:00Z"));
        // No exception — both saved cleanly.
    }

    @Test
    void findAvailableForListingHidesSlotsThatHaveAnActiveRequest() {
        Long listingId = newLiveListing().getId();
        Long applicantId = newApplicant().getId();
        InspectionSlot free = slotRepository.save(slot(listingId, "2026-06-01T10:00:00Z", "2026-06-01T11:00:00Z"));
        InspectionSlot pendingClaimed = slotRepository.save(slot(listingId, "2026-06-02T10:00:00Z", "2026-06-02T11:00:00Z"));
        InspectionSlot declined = slotRepository.save(slot(listingId, "2026-06-03T10:00:00Z", "2026-06-03T11:00:00Z"));

        requestRepository.save(request(pendingClaimed.getId(), applicantId, InspectionRequestStatus.PENDING));
        requestRepository.save(request(declined.getId(), applicantId, InspectionRequestStatus.DECLINED));

        List<InspectionSlot> available = slotRepository.findAvailableForListing(listingId);

        assertThat(available).extracting(InspectionSlot::getId)
                .containsExactlyInAnyOrder(free.getId(), declined.getId());
    }

    private InspectionSlot slot(Long listingId, String starts, String ends) {
        return InspectionSlot.builder()
                .listingId(listingId)
                .startsAt(Instant.parse(starts))
                .endsAt(Instant.parse(ends))
                .createdAt(Instant.now()).build();
    }

    private InspectionRequest request(Long slotId, Long applicantId, InspectionRequestStatus status) {
        Instant now = Instant.now();
        return InspectionRequest.builder()
                .slotId(slotId).applicantId(applicantId).status(status)
                .createdAt(now).updatedAt(now).build();
    }

    private Listing newLiveListing() {
        User owner = userRepository.save(User.builder()
                .email("owner-slotrepo-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.OWNER).fullName("Owner")
                .displayName("Owner")
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
                .email("applicant-slotrepo-" + System.nanoTime() + "@example.com")
                .passwordHash("hash").role(Role.APPLICANT).fullName("Applicant")
                .displayName("Applicant")
                .tokenVersion(1).createdAt(Instant.now()).build());
    }
}
