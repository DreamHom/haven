package com.dreamhomes.haven.property;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;

@Transactional
class PropertyRepositoryIT extends AbstractPostgresIT {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PropertyRepository propertyRepository;

    @Test
    void persistsAllFieldsRoundTripThroughTheSchema() {
        User owner = userRepository.save(User.builder()
                .email("owner-prop-1@example.com")
                .passwordHash("hash").role(Role.OWNER).fullName("Owner One")
                .tokenVersion(1).createdAt(Instant.now()).build());

        Property saved = propertyRepository.save(Property.builder()
                .ownerId(owner.getId())
                .type(PropertyType.APARTMENT)
                .address("12 Lekki Phase 1, Lagos")
                .bedrooms(3)
                .bathrooms(2)
                .sizeSqm(new BigDecimal("128.50"))
                .description("Top floor, ocean view")
                .createdAt(Instant.now())
                .build());

        assertThat(saved.getId()).isNotNull();

        Optional<Property> found = propertyRepository.findById(saved.getId());
        assertThat(found).isPresent();
        Property p = found.get();
        assertThat(p.getOwnerId()).isEqualTo(owner.getId());
        assertThat(p.getType()).isEqualTo(PropertyType.APARTMENT);
        assertThat(p.getAddress()).isEqualTo("12 Lekki Phase 1, Lagos");
        assertThat(p.getBedrooms()).isEqualTo(3);
        assertThat(p.getBathrooms()).isEqualTo(2);
        assertThat(p.getSizeSqm()).isEqualByComparingTo("128.50");
        assertThat(p.getDescription()).isEqualTo("Top floor, ocean view");
        assertThat(p.getCreatedAt()).isNotNull();
    }
}
