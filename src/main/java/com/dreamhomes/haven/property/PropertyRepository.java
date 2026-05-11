package com.dreamhomes.haven.property;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.property.model.Property;

public interface PropertyRepository extends JpaRepository<Property, Long> {
}
