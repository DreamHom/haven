package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.listing.dto.CreateListingCommand;
import com.dreamhomes.haven.listing.exception.ListingDuplicateOpenForTypeException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;

@ExtendWith(MockitoExtension.class)
class ListingServiceCreateTest {

    @Mock
    PropertyService propertyService;

    @Mock
    ListingRepository listingRepository;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, propertyService, new com.dreamhomes.haven.listing.ListingMapperImpl(), org.mockito.Mockito.mock(com.dreamhomes.haven.agentlisting.AgentListingRepository.class), org.mockito.Mockito.mock(com.dreamhomes.haven.listingreport.ListingReportRepository.class), org.mockito.Mockito.mock(com.dreamhomes.haven.user.repository.UserRepository.class), org.mockito.Mockito.mock(com.dreamhomes.haven.listing.embedding.ListingSearchEmbeddingService.class));
    }

    @Test
    void persistsListingWithDefaultStatusAndCurrency() {
        when(propertyService.ownerOf(7L)).thenReturn(Optional.of(99L));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            l.setId(123L);
            return l;
        });

        Listing result = listingService.create(99L, new CreateListingCommand(
                7L, ListingType.RENT, new BigDecimal("1500000.00"),
                null, null, null, null,
                null, null, null,
                null, null,
                false, null, null, null));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        Listing saved = captor.getValue();
        assertThat(saved.getPropertyId()).isEqualTo(7L);
        assertThat(saved.getOwnerId()).isEqualTo(99L);
        assertThat(saved.getListingType()).isEqualTo(ListingType.RENT);
        assertThat(saved.getAskingPrice()).isEqualByComparingTo("1500000.00");
        assertThat(saved.getCurrency()).isEqualTo("NGN");
        assertThat(saved.getStatus()).isEqualTo(ListingStatus.LIVE);
        // createdAt + updatedAt are populated by JPA auditing on persist (Listing has
        // @CreatedDate + @LastModifiedDate). Auditing behavior is exercised by ListingRepositoryIT.
        assertThat(result.getId()).isEqualTo(123L);
    }

    @Test
    void throwsWhenPropertyDoesNotExist() {
        when(propertyService.ownerOf(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.create(99L, new CreateListingCommand(
                404L, ListingType.RENT, new BigDecimal("1000000.00"),
                null, null, null, null,
                null, null, null,
                null, null,
                false, null, null, null)))
                .isInstanceOf(PropertyNotFoundException.class);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void throwsWhenCallerIsNotThePropertyOwner() {
        when(propertyService.ownerOf(7L)).thenReturn(Optional.of(200L));

        assertThatThrownBy(() -> listingService.create(99L, new CreateListingCommand(
                7L, ListingType.RENT, new BigDecimal("1000000.00"),
                null, null, null, null,
                null, null, null,
                null, null,
                false, null, null, null)))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(listingRepository, never()).save(any());
    }

    /** Item 12 — service-level pre-check before the V47 partial UQ has to refuse. */
    @Test
    void throwsWhenPropertyAlreadyHasLiveListingOfSameType() {
        when(propertyService.ownerOf(7L)).thenReturn(Optional.of(99L));
        when(listingRepository.existsByPropertyIdAndListingTypeAndStatus(
                7L, ListingType.RENT, ListingStatus.LIVE)).thenReturn(true);

        assertThatThrownBy(() -> listingService.create(99L, new CreateListingCommand(
                7L, ListingType.RENT, new BigDecimal("1000000.00"),
                null, null, null, null,
                null, null, null,
                null, null,
                false, null, null, null)))
                .isInstanceOf(ListingDuplicateOpenForTypeException.class);

        verify(listingRepository, never()).save(any());
    }

    /** Item 12 — different listing type on the same property is allowed (RENT + SALE coexist). */
    @Test
    void allowsDifferentListingTypeOnSameProperty() {
        when(propertyService.ownerOf(7L)).thenReturn(Optional.of(99L));
        when(listingRepository.existsByPropertyIdAndListingTypeAndStatus(
                7L, ListingType.SALE, ListingStatus.LIVE)).thenReturn(false);
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            l.setId(124L);
            return l;
        });

        Listing result = listingService.create(99L, new CreateListingCommand(
                7L, ListingType.SALE, new BigDecimal("50000000.00"),
                null, null, null, null,
                null, null, null,
                null, null,
                false, null, null, null));

        assertThat(result.getId()).isEqualTo(124L);
        verify(listingRepository).save(any(Listing.class));
    }

    /** Item 12 — race-safety net: even if the pre-check missed, a DB UQ violation maps to 409. */
    @Test
    void translatesDataIntegrityViolationToDuplicateOpenException() {
        when(propertyService.ownerOf(7L)).thenReturn(Optional.of(99L));
        when(listingRepository.existsByPropertyIdAndListingTypeAndStatus(
                7L, ListingType.RENT, ListingStatus.LIVE)).thenReturn(false);
        when(listingRepository.save(any(Listing.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"listings_one_open_per_type_per_property\""));

        assertThatThrownBy(() -> listingService.create(99L, new CreateListingCommand(
                7L, ListingType.RENT, new BigDecimal("1000000.00"),
                null, null, null, null,
                null, null, null,
                null, null,
                false, null, null, null)))
                .isInstanceOf(ListingDuplicateOpenForTypeException.class);
    }
}
