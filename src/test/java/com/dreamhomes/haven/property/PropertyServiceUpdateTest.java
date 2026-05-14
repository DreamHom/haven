package com.dreamhomes.haven.property;

import com.dreamhomes.haven.property.dto.PropertyResponse;
import com.dreamhomes.haven.property.dto.UpdatePropertyCommand;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServiceUpdateTest {

    @Mock
    PropertyRepository propertyRepository;

    PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository, new PropertyMapperImpl());
    }

    @Test
    void ownerCanPatchAddress() {
        Property p = apartment(99L);
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(p));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        PropertyResponse out = propertyService.update(99L, Role.OWNER, 7L,
                new UpdatePropertyCommand("New road 1", null, null, null, null, null, null));

        assertThat(out.address()).isEqualTo("New road 1");
        verify(propertyRepository).save(p);
    }

    @Test
    void nonOwnerNonAdminGetsNotFound() {
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(apartment(200L)));

        assertThatThrownBy(() -> propertyService.update(99L, Role.OWNER, 7L,
                new UpdatePropertyCommand("X", null, null, null, null, null, null)))
                .isInstanceOf(PropertyNotFoundException.class);
    }

    @Test
    void missingPropertyReturnsNotFound() {
        when(propertyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.update(99L, Role.OWNER, 404L,
                new UpdatePropertyCommand("X", null, null, null, null, null, null)))
                .isInstanceOf(PropertyNotFoundException.class);
    }

    @Test
    void adminCanPatchSomeoneElsesProperty() {
        Property p = apartment(200L);
        when(propertyRepository.findById(7L)).thenReturn(Optional.of(p));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        PropertyResponse out = propertyService.update(1L, Role.ADMIN, 7L,
                new UpdatePropertyCommand("Admin fixed typo", null, null, null, null, null, null));

        assertThat(out.address()).isEqualTo("Admin fixed typo");
    }

    private static Property apartment(long ownerId) {
        return Property.builder()
                .id(7L)
                .ownerId(ownerId)
                .type(PropertyType.APARTMENT)
                .address("Old")
                .bedrooms(3)
                .bathrooms(2)
                .sizeSqm(new BigDecimal("100"))
                .description("d")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
