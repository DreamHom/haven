package com.dreamhomes.haven.property;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.property.model.Property;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    /** Backs {@code GET /api/properties/mine}: owner's portfolio, newest first. */
    Page<Property> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);
}
