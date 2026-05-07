package com.dreamhomes.haven.domain.property.repository;

import com.dreamhomes.haven.domain.property.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, Long> {}

