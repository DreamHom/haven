package com.dreamhomes.haven.user;

import com.dreamhomes.haven.agentmarketing.AgentMarketingMediaRepository;
import com.dreamhomes.haven.common.config.CacheConfig;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.review.ReviewService;
import com.dreamhomes.haven.review.dto.ReviewAggregate;
import com.dreamhomes.haven.user.dto.PublicUserProfile;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@code @Cacheable} on {@link UserProfileService#findPublicProfile(Long)} —
 * a hot public read that backs the profile card every listing detail page surfaces.
 */
@SpringJUnitConfig
class UserProfileServiceCacheIT {

    @Autowired UserProfileService service;
    @Autowired UserRepository userRepository;
    @Autowired ReviewService reviewService;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void clearCachesAndMocks() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        reset(userRepository, reviewService);
        lenient().when(reviewService.aggregateForUser(anyLong())).thenReturn(ReviewAggregate.empty());
    }

    @Test
    void repeatedLookupsForSameUserHitRepositoryOnlyOnce() {
        User owner = ownerWithId(50L);
        when(userRepository.findById(50L)).thenReturn(Optional.of(owner));

        for (int i = 0; i < 3; i++) {
            PublicUserProfile profile = service.findPublicProfile(50L);
            assertThat(profile.id()).isEqualTo(50L);
        }

        verify(userRepository, times(1)).findById(50L);
    }

    @Test
    void differentUserIdsCacheIndependently() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(ownerWithId(50L)));
        when(userRepository.findById(60L)).thenReturn(Optional.of(ownerWithId(60L)));

        service.findPublicProfile(50L);
        service.findPublicProfile(60L);
        service.findPublicProfile(50L);

        verify(userRepository, times(1)).findById(50L);
        verify(userRepository, times(1)).findById(60L);
    }

    @Test
    void notFoundLookupsAreNotCachedSoLaterRegistrationsBecomeVisibleImmediately() {
        when(userRepository.findById(404L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ownerWithId(404L)));

        assertThatThrownBy(() -> service.findPublicProfile(404L))
                .isInstanceOf(UserNotFoundException.class);
        PublicUserProfile profile = service.findPublicProfile(404L);
        assertThat(profile.id()).isEqualTo(404L);
    }

    private static User ownerWithId(Long id) {
        return User.builder()
                .id(id).email("u" + id + "@x").passwordHash("x")
                .fullName("Owner " + id).displayName("Owner " + id)
                .role(Role.OWNER).tokenVersion(1)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @Configuration
    @Import(CacheConfig.class)
    static class MockCollaborators {
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean AgentProfileRepository agentProfileRepository() { return mock(AgentProfileRepository.class); }
        @Bean ReviewService reviewService() { return mock(ReviewService.class); }
        @Bean ListingRepository listingRepository() { return mock(ListingRepository.class); }
        @Bean OfferRepository offerRepository() { return mock(OfferRepository.class); }
        @Bean AgentMarketingMediaRepository agentMarketingMediaRepository() {
            AgentMarketingMediaRepository m = mock(AgentMarketingMediaRepository.class);
            lenient().when(m.findByUserIdOrderByDisplayOrderAscIdAsc(anyLong()))
                    .thenReturn(Collections.emptyList());
            return m;
        }
        @Bean UserProfileService userProfileService(UserRepository userRepository,
                                                    AgentProfileRepository agentProfileRepository,
                                                    ReviewService reviewService,
                                                    ListingRepository listingRepository,
                                                    OfferRepository offerRepository,
                                                    AgentMarketingMediaRepository agentMarketingMediaRepository) {
            return new UserProfileService(userRepository, agentProfileRepository, reviewService,
                    listingRepository, offerRepository, agentMarketingMediaRepository);
        }
    }
}
