package com.dreamhomes.haven.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.property.dto.CreatePropertyCommand;
import com.dreamhomes.haven.property.exception.InvalidPropertyForTypeException;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;

@ExtendWith(MockitoExtension.class)
class PropertyServiceCreateTest {

    @Mock
    PropertyRepository propertyRepository;

    PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository, new com.dreamhomes.haven.property.PropertyMapperImpl());
    }

    @Test
    void persistsPropertyWithCallerAsOwnerAndStampsCreatedAt() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> {
            Property p = inv.getArgument(0);
            p.setId(7L);
            return p;
        });

        Property result = propertyService.create(99L, new CreatePropertyCommand(
                PropertyType.APARTMENT, "12 Lekki Phase 1, Lagos",
                3, 2, new BigDecimal("128.50"), "Top floor, ocean view"));

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        Property persisted = captor.getValue();
        assertThat(persisted.getOwnerId()).isEqualTo(99L);
        assertThat(persisted.getType()).isEqualTo(PropertyType.APARTMENT);
        assertThat(persisted.getAddress()).isEqualTo("12 Lekki Phase 1, Lagos");
        assertThat(persisted.getBedrooms()).isEqualTo(3);
        assertThat(persisted.getBathrooms()).isEqualTo(2);
        // createdAt is populated by JPA auditing on persist (Property has @CreatedDate);
        // not the service's responsibility. PropertyRepositoryIT verifies the persist path.
        assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    void rejectsApartmentMissingBedroomsBeforeCallingSave() {
        assertThatThrownBy(() -> propertyService.create(1L, new CreatePropertyCommand(
                PropertyType.APARTMENT, "Address", null, 2, null, null)))
                .isInstanceOf(InvalidPropertyForTypeException.class);

        verify(propertyRepository, never()).save(any());
    }

    @Test
    void rejectsHouseMissingBathroomsBeforeCallingSave() {
        assertThatThrownBy(() -> propertyService.create(1L, new CreatePropertyCommand(
                PropertyType.HOUSE, "Address", 4, null, null, null)))
                .isInstanceOf(InvalidPropertyForTypeException.class);

        verify(propertyRepository, never()).save(any());
    }

    @Test
    void acceptsLandWithoutRoomCounts() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        Property result = propertyService.create(1L, new CreatePropertyCommand(
                PropertyType.LAND, "5 acres along Lekki-Epe expressway",
                null, null, new BigDecimal("20235.00"), null));

        assertThat(result.getType()).isEqualTo(PropertyType.LAND);
        assertThat(result.getBedrooms()).isNull();
        assertThat(result.getBathrooms()).isNull();
    }

    @Test
    void acceptsCommercialWithoutRoomCounts() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        Property result = propertyService.create(1L, new CreatePropertyCommand(
                PropertyType.COMMERCIAL, "Office tower, Victoria Island",
                null, null, new BigDecimal("500.00"), null));

        assertThat(result.getType()).isEqualTo(PropertyType.COMMERCIAL);
    }
}
