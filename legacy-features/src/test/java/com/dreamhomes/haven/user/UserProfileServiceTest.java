package com.dreamhomes.haven.user;

import com.dreamhomes.haven.admin.UserNotFoundException;
import com.dreamhomes.haven.review.ReviewAggregate;
import com.dreamhomes.haven.review.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock AgentProfileRepository agentProfileRepository;
    @Mock ReviewService reviewService;

    UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, agentProfileRepository, reviewService);
        // Default: no reviews. Individual tests override when they need real numbers.
        org.mockito.Mockito.lenient().when(reviewService.aggregateForUser(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(ReviewAggregate.empty());
    }

    @Test
    void publicProfileForOwnerReturnsBadgeFieldsAndOmitsAgentSpecificData() {
        User owner = User.builder()
                .id(50L).email("o@x").passwordHash("x").fullName("Ada Owner")
                .role(Role.OWNER).tokenVersion(1)
                .identityVerifiedAt(Instant.parse("2026-04-01T10:00:00Z"))
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(owner));

        PublicUserProfile profile = service.findPublicProfile(50L);

        assertThat(profile.id()).isEqualTo(50L);
        assertThat(profile.fullName()).isEqualTo("Ada Owner");
        assertThat(profile.role()).isEqualTo(Role.OWNER);
        assertThat(profile.identityVerifiedAt()).isEqualTo(Instant.parse("2026-04-01T10:00:00Z"));
        assertThat(profile.joinedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(profile.agentCredentialVerifiedAt()).isNull();
        // Agent profile is only loaded when role = AGENT — saves a roundtrip on every public hit.
        verify(agentProfileRepository, never()).findById(50L);
    }

    @Test
    void publicProfileForAgentIncludesCredentialVerifiedAt() {
        User agent = User.builder()
                .id(60L).email("a@x").passwordHash("x").fullName("Bola Agent")
                .role(Role.AGENT).tokenVersion(1)
                .createdAt(Instant.now()).build();
        AgentProfile profile = AgentProfile.builder()
                .userId(60L).licenseNumber("LIC-1")
                .credentialVerifiedAt(Instant.parse("2026-03-01T00:00:00Z"))
                .createdAt(Instant.now())
                .build();
        when(userRepository.findById(60L)).thenReturn(Optional.of(agent));
        when(agentProfileRepository.findById(60L)).thenReturn(Optional.of(profile));

        PublicUserProfile result = service.findPublicProfile(60L);

        assertThat(result.role()).isEqualTo(Role.AGENT);
        assertThat(result.agentCredentialVerifiedAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    void publicProfileNeverLeaksEmailOrPhone() {
        User u = User.builder()
                .id(50L).email("private@example.com").phone("0801-secret")
                .passwordHash("x").fullName("Public Name")
                .role(Role.APPLICANT).tokenVersion(1).createdAt(Instant.now()).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(u));

        PublicUserProfile result = service.findPublicProfile(50L);

        // Compile-time guarantee — record only declares public fields. Runtime sanity:
        // the record's components don't include any private contact data.
        assertThat(result.toString())
                .doesNotContain("private@example.com")
                .doesNotContain("0801-secret");
    }

    @Test
    void unknownUserIdReturns404() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findPublicProfile(404L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void publicProfileSurfacesReviewAggregate() {
        User owner = User.builder()
                .id(50L).email("o@x").passwordHash("x").fullName("Reviewed Owner")
                .role(Role.OWNER).tokenVersion(1).createdAt(Instant.now()).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(owner));
        when(reviewService.aggregateForUser(50L)).thenReturn(new ReviewAggregate(4.5, 12L));

        PublicUserProfile result = service.findPublicProfile(50L);

        assertThat(result.averageRating()).isEqualTo(4.5);
        assertThat(result.reviewCount()).isEqualTo(12L);
    }

    @Test
    void suspendedUserStillExposesPublicProfileButFlagsItSuspended() {
        // Suspending a user blocks them from acting — but their profile is still part of
        // the public record (e.g. an owner with active listings shouldn't disappear from
        // the listing's owner card just because they were suspended). The flag lets the
        // frontend render a muted/gone-fishing state.
        User u = User.builder()
                .id(50L).email("o@x").passwordHash("x").fullName("Suspended Sam")
                .role(Role.OWNER).tokenVersion(1)
                .suspendedAt(Instant.parse("2026-05-01T00:00:00Z"))
                .createdAt(Instant.now())
                .build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(u));

        PublicUserProfile result = service.findPublicProfile(50L);

        assertThat(result.suspended()).isTrue();
    }
}
