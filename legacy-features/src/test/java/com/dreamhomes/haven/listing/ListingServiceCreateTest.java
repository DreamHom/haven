package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyNotFoundException;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.PropertyType;
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

@ExtendWith(MockitoExtension.class)
class ListingServiceCreateTest {

    @Mock
    PropertyRepository propertyRepository;

    @Mock
    ListingRepository listingRepository;

    ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(listingRepository, propertyRepository);
    }

    @Test
    void persistsListingWithDefaultStatusAndCurrencyAndStampsTimestamps() {
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(propertyOwnedBy(7L, 99L)));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            l.setId(123L);
            return l;
        });

        Listing result = listingService.create(99L, new CreateListingCommand(
                7L, ListingType.RENT, new BigDecimal("1500000.00"),
                null, null, null, null));

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(captor.capture());
        Listing saved = captor.getValue();
        assertThat(saved.getPropertyId()).isEqualTo(7L);
        assertThat(saved.getOwnerId()).isEqualTo(99L);
        assertThat(saved.getListingType()).isEqualTo(ListingType.RENT);
        assertThat(saved.getAskingPrice()).isEqualByComparingTo("1500000.00");
        assertThat(saved.getCurrency()).isEqualTo("NGN");
        assertThat(saved.getStatus()).isEqualTo(ListingStatus.LIVE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(result.getId()).isEqualTo(123L);
    }

    @Test
    void throwsWhenPropertyDoesNotExist() {
        when(propertyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.create(99L, new CreateListingCommand(
                404L, ListingType.RENT, new BigDecimal("1000000.00"),
                null, null, null, null)))
                .isInstanceOf(PropertyNotFoundException.class);

        verify(listingRepository, never()).save(any());
    }

    @Test
    void throwsWhenCallerIsNotThePropertyOwner() {
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(propertyOwnedBy(7L, 200L)));

        assertThatThrownBy(() -> listingService.create(99L, new CreateListingCommand(
                7L, ListingType.RENT, new BigDecimal("1000000.00"),
                null, null, null, null)))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(listingRepository, never()).save(any());
    }

    private static Property propertyOwnedBy(Long propertyId, Long ownerId) {
        return Property.builder()
                .id(propertyId).ownerId(ownerId).type(PropertyType.HOUSE)
                .address("Some address").bedrooms(3).bathrooms(2)
                .build();
    }
}
