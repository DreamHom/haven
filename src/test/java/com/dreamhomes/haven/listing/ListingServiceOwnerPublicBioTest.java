package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceOwnerPublicBioTest {

    @Mock
    ListingRepository listingRepository;

    @Mock
    PropertyService propertyService;

    @Mock
    com.dreamhomes.haven.agentlisting.AgentListingRepository agentListingRepository;

    @Mock
    com.dreamhomes.haven.listingreport.ListingReportRepository listingReportRepository;

    @Mock
    com.dreamhomes.haven.user.repository.UserRepository userRepository;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, propertyService,
                new com.dreamhomes.haven.listing.ListingMapperImpl(),
                agentListingRepository, listingReportRepository, userRepository,
                org.mockito.Mockito.mock(com.dreamhomes.haven.listing.embedding.ListingSearchEmbeddingService.class));
    }

    @Test
    void findOwnerPublicBioDelegatesToUserRepository() {
        when(userRepository.findPublicBioByUserId(42L)).thenReturn(Optional.of("  About us  "));

        Optional<String> out = listingService.findOwnerPublicBio(42L);

        assertThat(out).contains("  About us  ");
    }

    @Test
    void findOwnerPublicBioEmptyWhenUnset() {
        when(userRepository.findPublicBioByUserId(7L)).thenReturn(Optional.empty());

        assertThat(listingService.findOwnerPublicBio(7L)).isEmpty();
    }
}
